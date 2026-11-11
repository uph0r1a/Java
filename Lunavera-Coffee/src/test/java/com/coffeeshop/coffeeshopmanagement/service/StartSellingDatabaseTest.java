package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.AuditDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 4.57 on a real database: the test sales are removed, the shop stays, the audit log holds the one new line, a wrong name or a
 * cashier changes nothing. The data is written with plain SQL so the test does not depend on the till's own code.
 */
public class StartSellingDatabaseTest {

    private int tableNumber;
    private int voucherId;
    private int customerId;
    private int productsBefore;
    private int usersBefore;
    private int categoriesBefore;

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void makeSomeTestSales() throws SQLException {
        TestSession.admin();
        try (Connection c = DatabaseConfig.getConnection()) {
            productsBefore = count(c, "products");
            usersBefore = count(c, "users");
            categoriesBefore = count(c, "categories");
            String now = DbTime.write(AppClock.now());
            customerId = insert(c, "INSERT INTO customers (full_name, phone, loyalty_points, lifetime_points, birthday_used_on, created_at) "
                    + "VALUES (?, ?, 40, 90, ?, ?)", "test-opening-" + UUID.randomUUID(), phone(), "2026-11-01", now);
            tableNumber = 9000 + new Random().nextInt(900);
            int orderId = insert(c, "INSERT INTO orders (order_date, status, subtotal, discount, total, payment_method, paid_at, table_number) "
                    + "VALUES (?, 'PAID', 50000, 0, 50000, 'CASH', ?, ?)", now, now, tableNumber);
            int openOrder = insert(c, "INSERT INTO orders (order_date, status, subtotal, discount, total, table_number) "
                    + "VALUES (?, 'OPEN', 20000, 0, 20000, ?)", now, tableNumber);
            update(c, "INSERT INTO dining_tables (table_number, name, status, capacity, current_order_id) VALUES (?, ?, 'OCCUPIED', 4, ?)",
                    tableNumber, "T" + tableNumber, openOrder);
            int itemId = insert(c, "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, line_total) "
                    + "VALUES (?, NULL, 'test line', 1, 50000, 50000)", orderId);
            update(c, "INSERT INTO order_payments (order_id, method, amount) VALUES (?, 'CASH', 30000)", orderId);
            update(c, "INSERT INTO order_payments (order_id, method, amount) VALUES (?, 'CARD', 20000)", orderId);
            update(c, "INSERT INTO refunds (order_id, order_item_id, quantity, line_amount, amount, method, refunded_by_name, refunded_at) "
                    + "VALUES (?, ?, 1, 50000, 50000, 'CASH', 'test', ?)", orderId, itemId, now);
            update(c, "INSERT INTO stock_takes (order_item_id, ingredient_id, amount) VALUES (?, NULL, 5)", itemId);
            int card = insert(c, "INSERT INTO gift_cards (code, initial_amount, balance, sold_by, created_at) VALUES (?, 100000, 50000, 'test', ?)",
                    "GC-" + UUID.randomUUID().toString().substring(0, 12), now);
            update(c, "INSERT INTO gift_card_moves (card_id, order_id, amount, moved_at) VALUES (?, ?, -50000, ?)", card, orderId, now);
            update(c, "INSERT INTO expenses (spent_on, amount, category, recorded_by_name, created_at) VALUES (?, 10000, 'test', 'test', ?)",
                    AppClock.now().toLocalDate().toString(), now);
            update(c, "INSERT INTO stock_log (logged_at, kind, product_name, quantity, stock_before, stock_after) VALUES (?, 'IN', 'test', 5, 0, 5)", now);
            update(c, "INSERT INTO shift_closes (period_start, period_end, closed_by_name, paid_orders, revenue, cash_sales, opening_cash, "
                    + "expected_cash, counted_cash, difference) VALUES (?, ?, 'test', 1, 50000, 50000, 0, 50000, 50000, 0)", now, now);
            // a closed row: the database allows one OPEN clock-in per person, and most tests here leave their rows behind
            update(c, "INSERT INTO time_clock (user_id, user_name, clock_in, clock_out) VALUES (1, 'test', ?, ?)", now, now);
            update(c, "INSERT INTO reservations (table_number, customer_name, reserved_for, people, created_by, created_at) "
                    + "VALUES (?, 'test', ?, 2, 'test', ?)", tableNumber, now, now);
            voucherId = insert(c, "INSERT INTO vouchers (code, kind, value, used_count, created_at) VALUES (?, 'PERCENT', 10, 3, ?)",
                    "V" + UUID.randomUUID().toString().substring(0, 10), now);
            AuditDAO.write(c, AuditAction.DISCOUNT_GIVEN, AuditTarget.ORDER, orderId, "#" + orderId, "0", "5000", "test");
        }
    }

