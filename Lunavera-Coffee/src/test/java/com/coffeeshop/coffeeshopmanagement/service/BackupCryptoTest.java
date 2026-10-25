package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 2A.4: a backup locked with a passphrase. No database needed: the class works on any file, so the tests use random
 * bytes standing in for a zip.
 */
public class BackupCryptoTest {

    private static final char[] PASSPHRASE = "mật khẩu sao lưu 123".toCharArray();
    private Path folder;

    @Before
    public void scratch() throws IOException {
        folder = Files.createTempDirectory("lunavera-crypto-test");
    }

    @After
    public void clean() throws IOException {
        try (Stream<Path> files = Files.walk(folder)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    private Path plainFile(int size) throws IOException {
        byte[] data = new byte[size];
        new SecureRandom().nextBytes(data);
        Path file = folder.resolve("plain-" + size + ".zip");
        Files.write(file, data);
        return file;
    }

    private Path lock(Path plain) throws IOException {
        Path out = folder.resolve("locked-" + plain.getFileName() + ".lvbak");
        BackupCrypto.encrypt(plain, out, PASSPHRASE);
        return out;
    }

    private void assertRoundTrip(int size) throws IOException {
        Path plain = plainFile(size);
        Path locked = lock(plain);
        Path back = folder.resolve("back-" + size);
        BackupCrypto.decrypt(locked, back, PASSPHRASE);
        assertArrayEquals("the decrypted file is the original", Files.readAllBytes(plain), Files.readAllBytes(back));
    }

    @Test
    public void aSmallFileComesBackUnchanged() throws IOException {
        assertRoundTrip(1_000);
    }

    @Test
    public void anEmptyFileComesBackUnchanged() throws IOException {
        assertRoundTrip(0);
    }

    @Test
    public void aFileOfExactlyTwoChunksComesBackUnchanged() throws IOException {
        assertRoundTrip(2 * BackupCrypto.CHUNK);
    }

    @Test
    public void aFileOfSeveralChunksAndAPartComesBackUnchanged() throws IOException {
        assertRoundTrip(3 * BackupCrypto.CHUNK + 12_345);
    }

    @Test
    public void theLockedFileIsMarkedAndAZipIsNot() throws IOException {
        Path plain = plainFile(500);
        Path locked = lock(plain);
        assertTrue(BackupCrypto.isEncrypted(locked));
        assertFalse(BackupCrypto.isEncrypted(plain));
        assertFalse(BackupCrypto.isEncrypted(folder.resolve("does-not-exist")));
        assertFalse(BackupCrypto.isEncrypted(null));
    }

    @Test
    public void theLockedFileDoesNotContainTheText() throws IOException {
        String secret = "Nguyen Van A 0901234567 pbkdf2$600000$";
        Path plain = folder.resolve("text.zip");
        Files.writeString(plain, secret.repeat(50), StandardCharsets.UTF_8);
        byte[] locked = Files.readAllBytes(lock(plain));
        assertEquals("the phone number is not readable in the locked file", -1, indexOf(locked, "0901234567".getBytes(StandardCharsets.UTF_8)));
        assertEquals(-1, indexOf(locked, "Nguyen".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void lockingTheSameFileTwiceGivesDifferentFiles() throws IOException {
        Path plain = plainFile(2_000);
        byte[] first = Files.readAllBytes(lock(plain));
        Files.delete(folder.resolve("locked-" + plain.getFileName() + ".lvbak"));
        byte[] second = Files.readAllBytes(lock(plain));
        assertFalse("a new random salt and IV every time", Arrays.equals(first, second));
    }

    @Test
    public void aWrongPassphraseIsRefusedWithItsOwnMessage() throws IOException {
        Path locked = lock(plainFile(3_000));
        try {
            BackupCrypto.decrypt(locked, folder.resolve("x"), "not the phrase".toCharArray());
            fail("a wrong passphrase must not open the file");
        } catch (BackupCrypto.WrongPassphraseException expected) {
            assertTrue(expected.getMessage().contains("Mật khẩu"));
        }
    }

    @Test
    public void anEmptyPassphraseIsRefusedWhenOpening() throws IOException {
        Path locked = lock(plainFile(3_000));
        assertThrows(BackupCrypto.WrongPassphraseException.class,
                () -> BackupCrypto.decrypt(locked, folder.resolve("x"), new char[0]));
        assertThrows(BackupCrypto.WrongPassphraseException.class,
                () -> BackupCrypto.decrypt(locked, folder.resolve("x"), null));
    }

    @Test
    public void aShortPassphraseIsRefusedWhenLocking() throws IOException {
        Path plain = plainFile(100);
        IOException e = assertThrows(IOException.class,
                () -> BackupCrypto.encrypt(plain, folder.resolve("o.lvbak"), "short".toCharArray()));
        assertTrue(e.getMessage().contains(String.valueOf(BackupCrypto.MIN_PASSPHRASE_LENGTH)));
        assertNotNull(BackupCrypto.passphraseProblem(null));
        assertNull(BackupCrypto.passphraseProblem("12345678".toCharArray()));
    }

    @Test
    public void aChangedByteInTheHeaderIsNotRestored() throws IOException {
        Path locked = lock(plainFile(3_000));
        byte[] bytes = Files.readAllBytes(locked);
        bytes[10] ^= 0x01; // inside the salt
        Path damaged = folder.resolve("damaged-header.lvbak");
        Files.write(damaged, bytes);
        assertThrows(IOException.class, () -> BackupCrypto.decrypt(damaged, folder.resolve("x"), PASSPHRASE));
    }

    @Test
    public void aChangedByteInALaterBlockIsReportedAsDamageNotAsAWrongPassphrase() throws IOException {
        Path locked = lock(plainFile(3 * BackupCrypto.CHUNK));
        byte[] bytes = Files.readAllBytes(locked);
        bytes[bytes.length - 40] ^= 0x01; // inside the last block
        Path damaged = folder.resolve("damaged-body.lvbak");
        Files.write(damaged, bytes);
        IOException e = assertThrows(IOException.class, () -> BackupCrypto.decrypt(damaged, folder.resolve("x"), PASSPHRASE));
        assertFalse(e instanceof BackupCrypto.WrongPassphraseException);
    }

    @Test
    public void aFileCutShortIsNotRestoredAsASmallerBackup() throws IOException {
        Path locked = lock(plainFile(3 * BackupCrypto.CHUNK));
        byte[] bytes = Files.readAllBytes(locked);
        // cut in the middle of the last block
        Path midBlock = folder.resolve("cut-mid.lvbak");
        Files.write(midBlock, Arrays.copyOf(bytes, bytes.length - 100));
        assertThrows(IOException.class, () -> BackupCrypto.decrypt(midBlock, folder.resolve("x"), PASSPHRASE));
        // cut exactly after a whole block: the file looks complete, but its last block was not marked as the last
        int firstBlock = BackupCrypto.HEADER_LENGTH + 4 + BackupCrypto.CHUNK + 16;
        Path atBoundary = folder.resolve("cut-boundary.lvbak");
        Files.write(atBoundary, Arrays.copyOf(bytes, firstBlock + 4 + BackupCrypto.CHUNK + 16));
        assertThrows(IOException.class, () -> BackupCrypto.decrypt(atBoundary, folder.resolve("x"), PASSPHRASE));
    }

    @Test
    public void anotherKindOfFileIsRefusedWithAClearMessage() throws IOException {
        Path notOurs = plainFile(500);
        IOException e = assertThrows(IOException.class, () -> BackupCrypto.decrypt(notOurs, folder.resolve("x"), PASSPHRASE));
        assertTrue(e.getMessage().contains("không phải"));
        Path tiny = folder.resolve("tiny.lvbak");
        Files.write(tiny, new byte[]{'L', 'V', 'B'});
        assertThrows(IOException.class, () -> BackupCrypto.decrypt(tiny, folder.resolve("x"), PASSPHRASE));
    }

    @Test
    public void aHeaderAskingForAbsurdWorkIsRefusedAtOnce() throws IOException {
        Path locked = lock(plainFile(500));
        byte[] bytes = Files.readAllBytes(locked);
        bytes[5] = 0x7F; // the iteration count becomes about two billion
        Path hostile = folder.resolve("hostile.lvbak");
        Files.write(hostile, bytes);
        long started = System.nanoTime();
        assertThrows(IOException.class, () -> BackupCrypto.decrypt(hostile, folder.resolve("x"), PASSPHRASE));
        assertTrue("it must not start the long key derivation", (System.nanoTime() - started) / 1_000_000L < 2_000L);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
