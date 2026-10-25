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
            Files.writeString(dir.resolve(String.format(java.util.Locale.ROOT, "auto-202601%02d-120000.zip", day)), "x");
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
    public void aRestoreNamesTheAccountsItBringsBack() throws Exception {
        Path live = liveDatabase();
        String ins = "INSERT INTO users(username, password_hash, role, status, created_at) VALUES ('%s','%s','EMPLOYEE','ACTIVE','2026-01-01T00:00:00')";
        exec(live, String.format(java.util.Locale.ROOT, ins, "zz-removed", "hash-a"));
        exec(live, String.format(java.util.Locale.ROOT, ins, "zz-locked", "hash-b"));
        exec(live, String.format(java.util.Locale.ROOT, ins, "zz-newpw", "OLD-SECRET-HASH"));
        exec(live, String.format(java.util.Locale.ROOT, ins, "zz-same", "hash-d"));
        // The backup must hold the four accounts above, which exist only in this scratch copy: BackupService.createBackup
        // reads the connection's own database (the shared test database), so a copy of the scratch file is the backup here.
        Path backup = scratch.resolve("outside").resolve("b.db");
        Files.createDirectories(backup.getParent());
        Files.copy(live, backup);

        // afterwards one is removed, one locked, one gets a new password, one is untouched, and one is created
        exec(live, "DELETE FROM users WHERE username='zz-removed'");
        exec(live, "UPDATE users SET status='LOCKED' WHERE username='zz-locked'");
        exec(live, "UPDATE users SET password_hash='NEW-SECRET-HASH' WHERE username='zz-newpw'");
        exec(live, String.format(java.util.Locale.ROOT, ins, "zz-created-later", "hash-e"));

        BackupService.scheduleRestore(backup);
        RestoreResult result = BackupService.applyPendingRestore();
        assertEquals(result.message(), RestoreOutcome.RESTORED, result.outcome());
        String message = result.message();
        assertTrue(message, message.contains("'zz-removed'"));
        assertTrue(message, message.contains("'zz-locked'"));
        assertTrue(message, message.contains("'zz-newpw'"));
        assertFalse("an account that did not change is not listed", message.contains("zz-same"));
        assertFalse("an account that the restore removes is not 'brought back'", message.contains("zz-created-later"));
        assertFalse("no password hash is ever shown", message.contains("SECRET-HASH"));
    }

    @Test
    public void aBackupWithoutAnActiveAdministratorIsRefused() throws Exception {
        liveDatabase();
        Path noAdmin = scratch.resolve("outside").resolve("no-admin.db");
        Files.createDirectories(noAdmin.getParent());
        DatabaseConfig.backupTo(noAdmin);
        exec(noAdmin, "UPDATE users SET status='LOCKED' WHERE role='ADMIN'");
        for (java.util.concurrent.Callable<Object> look : java.util.List.<java.util.concurrent.Callable<Object>>of(
                () -> BackupService.inspect(noAdmin), () -> BackupService.scheduleRestore(noAdmin))) {
            try {
                look.call();
                fail("a backup nobody can administer must be refused");
            } catch (IOException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("quản trị viên"));
            }
        }
        assertFalse("nothing was parked", BackupService.restorePending());
        exec(noAdmin, "UPDATE users SET status='ACTIVE' WHERE role='ADMIN'");
        assertNotNull("with an active administrator it is fine", BackupService.inspect(noAdmin));
    }

    @Test
    public void lookingAtABackupKeepsToTheSizeLimits() throws Exception {
        liveDatabase();
        Path good = Files.createTempFile("good", ".db");
        DatabaseConfig.backupTo(good);
        long dbSize = Files.size(good);

        // a database entry bigger than the cap
        Path bigDb = scratch.resolve("big-db.zip");
        try (OutputStream out = Files.newOutputStream(bigDb); ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("lunavera.db"));
            Files.copy(good, z);
            z.closeEntry();
        }
        try {
            BackupService.inspect(bigDb, new BackupService.Limits(dbSize - 1, 1 << 20, 1 << 20));
            fail("a database entry over the cap must be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("lớn bất thường"));
        }
        try (Stream<Path> left = Files.list(DataPaths.tmpDir())) {
            assertEquals("no partial copy is left in the temporary folder", 0, left.filter(p -> p.getFileName().toString().startsWith("lunavera-inspect")).count());
        }
        assertNotNull(BackupService.inspect(bigDb, new BackupService.Limits(dbSize, 1 << 20, 1 << 20)));

        // one picture over the per-picture cap, and pictures over the total cap
        Path bigPicture = scratch.resolve("big-picture.zip");
        try (OutputStream out = Files.newOutputStream(bigPicture); ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("lunavera.db"));
            Files.copy(good, z);
            z.closeEntry();
            for (int i = 0; i < 3; i++) {
                z.putNextEntry(new ZipEntry("images/p" + i + ".jpg"));
                z.write(new byte[1000]);
                z.closeEntry();
            }
        }
        try {
            BackupService.inspect(bigPicture, new BackupService.Limits(dbSize, 999, 1 << 20));
            fail("a picture over the cap must be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("lớn bất thường"));
        }
        try {
            BackupService.inspect(bigPicture, new BackupService.Limits(dbSize, 1000, 2999));
            fail("pictures over the total cap must be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("Ảnh"));
        }
        assertEquals(3, BackupService.inspect(bigPicture, new BackupService.Limits(dbSize, 1000, 3000)).pictures());

        // an info entry that is not a few lines
        Path bigInfo = scratch.resolve("big-info.zip");
        try (OutputStream out = Files.newOutputStream(bigInfo); ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("backup-info.txt"));
            z.write(new byte[BackupService.MAX_INFO_BYTES + 1]);
            z.closeEntry();
            z.putNextEntry(new ZipEntry("lunavera.db"));
            Files.copy(good, z);
            z.closeEntry();
        }
        try {
            BackupService.inspect(bigInfo);
            fail("an oversized info entry must be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("lớn bất thường"));
        }
    }

    @Test
    public void noAccountNotesWhenThereIsNothingToCompare() throws Exception {
        assertTrue(BackupService.accountsBroughtBack(null, scratch.resolve("x.db")).isEmpty());
        assertTrue(BackupService.accountsBroughtBack(scratch.resolve("missing.db"), scratch.resolve("also-missing.db")).isEmpty());
    }

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

    // ---------------------------------------------------------------- locked with a passphrase (TODO 2A.4)

    @Test
    public void aBackupLockedWithAPassphraseOpensOnlyWithIt() throws Exception {
        liveDatabase();
        write(DataPaths.imagesDir().resolve("1-1.jpg"), "picture");
        char[] phrase = "a long enough phrase".toCharArray();
        Path locked = BackupService.createEncryptedBackup(scratch.resolve("outside").resolve("mine"), phrase);
        assertTrue(locked.getFileName().toString().endsWith(BackupService.ENCRYPTED_EXTENSION));
        assertTrue(BackupService.isEncryptedBackup(locked));

        try {
            BackupService.inspect(locked);
            fail("a locked backup must not be read without its passphrase");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("mật khẩu"));
        }
        BackupService.Summary summary = BackupService.inspect(locked, phrase);
        assertEquals(1, summary.pictures());
        try {
            BackupService.inspect(locked, "another phrase here".toCharArray());
            fail("a wrong passphrase must be refused");
        } catch (BackupCrypto.WrongPassphraseException expected) {
            // good
        }
        try (Stream<Path> left = Files.list(locked.getParent())) {
            assertTrue("only the locked file is left next to it, no plain zip and no .part file",
                    left.allMatch(f -> f.getFileName().toString().endsWith(BackupService.ENCRYPTED_EXTENSION)));
        }
    }

    @Test
    public void aLockedBackupRestoresLikeAPlainOne() throws Exception {
        Path live = liveDatabase();
        char[] phrase = "a long enough phrase".toCharArray();
        Path locked = BackupService.createEncryptedBackup(scratch.resolve("outside").resolve("b"), phrase);
        exec(live, "INSERT INTO categories(name, description, active) VALUES ('after-marker','',1)");

        BackupService.scheduleRestore(locked, phrase);
        assertTrue(BackupService.restorePending());
        RestoreResult result = BackupService.applyPendingRestore();
        assertEquals(result.message(), RestoreOutcome.RESTORED, result.outcome());
        assertEquals(0, count(live, "SELECT COUNT(*) FROM categories WHERE name='after-marker'"));
    }

    @Test
    public void aShortPassphraseIsRefusedBeforeAnythingIsWritten() throws Exception {
        liveDatabase();
        Path target = scratch.resolve("outside").resolve("never");
        try {
            BackupService.createEncryptedBackup(target, "short".toCharArray());
            fail("a passphrase under the minimum must be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("ít nhất"));
        }
        assertFalse(Files.exists(BackupService.withEncryptedExtension(target)));
    }

    @Test
    public void theLockedExtensionReplacesZipAndIsAddedWhenMissing() {
        assertEquals("a.lvbak", BackupService.withEncryptedExtension(Path.of("a")).getFileName().toString());
        assertEquals("a.lvbak", BackupService.withEncryptedExtension(Path.of("a.zip")).getFileName().toString());
        assertEquals("a.lvbak", BackupService.withEncryptedExtension(Path.of("a.lvbak")).getFileName().toString());
        assertEquals("a.LVBAK", BackupService.withEncryptedExtension(Path.of("a.LVBAK")).getFileName().toString());
    }

    @org.junit.AfterClass
    public static void signOut() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.clear();
    }

    // ---- TODO 2.85: sales still in the write-ahead log after a crash are in the restore's safety copy ----

    @Test
    public void aRestoreAfterACrashSavesTheSalesThatWereStillInTheWriteAheadLog() throws Exception {
        Path live = liveDatabase();
        Path backup = BackupService.createBackup(scratch.resolve("outside").resolve("b.zip"));

        // A "crash": a sale is committed into the log, and the program dies before any checkpoint, so the files on disk are the
        // main file (without the sale) plus lunavera.db-wal (with it).
        Path crashFolder = Files.createTempDirectory("lunavera-crash");
        Path log = live.resolveSibling(live.getFileName() + "-wal");
        try (Connection keep = DriverManager.getConnection("jdbc:sqlite:" + live.toAbsolutePath());
             Statement st = keep.createStatement()) {
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA wal_autocheckpoint = 0");
            st.execute("INSERT INTO categories(name, description, active) VALUES ('wal-marker','',1)");
            assertTrue("the sale sits in the log file", Files.size(log) > 0);
            Files.copy(live, crashFolder.resolve("lunavera.db"));
            Files.copy(log, crashFolder.resolve("lunavera.db-wal"));
        }
        // Put the crashed pair back as the shop's files.
        Files.deleteIfExists(live);
        Files.deleteIfExists(log);
        Files.deleteIfExists(live.resolveSibling(live.getFileName() + "-shm"));
        Files.copy(crashFolder.resolve("lunavera.db"), live);
        Files.copy(crashFolder.resolve("lunavera.db-wal"), log);

        BackupService.scheduleRestore(backup);
        RestoreResult result = BackupService.applyPendingRestore();
        assertEquals(result.message(), RestoreOutcome.RESTORED, result.outcome());

        Path safety;
        try (Stream<Path> files = Files.list(DataPaths.backupsDir())) {
            safety = files.filter(p -> p.getFileName().toString().startsWith("before-restore-")).findFirst().orElseThrow();
        }
        Path safetyDb = Files.createTempFile("safety", ".db");
        try (ZipFile z = new ZipFile(safety.toFile())) {
            Files.copy(z.getInputStream(z.getEntry("lunavera.db")), safetyDb, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        assertEquals("the sale that was only in the log is in the safety copy", 1,
                count(safetyDb, "SELECT COUNT(*) FROM categories WHERE name='wal-marker'"));
        Files.deleteIfExists(safetyDb);
        assertEquals("the restored database is the backup", 0, count(live, "SELECT COUNT(*) FROM categories WHERE name='wal-marker'"));
        assertFalse("no old log file is left to be applied to the restored database", Files.exists(log));
    }

    @Test
    public void foldingWhenThereIsNoLogIsANoOpAndAnEmptyFolderIsFine() throws Exception {
        Path live = liveDatabase();
        assertTrue(BackupService.foldLeftoverLogInto(live));
        assertTrue(BackupService.foldLeftoverLogInto(scratch.resolve("does-not-exist.db")));
    }

    @Test
    public void aLogThatCannotBeFoldedIsKeptNotDeleted() throws Exception {
        Path notADatabase = scratch.resolve("broken.db");
        Files.writeString(notADatabase, "this is not an SQLite file at all, just text to be sure of it. ".repeat(20));
        Files.writeString(scratch.resolve("broken.db-wal"), "log bytes");
        assertFalse(BackupService.foldLeftoverLogInto(notADatabase));
        assertTrue("the log file is still there", Files.exists(scratch.resolve("broken.db-wal")));
    }

    // ---------------------------------------------------------------- TODO 3.63 / 3.65

    @Test
    public void aBackupWithATimeFarInTheFutureDoesNotBlockTheNextOne() throws Exception {
        Path dir = DataPaths.backupsDir();
        Files.createDirectories(dir);
        Path wrong = dir.resolve("auto-20990101-000000.zip");
        Files.writeString(wrong, "made while the clock was wrong");
        Files.setLastModifiedTime(wrong, FileTime.from(Instant.now().plusSeconds(400L * 86_400)));
        assertTrue("only a wrong-dated copy exists: a backup is due", BackupService.isDue(dir, Instant.now()));

        Path recent = dir.resolve("auto-20200101-000000.zip");
        Files.writeString(recent, "real, an hour old");
        Files.setLastModifiedTime(recent, FileTime.from(Instant.now().minusSeconds(3600)));
        assertFalse("a real recent copy counts, the wrong-dated one is ignored", BackupService.isDue(dir, Instant.now()));
    }

    @Test
    public void aTruncatedBackupFailsTheReadBackCheck() throws Exception {
        Path good = Files.createTempFile("lunavera-verify", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(good))) {
            zip.putNextEntry(new ZipEntry("notes.txt"));
            zip.write(new byte[200_000]);
            zip.closeEntry();
        }
        BackupService.verifyZip(good, false); // a sound zip passes (the database check is separate)
        byte[] bytes = Files.readAllBytes(good);
        Path cut = Files.createTempFile("lunavera-verify-cut", ".zip");
        Files.write(cut, java.util.Arrays.copyOf(bytes, bytes.length / 2));
        try {
            BackupService.verifyZip(cut, false);
            fail("a cut-off zip must not pass");
        } catch (IOException expected) {
            // good
        }
    }

    @Test
    public void aFreshBackupIsReadBackAndKept() throws Exception {
        Path made = BackupService.createBackup(scratch.resolve("fresh.zip"));
        assertTrue(Files.isRegularFile(made));
        assertNotNull(BackupService.inspect(made));
    }

    @Test
    public void aBackupWithoutPicturesSaysSoAndARestoreFromItKeepsTheCurrentPictures() throws Exception {
        Path live = liveDatabase();
        write(DataPaths.imagesDir().resolve("keep.jpg"), "my picture");
        Path backup = BackupService.createBackup(scratch.resolve("outside").resolve("nopics.zip"), false);

        Set<String> names = entries(backup);
        assertTrue(names.toString(), names.contains("lunavera.db") && names.contains("backup-info.txt"));
        assertFalse("no picture inside", names.stream().anyMatch(n -> n.startsWith("images/")));
        assertFalse(BackupService.picturesIncluded(backup));
        assertFalse(BackupService.inspect(backup).picturesIncluded());
        assertTrue(BackupService.inspect(backup).describe().contains(com.coffeeshop.coffeeshopmanagement.util.I18n.t("backup.nopictures")));

        exec(live, "INSERT INTO categories(name, description, active) VALUES ('after-nopics','',1)");
        write(DataPaths.imagesDir().resolve("later.jpg"), "added later");
        BackupService.scheduleRestore(backup);
        RestoreResult result = BackupService.applyPendingRestore();
        assertEquals(result.message(), RestoreOutcome.RESTORED, result.outcome());
        assertEquals("the database is the backup's", 0, count(live, "SELECT COUNT(*) FROM categories WHERE name='after-nopics'"));
        assertTrue("the pictures of the shop are still there", Files.exists(DataPaths.imagesDir().resolve("keep.jpg")));
        assertTrue(Files.exists(DataPaths.imagesDir().resolve("later.jpg")));
    }

    @Test
    public void anAutomaticBackupAfterAFullOneLeavesThePicturesOut() throws Exception {
        write(DataPaths.imagesDir().resolve("p.jpg"), "picture");
        assertTrue("the first automatic backup is a full one", BackupService.autoBackupIfDue().isPresent());
        Path dir = DataPaths.backupsDir();
        try (Stream<Path> files = Files.list(dir)) {
            Path first = files.filter(p -> p.getFileName().toString().startsWith("auto-")).findFirst().orElseThrow();
            assertTrue(entries(first).contains("images/p.jpg"));
            assertTrue(BackupService.picturesIncluded(first));
            // make it look two days old so the next one is due, then run again
            Files.setLastModifiedTime(first, java.nio.file.attribute.FileTime.from(java.time.Instant.now().minus(java.time.Duration.ofDays(2))));
        }
        assertTrue(BackupService.autoBackupIfDue().isPresent());
        try (Stream<Path> files = Files.list(dir)) {
            Path second = files.filter(p -> p.getFileName().toString().endsWith("-db.zip")).findFirst().orElseThrow();
            assertFalse(entries(second).stream().anyMatch(n -> n.startsWith("images/")));
        }
    }
}

