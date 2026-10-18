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
            assertEquals("columns of " + table, expected, new HashSet<>(SchemaColumns.columns(maria.get(table))));
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
