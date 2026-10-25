package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * TODO 2A.17 / 2A.34: a swapped transfer QR picture is noticed, and the accepted record lives in a store the folder's files
 * cannot reset. These tests use a plain in-memory store; {@link TransferQrGuardDatabaseTest} covers the real one.
 */
public class TransferQrGuardTest {

    /** A store held in memory. */
    static final class MemoryStore implements TransferQrGuard.Store {
        String picture;
        String info;
        boolean failing;

        @Override public Optional<String> picture() { check(); return Optional.ofNullable(picture); }
        @Override public Optional<String> info() { check(); return Optional.ofNullable(info); }
        @Override public void save(String p, String i) { check(); picture = p; info = i; }

        private void check() {
            if (failing) throw new IllegalStateException("database is down");
        }
    }

    private final MemoryStore store = new MemoryStore();

    @After
    public void signOut() {
        TestSession.clear();
    }

    private Path picture(String content) throws IOException {
        Path dir = Files.createTempDirectory("qrguard");
        Path p = dir.resolve("qr-transfer.png");
        Files.writeString(p, content);
        return p;
    }

    @Test
    public void aPictureNobodyAcceptedIsNeverTrustedAndNeverSilentlyRecorded() throws IOException {
        Path p = picture("shop qr");
        assertEquals(TransferQrGuard.State.UNCONFIRMED, TransferQrGuard.inspect(p, store));
        assertEquals("looking does not record it", TransferQrGuard.State.UNCONFIRMED, TransferQrGuard.inspect(p, store));
        assertNull(store.picture);
        assertTrue(TransferQrGuard.State.UNCONFIRMED.needsAttention());
        assertFalse(TransferQrGuard.State.OK.needsAttention());
    }

    @Test
    public void aSwappedPictureIsReportedUntilAnAdminAcceptsIt() throws IOException {
        TestSession.admin();
        Path p = picture("shop qr");
        TransferQrGuard.accept(p, store);
        assertEquals(TransferQrGuard.State.OK, TransferQrGuard.inspect(p, store));
        Files.writeString(p, "someone else's qr");
        assertEquals(TransferQrGuard.State.CHANGED, TransferQrGuard.inspect(p, store));
        assertEquals("still reported on the next look", TransferQrGuard.State.CHANGED, TransferQrGuard.inspect(p, store));
        TransferQrGuard.accept(p, store);
        assertEquals(TransferQrGuard.State.OK, TransferQrGuard.inspect(p, store));
    }

    @Test
    public void writingOrDeletingFilesBesideThePictureCannotResetTheCheck() throws IOException {
        // TODO 2A.34: the old attack, now that the record is not a file in the folder.
        TestSession.admin();
        Path p = picture("shop qr");
        TransferQrGuard.accept(p, store);
        Files.writeString(p, "someone else's qr");
        Files.writeString(p.resolveSibling("qr-transfer.sha256"), TransferQrGuard.fingerprint(p)); // the new picture's hash
        assertEquals(TransferQrGuard.State.CHANGED, TransferQrGuard.inspect(p, store));
        Files.deleteIfExists(p.resolveSibling("qr-transfer.sha256"));
        assertEquals(TransferQrGuard.State.CHANGED, TransferQrGuard.inspect(p, store));
    }

    @Test
    public void aChangedAccountTextIsReportedToo() throws IOException {
        TestSession.admin();
        Path p = picture("qr");
        Files.writeString(p.resolveSibling("qr-transfer.info"), "NGUYEN VAN A\nVietcombank");
        TransferQrGuard.accept(p, store);
        assertEquals(TransferQrGuard.State.OK, TransferQrGuard.inspect(p, store));
        Files.writeString(p.resolveSibling("qr-transfer.info"), "SOMEONE ELSE\nOther bank");
        assertEquals(TransferQrGuard.State.CHANGED, TransferQrGuard.inspect(p, store));
        Files.delete(p.resolveSibling("qr-transfer.info"));
        assertEquals("removing the text is a change too", TransferQrGuard.State.CHANGED, TransferQrGuard.inspect(p, store));
    }

    @Test
    public void aRecordThatCannotBeReadOrAPictureThatCannotBeReadIsNotTrusted() throws IOException {
        TestSession.admin();
        Path p = picture("shop qr");
        TransferQrGuard.accept(p, store);
        store.failing = true;
        assertEquals(TransferQrGuard.State.UNVERIFIABLE, TransferQrGuard.inspect(p, store));
        store.failing = false;
        Files.delete(p);
        assertEquals(TransferQrGuard.State.UNVERIFIABLE, TransferQrGuard.inspect(p, store));
        try {
            TransferQrGuard.accept(p, store);
            fail("an unreadable picture cannot be accepted");
        } catch (IOException expected) {
            // good
        }
    }

    @Test
    public void onlyAnAdminCanAcceptAPicture() throws IOException {
        Path p = picture("shop qr");
        TestSession.clear();
        try {
            TransferQrGuard.accept(p, store);
            fail("an employee must not accept the picture");
        } catch (Permissions.PermissionDeniedException expected) {
            assertNull(store.picture);
        }
    }

