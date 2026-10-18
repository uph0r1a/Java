package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * TODO 6.25 / 6.27: {@code DatabaseConfig.backupTo} on SQLite. The other backup rules (refusing the live file, the
 * {@code .zip} name, an older zip replaced only on success) are tested in {@code BackupSafetyTest} and
 * {@code BackupServiceTest}; this adds what was missing: the copy has the same rows, an existing target is replaced, nothing
 * is left beside it, and a failed copy keeps an older file. Replaces the throw-away {@code BackupRepro} program of the 5th scan.
 */
public class BackupToTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private static List<String> tableNames(Connection c) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            while (rs.next()) names.add(rs.getString(1));
        }
        return names;
    }

    private static long rows(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    public void theCopyHasTheSameTablesAndRowCountsAsTheLiveDatabase() throws Exception {
        TestDatabaseSupport.requireSqlite();
        Path target = Files.createTempDirectory("lunavera-backupto").resolve("copy.db");
        try (Connection live = DatabaseConfig.getConnection()) {
            DatabaseConfig.backupTo(target);
            try (Connection copy = DriverManager.getConnection("jdbc:sqlite:" + target)) {
                assertEquals(tableNames(live), tableNames(copy));
                for (String table : tableNames(live)) {
                    assertEquals("rows in " + table, rows(live, table), rows(copy, table));
                }
            }
        }
    }

    @Test
    public void anExistingTargetIsReplacedAndNothingIsLeftBesideIt() throws Exception {
        TestDatabaseSupport.requireSqlite();
        Path folder = Files.createTempDirectory("lunavera-backupto");
        Path target = folder.resolve("copy.db");
        Files.writeString(target, "an older file that is not a database", StandardCharsets.UTF_8);

        DatabaseConfig.backupTo(target);

        assertTrue("the old text is gone", Files.size(target) > 1000);
        try (Connection copy = DriverManager.getConnection("jdbc:sqlite:" + target)) {
            assertFalse(tableNames(copy).isEmpty());
        }
        try (var listing = Files.list(folder)) {
            assertEquals("only the backup itself is in the folder", List.of("copy.db"),
                    listing.map(p -> p.getFileName().toString()).toList());
        }
    }

    @Test
    public void aCopyThatCannotBeWrittenKeepsTheOlderFile() throws Exception {
        TestDatabaseSupport.requireSqlite();
        Path folder = Files.createTempDirectory("lunavera-backupto");
        Path target = folder.resolve("copy.db");
        String older = "older backup content";
        Files.writeString(target, older, StandardCharsets.UTF_8);
        // A folder the program cannot write into (not enforced for an administrator / on every system: then there is nothing to test).
        assertTrue(folder.toFile().setWritable(false));
        try {
            org.junit.Assume.assumeFalse("this system lets the test write anyway", Files.isWritable(folder));
            try {
                DatabaseConfig.backupTo(target);
                fail("writing into a read-only folder must fail");
            } catch (SQLException | IOException expected) {
                // expected
            }
            assertEquals("the older file is untouched", older, Files.readString(target, StandardCharsets.UTF_8));
        } finally {
            folder.toFile().setWritable(true);
        }
    }

    @Test
    public void aPartialFileFromAnEarlierCrashDoesNotBlockTheNextBackup() throws Exception {
        TestDatabaseSupport.requireSqlite();
        Path folder = Files.createTempDirectory("lunavera-backupto");
        Path target = folder.resolve("copy.db");
        Files.writeString(folder.resolve("copy.db.part"), "half written", StandardCharsets.UTF_8);

        DatabaseConfig.backupTo(target);

        assertTrue(Files.size(target) > 1000);
        assertFalse(Files.exists(folder.resolve("copy.db.part")));
    }
}
