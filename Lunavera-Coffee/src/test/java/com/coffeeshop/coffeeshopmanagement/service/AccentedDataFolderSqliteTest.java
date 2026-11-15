package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.service.BackupService.RestoreOutcome;
import com.coffeeshop.coffeeshopmanagement.service.BackupService.RestoreResult;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TODO 3.70, the SQLite half: a shop whose data folder, backup folder and backup file are named with Vietnamese letters makes a
 * backup of its SQLite database and its pictures, and restores from it. {@code AccentedDataFolderTest} covers the file side without a
 * database (and runs without the SQLite driver); this class needs the driver, so it is skipped when the run uses MariaDB (the
 * MariaDB dump in an accented folder is {@code MariaDbBackupFolderTest}) and when the JVM cannot make such a file name (a plain "C"
 * locale; run with a UTF-8 locale). Like {@code BackupServiceTest}, it points {@code lunavera.dataDir} at a scratch folder of its own
 * so the shared test database is never replaced. What stays a human check is the same thing under a real Windows account whose
 * user name has Vietnamese letters (section 0 of the TODO list).
 */
public class AccentedDataFolderSqliteTest {

    private static final String DATA_FOLDER = "Quán Cà Phê dữ liệu";
    private static final String BACKUP_FOLDER = "Sao lưu cửa hàng";
    private static final String PICTURE = "ảnh cà phê sữa đá.jpg";

    private String savedDataDir;
    private Path data;

    @BeforeClass
    public static void setUpDatabase() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.admin();
        TestDatabaseSupport.requireSqlite();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void pointTheDataFolderAtAnAccentedName() throws IOException {
        savedDataDir = System.getProperty(DataPaths.DATA_DIR_PROPERTY);
        Path base = Files.createTempDirectory("accented-sqlite-base");
        try {
            data = Files.createDirectories(base.resolve(DATA_FOLDER));
        } catch (InvalidPathException | IOException cannotNameIt) {
            Assume.assumeTrue("this JVM cannot make a file name with Vietnamese letters (use a UTF-8 locale)", false);
        }
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, data.toString());
    }

    @After
    public void restoreTheDataFolder() {
        if (savedDataDir == null) System.clearProperty(DataPaths.DATA_DIR_PROPERTY);
        else System.setProperty(DataPaths.DATA_DIR_PROPERTY, savedDataDir);
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

    private static Set<String> entries(Path zip) throws IOException {
        Set<String> names = new HashSet<>();
        try (ZipFile z = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            z.stream().forEach(e -> names.add(e.getName()));
        }
        return names;
    }

    @Test
    public void aSqliteBackupMadeInAnAccentedFolderRestoresTheDatabaseAndTheAccentedPicture() throws Exception {
        // the live database of this accented data folder: a valid copy of the test database (as BackupServiceTest does)
        DatabaseConfig.backupTo(DataPaths.databaseFile());
        Path live = DataPaths.databaseFile();
        assertTrue("the database file is inside the accented data folder", live.toString().contains(DATA_FOLDER));

        Path picture = DataPaths.imagesDir().resolve(PICTURE);
        Files.createDirectories(picture.getParent());
        Files.writeString(picture, "old picture", StandardCharsets.UTF_8);

        // the backup goes to an accented folder, under an accented name
        Path target = data.resolve(BACKUP_FOLDER).resolve("Sao lưu ngày hôm nay.zip");
        Path made = BackupService.createBackup(target);
        assertEquals("Sao lưu ngày hôm nay.zip", made.getFileName().toString());
        assertTrue(Files.isRegularFile(made));
        Set<String> names = entries(made);
        assertTrue(names.toString(), names.contains("lunavera.db"));
        assertTrue(names.toString(), names.contains("images/" + PICTURE));
        assertTrue(names.toString(), names.contains("backup-info.txt"));

        // the shop carries on: a new row, the picture gone and another one in its place
        exec(live, "INSERT INTO categories(name, description, active) VALUES ('Cà phê sữa - sau sao lưu','',1)");
        Files.delete(picture);
        Files.writeString(DataPaths.imagesDir().resolve("ảnh mới.jpg"), "new picture", StandardCharsets.UTF_8);

        BackupService.scheduleRestore(made);
        assertTrue(BackupService.restorePending());
        RestoreResult result = BackupService.applyPendingRestore();
        assertEquals(result.message(), RestoreOutcome.RESTORED, result.outcome());
        assertFalse(BackupService.restorePending());

        assertEquals(0, count(live, "SELECT COUNT(*) FROM categories WHERE name='Cà phê sữa - sau sao lưu'"));
        assertEquals("old picture", Files.readString(DataPaths.imagesDir().resolve(PICTURE), StandardCharsets.UTF_8));
        assertFalse(Files.exists(DataPaths.imagesDir().resolve("ảnh mới.jpg")));
        assertTrue("the state before the restore was kept inside the accented folder",
                Files.isDirectory(DataPaths.backupsDir()) && DataPaths.backupsDir().toString().contains(DATA_FOLDER));
    }
}
