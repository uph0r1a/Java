package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * A backup that is locked with a passphrase (TODO 2A.4). A normal backup zip holds customer names, phone numbers and
 * every password hash in clear; copied to a USB stick that is lost, anybody can read it. This class wraps the finished
 * zip in an encrypted file ({@code .lvbak}); the zip itself is unchanged, so restore, inspection and every other
 * backup rule keep working on the decrypted zip.
 *
 * <p>Format (all numbers big-endian):
 * <pre>
 *   "LVBK" (4 bytes) | version = 1 (1 byte) | PBKDF2 iterations (4) | salt (16) | IV prefix (8)
 *   then chunks: length (4) | AES-256-GCM ciphertext of up to 64 KiB plus its 16-byte tag
 * </pre>
 * The key comes from the passphrase with PBKDF2-HMAC-SHA256 (600,000 rounds, the same as the passwords). Every chunk has
 * its own IV (the 8-byte prefix and a 4-byte counter) and the header plus a "last chunk" flag are authenticated with it,
 * so a changed header, a reordered chunk or a file cut short is detected, not restored as a smaller backup.
 */
public final class BackupCrypto {

    /** A passphrase shorter than this is refused: the file is only as strong as the phrase. */
    public static final int MIN_PASSPHRASE_LENGTH = 8;

    static final byte[] MAGIC = {'L', 'V', 'B', 'K'};
    static final int VERSION = 1;
    static final int ITERATIONS = 600_000;
    static final int SALT_LENGTH = 16;
    static final int IV_PREFIX_LENGTH = 8;
    static final int TAG_BITS = 128;
    static final int CHUNK = 64 * 1024;
    /** Header length: magic 4 + version 1 + iterations 4 + salt 16 + IV prefix 8. */
    static final int HEADER_LENGTH = 4 + 1 + 4 + SALT_LENGTH + IV_PREFIX_LENGTH;
    /** Refuse a stored iteration count outside this range (a damaged or hostile header must not ask for hours of work). */
    private static final int MIN_ITERATIONS = 100_000;
    private static final int MAX_ITERATIONS = 5_000_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** The passphrase did not open the file (or the first block is damaged, which looks the same). */
    public static final class WrongPassphraseException extends IOException {
        private static final long serialVersionUID = 1L;
        public WrongPassphraseException() {
            super(I18n.t("backup.crypto.wrong"));
        }
    }

    private BackupCrypto() {
    }

