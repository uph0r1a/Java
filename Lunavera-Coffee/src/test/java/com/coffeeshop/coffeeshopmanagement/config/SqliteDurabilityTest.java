package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;

/**
 * TODO 2.92: a new SQLite connection answers {@code PRAGMA synchronous} with 2 (FULL), with write-ahead logging on and with it off.
 * A power cut itself cannot be staged here; this checks the setting that decides what survives one.
 */
public class SqliteDurabilityTest {

    private static int synchronousOf(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("PRAGMA synchronous")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    public void aConnectionOfTheProgramIsFullWhileWalIsOn() throws Exception {
        TestDatabaseSupport.ensureReady();
        try (Connection connection = DatabaseConfig.getConnection()) {
            assertEquals(2, synchronousOf(connection));
        }
    }

    @Test
    public void aConnectionIsFullAlsoWhenTheFileIsNotInWalMode() throws Exception {
        // a fresh in-memory database is never in WAL mode: the same set-up the program uses when WAL was refused
        try (Connection connection = DatabaseConfig.configureSqlite(DriverManager.getConnection("jdbc:sqlite::memory:"))) {
            assertEquals(2, synchronousOf(connection));
        }
    }
}
