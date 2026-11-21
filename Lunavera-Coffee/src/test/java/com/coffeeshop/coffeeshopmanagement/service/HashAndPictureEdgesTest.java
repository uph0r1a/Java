package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * TODO 6.78, mutation sweep 2026-11-16 (first batch, security classes): the edges that decide whether a stored password hash or the
 * header of an encrypted backup is used at all, the work that is still to be padded after a quick check, and the size limits and
 * sentences of the transfer-QR picture and its account text.
 */
public class HashAndPictureEdgesTest {

    private static final String SALT = "AAAAAAAAAAAAAAAAAAAAAA==";
    private static final String HASH = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Test
    public void theMissingWorkAfterAQuickCheckIsExactAndNeverNegative() {
        assertEquals(PasswordUtil.ITERATIONS, PasswordUtil.paddingIterations(0));
        assertEquals("a refused hash ran nothing", PasswordUtil.ITERATIONS, PasswordUtil.paddingIterations(-5));
        assertEquals(1, PasswordUtil.paddingIterations(PasswordUtil.ITERATIONS - 1));
        assertEquals("today's cost already spent", 0, PasswordUtil.paddingIterations(PasswordUtil.ITERATIONS));
        assertEquals(0, PasswordUtil.paddingIterations(PasswordUtil.ITERATIONS + 1));
    }

    @Test
    public void aStoredHashIsUsedFromOneThousandIterationsAndNotBelow() {
        assertEquals("below the range: refused without work", 0,
                PasswordUtil.check("secret", (PasswordUtil.MIN_ACCEPTED_ITERATIONS - 1) + ":" + SALT + ":" + HASH).iterationsRun());
        PasswordUtil.Check lowest = PasswordUtil.check("secret", PasswordUtil.MIN_ACCEPTED_ITERATIONS + ":" + SALT + ":" + HASH);
        assertEquals("the lowest count is used: that much work was done", PasswordUtil.MIN_ACCEPTED_ITERATIONS, lowest.iterationsRun());
        assertFalse(lowest.ok());
    }

    @Test
    public void aStoredHashIsUsedUpToFiveMillionIterationsAndNotAbove() {
        assertEquals("above the range: refused without work", 0,
                PasswordUtil.check("secret", (PasswordUtil.MAX_ACCEPTED_ITERATIONS + 1) + ":" + SALT + ":" + HASH).iterationsRun());
        assertEquals("the highest count is used (a few seconds of work)", PasswordUtil.MAX_ACCEPTED_ITERATIONS,
                PasswordUtil.check("secret", PasswordUtil.MAX_ACCEPTED_ITERATIONS + ":" + SALT + ":" + HASH).iterationsRun());
    }

    private static Path backupWithIterations(int iterations) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(BackupCrypto.HEADER_LENGTH);
        header.put(BackupCrypto.MAGIC).put((byte) BackupCrypto.VERSION).putInt(iterations)
                .put(new byte[BackupCrypto.SALT_LENGTH]).put(new byte[BackupCrypto.IV_PREFIX_LENGTH]);
        Path file = Files.createTempFile("lvbak-edge", ".lvbak");
        Files.write(file, header.array());
        return file;
    }

    private static String messageOfDecrypting(Path file) throws IOException {
        Path out = Files.createTempFile("lvbak-edge", ".zip");
        try {
            BackupCrypto.decrypt(file, out, "correct horse".toCharArray());
            fail("an empty body cannot be decrypted");
            return null;
        } catch (IOException expected) {
            return expected.getMessage();
        } finally {
            Files.deleteIfExists(out);
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void aBackupHeaderWithTooFewOrTooManyIterationsIsRefusedAtOnce() throws IOException {
        String params = I18n.t("backup.crypto.params");
        assertEquals(params, messageOfDecrypting(backupWithIterations(100_000 - 1)));
        assertEquals(params, messageOfDecrypting(backupWithIterations(5_000_000 + 1)));
    }

    @Test
    public void aBackupHeaderWithTheLowestIterationCountGoesOnToTheBody() throws IOException {
        assertEquals("the header is fine; there is no body", I18n.t("backup.crypto.nodata"), messageOfDecrypting(backupWithIterations(100_000)));
    }

    @Test
    public void aBackupHeaderWithTheHighestIterationCountGoesOnToTheBody() throws IOException {
        assertEquals(I18n.t("backup.crypto.nodata"), messageOfDecrypting(backupWithIterations(5_000_000)));
    }

    private static Path fileOf(Path dir, String name, byte[] content) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, content);
        return file;
    }

    private static byte[] pictureOf(int size, boolean png) {
        byte[] bytes = new byte[size];
        byte[] head = png ? new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A} : new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        System.arraycopy(head, 0, bytes, 0, Math.min(size, head.length));
        return bytes;
    }

    @Test
    public void aPictureIsRefusedWithTheSentenceOfItsOwnProblem() throws IOException {
        Path dir = Files.createTempDirectory("qr-edge");
        String unreadable = I18n.t("image.unreadable");
        String empty = I18n.t("qr.file.empty");
        String toolarge = I18n.t("qr.file.toolarge");
        String type = I18n.t("qr.file.type");
        assertEquals(4, java.util.Set.of(unreadable, empty, toolarge, type).size());
        for (String sentence : new String[]{unreadable, empty, toolarge, type}) assertFalse(sentence.isBlank());

        assertEquals(unreadable, TransferQrGuard.problemWith(null));
        assertEquals("a folder is not a picture", unreadable, TransferQrGuard.problemWith(dir));
        assertEquals(unreadable, TransferQrGuard.problemWith(dir.resolve("not-there.png")));
        assertEquals(empty, TransferQrGuard.problemWith(fileOf(dir, "empty.png", new byte[0])));
        assertEquals("seven bytes cannot hold a picture header", type, TransferQrGuard.problemWith(fileOf(dir, "short.png", new byte[7])));
        assertEquals("not a PNG or a JPEG", type, TransferQrGuard.problemWith(fileOf(dir, "text.png", "hello world, this is text".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    public void aPictureOfExactlyTwoMegabytesIsAllowedAndOneByteMoreIsNot() throws IOException {
        Path dir = Files.createTempDirectory("qr-edge");
        int limit = (int) TransferQrGuard.MAX_PICTURE_BYTES;
        assertNull(TransferQrGuard.problemWith(fileOf(dir, "limit.png", pictureOf(limit, true))));
        assertEquals(I18n.t("qr.file.toolarge"), TransferQrGuard.problemWith(fileOf(dir, "over.png", pictureOf(limit + 1, true))));
        assertNull("eight bytes are enough for the header", TransferQrGuard.problemWith(fileOf(dir, "tiny.png", pictureOf(8, true))));
        assertNull("a JPEG header", TransferQrGuard.problemWith(fileOf(dir, "photo.jpg", pictureOf(100, false))));
    }

    @Test
    public void theAccountTextIsReadUpToTenThousandBytesAndNotMore() throws IOException {
        Path dir = Files.createTempDirectory("qr-edge");
        Path picture = dir.resolve("qr-transfer.png");
        byte[] exactly = new byte[10_000];
        Arrays.fill(exactly, (byte) 'a');
        Files.write(dir.resolve("qr-transfer.info"), exactly);
        assertFalse("ten thousand bytes is still read", TransferQrGuard.accountInfo(picture).isEmpty());
        byte[] more = new byte[10_001];
        Arrays.fill(more, (byte) 'a');
        Files.write(dir.resolve("qr-transfer.info"), more);
        assertEquals("one byte more is not read at all", "", TransferQrGuard.accountInfo(picture));
    }
}
