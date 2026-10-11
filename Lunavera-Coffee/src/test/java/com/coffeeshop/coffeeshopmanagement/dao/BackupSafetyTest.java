package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** The backup must never be able to erase the database it is copying (TODO bug 2.23). */
public class BackupSafetyTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private static long users() throws Exception {
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    public void refusesToOverwriteTheLiveDatabaseAndLeavesItIntact() throws Exception {
        long before = users();
        long sizeBefore = Files.size(DatabaseConfig.databaseFile());
        assertTrue(before >= 1);

        for (Path target : new Path[]{
                DatabaseConfig.databaseFile(),
                DatabaseConfig.databaseFile().getParent().resolve(".").resolve("lunavera.db")}) {
            try {
                DatabaseConfig.backupTo(target);
                fail("backing up onto the live database must be refused: " + target);
            } catch (IOException expected) {
                // refused before anything was deleted
            }
        }

        assertEquals("the database is untouched", before, users());
        assertTrue(Files.size(DatabaseConfig.databaseFile()) >= sizeBefore);
    }

    @Test
    public void aBackupToAnotherFileWorksAndIsAValidDatabase() throws Exception {
        Path target = Files.createTempDirectory("lunavera-backup-test").resolve("copy.db");

        DatabaseConfig.backupTo(target);

        assertTrue(Files.size(target) > 0);
        try (Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + target);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            assertEquals(users(), rs.getLong(1));
        }
    }

    @Test
    public void theDatabaseLivesInTheConfiguredDataFolder() {
        assertEquals(com.coffeeshop.coffeeshopmanagement.util.DataPaths.databaseFile().toAbsolutePath().normalize(),
                DatabaseConfig.databaseFile().toAbsolutePath().normalize());
    }
}
