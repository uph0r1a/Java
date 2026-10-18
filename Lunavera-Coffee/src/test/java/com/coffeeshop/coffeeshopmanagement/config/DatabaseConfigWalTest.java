package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 6.16: the SQLite file runs in write-ahead logging, and every connection asks for the matching {@code synchronous}
 * level. SQLite only (the MariaDB run skips these).
 */
public class DatabaseConfigWalTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private static String pragma(Connection connection, String name) throws Exception {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery("PRAGMA " + name)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    @Test
    public void theDatabaseIsInWriteAheadMode() throws Exception {
        TestDatabaseSupport.requireSqlite();
        try (Connection c = DatabaseConfig.getConnection()) {
            assertEquals("wal", pragma(c, "journal_mode").toLowerCase());
        }
    }

    @Test
    public void everyConnectionUsesNormalSynchronousAndKeepsItsOtherSettings() throws Exception {
        TestDatabaseSupport.requireSqlite();
        try (Connection c = DatabaseConfig.getConnection()) {
            assertEquals("1 is NORMAL", "1", pragma(c, "synchronous"));
            assertEquals("1", pragma(c, "foreign_keys"));
            assertEquals("5000", pragma(c, "busy_timeout"));
        }
    }

    @Test
    public void aWriteWhileAnotherConnectionIsOpenGoesThroughTheLogFile() throws Exception {
        TestDatabaseSupport.requireSqlite();
        Path wal = Path.of(DatabaseConfig.databaseFile() + "-wal");
        try (Connection reader = DatabaseConfig.getConnection()) {
            pragma(reader, "journal_mode"); // keep one connection open, so the log is not removed after the next one closes
            try (Connection writer = DatabaseConfig.getConnection();
                 PreparedStatement st = writer.prepareStatement("INSERT INTO categories (name, description, active) VALUES (?, '', 1)")) {
                st.setString(1, "wal-" + UUID.randomUUID());
                st.executeUpdate();
            }
            assertTrue("the write is in " + wal.getFileName(), Files.exists(wal));
        }
    }

    @Test
    public void aBackupOfAWalDatabaseIsStillAPlainValidFile() throws Exception {
        TestDatabaseSupport.requireSqlite();
        Path target = Files.createTempDirectory("lunavera-wal-test").resolve("copy.db");
        DatabaseConfig.backupTo(target);
        assertTrue(Files.size(target) > 0);
        try (Connection copy = java.sql.DriverManager.getConnection("jdbc:sqlite:" + target)) {
            assertEquals("the copy needs no -wal file to be complete", "ok", pragma(copy, "integrity_check"));
        }
    }
}
