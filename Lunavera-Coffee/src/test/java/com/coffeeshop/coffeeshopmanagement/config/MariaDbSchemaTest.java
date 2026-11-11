package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Static checks on the MariaDB table definitions (they cannot be run without a server; the same file is exercised for real
 * by {@code mvn test -Dlunavera.test.db=mariadb}). They catch the usual slips: SQLite-only words, a table created before the one
 * it points to, and a column added to one database but not the other.
 */
public class MariaDbSchemaTest {

    private static final List<String> SQLITE_ONLY = List.of("AUTOINCREMENT", "PRAGMA", "TEXT PRIMARY KEY", "COLLATE NOCASE",
            "INTEGER PRIMARY KEY", "INSERT OR");

    @Test
    public void noSqliteOnlySyntaxLeaksIn() {
        for (String ddl : MariaDbSchema.TABLES) {
            for (String word : SQLITE_ONLY) {
                assertFalse(word + " in:\n" + ddl, ddl.toUpperCase().contains(word));
            }
        }
    }

    @Test
    public void everyTableIsInnoDbUtf8mb4AndRerunnable() {
        for (String ddl : MariaDbSchema.TABLES) {
            assertTrue(ddl, ddl.startsWith("CREATE TABLE IF NOT EXISTS "));
            assertTrue(ddl, ddl.trim().endsWith("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"));
        }
    }

    @Test
    public void aTableIsCreatedAfterTheTablesItPointsTo() {
        Set<String> created = new HashSet<>();
        for (String ddl : MariaDbSchema.TABLES) {
            for (String parent : SchemaColumns.referencedTables(ddl)) {
                assertTrue(SchemaColumns.tableName(ddl) + " points to " + parent + " which is not created before it",
                        created.contains(parent));
            }
            created.add(SchemaColumns.tableName(ddl));
        }
    }

    @Test
    public void dropOrderListsEveryTableWithChildrenBeforeParents() {
        Set<String> tables = SchemaColumns.byTable(MariaDbSchema.TABLES).keySet();
        assertEquals(tables, new HashSet<>(MariaDbSchema.DROP_ORDER));
        assertEquals(MariaDbSchema.DROP_ORDER.size(), tables.size());
        for (String ddl : MariaDbSchema.TABLES) {
            String child = SchemaColumns.tableName(ddl);
            for (String parent : SchemaColumns.referencedTables(ddl)) {
                assertTrue(child + " must be dropped before " + parent,
                        MariaDbSchema.DROP_ORDER.indexOf(child) < MariaDbSchema.DROP_ORDER.indexOf(parent));
            }
        }
    }

    /** The plain (non-unique) {@code INDEX name (columns)} lines of MariaDB's tables, as "table(columns)". Names may differ between the two databases. */
    private static Set<String> mariaPlainIndexes() {
        Set<String> found = new java.util.TreeSet<>();
        java.util.regex.Pattern line = java.util.regex.Pattern.compile("^\\s*INDEX\\s+\\w+\\s*\\(([^)]*)\\)");
        for (String ddl : MariaDbSchema.TABLES) {
            String table = SchemaColumns.tableName(ddl);
            for (String raw : ddl.split("\n")) {
                java.util.regex.Matcher m = line.matcher(raw);
                if (m.find()) found.add(table + "(" + m.group(1).replaceAll("\\s+", "") + ")");
            }
        }
        return found;
    }

    private static Set<String> sqlitePlainIndexes() {
        Set<String> found = new java.util.TreeSet<>();
        for (String[][] group : new String[][][] {SchemaMigrations.INDEXES_BEFORE_V5, SchemaMigrations.INDEXES_SINCE_V5,
                SchemaMigrations.INDEXES_SQLITE_ONLY_SINCE_V5}) {
            for (String[] index : group) found.add(index[1] + "(" + index[2].replaceAll("\\s+", "") + ")");
        }
        return found;
    }

    /**
     * TODO 6.72: SQLite had indexes only for orders, order_items and products; now every plain index MariaDB creates exists on SQLite too (same
     * table and columns; the one old name that differs is {@code idx_orders_table_number} against {@code idx_orders_table}). The reverse is not
     * required: SQLite also indexes five columns that MariaDB gets another way, listed here so a new gap cannot hide among them.
     */
    @Test
    public void everyMariaDbIndexAlsoExistsOnSqlite() {
        Set<String> missing = mariaPlainIndexes();
        missing.removeAll(sqlitePlainIndexes());
        assertTrue("MariaDB indexes SQLite does not have: " + missing, missing.isEmpty());

        Set<String> sqliteOnly = sqlitePlainIndexes();
        sqliteOnly.removeAll(mariaPlainIndexes());
        assertEquals("indexes SQLite has and MariaDB does not create by name: customer_id, order_id, product_id and category_id are indexed by MariaDB's "
                        + "foreign keys, status is the first column of idx_orders_status_date; order_date alone has no MariaDB index",
                new java.util.TreeSet<>(List.of("orders(order_date)", "orders(status)", "orders(customer_id)", "order_items(order_id)",
                        "products(category_id)", "order_items(product_id)")), sqliteOnly);
    }