    /** True when the file starts with the marker of an encrypted backup. Never throws. */
    public static boolean isEncrypted(Path file) {
        if (file == null || !Files.isRegularFile(file)) return false;
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(MAGIC.length);
            return Arrays.equals(head, MAGIC);
        } catch (IOException e) {
            return false;
        }
    }

    /** Why this passphrase cannot be used for a new backup, or {@code null} when it is fine. */
    public static String passphraseProblem(char[] passphrase) {
        if (passphrase == null || passphrase.length < MIN_PASSPHRASE_LENGTH) {
            return I18n.t("backup.crypto.short", MIN_PASSPHRASE_LENGTH);
        }
        return null;
    }

    /** Encrypts {@code plain} into {@code out} (overwritten). The plain file is left alone: the caller deletes it. */
    public static void encrypt(Path plain, Path out, char[] passphrase) throws IOException {
        String problem = passphraseProblem(passphrase);
        if (problem != null) throw new IOException(problem);
        byte[] salt = new byte[SALT_LENGTH];
        byte[] ivPrefix = new byte[IV_PREFIX_LENGTH];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(ivPrefix);
        byte[] header = header(ITERATIONS, salt, ivPrefix);
        SecretKey key = deriveKey(passphrase, salt, ITERATIONS);
        try (InputStream in = Files.newInputStream(plain);
             OutputStream raw = Files.newOutputStream(out)) {
            raw.write(header);
            byte[] current = in.readNBytes(CHUNK);
            long counter = 0;
            while (true) {
                // Look one chunk ahead so the last one can be marked as the last.
                byte[] next = current.length < CHUNK ? new byte[0] : in.readNBytes(CHUNK);
                boolean last = next.length == 0;
                byte[] sealed = seal(key, header, ivPrefix, counter, last, current);
                raw.write(ByteBuffer.allocate(4).putInt(sealed.length).array());
                raw.write(sealed);
                if (last) break;
                current = next;
                counter++;
            }
        } catch (GeneralSecurityException e) {
            throw new IOException(I18n.t("backup.crypto.encfail"), e);
        }
    }

    /**
     * Decrypts {@code encrypted} into {@code out} (overwritten).
     *
     * @throws WrongPassphraseException when the passphrase is wrong or the first block is damaged
     * @throws IOException              when the file is not an encrypted backup, is cut short or is damaged
     */
    public static void decrypt(Path encrypted, Path out, char[] passphrase) throws IOException {
        if (passphrase == null || passphrase.length == 0) throw new WrongPassphraseException();
        try (DataInputStream in = new DataInputStream(Files.newInputStream(encrypted));
             OutputStream raw = Files.newOutputStream(out)) {
            byte[] header = new byte[HEADER_LENGTH];
            try {
                in.readFully(header);
            } catch (EOFException e) {
                throw new IOException(I18n.t("backup.crypto.nohead"), e);
            }
            ByteBuffer buffer = ByteBuffer.wrap(header);
            byte[] magic = new byte[MAGIC.length];
            buffer.get(magic);
            if (!Arrays.equals(magic, MAGIC)) throw new IOException(I18n.t("backup.crypto.notours"));
            int version = buffer.get() & 0xFF;
            if (version != VERSION) {
                throw new IOException(I18n.t("backup.crypto.newer"));
            }
            int iterations = buffer.getInt();
            if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
                throw new IOException(I18n.t("backup.crypto.params"));
            }
            byte[] salt = new byte[SALT_LENGTH];
            buffer.get(salt);
            byte[] ivPrefix = new byte[IV_PREFIX_LENGTH];
            buffer.get(ivPrefix);
            SecretKey key = deriveKey(passphrase, salt, iterations);

            long counter = 0;
            int length = readLength(in);
            if (length < 0) throw new IOException(I18n.t("backup.crypto.nodata"));
            while (length >= 0) {
                byte[] sealed = new byte[length];
                in.readFully(sealed);
                int nextLength = readLength(in);
                boolean last = nextLength < 0;
                try {
                    raw.write(open(key, header, ivPrefix, counter, last, sealed));
                } catch (AEADBadTagException e) {
                    if (counter == 0) throw new WrongPassphraseException();
                    throw new IOException(I18n.t("backup.crypto.cut"), e);
                }
                counter++;
                length = nextLength;
            }
        } catch (EOFException e) {
            throw new IOException(I18n.t("backup.crypto.cut"), e);
        } catch (GeneralSecurityException e) {
            throw new IOException(I18n.t("backup.crypto.decfail"), e);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Next chunk length, or -1 at a clean end of file. A length that cannot be a chunk is a damaged file. */
    private static int readLength(DataInputStream in) throws IOException {
        byte[] four = new byte[4];
        int first = in.read();
        if (first < 0) return -1;
        four[0] = (byte) first;
        in.readFully(four, 1, 3);
        int length = ByteBuffer.wrap(four).getInt();
        if (length < TAG_BITS / 8 || length > CHUNK + TAG_BITS / 8) {
            throw new IOException(I18n.t("backup.crypto.badblock"));
        }
        return length;
    }

    private static byte[] header(int iterations, byte[] salt, byte[] ivPrefix) {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH);
        buffer.put(MAGIC).put((byte) VERSION).putInt(iterations).put(salt).put(ivPrefix);
        return buffer.array();
    }

    private static SecretKey deriveKey(char[] passphrase, byte[] salt, int iterations) throws IOException {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, iterations, 256);
        try {
            byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            try {
                return new SecretKeySpec(bytes, "AES");
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        } catch (GeneralSecurityException e) {
            throw new IOException(I18n.t("backup.crypto.unsupported"), e);
        } finally {
            spec.clearPassword();
        }
    }

    private static Cipher cipher(int mode, SecretKey key, byte[] header, byte[] ivPrefix, long counter, boolean last)
            throws GeneralSecurityException {
        byte[] iv = ByteBuffer.allocate(12).put(ivPrefix).putInt((int) counter).array();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(header);
        cipher.updateAAD(new byte[]{(byte) (last ? 1 : 0)});
        return cipher;
    }

    private static byte[] seal(SecretKey key, byte[] header, byte[] ivPrefix, long counter, boolean last, byte[] plain)
            throws GeneralSecurityException {
        return cipher(Cipher.ENCRYPT_MODE, key, header, ivPrefix, counter, last).doFinal(plain);
    }

    private static byte[] open(SecretKey key, byte[] header, byte[] ivPrefix, long counter, boolean last, byte[] sealed)
            throws GeneralSecurityException {
        return cipher(Cipher.DECRYPT_MODE, key, header, ivPrefix, counter, last).doFinal(sealed);
    }
}
