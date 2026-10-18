package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 2.6 (indexes as schema version 2) and 2.7 (one customer per phone, enforced by the database). */
public class SchemaMigrationsTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.requireSqlite();
        TestDatabaseSupport.ensureReady();
    }

    private static List<String> indexNames(Connection c) throws Exception {
        List<String> names = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'")) {
            while (rs.next()) names.add(rs.getString(1));
        }
        return names;
    }

    private static long scalar(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** A private copy of the schema as an older database would have it: version 1, no extra indexes. */
    private static Connection oldVersionCopy(Path file) throws Exception {
        DatabaseConfig.backupTo(file);
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
        try (Statement st = c.createStatement()) {
            for (String name : indexNames(c)) st.execute("DROP INDEX " + name);
            st.execute("PRAGMA user_version = 1");
        }
        return c;
    }

    /** TODO 6.6: raising LATEST_VERSION without writing its step would silently mark old databases as upgraded. */
    @Test
    public void everySqliteVersionAfterOneHasAStep() {
        for (int version = 2; version <= SchemaMigrations.LATEST_VERSION; version++) {
            assertTrue("no SQLite step registered for version " + version,
                    SchemaMigrations.SQLITE_STEPS.containsKey(version));
        }
        assertTrue("a step above the latest version would never run",
                SchemaMigrations.SQLITE_STEPS.keySet().stream().allMatch(v -> v <= SchemaMigrations.LATEST_VERSION));
    }

    @Test
    public void aFreshDatabaseHasTheIndexesAndVersion2() throws Exception {
        try (Connection c = DatabaseConfig.getConnection()) {
            List<String> names = indexNames(c);
            for (String expected : List.of("idx_orders_order_date", "idx_orders_status", "idx_orders_table_number",
                    "idx_orders_customer_id", "idx_order_items_order_id", "idx_products_category_id",
                    SchemaMigrations.UNIQUE_PHONE_INDEX)) {
                assertTrue("missing index " + expected + " in " + names, names.contains(expected));
            }
            assertEquals(2, scalar(c, "PRAGMA user_version"));
        }
    }

    @Test
    public void anOldDatabaseGetsTheIndexesOnUpgrade() throws Exception {
        Path file = Files.createTempFile("lunavera-v1", ".db");
        try (Connection c = oldVersionCopy(file)) {
            assertEquals(1, scalar(c, "PRAGMA user_version"));
            assertTrue(indexNames(c).isEmpty());
            SchemaMigrations.upgrade(c);
            assertEquals(2, scalar(c, "PRAGMA user_version"));
            assertTrue(indexNames(c).contains("idx_orders_status"));
            assertTrue(indexNames(c).contains(SchemaMigrations.UNIQUE_PHONE_INDEX));
            SchemaMigrations.upgrade(c); // running it again changes nothing and does not fail
            assertEquals(2, scalar(c, "PRAGMA user_version"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void theHistoryQueriesUseTheirIndexes() throws Exception {
        try (Connection c = DatabaseConfig.getConnection(); Statement st = c.createStatement()) {
            for (String[] q : new String[][]{
                    {"SELECT * FROM orders WHERE status = 'OPEN'", "idx_orders_status"},
                    {"SELECT * FROM orders WHERE customer_id = 1", "idx_orders_customer_id"},
                    {"SELECT * FROM orders WHERE table_number = 3", "idx_orders_table_number"},
                    {"SELECT * FROM order_items WHERE order_id = 1", "idx_order_items_order_id"}}) {
                StringBuilder plan = new StringBuilder();
                try (ResultSet rs = st.executeQuery("EXPLAIN QUERY PLAN " + q[0])) {
                    while (rs.next()) plan.append(rs.getString("detail")).append(' ');
                }
                assertTrue(q[0] + " should use " + q[1] + " but the plan is: " + plan, plan.toString().contains(q[1]));
            }
        }
    }

    @Test
    public void duplicatePhonesBlockTheUniqueIndexButNotTheStartUp() throws Exception {
        Path file = Files.createTempFile("lunavera-dup", ".db");
        try (Connection c = oldVersionCopy(file); Statement st = c.createStatement()) {
            st.execute("INSERT INTO customers(full_name, phone, loyalty_points, created_at) VALUES ('A','0911111111',0,'2026-01-01T00:00:00')");
            st.execute("INSERT INTO customers(full_name, phone, loyalty_points, created_at) VALUES ('B','0911111111',0,'2026-01-02T00:00:00')");
            SchemaMigrations.upgrade(c); // must not throw
            assertFalse(indexNames(c).contains(SchemaMigrations.UNIQUE_PHONE_INDEX));
            assertTrue("the other indexes are still made", indexNames(c).contains("idx_orders_status"));
            assertEquals(List.of("0911111111"), SchemaMigrations.duplicatePhones(c));

            st.execute("DELETE FROM customers WHERE phone = '0911111111' AND full_name = 'B'"); // "merge"
            SchemaMigrations.upgrade(c);
            assertTrue(indexNames(c).contains(SchemaMigrations.UNIQUE_PHONE_INDEX));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void blankPhonesMayRepeat() throws Exception {
        Path file = Files.createTempFile("lunavera-blank", ".db");
        try (Connection c = oldVersionCopy(file); Statement st = c.createStatement()) {
            st.execute("INSERT INTO customers(full_name, phone, loyalty_points, created_at) VALUES ('A','',0,'2026-01-01T00:00:00')");
            st.execute("INSERT INTO customers(full_name, phone, loyalty_points, created_at) VALUES ('B','',0,'2026-01-01T00:00:00')");
            st.execute("INSERT INTO customers(full_name, phone, loyalty_points, created_at) VALUES ('C',NULL,0,'2026-01-01T00:00:00')");
            st.execute("INSERT INTO customers(full_name, phone, loyalty_points, created_at) VALUES ('D',NULL,0,'2026-01-01T00:00:00')");
            SchemaMigrations.upgrade(c);
            assertTrue(indexNames(c).contains(SchemaMigrations.UNIQUE_PHONE_INDEX));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void theDatabaseRefusesASecondCustomerWithTheSamePhone() {
        CustomerDAO dao = new CustomerDAO();
        String phone = "09" + String.format("%08d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 100_000_000L);
        Customer first = new Customer();
        first.setFullName("test-unique-1");
        first.setPhone(phone);
        dao.insert(first);
        Customer second = new Customer();
        second.setFullName("test-unique-2");
        second.setPhone(phone);
        try {
            dao.insert(second);
            fail("a second customer with the same phone must be refused");
        } catch (DataAccessException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Số điện thoại"));
        }
        // and changing someone else's phone to this one is refused as well
        Customer third = new Customer();
        third.setFullName("test-unique-3");
        third.setPhone("09" + String.format("%08d", Math.abs(UUID.randomUUID().getLeastSignificantBits()) % 100_000_000L));
        dao.insert(third);
        third.setPhone(phone);
        try {
            dao.update(third);
            fail("update to a taken phone must be refused");
        } catch (DataAccessException e) {
            assertTrue(e.getMessage().contains("Số điện thoại"));
        }
    }
}
