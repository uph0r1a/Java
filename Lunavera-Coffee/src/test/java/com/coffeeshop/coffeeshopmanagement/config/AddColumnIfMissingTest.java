package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;

public class AddColumnIfMissingTest {

    /**
     * Calling {@code DatabaseConfig.addColumnIfMissing} loads the {@code DatabaseConfig} class, and that
     * class fixes its database file the moment it loads. This test is alphabetically the first one, so
     * without this line it would load the class before any temp folder was set and the whole test run
     * would then use the real {@code data/lunavera.db} (this happened: it left test rows in it).
     * Touching {@code TEMP_HOME} makes every test class agree on one throw-away folder first.
     */
    @BeforeClass
    public static void useTheThrowAwayDataFolder() {
        Object forceStaticInit = TestDatabaseSupport.TEMP_HOME;
    }

    private static int columnCount(Connection c, String table, String column) throws Exception {
        int n = 0;
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) if (column.equals(rs.getString("name"))) n++;
        }
        return n;
    }

    @Test
    public void addsTheColumnToAnOldTableAndKeepsItsRows() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:"); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, total INTEGER)");
            st.execute("INSERT INTO orders (total) VALUES (5000)");
            DatabaseConfig.addColumnIfMissing(c, "orders", "table_number", "INTEGER");
            assertEquals(1, columnCount(c, "orders", "table_number"));
            try (ResultSet rs = st.executeQuery("SELECT total, table_number FROM orders")) {
                rs.next();
                assertEquals(5000, rs.getInt(1));
                rs.getInt(2);
                assertEquals(true, rs.wasNull());
            }
        }
    }

    @Test
    public void doesNothingWhenTheColumnIsAlreadyThere() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:"); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, table_number INTEGER)");
            DatabaseConfig.addColumnIfMissing(c, "orders", "table_number", "INTEGER");
            DatabaseConfig.addColumnIfMissing(c, "orders", "Table_Number", "INTEGER"); // names are case-insensitive in SQLite
            assertEquals(1, columnCount(c, "orders", "table_number"));
        }
    }
}