    @After
    public void signOut() {
        TestSession.clear();
    }

    @Test
    public void theTestSalesAreRemovedAndTheShopStays() throws SQLException {
        StartSellingService.Result result = StartSellingService.start(StartSellingRules.expectedName(), false);

        try (Connection c = DatabaseConfig.getConnection()) {
            for (String table : StartSellingRules.EMPTIED) {
                if (table.equals("audit_log")) continue;
                assertEquals(table + " must be empty", 0, count(c, table));
            }
            assertEquals("the menu stays", productsBefore, count(c, "products"));
            assertEquals("the categories stay", categoriesBefore, count(c, "categories"));
            assertEquals("the accounts stay", usersBefore, count(c, "users"));
            assertEquals("the table stays", 1, countWhere(c, "dining_tables", "table_number = " + tableNumber));
            assertEquals("and is free", 1, countWhere(c, "dining_tables", "table_number = " + tableNumber + " AND status = 'EMPTY' AND current_order_id IS NULL"));
            assertEquals("the voucher stays", 1, countWhere(c, "vouchers", "id = " + voucherId));
            assertEquals("with no use counted", 1, countWhere(c, "vouchers", "id = " + voucherId + " AND used_count = 0"));
            assertEquals("the customer stays", 1, countWhere(c, "customers", "id = " + customerId));
            assertEquals("without the points of the test orders", 1, countWhere(c, "customers",
                    "id = " + customerId + " AND loyalty_points = 0 AND lifetime_points = 0 AND birthday_used_on IS NULL"));
        }
        assertTrue("some test rows were counted", result.total() >= 14);
        assertTrue("both orders of the test were counted", result.removed().get("orders") >= 2);
        assertFalse(result.customersRemoved());
    }

    @Test
    public void theAuditLogHoldsOnlyTheLineOfTheClearing() throws SQLException {
        StartSellingService.Result result = StartSellingService.start(StartSellingRules.expectedName(), false);
        try (Connection c = DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT action, target_type, target_label, new_value, detail, actor_name FROM audit_log")) {
            assertTrue(rs.next());
            assertEquals(AuditAction.SALES_STARTED.name(), rs.getString("action"));
            assertEquals(AuditTarget.SHOP, rs.getString("target_type"));
            assertEquals(StartSellingRules.expectedName(), rs.getString("target_label"));
            assertEquals("the line names the backup", result.backupName(), rs.getString("new_value"));
            assertTrue(rs.getString("detail").matches("[0-9]+\\|[0-9]+\\|0"));
            assertFalse("who did it is written", rs.getString("actor_name").isBlank());
            assertFalse("and it is the only line", rs.next());
        }
    }

    @Test
    public void aBackupIsMadeFirstAndKept() throws Exception {
        StartSellingService.Result result = StartSellingService.start(StartSellingRules.expectedName(), false);
        assertTrue(result.backupName().startsWith("before-opening-"));
        Path file = DataPaths.backupsDir().resolve(result.backupName());
        assertTrue("the backup file exists", Files.isRegularFile(file));
        assertTrue(Files.size(file) > 0);
    }

