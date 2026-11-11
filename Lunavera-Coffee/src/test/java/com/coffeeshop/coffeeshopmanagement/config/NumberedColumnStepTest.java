package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** TODO 6.6: the helper a numbered step uses to add columns to a database that already exists. Needs the SQLite driver. */
public class NumberedColumnStepTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.requireSqlite();
    }

    private static boolean hasColumn(Connection c, String table, String column) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return true;
            }
        }
        return false;
    }

    @Test
    public void theStepAddsTheColumnOnceAndKeepsTheRows() throws Exception {
        Path file = Files.createTempFile("lunavera-columns", ".db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath()); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE t(id INTEGER PRIMARY KEY, name TEXT)");
            st.execute("INSERT INTO t(name) VALUES ('keep me')");
            MariaDbMigrations.Step step = SchemaMigrations.addColumns(
                    new String[]{"t", "extra", "INTEGER NOT NULL DEFAULT 0"}, new String[]{"t", "note", "VARCHAR(40)"});
            assertTrue(!hasColumn(c, "t", "extra"));
            step.apply(c);
            assertTrue(hasColumn(c, "t", "extra"));
            assertTrue(hasColumn(c, "t", "note"));
            step.apply(c); // a second run changes nothing and does not fail
            try (ResultSet rs = st.executeQuery("SELECT name, extra, note FROM t")) {
                assertTrue(rs.next());
                assertEquals("keep me", rs.getString(1));
                assertEquals(0, rs.getInt(2));
                assertEquals(null, rs.getString(3));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void aStepThatFailsLeavesTheVersionAndTheColumnsAsTheyWere() throws Exception {
        Path file = Files.createTempFile("lunavera-columns-fail", ".db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath()); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE t(id INTEGER PRIMARY KEY)");
            MariaDbMigrations.Step step = SchemaMigrations.addColumns(
                    new String[]{"t", "first_col", "INTEGER"}, new String[]{"no_such_table", "x", "INTEGER"});
            try {
                DatabaseConfig.runAtomically(c, () -> {
                    step.apply(c);
                    DatabaseConfig.setUserVersion(c, 9);
                });
            } catch (Exception expected) {
                // the second column cannot be added
            }
            assertTrue("the first column was rolled back with the rest", !hasColumn(c, "t", "first_col"));
            try (ResultSet rs = st.executeQuery("PRAGMA user_version")) {
                rs.next();
                assertEquals(0, rs.getInt(1));
            }
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
