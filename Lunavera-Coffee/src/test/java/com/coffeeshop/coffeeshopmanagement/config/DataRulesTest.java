package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.dao.UniqueViolation;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.TableStatus;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 6.24 (the database refuses nonsense values: negative prices and stock, a line of 0 pieces, a discount above the
 * subtotal, an unknown status or role) and TODO 3.22 (one OPEN order per table). SQLite only. Each test works inside a
 * transaction that is rolled back, so the shared test database is not changed.
 */
public class DataRulesTest {

    private static final int FAR_TABLE = 987_654; // a table number no other test uses

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void sqliteOnly() {
        TestDatabaseSupport.requireSqlite();
    }

    private static void run(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static void refused(Connection c, String sql, String expectedPartOfMessage) throws SQLException {
        try {
            run(c, sql);
            fail("the database accepted: " + sql);
        } catch (SQLException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(expectedPartOfMessage));
        }
    }

    private static Connection open() throws SQLException {
        Connection c = DatabaseConfig.getConnection();
        c.setAutoCommit(false);
        return c;
    }

    private static int newOrder(Connection c, String status, int subtotal, int discount, int total, String tableNumber) throws SQLException {
        run(c, "INSERT INTO orders (order_date, status, subtotal, discount, total, table_number) VALUES ('2026-10-16T10:00:00', '"
                + status + "', " + subtotal + ", " + discount + ", " + total + ", " + tableNumber + ")");
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    public void ordinaryValuesAreAccepted() throws SQLException {
        try (Connection c = open()) {
            run(c, "INSERT INTO products (name, price, cost, stock) VALUES ('dr-" + UUID.randomUUID() + "', 45000, NULL, 0)");
            int order = newOrder(c, "PAID", 90000, 9000, 81000, "NULL");
            run(c, "INSERT INTO order_items (order_id, product_name, quantity, unit_price, line_total) VALUES (" + order + ", 'x', 2, 45000, 90000)");
            run(c, "UPDATE orders SET status = 'CANCELLED' WHERE id = " + order);
            c.rollback();
        }
    }

    @Test
    public void everyValueOfEveryStatusEnumIsAccepted() throws SQLException {
        try (Connection c = open()) {
            for (OrderStatus status : OrderStatus.values()) {
                newOrder(c, status.name(), 0, 0, 0, "NULL");
            }
            for (TableStatus status : TableStatus.values()) {
                run(c, "INSERT INTO dining_tables (table_number, name, status) VALUES (" + (FAR_TABLE + status.ordinal())
                        + ", 'dr', '" + status.name() + "')");
            }
            for (Role role : Role.values()) {
                for (AccountStatus status : AccountStatus.values()) {
                    run(c, "INSERT INTO users (username, password_hash, role, status, created_at) VALUES ('dr-" + UUID.randomUUID()
                            + "', 'x', '" + role.name() + "', '" + status.name() + "', '2026-10-16T10:00:00')");
                }
            }
            c.rollback();
        }
    }

    @Test
    public void negativePricesCostAndStockAreRefused() throws SQLException {
        try (Connection c = open()) {
            refused(c, "INSERT INTO products (name, price, stock) VALUES ('dr', -1, 0)", "không được âm");
            refused(c, "INSERT INTO products (name, price, cost, stock) VALUES ('dr', 1, -5, 0)", "không được âm");
            refused(c, "INSERT INTO products (name, price, stock) VALUES ('dr', 1, -1)", "không được âm");
            run(c, "INSERT INTO products (name, price, stock) VALUES ('dr', 1, 3)");
            refused(c, "UPDATE products SET stock = stock - 4 WHERE name = 'dr'", "không được âm");
            c.rollback();
        }
    }

    @Test
    public void anOrderLineOfZeroPiecesOrNegativeMoneyIsRefused() throws SQLException {
        try (Connection c = open()) {
            int order = newOrder(c, "OPEN", 0, 0, 0, "NULL");
            refused(c, "INSERT INTO order_items (order_id, product_name, quantity, unit_price, line_total) VALUES (" + order + ", 'x', 0, 1, 0)", "lớn hơn 0");
            refused(c, "INSERT INTO order_items (order_id, product_name, quantity, unit_price, line_total) VALUES (" + order + ", 'x', 1, -1, -1)", "lớn hơn 0");
            c.rollback();
        }
    }

    @Test
    public void aDiscountAboveTheSubtotalNegativeMoneyAndAnUnknownStatusAreRefused() throws SQLException {
        try (Connection c = open()) {
            refused(c, "INSERT INTO orders (order_date, status, subtotal, discount, total) VALUES ('t', 'PAID', 100, 200, 0)", "giảm giá không lớn hơn tạm tính");
            refused(c, "INSERT INTO orders (order_date, status, subtotal, discount, total) VALUES ('t', 'PAID', 100, 0, -5)", "giảm giá không lớn hơn tạm tính");
            refused(c, "INSERT INTO orders (order_date, status, subtotal, discount, total) VALUES ('t', 'paid', 0, 0, 0)", "Trạng thái đơn");
            int order = newOrder(c, "OPEN", 0, 0, 0, "NULL");
            refused(c, "UPDATE orders SET status = 'DONE' WHERE id = " + order, "Trạng thái đơn");
            c.rollback();
        }
    }

    @Test
    public void anUnknownRoleStatusOrTableStatusIsRefused() throws SQLException {
        try (Connection c = open()) {
            refused(c, "INSERT INTO users (username, password_hash, role, status, created_at) VALUES ('dr-" + UUID.randomUUID() + "', 'x', 'BOSS', 'ACTIVE', 't')", "Vai trò");
            refused(c, "INSERT INTO users (username, password_hash, role, status, created_at) VALUES ('dr-" + UUID.randomUUID() + "', 'x', 'ADMIN', 'active', 't')", "Vai trò");
            refused(c, "INSERT INTO dining_tables (table_number, name, status) VALUES (" + FAR_TABLE + ", 'dr', 'BUSY')", "Trạng thái bàn");
            refused(c, "INSERT INTO customers (full_name, loyalty_points, created_at) VALUES ('dr', -1, 't')", "Điểm tích lũy");
            c.rollback();
        }
    }

    @Test
    public void aTableCanHaveOnlyOneOpenOrderButManyClosedOnes() throws SQLException {
        try (Connection c = open()) {
            newOrder(c, "PAID", 10, 0, 10, String.valueOf(FAR_TABLE));
            newOrder(c, "CANCELLED", 10, 0, 10, String.valueOf(FAR_TABLE));
            newOrder(c, "OPEN", 0, 0, 0, String.valueOf(FAR_TABLE));
            try {
                newOrder(c, "OPEN", 0, 0, 0, String.valueOf(FAR_TABLE));
                fail("a second open order on the same table must be refused by the database");
            } catch (SQLException e) {
                assertTrue(e.getMessage(), UniqueViolation.isUnique(e));
            }
            newOrder(c, "OPEN", 0, 0, 0, String.valueOf(FAR_TABLE + 1)); // another table is fine
            newOrder(c, "OPEN", 0, 0, 0, "NULL");                         // an order without a table is fine
            newOrder(c, "OPEN", 0, 0, 0, "NULL");
            c.rollback();
        }
    }

    // ---- the migration itself, on scratch databases ----

    private static int triggers(Connection c, String like) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE '" + like + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    public void aDatabaseWithoutTheTablesIsLeftAloneAndRunningItTwiceChangesNothing() throws Exception {
        Path file = Files.createTempDirectory("lunavera-rules").resolve("scratch.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            SchemaMigrations.ensureDataRules(c); // no tables at all: nothing to do, no error
            assertEquals(0, triggers(c, "rule_%"));
            run(c, "CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT, price INTEGER NOT NULL, cost INTEGER, stock INTEGER NOT NULL DEFAULT 0)");
            SchemaMigrations.ensureDataRules(c);
            int first = triggers(c, "rule_products%");
            SchemaMigrations.ensureDataRules(c);
            assertEquals(2, first);
            assertEquals("the second run adds nothing", first, triggers(c, "rule_products%"));
        }
    }

    @Test
    public void aRuleIsSkippedWhileExistingRowsBreakItAndAppliedOnceTheyAreFixed() throws Exception {
        Path file = Files.createTempDirectory("lunavera-rules").resolve("scratch.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            run(c, "CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT, price INTEGER NOT NULL, cost INTEGER, stock INTEGER NOT NULL DEFAULT 0)");
            run(c, "INSERT INTO products (name, price, stock) VALUES ('old', 100, -3)"); // legacy nonsense already stored
            SchemaMigrations.ensureDataRules(c);
            assertEquals("not enforced yet: it would make every later update of that row fail", 0, triggers(c, "rule_products%"));

            run(c, "UPDATE products SET stock = 0");
            SchemaMigrations.ensureDataRules(c);
            assertEquals(2, triggers(c, "rule_products%"));
            try {
                run(c, "UPDATE products SET stock = -1");
                fail("now enforced");
            } catch (SQLException expected) {
                assertTrue(expected.getMessage(), expected.getMessage().contains("không được âm"));
            }
        }
    }
}
