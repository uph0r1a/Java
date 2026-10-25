package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The table list and the value conversions of the one-time SQLite to MariaDB import. */
public class SqliteToMariaDbImporterTest {

    private static SqliteToMariaDbImporter.TableSpec table(String name) {
        return SqliteToMariaDbImporter.TABLES.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    public void everyAppTableIsCopiedExceptTheVersionRow() {
        Set<String> copied = new HashSet<>(SqliteToMariaDbImporter.tableNames());
        Set<String> inMaria = new HashSet<>(SchemaColumns.byTable(MariaDbSchema.TABLES).keySet());
        inMaria.remove("schema_version"); // written by the app itself
        inMaria.remove("login_attempts"); // wrong-password counters of the last quarter of an hour, not data worth carrying over (TODO 2A.19)
        assertEquals(inMaria, copied);
    }

    @Test
    public void tablesAreCopiedAfterTheTablesTheyPointTo() {
        Map<String, String> ddl = SchemaColumns.byTable(MariaDbSchema.TABLES);
        List<String> order = SqliteToMariaDbImporter.tableNames();
        for (String name : order) {
            for (String parent : SchemaColumns.referencedTables(ddl.get(name))) {
                assertTrue(name + " is copied before " + parent, order.indexOf(parent) < order.indexOf(name));
            }
        }
    }

    @Test
    public void theCopiedColumnsAreExactlyTheColumnsOfTheMariaDbTables() {
        Map<String, String> ddl = SchemaColumns.byTable(MariaDbSchema.TABLES);
        for (SqliteToMariaDbImporter.TableSpec spec : SqliteToMariaDbImporter.TABLES) {
            assertEquals("columns of " + spec.name(), new HashSet<>(SchemaColumns.columns(ddl.get(spec.name()))),
                    new HashSet<>(spec.columns()));
            assertTrue(spec.name(), spec.columns().containsAll(spec.dateTimeColumns()));
            assertTrue(spec.name(), spec.columns().containsAll(spec.dateColumns()));
        }
    }

    @Test
    public void everyDatetimeColumnOfTheSchemaIsConverted() {
        Map<String, String> ddl = SchemaColumns.byTable(MariaDbSchema.TABLES);
        for (SqliteToMariaDbImporter.TableSpec spec : SqliteToMariaDbImporter.TABLES) {
            for (String line : ddl.get(spec.name()).split("\n")) {
                String l = line.trim();
                if (l.contains(" DATETIME")) {
                    assertTrue(spec.name() + ": " + l, spec.dateTimeColumns().contains(l.split("\\s+")[0]));
                }
                if (l.contains(" DATE,") || l.endsWith(" DATE")) {
                    assertTrue(spec.name() + ": " + l, spec.dateColumns().contains(l.split("\\s+")[0]));
                }
            }
        }
    }

    @Test
    public void dateTextIsConvertedAndEverythingElseIsPassedThrough() {
        SqliteToMariaDbImporter.TableSpec orders = table("orders");
        assertEquals("2026-10-11 14:30:05", SqliteToMariaDbImporter.convert(orders, "order_date", "2026-10-11T14:30:05.5"));
        assertEquals("2026-10-11 14:30:00", SqliteToMariaDbImporter.convert(orders, "paid_at", "2026-10-11T14:30"));
        assertEquals(25000L, SqliteToMariaDbImporter.convert(orders, "total", 25000L));
        assertEquals("PAID", SqliteToMariaDbImporter.convert(orders, "status", "PAID"));
        assertNull(SqliteToMariaDbImporter.convert(orders, "paid_at", null));
        assertEquals("2024-03-01", SqliteToMariaDbImporter.convert(table("employees"), "hire_date", "2024-03-01"));
        assertEquals("2024-03-01", SqliteToMariaDbImporter.convert(table("employees"), "hire_date", " 2024-03-01T00:00 "));
    }

    @Test
    public void aBlankCustomerPhoneBecomesNullSoTheUniqueKeyAllowsIt() {
        SqliteToMariaDbImporter.TableSpec customers = table("customers");
        assertNull(SqliteToMariaDbImporter.convert(customers, "phone", ""));
        assertNull(SqliteToMariaDbImporter.convert(customers, "phone", "   "));
        assertEquals("0912345678", SqliteToMariaDbImporter.convert(customers, "phone", "0912345678"));
        // a blank staff phone becomes NULL too (the staff phone is unique in MariaDB as well)
        assertNull(SqliteToMariaDbImporter.convert(table("employees"), "phone", ""));
        assertEquals("0912345678", SqliteToMariaDbImporter.convert(table("employees"), "phone", "0912345678"));
    }

    // ---- when the import may go ahead ------------------------------------------------------------------------------

    private static final java.util.Set<String> STARTER_PRODUCTS = java.util.Set.of("Brownie", "Croissant", "Cafe sữa");
    private static final java.util.Set<String> STARTER_CATEGORIES = java.util.Set.of("Bánh ngọt", "Cà phê");

    private static SqliteToMariaDbImporter.State classify(int users, int admins, int customers, int orders, int employees,
                                                          java.util.Set<String> products, java.util.Set<String> categories) {
        return SqliteToMariaDbImporter.classify(
                new SqliteToMariaDbImporter.Counts(users, admins, customers, orders, employees, products.size()),
                products, categories, STARTER_PRODUCTS, STARTER_CATEGORIES);
    }

    @Test
    public void aDatabaseWithNothingInItIsEmpty() {
        assertEquals(SqliteToMariaDbImporter.State.EMPTY, classify(0, 0, 0, 0, 0, java.util.Set.of(), java.util.Set.of()));
    }

    @Test
    public void aDatabaseStartedOnceHoldsOnlyFirstRunDataAndMayBeReplaced() {
        // the admin account and its employee row, plus the starter menu: exactly what the first start creates
        assertEquals(SqliteToMariaDbImporter.State.ONLY_FIRST_RUN_DATA,
                classify(1, 1, 0, 0, 1, STARTER_PRODUCTS, STARTER_CATEGORIES));
        // someone already deleted the whole starter menu: still nothing of their own
        assertEquals(SqliteToMariaDbImporter.State.ONLY_FIRST_RUN_DATA,
                classify(1, 1, 0, 0, 1, java.util.Set.of(), java.util.Set.of()));
        // only some of the starter products left
        assertEquals(SqliteToMariaDbImporter.State.ONLY_FIRST_RUN_DATA,
                classify(1, 1, 0, 0, 1, java.util.Set.of("Brownie"), java.util.Set.of("Bánh ngọt")));
    }

    @Test
    public void anythingTheShopAddedMakesItRealDataThatIsNeverTouched() {
        var starterP = STARTER_PRODUCTS;
        var starterC = STARTER_CATEGORIES;
        assertEquals("a product of their own", SqliteToMariaDbImporter.State.HAS_DATA,
                classify(1, 1, 0, 0, 1, java.util.Set.of("Brownie", "Phở bò"), starterC));
        assertEquals("a category of their own", SqliteToMariaDbImporter.State.HAS_DATA,
                classify(1, 1, 0, 0, 1, starterP, java.util.Set.of("Bánh ngọt", "Đồ ăn")));
        assertEquals("a customer", SqliteToMariaDbImporter.State.HAS_DATA, classify(1, 1, 1, 0, 1, starterP, starterC));
        assertEquals("an order", SqliteToMariaDbImporter.State.HAS_DATA, classify(1, 1, 0, 1, 1, starterP, starterC));
        assertEquals("a second account", SqliteToMariaDbImporter.State.HAS_DATA, classify(2, 1, 0, 0, 2, starterP, starterC));
        assertEquals("a second employee", SqliteToMariaDbImporter.State.HAS_DATA, classify(1, 1, 0, 0, 2, starterP, starterC));
        assertEquals("one account that is not the admin", SqliteToMariaDbImporter.State.HAS_DATA,
                classify(1, 0, 0, 0, 1, starterP, starterC));
    }

    @Test
    public void aProductWithOnlyItsPriceChangedIsStillFirstRunData() {
        // the check looks at names, so tidying a starter price does not count as real data
        assertEquals(SqliteToMariaDbImporter.State.ONLY_FIRST_RUN_DATA,
                classify(1, 1, 0, 0, 1, java.util.Set.of("Croissant"), java.util.Set.of("Bánh ngọt")));
    }

    @Test
    public void theStarterNamesUsedForTheCheckAreTheRealStarterMenu() {
        assertTrue(MenuSeedData.ITEMS.size() > 0);
        for (MenuSeedData.MenuItem item : MenuSeedData.ITEMS) {
            assertEquals(SqliteToMariaDbImporter.State.ONLY_FIRST_RUN_DATA,
                    SqliteToMariaDbImporter.classify(new SqliteToMariaDbImporter.Counts(1, 1, 0, 0, 1, 1),
                            java.util.Set.of(item.name()), java.util.Set.of(item.category()),
                            MenuSeedData.ITEMS.stream().map(MenuSeedData.MenuItem::name).collect(java.util.stream.Collectors.toSet()),
                            MenuSeedData.ITEMS.stream().map(MenuSeedData.MenuItem::category).collect(java.util.stream.Collectors.toSet())));
        }
    }

    // ---- what to say when the import fails ------------------------------------------------------------------------

    @Test
    public void aDuplicatePhoneIsExplainedInPlainWords() {
        String text = SqliteToMariaDbImporter.describeFailure(new java.sql.SQLException(
                "Duplicate entry '0912345678' for key 'ux_customers_phone'", "23000", 1062));
        assertTrue(text, text.contains("0912345678"));
        assertTrue(text, text.contains("cùng số điện thoại"));
        assertTrue(text, text.contains("Quản lý khách hàng"));
    }

    @Test
    public void everyOtherFailureKeepsItsOwnMessage() {
        assertEquals("Table 'x' doesn't exist", SqliteToMariaDbImporter.describeFailure(new java.sql.SQLException("Table 'x' doesn't exist", "42S02", 1146)));
        assertEquals("Duplicate entry '1' for key 'PRIMARY'",
                SqliteToMariaDbImporter.describeFailure(new java.sql.SQLException("Duplicate entry '1' for key 'PRIMARY'", "23000", 1062)));
        assertEquals("boom", SqliteToMariaDbImporter.describeFailure(new IllegalStateException("boom")));
    }
}
