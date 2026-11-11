package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/** TODO 4.26: choosing a picture only previews it; nothing is stored, checked as stored, or logged until Save. */
public class TransferQrChangeTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @After
    public void signOut() {
        TestSession.clear();
    }

    private static byte[] png(int extra) {
        byte[] bytes = new byte[11 + extra];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, 3}, 0, bytes, 0, 11);
        return bytes;
    }

    private static Path picture(Path dir, String name, byte[] bytes) throws IOException {
        Path p = dir.resolve(name);
        Files.write(p, bytes);
        return p;
    }

    private static List<String> filesIn(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    // ---- choosing ----

    @Test
    public void aUsablePictureIsTakenAndShownInPlaceOfTheStoredOne() throws IOException {
        Path dir = Files.createTempDirectory("qrchange");
        Path stored = picture(dir, "qr-transfer.png", png(1));
        Path fresh = picture(Files.createTempDirectory("qrnew"), "new.png", png(5));
        TransferQrChange change = new TransferQrChange(stored, "Old holder");
        assertEquals("the stored one is shown first", stored, change.pictureToShow());
        assertNull(change.choose(fresh));
        assertTrue(change.hasNewPicture());
        assertEquals(fresh, change.pictureToShow());
        assertFalse(change.isTextOnly());
    }

    @Test
    public void aPictureThatCannotBeUsedIsRefusedWithAReasonAndTheEarlierChoiceStays() throws IOException {
        Path dir = Files.createTempDirectory("qrbad");
        Path good = picture(dir, "good.png", png(2));
        TransferQrChange change = new TransferQrChange(null, "");
        assertNull(change.choose(good));

        assertNotNull("an empty file", change.choose(picture(dir, "empty.png", new byte[0])));
        assertNotNull("a text file", change.choose(picture(dir, "note.png", "not a picture at all".getBytes())));
        assertNotNull("too big", change.choose(picture(dir, "huge.png", png((int) TransferQrGuard.MAX_PICTURE_BYTES))));
        assertNotNull("a missing file", change.choose(dir.resolve("missing.png")));
        assertNotNull(change.choose(null));
        assertEquals("the good one is still the choice", good, change.newPicture());
    }

    // ---- Save may be pressed ----

    @Test
    public void saveIsOnlyOfferedWhenSomethingWouldChange() throws IOException {
        Path stored = picture(Files.createTempDirectory("qrsave"), "qr-transfer.png", png(1));
        TransferQrChange change = new TransferQrChange(stored, "NGUYEN VAN A\nVCB 123");
        assertFalse("nothing changed", change.canSave("NGUYEN VAN A\nVCB 123"));
        assertFalse("only a stray space and a carriage return", change.canSave("  NGUYEN VAN A\r\nVCB 123 "));
        assertTrue("the text changed", change.canSave("NGUYEN VAN A\nTCB 999"));
        assertTrue(change.isTextOnly());
    }

    @Test
    public void aTextAloneHasNothingToBelongToWhenThereIsNoPicture() throws IOException {
        TransferQrChange change = new TransferQrChange(null, "");
        assertFalse(change.canSave("Somebody"));
        Path fresh = picture(Files.createTempDirectory("qrfresh"), "new.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0, 0});
        assertNull(change.choose(fresh));
        assertTrue("a new picture can always be saved", change.canSave(""));
    }

    // ---- nothing is stored before Save ----

    @Test
    public void choosingStoresNothing() throws IOException {
        Path data = Files.createTempDirectory("qrdata");
        Path fresh = picture(Files.createTempDirectory("qrsrc"), "new.png", png(3));
        TransferQrChange change = new TransferQrChange(null, "");
        assertNull(change.choose(fresh));
        change.canSave("text");
        assertEquals("the data folder is untouched", List.of(), filesIn(data));
    }

    // ---- Save ----

    @Test
    public void savingANewPictureStoresItWithItsTextAndTheGuardAcceptsIt() throws IOException {
        TestSession.admin();
        Path data = Files.createTempDirectory("qrapply");
        Path fresh = picture(Files.createTempDirectory("qrsrc"), "from-zalo.png", png(7));
        TransferQrChange change = new TransferQrChange(null, "");
        assertNull(change.choose(fresh));
        change.apply(data, "TRAN THI B\nACB 555");

        Path stored = data.resolve("qr-transfer.png");
        assertArrayEquals(Files.readAllBytes(fresh), Files.readAllBytes(stored));
        assertEquals("TRAN THI B\nACB 555", TransferQrGuard.accountInfo(stored));
        assertEquals("it is the accepted picture, not an unconfirmed one", TransferQrGuard.State.OK, TransferQrGuard.inspect(stored));
    }

    @Test
    public void savingTheTextAloneKeepsThePictureAndKeepsItAccepted() throws IOException {
        TestSession.admin();
        Path data = Files.createTempDirectory("qrtext");
        Path fresh = picture(Files.createTempDirectory("qrsrc"), "a.png", png(4));
        TransferQrChange first = new TransferQrChange(null, "");
        first.choose(fresh);
        first.apply(data, "Old holder");
        Path stored = data.resolve("qr-transfer.png");
        byte[] before = Files.readAllBytes(stored);

        TransferQrChange second = new TransferQrChange(stored, TransferQrGuard.accountInfo(stored));
        assertTrue(second.canSave("New holder\nNew bank"));
        assertTrue(second.isTextOnly());
        second.apply(data, "New holder\nNew bank");
        assertArrayEquals("the picture is the same file", before, Files.readAllBytes(stored));
        assertEquals("New holder\nNew bank", TransferQrGuard.accountInfo(stored));
        assertEquals(TransferQrGuard.State.OK, TransferQrGuard.inspect(stored));
    }

    @Test
    public void aCashierCannotSaveAndNothingIsStored() throws IOException {
        User u = new User();
        u.setId(2);
        u.setUsername("test-cashier");
        u.setRole(Role.EMPLOYEE);
        Session.start(u, null);
        Path data = Files.createTempDirectory("qrcashier");
        Path fresh = picture(Files.createTempDirectory("qrsrc"), "x.png", png(2));
        TransferQrChange change = new TransferQrChange(null, "");
        assertNull(change.choose(fresh));
        assertThrows(Permissions.PermissionDeniedException.class, () -> change.apply(data, "Someone else's account"));
        assertEquals(List.of(), filesIn(data));
    }

    @Test
    public void savingTheTextWithNoStoredPictureSaysSoInsteadOfInventingOne() throws IOException {
        TestSession.admin();
        Path data = Files.createTempDirectory("qrnopic");
        TransferQrChange change = new TransferQrChange(null, "");
        IOException refused = assertThrows(IOException.class, () -> change.apply(data, "Text only"));
        assertEquals(I18n.t("admin.qr.textonly.nopicture"), refused.getMessage());
        assertEquals(List.of(), filesIn(data));
    }
}