    @Test
    public void theRecordOfTheOldVersionIsTakenOverOnceWhenItStillMatches() throws IOException {
        Path p = picture("shop qr");
        Path legacy = p.resolveSibling("qr-transfer.sha256");
        Files.writeString(legacy, TransferQrGuard.fingerprint(p));
        assertEquals(TransferQrGuard.State.OK, TransferQrGuard.inspect(p, store));
        assertEquals(TransferQrGuard.fingerprint(p), store.picture);
        assertFalse("the old file is gone, so deleting it later changes nothing", Files.exists(legacy));
        Files.writeString(p, "someone else's qr");
        assertEquals(TransferQrGuard.State.CHANGED, TransferQrGuard.inspect(p, store));
    }

    @Test
    public void anOldRecordThatDoesNotMatchIsNotTakenOver() throws IOException {
        Path p = picture("shop qr");
        Files.writeString(p.resolveSibling("qr-transfer.sha256"), "0".repeat(64));
        assertEquals(TransferQrGuard.State.UNCONFIRMED, TransferQrGuard.inspect(p, store));
        assertNull(store.picture);
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
        Path src = Files.createTempFile("pic", ".png");
        Files.write(src, png());
        Path folder = Files.createTempDirectory("qrset");
        Files.writeString(folder.resolve("QR-Transfer.JPG"), "old");
        Files.writeString(folder.resolve("qr-transfer.sha256"), "left over from the old version");
        Path stored = TransferQrGuard.replace(folder, src, "Nguyen Van A\nVCB 0123", store);
        assertEquals("qr-transfer.png", stored.getFileName().toString());
        assertFalse("older picture removed", Files.exists(folder.resolve("QR-Transfer.JPG")));
        assertFalse("old-style record removed", Files.exists(folder.resolve("qr-transfer.sha256")));
        assertEquals(TransferQrGuard.State.OK, TransferQrGuard.inspect(stored, store));
        assertEquals("Nguyen Van A\nVCB 0123", TransferQrGuard.accountInfo(stored));
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
            TransferQrGuard.replace(folder, ok, "x", store);
            fail("an employee must not set the QR");
        } catch (Permissions.PermissionDeniedException expected) {
            // good
        }
        TestSession.admin();
        try {
            TransferQrGuard.replace(folder, text, "x", store);
            fail("a non-picture must be refused");
        } catch (IOException expected) {
            assertFalse(Files.exists(folder.resolve("qr-transfer.png")));
        }
    }

    /** TODO 5.32: a typo in the holder's name is fixed without choosing the picture again. */
    @Test
    public void anAdminCanChangeJustTheAccountTextAndThePictureStaysAccepted() throws IOException {
        TestSession.admin();
        Path src = Files.createTempFile("pic", ".png");
        Files.write(src, png());
        Path folder = Files.createTempDirectory("qrtextonly");
        Path stored = TransferQrGuard.replace(folder, src, "Nguyen Van A\nVCB 0123", store);
        byte[] pictureBefore = Files.readAllBytes(stored);

        TransferQrGuard.replaceInfo(folder, "  Nguyen Van B\r\nVCB 0123  ", store);

        assertArrayEquals("the picture is not touched", pictureBefore, Files.readAllBytes(stored));
        assertEquals("Nguyen Van B\nVCB 0123", TransferQrGuard.accountInfo(stored));
        assertEquals("the new text is accepted with the old picture", TransferQrGuard.State.OK, TransferQrGuard.inspect(stored, store));

        TransferQrGuard.replaceInfo(folder, "", store);
        assertEquals("", TransferQrGuard.accountInfo(stored));
        assertEquals(TransferQrGuard.State.OK, TransferQrGuard.inspect(stored, store));
    }

    /** The shortcut must not become a way to accept a picture somebody put in the folder by hand. */
    @Test
    public void changingOnlyTheTextIsRefusedWhenThePictureIsNotTheAcceptedOne() throws IOException {
        TestSession.admin();
        Path src = Files.createTempFile("pic", ".png");
        Files.write(src, png());
        Path folder = Files.createTempDirectory("qrtextswap");
        Path stored = TransferQrGuard.replace(folder, src, "Nguyen Van A", store);
        Files.writeString(stored, "somebody else's QR");
        try {
            TransferQrGuard.replaceInfo(folder, "Evil Co", store);
            fail("a swapped picture must not be accepted through the text-only button");
        } catch (IOException expected) {
            assertEquals("the text was not written", "Nguyen Van A", TransferQrGuard.accountInfo(stored));
        }
        assertEquals(TransferQrGuard.State.CHANGED, TransferQrGuard.inspect(stored, store));

        // a picture nobody accepted yet is refused as well, and so is a folder with no picture at all
        MemoryStore empty = new MemoryStore();
        try {
            TransferQrGuard.replaceInfo(folder, "x", empty);
            fail("an unaccepted picture must be refused");
        } catch (IOException expected) {
            assertNull(empty.picture);
        }
        try {
            TransferQrGuard.replaceInfo(Files.createTempDirectory("qrnone"), "x", store);
            fail("no picture, nothing to keep");
        } catch (IOException expected) {
            // good
        }
    }

    @Test
    public void changingOnlyTheTextIsForAdminsOnly() throws IOException {
        TestSession.admin();
        Path src = Files.createTempFile("pic", ".png");
        Files.write(src, png());
        Path folder = Files.createTempDirectory("qrtextrole");
        Path stored = TransferQrGuard.replace(folder, src, "Nguyen Van A", store);
        TestSession.clear();
        try {
            TransferQrGuard.replaceInfo(folder, "Evil Co", store);
            fail("an employee must not change the account text");
        } catch (Permissions.PermissionDeniedException expected) {
            assertEquals("Nguyen Van A", TransferQrGuard.accountInfo(stored));
        }
    }
}
