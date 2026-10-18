package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.service.BackupService.RestoreOutcome;
import com.coffeeshop.coffeeshopmanagement.service.BackupService.RestoreResult;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/**
 * TODO 2.8: full backups (database + pictures), automatic ones that prune themselves, and restore at the next start.
 * Each test points {@code lunavera.dataDir} at its own scratch folder (DataPaths reads it on every call), so the
 * shared throw-away test database is never replaced.
 */
public class BackupServiceTest {

    private String savedDataDir;
    private Path scratch;

    @BeforeClass
    public static void setUpDatabase() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.admin();
        TestDatabaseSupport.requireSqlite();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void useScratchDataFolder() throws Exception {
        savedDataDir = System.getProperty(DataPaths.DATA_DIR_PROPERTY);
        scratch = Files.createTempDirectory("lunavera-backup-test");
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, scratch.toString());
    }

    @After
    public void restoreProperty() {
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, savedDataDir);
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    private static Set<String> entries(Path zip) throws IOException {
        Set<String> names = new HashSet<>();
        try (ZipFile z = new ZipFile(zip.toFile())) {
            z.stream().forEach(e -> names.add(e.getName()));
        }
        return names;
    }

    private static long count(Path db, String sql) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void exec(Path db, String sql) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    /** A scratch "live" database: a valid copy of the test database. */
    private Path liveDatabase() throws Exception {
        DatabaseConfig.backupTo(DataPaths.databaseFile());
        return DataPaths.databaseFile();
    }

    // ---------------------------------------------------------------- making backups

    @Test
    public void aBackupHoldsTheDatabaseThePicturesAndTheTransferQr() throws Exception {
        write(DataPaths.imagesDir().resolve("1-1.jpg"), "picture");
        write(DataPaths.imagesDir().resolve("sub/2-2.png"), "picture2");
        write(scratch.resolve("qr-transfer.png"), "qr");
        Path made = BackupService.createBackup(scratch.resolve("out").resolve("mine.zip"));

        Set<String> names = entries(made);
        assertTrue(names.toString(), names.containsAll(Set.of("lunavera.db", "images/1-1.jpg", "images/sub/2-2.png", "qr-transfer.png", "backup-info.txt")));
        Path extracted = Files.createTempFile("x", ".db");
        try (ZipFile z = new ZipFile(made.toFile())) {
            Files.copy(z.getInputStream(z.getEntry("lunavera.db")), extracted, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        assertTrue("the database inside is a real, readable Lunavera database", count(extracted, "SELECT COUNT(*) FROM users") >= 1);
        Files.deleteIfExists(extracted);
        try (Stream<Path> left = Files.list(made.getParent())) {
            assertTrue("no half-written .part file is left behind", left.noneMatch(p -> p.toString().endsWith(".part")));
        }
    }

    @Test
    public void aNameWithoutAnExtensionGetsZip() throws Exception {
        Path made = BackupService.createBackup(scratch.resolve("mybackup"));
        assertEquals("mybackup.zip", made.getFileName().toString());
        assertTrue(Files.exists(made));
    }

    @Test
    public void aNewBackupReplacesAnOlderOneOfTheSameNameOnlyOnSuccess() throws Exception {
        Path target = scratch.resolve("same.zip");
        BackupService.createBackup(target);
        long first = Files.size(target);
        write(DataPaths.imagesDir().resolve("big.jpg"), "x".repeat(5000));
        BackupService.createBackup(target);
        assertTrue(Files.size(target) > first - 1);
        assertTrue(entries(target).contains("images/big.jpg"));
    }

    @Test
    public void choosingTheLiveDatabaseAsTheTargetCannotDestroyIt() throws Exception {
        Path live = liveDatabase();
        long before = Files.size(live);
        Path made = BackupService.createBackup(live); // the old dialog allowed exactly this and emptied the file
        assertEquals("lunavera.db.zip", made.getFileName().toString());
        assertEquals(before, Files.size(live));
        assertTrue(count(live, "SELECT COUNT(*) FROM users") >= 1);
    }

    // ---------------------------------------------------------------- automatic backups and pruning

    @Test
    public void anAutomaticBackupIsMadeWhenNoneExistsAndNotAgainTheSameDay() throws Exception {
        assertTrue(BackupService.autoBackupIfDue().isPresent());
        assertFalse("one a day is enough", BackupService.autoBackupIfDue().isPresent());
        try (Stream<Path> files = Files.list(DataPaths.backupsDir())) {
            assertEquals(1, files.filter(p -> p.getFileName().toString().startsWith("auto-")).count());
        }
    }

    @Test
    public void anOldAutomaticBackupMakesANewOneDue() throws Exception {
        Path dir = DataPaths.backupsDir();
        Files.createDirectories(dir);
        Path old = dir.resolve("auto-20200101-000000.zip");
        Files.writeString(old, "old");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minusSeconds(3 * 86_400)));
        assertTrue(BackupService.isDue(dir, Instant.now()));
        assertTrue(BackupService.autoBackupIfDue().isPresent());
    }

    @Test
    public void pruningKeepsTheNewestAndOnlyTouchesAutomaticBackups() throws Exception {
        Path dir = DataPaths.backupsDir();
        Files.createDirectories(dir);
        for (int day = 1; day <= 20; day++) {
            Files.writeString(dir.resolve(String.format("auto-202601%02d-120000.zip", day)), "x");
        }
        Files.writeString(dir.resolve("lunavera-backup-manual.zip"), "mine");
        Files.writeString(dir.resolve("before-money-migration.db"), "old db");
        Files.writeString(dir.resolve("before-restore-20260101-000000.zip"), "safety");

        assertEquals(6, BackupService.pruneAutomatic(dir, 14));
        try (Stream<Path> files = Files.list(dir)) {
            Set<String> left = new HashSet<>();
            files.forEach(p -> left.add(p.getFileName().toString()));
            assertEquals(14 + 3, left.size());
            assertTrue(left.contains("auto-20260120-120000.zip"));
            assertFalse(left.contains("auto-20260106-120000.zip"));
            assertTrue(left.contains("lunavera-backup-manual.zip"));
            assertTrue(left.contains("before-money-migration.db"));
            assertTrue(left.contains("before-restore-20260101-000000.zip"));
        }
    }

    // ---------------------------------------------------------------- restore

    @Test
    public void aRestoreBringsBackTheDatabaseAndThePicturesAndSavesTheCurrentState() throws Exception {
        Path live = liveDatabase();
        write(DataPaths.imagesDir().resolve("old.jpg"), "old picture");
        Path backup = BackupService.createBackup(scratch.resolve("outside").resolve("b.zip"));

        // the shop carries on and changes things after the backup
        exec(live, "INSERT INTO categories(name, description, active) VALUES ('after-marker','',1)");
        Files.delete(DataPaths.imagesDir().resolve("old.jpg"));
        write(DataPaths.imagesDir().resolve("new.jpg"), "new picture");

        BackupService.scheduleRestore(backup);
        assertTrue(BackupService.restorePending());
        assertEquals("nothing is touched until the next start", 1, count(live, "SELECT COUNT(*) FROM categories WHERE name='after-marker'"));

        RestoreResult result = BackupService.applyPendingRestore();
        assertEquals(result.message(), RestoreOutcome.RESTORED, result.outcome());
        assertFalse(BackupService.restorePending());
        assertEquals(0, count(live, "SELECT COUNT(*) FROM categories WHERE name='after-marker'"));
        assertTrue(Files.exists(DataPaths.imagesDir().resolve("old.jpg")));
        assertFalse(Files.exists(DataPaths.imagesDir().resolve("new.jpg")));

        // the state before the restore was kept
        Path safety;
        try (Stream<Path> files = Files.list(DataPaths.backupsDir())) {
            safety = files.filter(p -> p.getFileName().toString().startsWith("before-restore-")).findFirst().orElseThrow();
        }
        assertTrue(entries(safety).contains("images/new.jpg"));
        Path safetyDb = Files.createTempFile("safety", ".db");
        try (ZipFile z = new ZipFile(safety.toFile())) {
            Files.copy(z.getInputStream(z.getEntry("lunavera.db")), safetyDb, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        assertEquals(1, count(safetyDb, "SELECT COUNT(*) FROM categories WHERE name='after-marker'"));
        Files.deleteIfExists(safetyDb);
    }

    @Test
    public void anOldPlainDbBackupRestoresTheDatabaseAndLeavesThePicturesAlone() throws Exception {
        Path live = liveDatabase();
        Path plain = scratch.resolve("outside").resolve("old.db");
        Files.createDirectories(plain.getParent());
        DatabaseConfig.backupTo(plain);
        exec(live, "INSERT INTO categories(name, description, active) VALUES ('after-marker2','',1)");
        write(DataPaths.imagesDir().resolve("keep.jpg"), "keep");

        BackupService.scheduleRestore(plain);
        assertEquals(RestoreOutcome.RESTORED, BackupService.applyPendingRestore().outcome());
        assertEquals(0, count(live, "SELECT COUNT(*) FROM categories WHERE name='after-marker2'"));
        assertTrue(Files.exists(DataPaths.imagesDir().resolve("keep.jpg")));
    }

    @Test
    public void filesThatAreNotUsableBackupsAreRefusedBeforeAnythingIsParked() throws Exception {
        Path garbageZip = scratch.resolve("garbage.zip");
        Files.writeString(garbageZip, "this is not a zip");
        Path zipWithoutDb = scratch.resolve("nodb.zip");
        try (OutputStream out = Files.newOutputStream(zipWithoutDb); ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("hello.txt"));
            z.write("hi".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        Path emptySqlite = scratch.resolve("empty.db");
        exec(emptySqlite, "CREATE TABLE something_else (a INTEGER)");
        Path notSqlite = scratch.resolve("text.db");
        Files.writeString(notSqlite, "plain text, definitely not sqlite, " + "x".repeat(200));

        for (Path bad : new Path[]{scratch.resolve("missing.zip"), garbageZip, zipWithoutDb, emptySqlite, notSqlite}) {
            try {
                BackupService.scheduleRestore(bad);
                fail(bad.getFileName() + " should have been refused");
            } catch (IOException expected) {
                assertNotNull(expected.getMessage());
            }
            assertFalse("nothing parked for " + bad.getFileName(), BackupService.restorePending());
        }
    }

    @Test
    public void aBrokenPendingRestoreFailsSafelyAndIsNotRetriedForever() throws Exception {
        Path live = liveDatabase();
        long before = Files.size(live);
        Files.writeString(scratch.resolve("restore-pending.zip"), "corrupt");

        RestoreResult result = BackupService.applyPendingRestore();
        assertEquals(RestoreOutcome.FAILED, result.outcome());
        assertFalse("not retried on every start", BackupService.restorePending());
        assertEquals("the current database is untouched", before, Files.size(live));
        try (Stream<Path> files = Files.list(scratch)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("restore-failed-")));
        }
    }

    @Test
    public void aMaliciousPictureNameCannotLeaveTheImagesFolder() throws Exception {
        Path live = liveDatabase();
        Path good = Files.createTempFile("good", ".db");
        DatabaseConfig.backupTo(good);
        Path evilZip = scratch.resolve("evil.zip");
        try (OutputStream out = Files.newOutputStream(evilZip); ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("lunavera.db"));
            Files.copy(good, z);
            z.closeEntry();
            z.putNextEntry(new ZipEntry("images/../../escaped.txt"));
            z.write("evil".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            z.putNextEntry(new ZipEntry("images/fine.jpg"));
            z.write("fine".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        Files.deleteIfExists(good);
        BackupService.scheduleRestore(evilZip);
        assertEquals(RestoreOutcome.RESTORED, BackupService.applyPendingRestore().outcome());
        assertTrue(Files.exists(DataPaths.imagesDir().resolve("fine.jpg")));
        assertFalse(Files.exists(scratch.resolve("escaped.txt")));
        assertFalse(Files.exists(scratch.getParent().resolve("escaped.txt")));
        assertTrue(Files.exists(live));
    }

    @Test
    public void nothingPendingMeansNothingHappens() {
        assertEquals(RestoreOutcome.NOTHING_PENDING, BackupService.applyPendingRestore().outcome());
    }

    // ---------------------------------------------------------------- TODO 2.41, 2.42, 5.22

    /** A zip with a valid database and one stored (uncompressed) picture whose bytes are then damaged in the file. */
    private Path zipWithDamagedPicture() throws Exception {
        Path good = Files.createTempFile("good", ".db");
        DatabaseConfig.backupTo(good);
        Path zipFile = scratch.resolve("damaged.zip");
        byte[] picture = "PICTUREDATA-0123456789".getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = Files.newOutputStream(zipFile); ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("lunavera.db"));
            Files.copy(good, z);
            z.closeEntry();
            ZipEntry entry = new ZipEntry("images/damaged.jpg");
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(picture.length);
            entry.setCompressedSize(picture.length);
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(picture);
            entry.setCrc(crc.getValue());
            z.putNextEntry(entry);
            z.write(picture);
            z.closeEntry();
        }
        Files.deleteIfExists(good);
        byte[] bytes = Files.readAllBytes(zipFile);
        byte[] needle = "PICTUREDATA".getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i < bytes.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) if (bytes[i + j] != needle[j]) continue outer;
            bytes[i] = 'X';
            break;
        }
        Files.write(zipFile, bytes);
        return zipFile;
    }

    @Test
    public void aBackupWithADamagedPictureIsRefusedBeforeAnythingIsParked() throws Exception {
        liveDatabase();
        try {
            BackupService.scheduleRestore(zipWithDamagedPicture());
            fail("a backup with a damaged picture must be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("hỏng"));
        }
        assertFalse(BackupService.restorePending());
    }

    @Test
    public void aRestoreThatFailsWhileExtractingPicturesKeepsTheOldPictures() throws Exception {
        liveDatabase();
        write(DataPaths.imagesDir().resolve("old-picture.jpg"), "old");
        Files.copy(zipWithDamagedPicture(), scratch.resolve("restore-pending.zip"));
        RestoreResult result = BackupService.applyPendingRestore();
        assertEquals(RestoreOutcome.FAILED, result.outcome());
        assertTrue("the old pictures are still there", Files.exists(DataPaths.imagesDir().resolve("old-picture.jpg")));
        assertFalse(Files.exists(scratch.resolve("images.restoring")));
        assertFalse(Files.exists(scratch.resolve("lunavera.db.restoring")));
    }

    @Test
    public void aBackupMadeByANewerVersionIsRefused() throws Exception {
        liveDatabase();
        Path newer = scratch.resolve("outside").resolve("newer.db");
        Files.createDirectories(newer.getParent());
        DatabaseConfig.backupTo(newer);
        exec(newer, "PRAGMA user_version = 99");
        try {
            BackupService.scheduleRestore(newer);
            fail("a newer data version must be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("mới hơn"));
        }
    }

    @Test
    public void theTransferQrComesBackWithARestore() throws Exception {
        liveDatabase();
        write(scratch.resolve("qr-transfer.png"), "bank-qr");
        Path made = BackupService.createBackup(scratch.resolve("out").resolve("with-qr.zip"));
        Files.delete(scratch.resolve("qr-transfer.png"));
        BackupService.scheduleRestore(made);
        assertEquals(RestoreOutcome.RESTORED, BackupService.applyPendingRestore().outcome());
        assertEquals("bank-qr", Files.readString(scratch.resolve("qr-transfer.png")));
    }

    @Test
    public void aPendingRestoreCanBeSeenAndCancelled() throws Exception {
        liveDatabase();
        Path made = BackupService.createBackup(scratch.resolve("out").resolve("pending.zip"));
        assertTrue(BackupService.pendingSince().isEmpty());
        BackupService.scheduleRestore(made);
        assertTrue(BackupService.restorePending());
        assertTrue(BackupService.pendingSince().isPresent());
        assertFalse(BackupService.pendingIsOlderThan(java.time.Duration.ofHours(1)));
        assertTrue(BackupService.cancelPendingRestore());
        assertFalse(BackupService.restorePending());
        assertEquals(RestoreOutcome.NOTHING_PENDING, BackupService.applyPendingRestore().outcome());
    }

    @Test
    public void theSummaryTellsWhatABackupHolds() throws Exception {
        liveDatabase();
        write(DataPaths.imagesDir().resolve("a.jpg"), "a");
        write(DataPaths.imagesDir().resolve("b.jpg"), "b");
        Path made = BackupService.createBackup(scratch.resolve("out").resolve("sum.zip"));
        BackupService.Summary summary = BackupService.inspect(made);
        assertEquals(2, summary.pictures());
        assertNotNull(summary.created());
        assertTrue(summary.describe(), summary.describe().contains("Số đơn hàng"));
        assertFalse("inspect() parks nothing", BackupService.restorePending());
    }

    @Test
    public void onlyTheNewestFailedRestoresAreKept() throws Exception {
        for (int i = 1; i <= 5; i++) {
            Files.writeString(scratch.resolve("restore-failed-2026010" + i + "-000000.zip"), "x");
        }
        BackupService.pruneFailedRestores(scratch, 3);
        try (Stream<Path> files = Files.list(scratch)) {
            assertEquals(3, files.filter(p -> p.getFileName().toString().startsWith("restore-failed-")).count());
        }
        assertTrue(Files.exists(scratch.resolve("restore-failed-20260105-000000.zip")));
    }

    @org.junit.AfterClass
    public static void signOut() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.clear();
    }
}