    @Test
    public void aWrongNameChangesNothingAndMakesNoBackup() throws Exception {
        int ordersBefore;
        try (Connection c = DatabaseConfig.getConnection()) {
            ordersBefore = count(c, "orders");
        }
        long backupsBefore = openingBackups();
        assertThrows(DataAccessException.class, () -> StartSellingService.start("not the name", false));
        assertThrows(DataAccessException.class, () -> StartSellingService.start("", true));
        try (Connection c = DatabaseConfig.getConnection()) {
            assertEquals(ordersBefore, count(c, "orders"));
            assertTrue(count(c, "customers") > 0);
        }
        assertEquals("no backup for a refused request", backupsBefore, openingBackups());
    }

    @Test
    public void aCashierIsRefused() throws Exception {
        TestSession.clear();
        int ordersBefore;
        try (Connection c = DatabaseConfig.getConnection()) {
            ordersBefore = count(c, "orders");
        }
        assertThrows(Permissions.PermissionDeniedException.class,
                () -> StartSellingService.start(StartSellingRules.expectedName(), false));
        assertThrows(Permissions.PermissionDeniedException.class, () -> StartSellingService.preview(false));
        try (Connection c = DatabaseConfig.getConnection()) {
            assertEquals(ordersBefore, count(c, "orders"));
        }
    }

    @Test
    public void theSecondBoxRemovesTheCustomersAndUnlinksTheirOldLogins() throws SQLException {
        int userId;
        try (Connection c = DatabaseConfig.getConnection()) {
            userId = insert(c, "INSERT INTO users (username, password_hash, role, status, customer_id, created_at) "
                    + "VALUES (?, 'x', 'CUSTOMER', 'LOCKED', ?, ?)", "test-opening-" + UUID.randomUUID(), customerId, DbTime.write(AppClock.now()));
        }
        StartSellingService.Result result = StartSellingService.start(StartSellingRules.expectedName(), true);
        assertTrue(result.customersRemoved());
        try (Connection c = DatabaseConfig.getConnection()) {
            assertEquals("no customer is left", 0, count(c, "customers"));
            assertEquals("the old login stays, unlinked", 1, countWhere(c, "users", "id = " + userId + " AND customer_id IS NULL"));
            assertEquals("the other accounts stay", usersBefore + 1, count(c, "users"));
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT detail FROM audit_log")) {
                assertTrue(rs.next());
                assertTrue(rs.getString(1).endsWith("|1"));
            }
        }
    }

    @Test
    public void thePreviewCountsWhatWouldGoAndChangesNothing() throws SQLException {
        var counts = StartSellingService.preview(true);
        assertTrue(counts.get("orders") >= 2); // the shared test database may hold the rows of other tests too
        assertTrue(counts.get("order_payments") >= 2);
        assertTrue(counts.get("customers") >= 1);
        try (Connection c = DatabaseConfig.getConnection()) {
            assertTrue("nothing was removed", count(c, "orders") >= 2);
        }
        assertFalse(StartSellingService.preview(false).containsKey("customers"));
    }

    @Test
    public void doingItTwiceIsHarmless() {
        StartSellingService.start(StartSellingRules.expectedName(), false);
        StartSellingService.Result second = StartSellingService.start(StartSellingRules.expectedName(), false);
        assertEquals(0, second.removed().get("orders").intValue());
    }

    // ------------------------------------------------------------------ helpers

    private static long openingBackups() throws java.io.IOException {
        Path dir = DataPaths.backupsDir();
        if (!Files.isDirectory(dir)) return 0;
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().startsWith("before-opening-")).count();
        }
    }

    private static String phone() {
        return "09" + String.format(java.util.Locale.ROOT, "%08d", new Random().nextInt(100_000_000));
    }

    private static int count(Connection c, String table) throws SQLException {
        return countWhere(c, table, "1 = 1");
    }

    private static int countWhere(Connection c, String table, String where) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
    }

    private static void update(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    private static int insert(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }
}
