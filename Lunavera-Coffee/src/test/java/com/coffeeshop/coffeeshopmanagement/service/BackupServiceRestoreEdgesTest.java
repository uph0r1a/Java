package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.BackupService.Summary;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 6.78 (mutation sweep, profile pit-db): the parts of {@link BackupService} around a restore that the older tests did not
 * see: the text that describes a backup, the parked restore (look, age, cancel), a locked backup looked at and scheduled, the
 * safety copies and their tidying, the automatic backup started on its own thread, and two small file helpers.
 * Each test works in its own scratch data folder, as {@link BackupServiceTest} does; the shared test database is only read.
 */
public class BackupServiceRestoreEdgesTest {

    private static final char[] PASSPHRASE = "a long enough passphrase".toCharArray();

    private String savedDataDir;
    private Path scratch;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.requireSqlite();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void useScratchDataFolder() throws Exception {
        savedDataDir = System.getProperty(DataPaths.DATA_DIR_PROPERTY);
        scratch = Files.createTempDirectory("lunavera-backup-edges");
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, scratch.toString());
        TestSession.admin();
    }

    @After
    public void restoreProperty() {
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, savedDataDir);
        TestSession.admin();
    }

    private static void signInAsEmployee() {
        User user = new User();
        user.setId(2);
        user.setUsername("test-employee");
        user.setRole(Role.EMPLOYEE);
        Session.start(user, null);
    }

    private Path madeBackup(String name) throws Exception {
        return BackupService.createBackup(scratch.resolve("made").resolve(name));
    }

    private static long count(Path dir, String glob) throws IOException {
        if (!Files.isDirectory(dir)) return 0;
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
            long n = 0;
            for (Path ignored : stream) n++;
            return n;
        }
    }

    // ---- the text that describes a backup ----

    @Test
    public void describeNamesTheTimeOrSaysItIsUnknown() {
        String known = new Summary("2026-01-01T10:00", 5, null, 3, 2, 1, true).describe();
        assertTrue(known, known.contains("2026-01-01T10:00"));
        String unknown = new Summary(null, 5, null, 3, 2, 1, true).describe();
        assertTrue(unknown, unknown.contains(I18n.t("startup.unknown")));
    }

    @Test
    public void describeWarnsOnlyWhenThePicturesAreLeftOut() {
        String warning = I18n.t("backup.nopictures");
        assertFalse(new Summary("2026-01-01T10:00", 5, null, 3, 2, 1, true).describe().contains(warning));
        assertTrue(new Summary("2026-01-01T10:00", 5, null, 3, 2, 0, false).describe().contains(warning));
    }

    // ---- the parked restore ----

    @Test
    public void withNothingParkedThereIsNothingToSeeAgeOrCancel() {
        assertTrue(BackupService.pendingFile().isEmpty());
        assertFalse(BackupService.pendingIsOlderThan(Duration.ZERO));
        assertFalse(BackupService.cancelPendingRestore());
    }

    @Test
    public void aParkedDatabaseFileIsSeenAgedAndCancelled() throws Exception {
        Path parked = scratch.resolve(BackupService.PENDING_DB);
        Files.writeString(parked, "parked");
        Optional<Path> seen = BackupService.pendingFile();
        assertTrue("an old plain database copy is a parked restore too", seen.isPresent());
        assertEquals(parked.getFileName(), seen.get().getFileName());

        assertFalse("just parked", BackupService.pendingIsOlderThan(Duration.ofHours(1)));
        Files.setLastModifiedTime(parked, FileTime.from(Instant.now().minusSeconds(2 * 3600L)));
        assertTrue("parked two hours ago", BackupService.pendingIsOlderThan(Duration.ofHours(1)));
        assertFalse("not yet three hours", BackupService.pendingIsOlderThan(Duration.ofHours(3)));

        assertTrue(BackupService.cancelPendingRestore());
        assertFalse("gone", Files.exists(parked));
        assertFalse("nothing is left to cancel", BackupService.cancelPendingRestore());
    }

    @Test
    public void aParkedZipIsSeenAndCancelled() throws Exception {
        Path parked = scratch.resolve(BackupService.PENDING_ZIP);
        Files.writeString(parked, "parked");
        assertEquals(parked.getFileName(), BackupService.pendingFile().orElseThrow().getFileName());
        assertTrue(BackupService.cancelPendingRestore());
        assertFalse(Files.exists(parked));
    }

    // ---- a locked backup: looked at and scheduled ----

    @Test
    public void aLockedAndAnUnlockedBackupAreBothLookedAtWithAPassphraseGiven() throws Exception {
        Path plain = madeBackup("plain.zip");
        Path locked = scratch.resolve("locked.zip");
        BackupCrypto.encrypt(plain, locked, PASSPHRASE);

        assertNotNull("a plain backup is looked at as it is", BackupService.inspect(plain, PASSPHRASE));
        Summary opened = BackupService.inspect(locked, PASSPHRASE);
        assertNotNull(opened);
        assertEquals(BackupService.inspect(plain).orders(), opened.orders());
        assertEquals(BackupService.inspect(plain).products(), opened.products());
    }

    @Test
    public void aLockedAndAnUnlockedBackupAreBothScheduledWithAPassphraseGiven() throws Exception {
        Path plain = madeBackup("plain.zip");
        Path locked = scratch.resolve("locked.zip");
        BackupCrypto.encrypt(plain, locked, PASSPHRASE);

        assertNotNull("a plain backup is scheduled as it is", BackupService.scheduleRestore(plain, PASSPHRASE));
        assertTrue(BackupService.restorePending());
        assertTrue(BackupService.cancelPendingRestore());

        assertNotNull(BackupService.scheduleRestore(locked, PASSPHRASE));
        assertTrue(BackupService.restorePending());
        assertTrue(BackupService.cancelPendingRestore());
    }

    @Test
    public void anEmployeeIsRefusedForALockedBackupBeforeItIsOpened() throws Exception {
        Path plain = madeBackup("plain.zip");
        Path locked = scratch.resolve("locked.zip");
        BackupCrypto.encrypt(plain, locked, PASSPHRASE);
        signInAsEmployee();
        // a wrong passphrase: only the permission check, which comes first, can give this answer
        assertThrows(Permissions.PermissionDeniedException.class, () -> BackupService.scheduleRestore(locked, "wrong".toCharArray()));
        assertFalse(BackupService.restorePending());
    }

    // ---- the safety copies ----

    private void fakeOldCopies(Path dir, String prefix, int how) throws Exception {
        Files.createDirectories(dir);
        for (int day = 1; day <= how; day++) {
            Files.writeString(dir.resolve(String.format(Locale.ROOT, "%s2020010%d-000000.zip", prefix, day)), "old");
        }
    }

    @Test
    public void theCopyBeforeCleaningPicturesIsMadeAndTheOldOnesAreTidied() throws Exception {
        Path dir = DataPaths.backupsDir();
        fakeOldCopies(dir, "before-cleanup-", 3);
        Path made = BackupService.createBeforeCleanupBackup();
        assertNotNull(made);
        assertTrue(Files.isRegularFile(made));
        assertTrue(made.getFileName().toString().startsWith("before-cleanup-"));
        assertEquals("the newest three are kept", BackupService.KEEP_BEFORE_RESTORE, count(dir, "before-cleanup-*.zip"));
        assertFalse("the oldest went", Files.exists(dir.resolve("before-cleanup-20200101-000000.zip")));
    }

    @Test
    public void theCopyBeforeARestoreIsMadeAndTheOldOnesAreTidied() throws Exception {
        Path dir = DataPaths.backupsDir();
        fakeOldCopies(dir, "before-restore-", 3);
        Path made = BackupService.createBeforeRestoreBackup();
        assertNotNull(made);
        assertTrue(Files.isRegularFile(made));
        assertTrue(made.getFileName().toString().startsWith("before-restore-"));
        assertEquals(BackupService.KEEP_BEFORE_RESTORE, count(dir, "before-restore-*.zip"));
        assertFalse(Files.exists(dir.resolve("before-restore-20200101-000000.zip")));
    }

    @Test
    public void anAutomaticBackupAlsoTidiesTheOldBeforeRestoreCopies() throws Exception {
        Path dir = DataPaths.backupsDir();
        fakeOldCopies(dir, "before-restore-", 4);
        // another automatic backup (from a test of this class that ended a moment ago) may still be running: try again for a while
        Optional<Path> made = Optional.empty();
        long deadline = System.currentTimeMillis() + 15_000L;
        while (made.isEmpty() && System.currentTimeMillis() < deadline) {
            made = BackupService.autoBackupIfDue();
            if (made.isEmpty()) Thread.sleep(200);
        }
        assertTrue("nothing automatic yet, so one is due and is made", made.isPresent());
        assertEquals(BackupService.KEEP_BEFORE_RESTORE, count(dir, "before-restore-*.zip"));
    }

    // ---- the automatic backup on its own thread ----

    @Test
    public void theBackgroundStartMakesTheBackupOnItsOwnThread() throws Exception {
        Path dir = DataPaths.backupsDir();
        BackupService.autoBackupInBackground();
        long deadline = System.currentTimeMillis() + 20_000L;
        while (System.currentTimeMillis() < deadline && count(dir, "auto-*.zip") == 0) {
            Thread.sleep(100);
        }
        assertTrue("an automatic backup appeared", count(dir, "auto-*.zip") > 0);
        Thread.sleep(500); // let the thread finish its tidying before the data folder is switched back
    }

    @Test
    public void theStartOnLeavingWaitsForTheBackup() throws Exception {
        Path dir = DataPaths.backupsDir();
        BackupService.autoBackupBeforeExit(20_000L);
        assertTrue("it came back only after the backup was made", count(dir, "auto-*.zip") > 0);
    }

    // ---- two small file helpers ----

    @Test
    public void aRestoreBringsBackTheTransferQrAndRemovesTheOldOnes() throws Exception {
        Path zip = scratch.resolve("with-qr.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("qr-transfer.png"));
            out.write("new picture".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        Files.writeString(scratch.resolve("qr-transfer.jpg"), "an old one of another type");
        Files.writeString(scratch.resolve("qr-transfer.png"), "the old picture");

        BackupService.restoreTransferQr(zip, scratch);

        assertFalse("the old one of another type is removed", Files.exists(scratch.resolve("qr-transfer.jpg")));
        assertEquals("new picture", Files.readString(scratch.resolve("qr-transfer.png")));
    }

    @Test
    public void onlyTheNewestFailedRestoresAreKept() throws Exception {
        for (int n = 1; n <= 8; n++) {
            Files.writeString(scratch.resolve("restore-failed-2026010" + n), "x");
        }
        BackupService.pruneFailedRestores(scratch, 2);
        try (Stream<Path> left = Files.list(scratch)) {
            assertEquals(java.util.Set.of("restore-failed-20260107", "restore-failed-20260108"),
                    left.map(p -> p.getFileName().toString()).filter(n -> n.startsWith("restore-failed-"))
                            .collect(java.util.stream.Collectors.toSet()));
        }
    }
}
