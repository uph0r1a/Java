package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.StaffRow;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.AccountService;
import com.coffeeshop.coffeeshopmanagement.service.ExpenseRules;
import com.coffeeshop.coffeeshopmanagement.service.OrderService;
import com.coffeeshop.coffeeshopmanagement.service.StaffService;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/**
 * TODO 2.87: making a former member of staff anonymous leaves their name in NONE of the tables that copy it: refunds, expenses, the
 * time clock, gift cards and bookings (besides the stock log, the closed shifts and the audit log that were already scrubbed).
 * Real database; every row is made under the test person's own session, so the name is copied the way the program copies it.
 */
public class StaffAnonymiseScrubTest {

    private final StaffService staff = new StaffService();
    private final AccountService accounts = new AccountService();
    private final OrderService orders = new OrderService();
    private final OrderDAO orderDAO = new OrderDAO();
    private final List<Integer> opened = new java.util.ArrayList<>();

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void admin() {
        TestSession.admin();
    }

    @After
    public void closeOrders() {
        TestSession.admin();
        for (int id : opened) orderDAO.cancelOpenTakeAway(id);
        opened.clear();
    }

    private static Employee typed(String name) {
        Employee e = new Employee();
        e.setFullName(name);
        e.setPhone("09" + String.format(java.util.Locale.ROOT, "%08d", new Random().nextInt(100_000_000)));
        e.setPosition("Pha chế");
        e.setSalary(new BigDecimal("8000000"));
        e.setHireDate(LocalDate.of(2026, 3, 1));
        return e;
    }

    private User account(Role role, String fullName) {
        String username = "s" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return accounts.create(new AccountService.Form(username, "Abc12345x", role, true, typed(fullName)));
    }

    private StaffRow rowOf(int employeeId) {
        return staff.list().stream().filter(r -> r.employee().getId() == employeeId).findFirst().orElseThrow();
    }