    /** A name that MariaDB and SQLite both use must stand for the same columns, so a person comparing the two files is not misled. */
    @Test
    public void aSharedIndexNameMeansTheSameColumnsOnBoth() {
        Map<String, String> maria = new java.util.HashMap<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?m)^\\s*INDEX\\s+(\\w+)\\s*\\(([^)]*)\\)");
        for (String ddl : MariaDbSchema.TABLES) {
            java.util.regex.Matcher m = p.matcher(ddl);
            while (m.find()) maria.put(m.group(1), SchemaColumns.tableName(ddl) + "(" + m.group(2).replaceAll("\\s+", "") + ")");
        }
        for (String[][] group : new String[][][] {SchemaMigrations.INDEXES_BEFORE_V5, SchemaMigrations.INDEXES_SINCE_V5,
                SchemaMigrations.INDEXES_SQLITE_ONLY_SINCE_V5}) {
            for (String[] index : group) {
                if (maria.containsKey(index[0])) {
                    assertEquals(index[0], maria.get(index[0]), index[1] + "(" + index[2].replaceAll("\\s+", "") + ")");
                }
            }
        }
    }

    @Test
    public void bothDatabasesHaveTheSameColumnsInEveryTable() {
        Map<String, String> sqlite = SchemaColumns.byTable(SqliteSchema.TABLES);
        Map<String, String> maria = SchemaColumns.byTable(MariaDbSchema.TABLES);
        for (String table : sqlite.keySet()) {
            assertTrue("MariaDB has no table " + table, maria.containsKey(table));
            Set<String> expected = new HashSet<>(SchemaColumns.columns(sqlite.get(table)));
            for (String[] later : SqliteSchema.LATER_COLUMNS) {
                if (later[0].equals(table)) expected.add(later[1]);
            }
            Set<String> actual = new HashSet<>(SchemaColumns.columns(maria.get(table)));
            if (table.equals("users")) actual.remove("username_key"); // MariaDB only: the lower-case key that makes login names unique (TODO 1.9)
            // MariaDB only (TODO 2.98): the stored generated columns behind its "one at a time" unique keys; SQLite has partial unique indexes
            if (table.equals("orders")) actual.remove("open_table_key");
            if (table.equals("time_clock")) actual.remove("open_user_key");
            assertEquals("columns of " + table, expected, actual);
        }
        List<String> onlyInMaria = new ArrayList<>(maria.keySet());
        onlyInMaria.removeAll(sqlite.keySet());
        assertEquals(List.of("app_flags", "schema_version"), onlyInMaria);
    }

    @Test
    public void moneyIsWholeDongAndDatesAreRealDatetimes() {
        String orders = SchemaColumns.byTable(MariaDbSchema.TABLES).get("orders");
        for (String money : List.of("subtotal", "discount", "total")) {
            assertTrue(money, orders.contains(money + " BIGINT"));
        }
        assertTrue(orders.contains("order_date DATETIME NOT NULL"));
        assertTrue(orders.contains("paid_at DATETIME NULL"));
        String products = SchemaColumns.byTable(MariaDbSchema.TABLES).get("products");
        assertTrue(products.contains("price BIGINT NOT NULL"));
        assertTrue(products.contains("cost BIGINT"));
        assertTrue(SchemaColumns.byTable(MariaDbSchema.TABLES).get("employees").contains("hire_date DATE"));
    }

    @Test
    public void loginNamesStayCaseSensitiveAndCustomersKeepOnePhoneEach() {
        String users = SchemaColumns.byTable(MariaDbSchema.TABLES).get("users");
        assertTrue(users, users.contains("username VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL UNIQUE"));
        // ...yet Admin and admin are one account for the server (TODO 1.9): a stored lower-case copy with its own unique key
        assertTrue(users, users.contains("username_key VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin AS (LOWER(username)) STORED"));
        assertTrue(users, users.contains("UNIQUE KEY ux_users_username_key (username_key)"));
        String customers = SchemaColumns.byTable(MariaDbSchema.TABLES).get("customers");
        assertTrue(customers, customers.contains("UNIQUE KEY ux_customers_phone (phone)"));
    }

    @Test
    public void everyIndexedTextColumnHasALength() {
        for (String ddl : MariaDbSchema.TABLES) {
            for (String line : ddl.split("\n")) {
                String l = line.trim().toUpperCase();
                if (l.endsWith(" TEXT UNIQUE") || l.contains(" TEXT PRIMARY") || l.contains(" TEXT NOT NULL UNIQUE")) {
                    throw new AssertionError("a plain TEXT column cannot be a key in MySQL: " + line);
                }
            }
        }
    }
}
