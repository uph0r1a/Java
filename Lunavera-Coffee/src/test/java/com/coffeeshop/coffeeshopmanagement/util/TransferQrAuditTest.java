package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * TODO 2A.44: every change of the bank-transfer QR hands the store what the audit line says, so the real store can write the line
 * in the same transaction as the fingerprints. These tests use a store held in memory; {@link TransferQrGuardDatabaseTest} covers
 * the real one.
 */
public class TransferQrAuditTest {

    /** A store that keeps what it was told about each change. */
    static final class RecordingStore implements TransferQrGuard.Store {
        String picture;
        String info;
        final List<TransferQrGuard.Change> changes = new ArrayList<>();

        @Override public Optional<String> picture() { return Optional.ofNullable(picture); }
        @Override public Optional<String> info() { return Optional.ofNullable(info); }
        @Override public void save(String p, String i) { picture = p; info = i; }
        @Override public void save(String p, String i, TransferQrGuard.Change change) { save(p, i); changes.add(change); }
    }

    private final RecordingStore store = new RecordingStore();

    @After
    public void signOut() {
        TestSession.clear();
    }

    private static Path picture(String content) throws IOException {
        Path dir = Files.createTempDirectory("qraudit");
        Path p = dir.resolve("qr-transfer.png");
        Files.writeString(p, content);
        return p;
    }

    /** A file the guard takes for a PNG (its first bytes), different for each {@code tag}. */
    private static Path png(int tag) throws IOException {
        Path p = Files.createTempFile("pic", ".png");
        Files.write(p, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1, 2, (byte) tag});
        return p;
    }

    @Test
    public void acceptingThePictureIsReportedWithNoTexts() throws IOException {
        TestSession.admin();
        TransferQrGuard.accept(picture("shop qr"), store);
        assertEquals(1, store.changes.size());
        assertEquals(new TransferQrGuard.Change(TransferQrGuard.ACCEPTED, null, null), store.changes.get(0));
    }

    @Test
    public void choosingAPictureAgainReportsTheOldAndTheNewAccountText() throws IOException {
        TestSession.admin();
        Path folder = Files.createTempDirectory("qrfolder");
        Path first = png(1);
        TransferQrGuard.replace(folder, first, "Nguyen A\nVCB 0123", store);
        Path second = png(2);
        TransferQrGuard.replace(folder, second, "Evil Co", store);
        assertEquals(2, store.changes.size());
        assertEquals(new TransferQrGuard.Change(TransferQrGuard.PICTURE, "", "Nguyen A\nVCB 0123"), store.changes.get(0));
        assertEquals("the second change knows what the first one stored",
                new TransferQrGuard.Change(TransferQrGuard.PICTURE, "Nguyen A\nVCB 0123", "Evil Co"), store.changes.get(1));
    }

    @Test
    public void changingOnlyTheTextReportsTheOldAndTheNewText() throws IOException {
        TestSession.admin();
        Path folder = Files.createTempDirectory("qrfolder");
        TransferQrGuard.replace(folder, png(3), "Old text", store);
        TransferQrGuard.replaceInfo(folder, "  New text  ", store);
        TransferQrGuard.replaceInfo(folder, "", store);
        assertEquals(3, store.changes.size());
        assertEquals(new TransferQrGuard.Change(TransferQrGuard.INFO, "Old text", "New text"), store.changes.get(1));
        assertEquals(new TransferQrGuard.Change(TransferQrGuard.INFO, "New text", ""), store.changes.get(2));
    }

    @Test
    public void aRefusedChangeReportsNothing() throws IOException {
        TestSession.admin();
        Path folder = Files.createTempDirectory("qrfolder");
        TransferQrGuard.replace(folder, png(3), "Shop", store);
        Files.writeString(TransferQr.findInFolder(folder).orElseThrow(), "swapped by hand");
        try {
            TransferQrGuard.replaceInfo(folder, "Evil Co", store);
            fail("a swapped picture must stop the text-only change");
        } catch (IOException expected) {
            assertNotNull(expected.getMessage());
        }
        assertEquals("only the first change was reported", 1, store.changes.size());
    }

    @Test
    public void aStoreThatOnlyKnowsTheShortSaveStillWorks() throws IOException {
        TestSession.admin();
        TransferQrGuardTest.MemoryStore plain = new TransferQrGuardTest.MemoryStore();
        Path p = picture("shop qr");
        TransferQrGuard.accept(p, plain);
        assertEquals(TransferQrGuard.State.OK, TransferQrGuard.inspect(p, plain));
    }
}
