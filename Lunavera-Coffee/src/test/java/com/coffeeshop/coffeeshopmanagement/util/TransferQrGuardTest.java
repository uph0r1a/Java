package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** TODO 2A.17: a swapped transfer QR picture is noticed. */
public class TransferQrGuardTest {

    private Path picture(String content) throws IOException {
        Path dir = Files.createTempDirectory("qrguard");
        Path p = dir.resolve("qr-transfer.png");
        Files.writeString(p, content);
        return p;
    }

    @Test
    public void thePictureIsRecordedThenRecognised() throws IOException {
        Path p = picture("shop qr");
        assertEquals(TransferQrGuard.State.FIRST_SEEN, TransferQrGuard.inspect(p));
        assertEquals(TransferQrGuard.State.OK, TransferQrGuard.inspect(p));
    }

    @Test
    public void aSwappedPictureIsReportedUntilAnAdminAcceptsIt() throws IOException {
        Path p = picture("shop qr");
        TransferQrGuard.inspect(p);
        Files.writeString(p, "someone else's qr");
        assertEquals(TransferQrGuard.State.CHANGED, TransferQrGuard.inspect(p));
        assertEquals("still reported on the next look", TransferQrGuard.State.CHANGED, TransferQrGuard.inspect(p));
        TransferQrGuard.accept(p);
        assertEquals(TransferQrGuard.State.OK, TransferQrGuard.inspect(p));
    }

    @Test
    public void theAccountTextIsShownButLimited() throws IOException {
        Path p = picture("qr");
        assertEquals("", TransferQrGuard.accountInfo(p));
        Files.writeString(p.resolveSibling("qr-transfer.info"), "NGUYEN VAN A\nVietcombank\n");
        assertEquals("NGUYEN VAN A\nVietcombank", TransferQrGuard.accountInfo(p));
        Files.writeString(p.resolveSibling("qr-transfer.info"), "x".repeat(5000));
        assertEquals(TransferQrGuard.MAX_INFO_CHARS, TransferQrGuard.accountInfo(p).length());
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3};
    }

    @Test
    public void anAdminSetsThePictureAndItIsAcceptedWithItsAccountText() throws IOException {
        TestSession.admin();
        try {
            Path src = Files.createTempFile("pic", ".png");
            Files.write(src, png());
            Path folder = Files.createTempDirectory("qrset");
            Files.writeString(folder.resolve("QR-Transfer.JPG"), "old");
            Path stored = TransferQrGuard.replace(folder, src, "Nguyen Van A\nVCB 0123");
            assertEquals("qr-transfer.png", stored.getFileName().toString());
            assertFalse("older picture removed", Files.exists(folder.resolve("QR-Transfer.JPG")));
            assertEquals(TransferQrGuard.State.OK, TransferQrGuard.inspect(stored));
            assertEquals("Nguyen Van A\nVCB 0123", TransferQrGuard.accountInfo(stored));
        } finally {
            TestSession.clear();
        }
    }

    @Test
    public void onlyAdminsAndOnlyRealSmallPicturesAreAccepted() throws IOException {
        Path folder = Files.createTempDirectory("qrbad");
        Path text = Files.createTempFile("pic", ".png");
        Files.writeString(text, "not a picture at all");
        Path big = Files.createTempFile("big", ".png");
        byte[] bytes = new byte[(int) TransferQrGuard.MAX_PICTURE_BYTES + 1];
        System.arraycopy(png(), 0, bytes, 0, 8);
        Files.write(big, bytes);
        assertNotNull(TransferQrGuard.problemWith(text));
        assertNotNull(TransferQrGuard.problemWith(big));
        Path ok = Files.createTempFile("ok", ".png");
        Files.write(ok, png());
        assertNull(TransferQrGuard.problemWith(ok));
        TestSession.clear();
        try {
            TransferQrGuard.replace(folder, ok, "x");
            fail("an employee must not set the QR");
        } catch (com.coffeeshop.coffeeshopmanagement.util.Permissions.PermissionDeniedException expected) {
            // good
        } finally {
            TestSession.clear();
        }
        TestSession.admin();
        try {
            TransferQrGuard.replace(folder, text, "x");
            fail("a non-picture must be refused");
        } catch (IOException expected) {
            assertFalse(Files.exists(folder.resolve("qr-transfer.png")));
        } finally {
            TestSession.clear();
        }
    }
}