    private static String uniqueName() {
        return "Scrub " + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String text(String sql, Object... params) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) st.setObject(i + 1, params[i]);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void run(String sql, Object... params) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) st.setObject(i + 1, params[i]);
            st.executeUpdate();
        }
    }

    private static long count(String table, String column, String value) throws SQLException {
        return Long.parseLong(text("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", value));
    }

    /** An open take-away order with one line, for a refund row to point at. */
    private int orderLineOf(Category category) {
        Product p = new Product();
        p.setName("test-scrub-product-" + UUID.randomUUID());
        p.setCategoryId(category.getId());
        p.setPrice(new BigDecimal("40000"));
        p.setStock(10);
        p.setActive(true);
        p = new ProductDAO().insert(p);
        Order order = orders.start(null, "", "", null).order();
        opened.add(order.getId());
        OrderItem line = new OrderItem();
        line.setProductId(p.getId());
        line.setProductName(p.getName());
        line.setQuantity(1);
        line.setUnitPrice(p.getPrice());
        line.setLineTotal(p.getPrice());
        orderDAO.updateIfStillOpen(order, List.of(line));
        return orderDAO.findItemsByOrderId(order.getId()).get(0).getId();
    }

    private void insertRefund(int itemId, int orderId, Integer byId, String byName) throws SQLException {
        run("INSERT INTO refunds (order_id, order_item_id, quantity, line_amount, amount, method, restocked, reason, refunded_by, "
                + "refunded_by_name, refunded_at) VALUES (?, ?, 1, 40000, 40000, 'CASH', 0, 'test', ?, ?, '2026-11-02 10:00:00')",
                orderId, itemId, byId, byName);
    }

    // ------------------------------------------------------------------ the five tables

    @Test
    public void noTableKeepsTheNameOfAPersonWhoWasMadeAnonymous() throws Exception {
        String oldName = uniqueName();
        User user = account(Role.ADMIN, oldName);
        account(Role.ADMIN, uniqueName()); // another administrator, so this one may be locked
        Employee employee = rowOf(user.getEmployeeId()).employee();
        Category category = new Category();
        category.setName("test-scrub-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        int itemId = orderLineOf(category);
        int orderId = Integer.parseInt(text("SELECT order_id FROM order_items WHERE id = ?", itemId));

        Session.start(user, employee); // every row below copies this person's name the way the program does
        insertRefund(itemId, orderId, employee.getId(), employee.getFullName());
        new ExpenseDAO().add(AppClock.today(), new BigDecimal("50000"), ExpenseRules.SUPPLIES, "test-scrub-expense", false);
        new TimeClockDAO().clockIn();
        new TimeClockDAO().clockOut();
        String cardCode = new GiftCardDAO().sell(100_000).code();
        int bookingId = new ReservationDAO().add(1, "Khách thử", null, AppClock.now().toLocalDate().plusDays(3 + new Random().nextInt(50)).atTime(11, 3) /* inside the 60 days a booking may be made ahead, and not at the 18:00 of ReservationDaoTest */, 2, "test-scrub").id();
        TestSession.admin();

        staff.setWorking(rowOf(employee.getId()), false);
        staff.makeAnonymous(rowOf(employee.getId()));
        String newName = rowOf(employee.getId()).employee().getFullName();

        assertNotEquals(oldName, newName);
        assertEquals(newName, text("SELECT refunded_by_name FROM refunds WHERE refunded_by = ?", employee.getId()));
        assertEquals(newName, text("SELECT recorded_by_name FROM expenses WHERE recorded_by = ? AND note = 'test-scrub-expense'", employee.getId()));
        assertEquals(newName, text("SELECT user_name FROM time_clock WHERE user_id = ?", user.getId()));
        assertEquals(newName, text("SELECT sold_by FROM gift_cards WHERE code = ?", cardCode));
        assertEquals(newName, text("SELECT created_by FROM reservations WHERE id = ?", bookingId));
        for (String[] where : new String[][]{{"refunds", "refunded_by_name"}, {"expenses", "recorded_by_name"}, {"time_clock", "user_name"},
                {"gift_cards", "sold_by"}, {"reservations", "created_by"}}) {
            assertEquals("nothing is left under the old name in " + where[0], 0, count(where[0], where[1], oldName));
        }
    }

    @Test
    public void theRowsOfAnotherPersonAreNotTouched() throws Exception {
        User leaving = account(Role.ADMIN, uniqueName());
        account(Role.ADMIN, uniqueName());
        String staysName = uniqueName();
        User stays = account(Role.ADMIN, staysName); // an expense may be recorded by an administrator only (TODO 2A.44), and this person must have recorded one
        Employee staysEmployee = rowOf(stays.getEmployeeId()).employee();
        Session.start(stays, staysEmployee);
        String card = new GiftCardDAO().sell(50_000).code();
        new ExpenseDAO().add(AppClock.today(), new BigDecimal("20000"), ExpenseRules.SUPPLIES, "test-scrub-stays", false);
        TestSession.admin();

        staff.setWorking(rowOf(leaving.getEmployeeId()), false);
        staff.makeAnonymous(rowOf(leaving.getEmployeeId()));

        assertEquals(staysName, text("SELECT sold_by FROM gift_cards WHERE code = ?", card));
        assertEquals(staysName, text("SELECT recorded_by_name FROM expenses WHERE note = 'test-scrub-stays'"));
    }

    @Test
    public void rowsWrittenBeforeTheIdColumnsExistedAreFoundByTheOldName() throws Exception {
        String oldName = uniqueName();
        User user = account(Role.ADMIN, oldName);
        account(Role.ADMIN, uniqueName());
        Employee employee = rowOf(user.getEmployeeId()).employee();
        String code = "OLD" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        run("INSERT INTO gift_cards (code, initial_amount, balance, sold_by, created_at) VALUES (?, 10000, 10000, ?, '2026-01-01 08:00:00')", code, oldName);
        assertNull(text("SELECT sold_by_id FROM gift_cards WHERE code = ?", code));

        staff.setWorking(rowOf(employee.getId()), false);
        staff.makeAnonymous(rowOf(employee.getId()));

        assertNotEquals(oldName, text("SELECT sold_by FROM gift_cards WHERE code = ?", code));
        assertEquals(rowOf(employee.getId()).employee().getFullName(), text("SELECT sold_by FROM gift_cards WHERE code = ?", code));
    }

    @Test
    public void aNameThatAnotherStaffMemberAlsoCarriesIsNotRewrittenByText() throws Exception {
        String shared = uniqueName();
        User leaving = account(Role.ADMIN, shared);
        account(Role.ADMIN, shared); // somebody else with exactly the same name stays
        String code = "SAME" + UUID.randomUUID().toString().replace("-", "").substring(0, 9);
        run("INSERT INTO gift_cards (code, initial_amount, balance, sold_by, created_at) VALUES (?, 10000, 10000, ?, '2026-01-01 08:00:00')", code, shared);

        staff.setWorking(rowOf(leaving.getEmployeeId()), false);
        staff.makeAnonymous(rowOf(leaving.getEmployeeId()));

        assertEquals("a row with no id cannot be told apart, so it is left alone", shared, text("SELECT sold_by FROM gift_cards WHERE code = ?", code));
    }
}
