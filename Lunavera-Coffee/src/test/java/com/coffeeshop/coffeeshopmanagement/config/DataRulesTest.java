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

    // ---- TODO 6.87: the tables added after 6.24 -------------------------------------------------------------------------------

    private static int lastId(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int newCard(Connection c, int initial, int balance) throws SQLException {
        run(c, "INSERT INTO gift_cards (code, initial_amount, balance, sold_by, created_at) VALUES ('dr-" + UUID.randomUUID() + "', "
                + initial + ", " + balance + ", 'x', '2026-10-16 10:00:00')");
        return lastId(c);
    }

    private static int newVoucherRow(Connection c, String kind, int value, String maxUses, int used) throws SQLException {
        run(c, "INSERT INTO vouchers (code, kind, value, max_uses, used_count, created_at) VALUES ('dr-" + UUID.randomUUID() + "', '"
                + kind + "', " + value + ", " + maxUses + ", " + used + ", '2026-10-16 10:00:00')");
        return lastId(c);
    }

    @Test
    public void ordinaryValuesOfTheNewerTablesAreAccepted() throws SQLException {
        try (Connection c = open()) {
            int card = newCard(c, 100000, 0);
            run(c, "INSERT INTO gift_card_moves (card_id, order_id, amount, moved_at) VALUES (" + card + ", NULL, 100000, '2026-10-16 10:00:00')");
            run(c, "INSERT INTO gift_card_moves (card_id, order_id, amount, moved_at) VALUES (" + card + ", NULL, -100000, '2026-10-16 11:00:00')");
            run(c, "UPDATE gift_cards SET balance = 5 WHERE id = " + card);
            run(c, "INSERT INTO time_clock (user_id, user_name, clock_in, clock_out) VALUES (" + FAR_TABLE + ", 'dr', '2026-10-16 08:00:00', '2026-10-16 12:00:00')");
            run(c, "INSERT INTO time_clock (user_id, user_name, clock_in) VALUES (" + (FAR_TABLE + 1) + ", 'dr', '2026-10-16 08:00:00')");
            run(c, "UPDATE time_clock SET clock_out = '2026-10-16 17:00:00' WHERE user_id = " + (FAR_TABLE + 1));
            newVoucherRow(c, "PERCENT", 100, "NULL", 0);
            newVoucherRow(c, "AMOUNT", 5000, "3", 3);
            run(c, "INSERT INTO expenses (spent_on, amount, category, recorded_by_name, created_at) VALUES ('2026-10-16', 10000, 'x', 'dr', '2026-10-16 10:00:00')");
            int order = newOrder(c, "PAID", 50000, 0, 50000, "NULL");
            run(c, "INSERT INTO order_items (order_id, product_name, quantity, unit_price, line_total) VALUES (" + order + ", 'x', 1, 50000, 50000)");
            int item = lastId(c);
            run(c, "INSERT INTO order_payments (order_id, method, amount) VALUES (" + order + ", 'CASH', 30000)");
            run(c, "INSERT INTO order_payments (order_id, method, amount) VALUES (" + order + ", 'CARD', 20000)");
            run(c, "INSERT INTO refunds (order_id, order_item_id, quantity, line_amount, amount, method, refunded_by_name, refunded_at) VALUES ("
                    + order + ", " + item + ", 1, 50000, 50000, 'CASH', 'dr', '2026-10-16 12:00:00')");
            // the rest of a line paid partly by gift card, and a line the discount made free: 0 pieces or 0 dong is legitimate
            run(c, "INSERT INTO refunds (order_id, order_item_id, quantity, line_amount, amount, method, refunded_by_name, refunded_at) VALUES ("
                    + order + ", " + item + ", 0, 0, 7000, 'CASH', 'dr', '2026-10-16 12:00:00')");
            run(c, "INSERT INTO refunds (order_id, order_item_id, quantity, line_amount, amount, method, refunded_by_name, refunded_at) VALUES ("
                    + order + ", " + item + ", 1, 0, 0, 'CASH', 'dr', '2026-10-16 12:00:00')");
            for (com.coffeeshop.coffeeshopmanagement.model.Reservation.Status status : com.coffeeshop.coffeeshopmanagement.model.Reservation.Status.values()) {
                run(c, "INSERT INTO reservations (table_number, customer_name, reserved_for, people, status, created_by, created_at) VALUES ("
                        + FAR_TABLE + ", 'dr', '2026-10-16 19:00:00', 4, '" + status.name() + "', 'dr', '2026-10-16 10:00:00')");
            }
            c.rollback();
        }
    }

    @Test
    public void aGiftCardWithNoMoneyOrANegativeBalanceIsRefused() throws SQLException {
        try (Connection c = open()) {
            refused(c, "INSERT INTO gift_cards (code, initial_amount, balance, sold_by, created_at) VALUES ('dr-a', 0, 0, 'x', 't')", "Thẻ quà tặng");
            refused(c, "INSERT INTO gift_cards (code, initial_amount, balance, sold_by, created_at) VALUES ('dr-b', 1000, -1, 'x', 't')", "Thẻ quà tặng");
            int card = newCard(c, 1000, 1000);
            refused(c, "UPDATE gift_cards SET balance = balance - 1001 WHERE id = " + card, "số dư không được âm");
            c.rollback();
        }
    }

    @Test
    public void aGiftCardMoveOfZeroDongIsRefused() throws SQLException {
        try (Connection c = open()) {
            int card = newCard(c, 1000, 1000);
            refused(c, "INSERT INTO gift_card_moves (card_id, order_id, amount, moved_at) VALUES (" + card + ", NULL, 0, 't')", "0 đồng");
            c.rollback();
        }
    }

    @Test
    public void aClockOutBeforeTheClockInIsRefused() throws SQLException {
        try (Connection c = open()) {
            refused(c, "INSERT INTO time_clock (user_id, user_name, clock_in, clock_out) VALUES (" + FAR_TABLE
                    + ", 'dr', '2026-10-16 12:00:00', '2026-10-16 08:00:00')", "Giờ ra ca");
            run(c, "INSERT INTO time_clock (user_id, user_name, clock_in) VALUES (" + FAR_TABLE + ", 'dr', '2026-10-16 12:00:00')");
            refused(c, "UPDATE time_clock SET clock_out = '2026-10-16 08:00:00' WHERE user_id = " + FAR_TABLE, "Giờ ra ca");
            c.rollback();
        }
    }

    @Test
    public void aVoucherWithNoValueAPercentAboveHundredAnUnknownKindOrTooManyUsesIsRefused() throws SQLException {
        try (Connection c = open()) {
            refused(c, "INSERT INTO vouchers (code, kind, value, created_at) VALUES ('dr-1', 'AMOUNT', 0, 't')", "Mã giảm giá không hợp lệ");
            refused(c, "INSERT INTO vouchers (code, kind, value, created_at) VALUES ('dr-2', 'PERCENT', 101, 't')", "Mã giảm giá không hợp lệ");
            refused(c, "INSERT INTO vouchers (code, kind, value, created_at) VALUES ('dr-3', 'GIFT', 10, 't')", "Mã giảm giá không hợp lệ");
            refused(c, "INSERT INTO vouchers (code, kind, value, max_uses, used_count, created_at) VALUES ('dr-4', 'AMOUNT', 10, 2, 3, 't')", "Mã giảm giá không hợp lệ");
            refused(c, "INSERT INTO vouchers (code, kind, value, max_uses, created_at) VALUES ('dr-5', 'AMOUNT', 10, 0, 't')", "Mã giảm giá không hợp lệ");
            int voucher = newVoucherRow(c, "AMOUNT", 10, "2", 2);
            refused(c, "UPDATE vouchers SET used_count = used_count + 1 WHERE id = " + voucher, "Mã giảm giá không hợp lệ");
            c.rollback();
        }
    }

    @Test
    public void anExpenseOfNoMoneyIsRefused() throws SQLException {
        try (Connection c = open()) {
            refused(c, "INSERT INTO expenses (spent_on, amount, category, recorded_by_name, created_at) VALUES ('2026-10-16', 0, 'x', 'dr', 't')", "Số tiền chi");
            refused(c, "INSERT INTO expenses (spent_on, amount, category, recorded_by_name, created_at) VALUES ('2026-10-16', -5, 'x', 'dr', 't')", "Số tiền chi");
            c.rollback();
        }
    }

    @Test
    public void aNegativeRefundAndAPaymentOfNoMoneyAreRefused() throws SQLException {
        try (Connection c = open()) {
            int order = newOrder(c, "PAID", 50000, 0, 50000, "NULL");
            run(c, "INSERT INTO order_items (order_id, product_name, quantity, unit_price, line_total) VALUES (" + order + ", 'x', 1, 50000, 50000)");
            int item = lastId(c);
            String refund = "INSERT INTO refunds (order_id, order_item_id, quantity, line_amount, amount, method, refunded_by_name, refunded_at) VALUES ("
                    + order + ", " + item + ", ";
            refused(c, refund + "1, 50000, -1, 'CASH', 'dr', 't')", "hoàn không được âm");
            refused(c, refund + "-1, 50000, 50000, 'CASH', 'dr', 't')", "hoàn không được âm");
            refused(c, refund + "1, -50000, 50000, 'CASH', 'dr', 't')", "hoàn không được âm");
            refused(c, "INSERT INTO order_payments (order_id, method, amount) VALUES (" + order + ", 'CASH', 0)", "cách thanh toán");
            refused(c, "INSERT INTO order_payments (order_id, method, amount) VALUES (" + order + ", 'CASH', -10)", "cách thanh toán");
            c.rollback();
        }
    }

    @Test
    public void aReservationForNobodyOrWithAnUnknownStatusIsRefused() throws SQLException {
        try (Connection c = open()) {
            String head = "INSERT INTO reservations (table_number, customer_name, reserved_for, people, status, created_by, created_at) VALUES ("
                    + FAR_TABLE + ", 'dr', 't', ";
            refused(c, head + "0, 'BOOKED', 'dr', 't')", "Đặt bàn không hợp lệ");
            refused(c, head + "2, 'DONE', 'dr', 't')", "Đặt bàn không hợp lệ");
            run(c, head + "2, 'BOOKED', 'dr', 't')");
            refused(c, "UPDATE reservations SET status = 'LATER' WHERE table_number = " + FAR_TABLE, "Đặt bàn không hợp lệ");
            c.rollback();
        }
    }
}
