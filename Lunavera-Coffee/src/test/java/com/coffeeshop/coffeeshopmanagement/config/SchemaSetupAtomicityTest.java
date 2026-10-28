package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * TODO 3.37: the set-up of a new SQLite database is one transaction. Before, about nine auto-committed {@code CREATE TABLE}
 * statements came first and the version number last; a start killed in between left a file with a {@code users} table and version 0,
 * which the next start took for an old database and ran the money migration on, replacing the current table definitions with the
 * first release's. Every test works on a private file and simulates the crash with a statement that fails.
 */
public class SchemaSetupAtomicityTest {

    @BeforeClass
    public static void setUp() {
        TestDatabaseSupport.requireSqlite();
        TestDatabaseSupport.ensureReady();
    }

    private static Connection newFile() throws Exception {
        Path file = Files.createTempFile("lunavera-setup", ".db");
        Files.delete(file); // a new file is created on connect, as for a new shop
        return DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
    }

    private static List<String> tables(Connection c) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            while (rs.next()) names.add(rs.getString(1));
        }
        return names;
    }

    private static int version(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static List<String> columns(Connection c, String table) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) names.add(rs.getString("name"));
        }
        return names;
    }

    private static String declaredType(Connection c, String table, String column) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) if (column.equals(rs.getString("name"))) return rs.getString("type");
        }
        return null;
    }

    /** The real statements up to and including {@code users} (the table the next start looks for), then one that cannot run. */
    private static List<String> tablesThenACrash() {
        List<String> ddl = new ArrayList<>();
        for (String statement : SqliteSchema.TABLES) {
            ddl.add(statement);
            if (statement.contains("EXISTS users")) break;
        }
        ddl.add("CREATE TABLE the_process_died_here (");
        return ddl;
    }

    // ---- a new database is all or nothing

    @Test
    public void aSetupThatFailsAfterTheUsersTableLeavesNoTableAndVersionZero() throws Exception {
        try (Connection c = newFile()) {
            try {
                DatabaseConfig.createFreshSqliteSchema(c, tablesThenACrash());
                fail("the broken statement must make the set-up fail");
            } catch (SQLException expected) {
                // the process died here
            }
            assertEquals("no table is left behind, not even the ones created before the failure", List.of(), tables(c));
            assertEquals(0, version(c));
            assertTrue("the connection is back in auto-commit mode", c.getAutoCommit());
        }
    }

    @Test
    public void aFailureIsAlsoUndoneInTheFileAndNotJustInTheConnection() throws Exception {
        Path file = Files.createTempFile("lunavera-setup", ".db");
        Files.delete(file);
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath())) {
            assertThrows(SQLException.class, () -> DatabaseConfig.createFreshSqliteSchema(c, tablesThenACrash()));
        }
        try (Connection again = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath())) {
            assertEquals("a new connection to the same file sees an empty database", List.of(), tables(again));
            assertEquals(0, version(again));
        }
    }

    @Test
    public void theNextStartAfterTheFailureIsAFreshCompleteSetupNotAMigration() throws Exception {
        try (Connection c = newFile()) {
            assertThrows(SQLException.class, () -> DatabaseConfig.createFreshSqliteSchema(c, tablesThenACrash()));
            // next start: no users table, so it is a new database again
            DatabaseConfig.createFreshSqliteSchema(c, SqliteSchema.TABLES);
            assertEquals(DatabaseConfig.CURRENT_SCHEMA_VERSION, version(c));
            for (String name : tables(c)) assertFalse("no table of the money migration: " + name, name.endsWith("_old_v0"));
            assertTrue(tables(c).containsAll(List.of("employees", "customers", "users", "categories", "products", "orders", "order_items")));
            assertEquals("the current definition, not the first release's", "INTEGER", declaredType(c, "products", "price"));
        }
    }

    @Test
    public void aCompleteSetupHasEveryTableTheLaterColumnsAndTheVersion() throws Exception {
        try (Connection c = newFile()) {
            DatabaseConfig.createFreshSqliteSchema(c, SqliteSchema.TABLES);
            assertEquals(DatabaseConfig.CURRENT_SCHEMA_VERSION, version(c));
            for (String[] later : SqliteSchema.LATER_COLUMNS) {
                assertTrue(later[0] + "." + later[1], columns(c, later[0]).contains(later[1]));
            }
        }
    }

    @Test
    public void settingUpTwiceChangesNothing() throws Exception {
        try (Connection c = newFile()) {
            DatabaseConfig.createFreshSqliteSchema(c, SqliteSchema.TABLES);
            List<String> before = tables(c);
            DatabaseConfig.createFreshSqliteSchema(c, SqliteSchema.TABLES);
            assertEquals(before, tables(c));
            assertEquals(DatabaseConfig.CURRENT_SCHEMA_VERSION, version(c));
        }
    }

    // ---- the helper itself

    @Test
    public void runAtomicallyUndoesTablesColumnsIndexesAndTheVersionTogether() throws Exception {
        try (Connection c = newFile()) {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE keep (id INTEGER PRIMARY KEY, name TEXT)");
                st.execute("PRAGMA user_version = 7");
            }
            assertThrows(IllegalStateException.class, () -> DatabaseConfig.runAtomically(c, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("CREATE TABLE added (id INTEGER)");
                    st.execute("ALTER TABLE keep ADD COLUMN extra TEXT");
                    st.execute("CREATE INDEX idx_added ON keep(name)");
                    st.execute("PRAGMA user_version = 8");
                }
                throw new IllegalStateException("killed");
            }));
            assertEquals(List.of("keep"), tables(c));
            assertEquals(List.of("id", "name"), columns(c, "keep"));
            assertEquals(7, version(c));
            assertTrue(c.getAutoCommit());
        }
    }

    @Test
    public void runAtomicallyKeepsEverythingWhenTheWorkSucceeds() throws Exception {
        try (Connection c = newFile()) {
            DatabaseConfig.runAtomically(c, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("CREATE TABLE made (id INTEGER)");
                    st.execute("PRAGMA user_version = 3");
                }
            });
            assertEquals(List.of("made"), tables(c));
            assertEquals(3, version(c));
            assertTrue(c.getAutoCommit());
        }
    }

    @Test
    public void runAtomicallyJoinsATransactionThatIsAlreadyOpen() throws Exception {
        try (Connection c = newFile()) {
            c.setAutoCommit(false);
            DatabaseConfig.runAtomically(c, () -> {
                try (Statement st = c.createStatement()) {
                    st.execute("CREATE TABLE joined (id INTEGER)");
                }
            });
            assertFalse("it did not commit the caller's transaction", c.getAutoCommit());
            c.rollback();
            assertEquals(List.of(), tables(c));
        }
    }

    // ---- an upgrade step and its version number

    @Test
    public void aStepThatFailsLeavesTheDatabaseAtTheOldVersionWithNoneOfTheStepApplied() throws Exception {
        MariaDbMigrations.Step original = SchemaMigrations.SQLITE_STEPS.get(SchemaMigrations.LATEST_VERSION);
        try (Connection c = newFile()) {
            DatabaseConfig.createFreshSqliteSchema(c, SqliteSchema.TABLES);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA user_version = " + (SchemaMigrations.LATEST_VERSION - 1));
            }
            SchemaMigrations.SQLITE_STEPS.put(SchemaMigrations.LATEST_VERSION, connection -> {
                try (Statement st = connection.createStatement()) {
                    st.execute("CREATE INDEX idx_half_done ON orders(status)");
                }
                throw new SQLException("killed in the middle of the step");
            });
            assertThrows(SQLException.class, () -> SchemaMigrations.upgrade(c));
            assertEquals(SchemaMigrations.LATEST_VERSION - 1, version(c));
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE name = 'idx_half_done'")) {
                rs.next();
                assertEquals("half of the step is not left behind", 0, rs.getInt(1));
            }
        } finally {
            SchemaMigrations.SQLITE_STEPS.put(SchemaMigrations.LATEST_VERSION, original);
        }
    }

    // ---- a database already left half-made by an older build

    /** The tables of a new database but version 0: what every build before TODO 3.37 could leave after a crash. */
    private static Connection leftoverOfACrashedSetup() throws Exception {
        Connection c = newFile();
        try (Statement st = c.createStatement()) {
            for (String ddl : SqliteSchema.TABLES) st.execute(ddl);
        }
        DatabaseConfig.addLaterColumns(c);
        return c;
    }

    private static Map<String, String> definitions(Connection c) throws SQLException {
        Map<String, String> sql = new LinkedHashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name, sql FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            while (rs.next()) sql.put(rs.getString(1), rs.getString(2));
        }
        return sql;
    }

    @Test
    public void aNewFormatDatabaseAtVersionZeroIsNotRebuiltByTheMoneyMigration() throws Exception {
        try (Connection c = leftoverOfACrashedSetup()) {
            try (Statement st = c.createStatement()) {
                st.execute("INSERT INTO categories (name) VALUES ('Coffee')");
                st.execute("INSERT INTO products (name, category_id, price, stock) VALUES ('Latte', 1, 45000, 3)");
                st.execute("INSERT INTO orders (order_date, status, total) VALUES ('2026-10-27T09:00:00', 'PAID', 45000)");
                st.execute("INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, line_total, unit_cost) "
                        + "VALUES (1, 1, 'Latte', 1, 45000, 45000, 12000)");
            }
            assertEquals(0, version(c));
            Map<String, String> before = definitions(c);

            MoneyMigration.runIfNeeded(c, DatabaseConfig.CURRENT_SCHEMA_VERSION);

            assertEquals("the version is recorded", DatabaseConfig.CURRENT_SCHEMA_VERSION, version(c));
            assertEquals("no table definition was replaced", before, definitions(c));
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT unit_cost FROM order_items WHERE id = 1")) {
                rs.next();
                assertEquals("a column the rebuild would have emptied", 12000, rs.getInt(1));
            }
        }
    }

    @Test
    public void theGuardTellsTheNewFormatFromTheFirstReleasesRealMoney() throws Exception {
        try (Connection c = leftoverOfACrashedSetup()) {
            assertTrue(MoneyMigration.alreadyInIntegerDong(c));
        }
        try (Connection old = newFile()) {
            try (Statement st = old.createStatement()) {
                st.execute("CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT, price REAL NOT NULL)");
            }
            assertFalse("the first release declared price REAL: that one still needs converting", MoneyMigration.alreadyInIntegerDong(old));
        }
        try (Connection none = newFile()) {
            assertFalse("no products table: not judged, the old behaviour stays", MoneyMigration.alreadyInIntegerDong(none));
        }
    }
}
