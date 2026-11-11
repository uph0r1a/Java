package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * TODO 1.8: the whole in-app restore on a MariaDB {@code _test} database: a real backup zip (made by the program, so it needs
 * {@code mysqldump}), the safety copy, the pictures, the audit line and the refusals around it. Skipped on SQLite and when
 * {@code mysqldump} cannot be run. Run with {@code -Dlunavera.test.db=mariadb}.
 */
public class MariaDbRestoreServiceServerTest {

    @BeforeClass
    public static void database() {
        TestDatabaseSupport.ensureReady();
        Assume.assumeTrue("MariaDB-only test (this run uses SQLite)", !DatabaseConfig.isSqlite());
    }

    @Before
    public void admin() {
        TestSession.admin();
    }

    @After
    public void clear() {
        TestSession.clear();
    }

    private static void addCategory(String name) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO categories (name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
    }

    private static boolean categoryExists(String name) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM categories WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private static void removeCategory(String name) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM categories WHERE name = ?")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
    }

    private static Path backupOrSkip(Path target) throws Exception {
        try {
            return BackupService.createBackup(target);
        } catch (IOException cannotDump) {
            Assume.assumeTrue("mysqldump cannot be run here: " + cannotDump.getMessage(), false);
            return null;
        }
    }

    private static void writePicture(String name, String text) throws IOException {
        Files.createDirectories(DataPaths.imagesDir());
        Files.writeString(DataPaths.imagesDir().resolve(name), text);
    }

    @Test
    public void aBackupZipRestoresTheRowsThePicturesAndLeavesASafetyCopy() throws Exception {
        String before = "svc-before-" + UUID.randomUUID().toString().substring(0, 8);
        String after = "svc-after-" + UUID.randomUUID().toString().substring(0, 8);
        String picture = "svc-" + UUID.randomUUID().toString().substring(0, 8) + ".txt";
        String laterPicture = "svc-later-" + UUID.randomUUID().toString().substring(0, 8) + ".txt";
        addCategory(before);
        writePicture(picture, "old picture");
        Path zip = backupOrSkip(Files.createTempFile("svc-restore", ".zip"));
        try {
            addCategory(after);
            removeCategory(before);
            writePicture(laterPicture, "added after the backup");
            Files.delete(DataPaths.imagesDir().resolve(picture));

            BackupService.Summary summary = MariaDbRestoreService.inspect(zip);
            assertTrue(summary.picturesIncluded());
            assertFalse("inspecting changes nothing", categoryExists(before));

            String message = MariaDbRestoreService.restoreNow(zip);
            assertFalse(message.isBlank());
            assertTrue("the backed-up row is back", categoryExists(before));
            assertFalse("the later row is gone", categoryExists(after));
            assertTrue("the backed-up picture is back", Files.exists(DataPaths.imagesDir().resolve(picture)));
            assertFalse("a picture added after the backup is gone", Files.exists(DataPaths.imagesDir().resolve(laterPicture)));
            assertFalse("no leftovers", Files.exists(DataPaths.dataDir().resolve("images.old"))
                    || Files.exists(DataPaths.dataDir().resolve("images.restoring")));
            try (Stream<Path> files = Files.list(DataPaths.backupsDir())) {
                assertTrue("the safety copy is in the backups folder",
                        files.anyMatch(f -> f.getFileName().toString().startsWith("before-restore-")));
            }
            try (Connection c = DatabaseConfig.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM audit_log WHERE action = 'BACKUP_RESTORED'");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertTrue("the restore is in the audit log", rs.getInt(1) >= 1);
            }
        } finally {
            Files.deleteIfExists(zip);
            removeCategory(before);
        }
    }

    @Test
    public void aCashierMayNotRestore() throws Exception {
        TestSession.clear();
        Path zip = Files.createTempFile("svc-denied", ".zip");
        try {
            MariaDbRestoreService.restoreNow(zip);
            fail("only an administrator may restore");
        } catch (RuntimeException | IOException expected) {
            // the permission check comes before the file is even looked at
        } finally {
            Files.deleteIfExists(zip);
        }
    }

    @Test
    public void aZipWithoutTheDumpIsRefusedAndNothingChanges() throws Exception {
        String mark = "svc-keep-" + UUID.randomUUID().toString().substring(0, 8);
        addCategory(mark);
        Path zip = Files.createTempFile("svc-nodump", ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("backup-info.txt"));
            out.write("Lunavera Coffee backup\n".getBytes());
            out.closeEntry();
        }
        try {
            MariaDbRestoreService.restoreNow(zip);
            fail("a zip with no dump is not a backup");
        } catch (IOException expected) {
            assertFalse(expected.getMessage().isBlank());
        } finally {
            Files.deleteIfExists(zip);
        }
        assertTrue(categoryExists(mark));
        removeCategory(mark);
    }

    @Test
    public void aDamagedZipIsRefusedAndNothingChanges() throws Exception {
        String mark = "svc-damaged-" + UUID.randomUUID().toString().substring(0, 8);
        addCategory(mark);
        Path zip = Files.createTempFile("svc-damaged", ".zip");
        Files.writeString(zip, "this is not a zip file");
        try {
            MariaDbRestoreService.restoreNow(zip);
            fail("a damaged zip must be refused");
        } catch (IOException expected) {
            assertFalse(expected.getMessage().isBlank());
        } finally {
            Files.deleteIfExists(zip);
        }
        assertTrue(categoryExists(mark));
        removeCategory(mark);
    }
}
