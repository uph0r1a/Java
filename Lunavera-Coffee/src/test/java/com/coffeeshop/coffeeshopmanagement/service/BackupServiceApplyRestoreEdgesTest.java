package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.service.BackupService.RestoreOutcome;
import com.coffeeshop.coffeeshopmanagement.service.BackupService.RestoreResult;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/**
 * TODO 6.78 (mutation sweep, profile pit-db): {@link BackupService#applyPendingRestore()} at the points the older tests did
 * not look at: what an earlier, broken restore left behind is swept first, a file that is no database is refused, the old
 * pictures come back when the restore fails after they were swapped, the answer's text is exact, and the old
 * {@code restore-failed-*} files are tidied after a success and after a failure. Each test has its own scratch data folder.
 */
public class BackupServiceApplyRestoreEdgesTest {

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
        scratch = Files.createTempDirectory("lunavera-apply-edges");
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, scratch.toString());
        TestSession.admin();
    }

    @After
    public void restoreProperty() {
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, savedDataDir);
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    /** A scratch "live" database: a valid copy of the test database. */
    private void liveDatabase() throws Exception {
        DatabaseConfig.backupTo(DataPaths.databaseFile());
    }

    /** A backup that holds one picture, {@code backup-pic.jpg}; the shop's own pictures are then replaced by {@code current.jpg}. */
    private Path backupWithOnePictureThenOtherPictures() throws Exception {
        write(DataPaths.imagesDir().resolve("backup-pic.jpg"), "from the backup");
        Path zip = BackupService.createBackup(scratch.resolve("made").resolve("b.zip"));
        Files.delete(DataPaths.imagesDir().resolve("backup-pic.jpg"));
        write(DataPaths.imagesDir().resolve("current.jpg"), "in the shop now");
        return zip;
    }

    private static Path zipHoldingAFakeDatabase(Path where) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(where))) {
            out.putNextEntry(new ZipEntry("lunavera.db"));
            out.write("this is not a database".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return where;
    }

    private static long count(Path dir, String glob) throws IOException {
        if (!Files.isDirectory(dir)) return 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, glob)) {
            long n = 0;
            for (Path ignored : stream) n++;
            return n;
        }
    }

    private void fiveOldFailedRestores() throws IOException {
        for (int n = 1; n <= 5; n++) {
            write(scratch.resolve("restore-failed-2020010" + n + ".zip"), "old");
        }
    }

    // ---- a restore that works ----

    @Test
    public void aRestoreSweepsWhatAnEarlierBrokenOneLeftAndTidiesOldFailedRestores() throws Exception {
        liveDatabase();
        Path zip = backupWithOnePictureThenOtherPictures();
        BackupService.scheduleRestore(zip);
        write(scratch.resolve("images.restoring").resolve("junk-one.txt"), "left by a broken restore");
        write(scratch.resolve("images.old").resolve("junk-two.txt"), "left by a broken restore");
        fiveOldFailedRestores();

        RestoreResult result = BackupService.applyPendingRestore();

        assertEquals(result.message(), RestoreOutcome.RESTORED, result.outcome());
        Path images = DataPaths.imagesDir();
        assertTrue("the picture of the backup is back", Files.exists(images.resolve("backup-pic.jpg")));
        assertFalse("the shop's later picture is not in the backup", Files.exists(images.resolve("current.jpg")));
        assertFalse("what the earlier restore left in its working folder is not carried in", Files.exists(images.resolve("junk-one.txt")));
        assertFalse("the folder of the old pictures is removed again", Files.exists(scratch.resolve("images.old")));
        assertEquals("only the newest three failed restores are kept", 3, count(scratch, "restore-failed-*"));
    }

    @Test
    public void theAnswerNamesTheSafetyCopyAndNothingElseWhenAllWentWell() throws Exception {
        liveDatabase();
        Path zip = backupWithOnePictureThenOtherPictures();
        BackupService.scheduleRestore(zip);

        RestoreResult result = BackupService.applyPendingRestore();

        assertEquals(result.message(), RestoreOutcome.RESTORED, result.outcome());
        Path safety;
        try (DirectoryStream<Path> copies = Files.newDirectoryStream(DataPaths.backupsDir(), "before-restore-*.zip")) {
            safety = copies.iterator().next();
        }
        assertEquals(I18n.t("backup.restored", " " + I18n.t("backup.safety", safety)), result.message());
    }

    @Test
    public void withNoDatabaseInTheShopNoSafetyCopyIsMadeOrNamed() throws Exception {
        write(DataPaths.imagesDir().resolve("backup-pic.jpg"), "from the backup");
        Path zip = BackupService.createBackup(scratch.resolve("made").resolve("b.zip"));
        assertFalse("no live database yet", Files.exists(DataPaths.databaseFile()));
        BackupService.scheduleRestore(zip);

        RestoreResult result = BackupService.applyPendingRestore();

        assertEquals(result.message(), RestoreOutcome.RESTORED, result.outcome());
        assertEquals(I18n.t("backup.restored", ""), result.message());
        assertEquals(0, count(DataPaths.backupsDir(), "before-restore-*.zip"));
    }

    // ---- a restore that fails ----

    @Test
    public void aFileThatIsNoDatabaseIsRefusedAndKeptAsAFailedRestore() throws Exception {
        liveDatabase();
        zipHoldingAFakeDatabase(scratch.resolve(BackupService.PENDING_ZIP));
        long before = Files.size(DataPaths.databaseFile());

        RestoreResult result = BackupService.applyPendingRestore();

        assertEquals(RestoreOutcome.FAILED, result.outcome());
        assertEquals("the live database was not touched", before, Files.size(DataPaths.databaseFile()));
        assertFalse(Files.exists(scratch.resolve(BackupService.PENDING_ZIP)));
        assertEquals("kept under a name that is not tried again", 1, count(scratch, "restore-failed-*.zip"));
    }

    @Test
    public void aFailedPlainDatabaseIsKeptWithTheDatabaseEnding() throws Exception {
        liveDatabase();
        write(scratch.resolve(BackupService.PENDING_DB), "this is not a database");

        RestoreResult result = BackupService.applyPendingRestore();

        assertEquals(RestoreOutcome.FAILED, result.outcome());
        assertEquals(1, count(scratch, "restore-failed-*.db"));
        assertEquals(0, count(scratch, "restore-failed-*.zip"));
    }

    @Test
    public void aFailureTidiesTheOldFailedRestoresToo() throws Exception {
        liveDatabase();
        fiveOldFailedRestores();
        zipHoldingAFakeDatabase(scratch.resolve(BackupService.PENDING_ZIP));

        assertEquals(RestoreOutcome.FAILED, BackupService.applyPendingRestore().outcome());

        assertEquals("the new one and the two newest old ones", 3, count(scratch, "restore-failed-*"));
    }

    @Test
    public void aFailureAfterThePicturesWereSwappedPutsTheOldPicturesBack() throws Exception {
        liveDatabase();
        Path zip = backupWithOnePictureThenOtherPictures();
        BackupService.scheduleRestore(zip);
        // a folder where the program expects a file: removing it fails, after the pictures were already swapped
        write(scratch.resolve(DataPaths.DATABASE_FILE_NAME + "-journal").resolve("in-the-way.txt"), "x");

        RestoreResult result = BackupService.applyPendingRestore();

        assertEquals(RestoreOutcome.FAILED, result.outcome());
        Path images = DataPaths.imagesDir();
        assertTrue("the shop's own picture is back", Files.exists(images.resolve("current.jpg")));
        assertFalse("the picture of the backup is not left behind", Files.exists(images.resolve("backup-pic.jpg")));
        assertFalse(Files.exists(scratch.resolve("images.old")));
        assertFalse(Files.exists(scratch.resolve("images.restoring")));
        assertFalse(Files.exists(scratch.resolve("lunavera.db.restoring")));
    }
}
