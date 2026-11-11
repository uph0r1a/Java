package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditEntry;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.StaffRow;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.AccountService;
import com.coffeeshop.coffeeshopmanagement.service.BackupService;
import com.coffeeshop.coffeeshopmanagement.service.CustomerMergeService;
import com.coffeeshop.coffeeshopmanagement.service.PasswordChangeService;
import com.coffeeshop.coffeeshopmanagement.service.StaffService;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 4.2: each place that must leave a line in the audit log does, in the same transaction as the change it describes. */
public class AuditHooksTest {

    private final AuditDAO audit = new AuditDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final TableDAO tableDAO = new TableDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final CustomerMergeService merges = new CustomerMergeService();
    private final StaffService staff = new StaffService();
    private final AccountService accounts = new AccountService();
    private final UserDAO userDAO = new UserDAO();

    private LocalDate savedToday;
    private Product product;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void setUp() {
        TestSession.admin();
        savedToday = AppClock.today();
        Category category = new Category();
        category.setName("test-audit-category-" + UUID.randomUUID());
        category.setActive(true);
        categoryDAO.insert(category);
        product = new Product();
        product.setName("test-audit-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("50000"));
        product.setCost(new BigDecimal("20000"));
        product.setStock(1000);
        product.setActive(true);
        productDAO.insert(product);
    }

    @After
    public void restore() {
        AppClock.pinDate(savedToday);
        TestSession.admin();
    }

    // ------------------------------------------------------------------ helpers

    private List<AuditEntry> about(String type, int id, AuditAction action) {
        return audit.find(new AuditDAO.Filter(null, null, action, null, String.valueOf(id)), 50, 0).stream()
                .filter(e -> type.equals(e.targetType()) && e.targetId() != null && e.targetId() == id).toList();
    }

    private OrderItem line(int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private Order paidOrder(String discount, String reason) {
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(new BigDecimal("100000"));
        order.setDiscount(new BigDecimal(discount));
        order.setDiscountReason(reason);
        order.setTotal(new BigDecimal("100000").subtract(new BigDecimal(discount)));
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaidAt(AppClock.now());
        return orderDAO.insert(order, List.of(line(2)));
    }

    private String[] cancellation(int orderId) throws Exception {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT status, cancelled_by, cancelled_at, cancel_reason FROM orders WHERE id = ?")) {
            st.setInt(1, orderId);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return new String[]{rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)};
            }
        }
    }

    private static String phone() {
        return "09" + String.format(java.util.Locale.ROOT, "%08d", new Random().nextInt(100_000_000));
    }

    private static Employee typed() {
        Employee e = new Employee();
        e.setFullName("Audit " + UUID.randomUUID().toString().substring(0, 8));
        e.setPhone(phone());
        e.setPosition("Pha chế");
        e.setSalary(new BigDecimal("8000000"));
        e.setHireDate(LocalDate.of(2026, 3, 1));
        return e;
    }

    private User account(Role role) {
        String username = "a" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return accounts.create(new AccountService.Form(username, "Abc12345x", role, true, typed()));
    }

    private StaffRow rowOf(int employeeId) {
        return staff.list().stream().filter(r -> r.employee().getId() == employeeId).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ discounts

    @Test
    public void aDiscountOnADirectSaleIsLoggedWithItsReason() {
        Order paid = paidOrder("5000", "khách quen");
        AuditEntry e = about(AuditTarget.ORDER, paid.getId(), AuditAction.DISCOUNT_GIVEN).get(0);
        assertEquals("5000", e.newValue());
        assertEquals("khách quen", e.detail());
        assertEquals("#" + paid.getId(), e.targetLabel());
    }

    @Test
    public void aSaleWithoutADiscountWritesNoDiscountLine() {
        Order paid = paidOrder("0", null);
        assertTrue(about(AuditTarget.ORDER, paid.getId(), AuditAction.DISCOUNT_GIVEN).isEmpty());
    }

    @Test
    public void aDiscountGivenWhenPayingAnOpenOrderIsLogged() {
        int table = tableDAO.add("test-audit-" + UUID.randomUUID().toString().substring(0, 6), 2);
        Order open = new Order();
        open.setOrderDate(AppClock.now());
        open.setTableNumber(table);
        open.setStatus(OrderStatus.OPEN);
        open.setSubtotal(new BigDecimal("100000"));
        open.setDiscount(BigDecimal.ZERO);
        open.setTotal(new BigDecimal("100000"));
        open = orderDAO.saveOrUpdateOpenOrder(open, List.of(line(2)));
        orderDAO.payExistingOrder(open.getId(), null, PaymentMethod.CASH, new BigDecimal("10000"), "sinh nhật",
                new BigDecimal("90000"), List.of(line(2)), null);
        AuditEntry e = about(AuditTarget.ORDER, open.getId(), AuditAction.DISCOUNT_GIVEN).get(0);
        assertEquals("10000", e.newValue());
        assertEquals("sinh nhật", e.detail());
    }

    // ------------------------------------------------------------------ refunds

    @Test
    public void aRefundFillsTheThreeColumnsAndLeavesALine() throws Exception {
        Order paid = paidOrder("0", null);
        orderDAO.cancelPaidOrder(paid.getId(), true, "  khách   trả lại ");
        String[] c = cancellation(paid.getId());
        assertEquals("CANCELLED", c[0]);
        assertNull("an administrator with no staff record: nobody to name", c[1]);
        assertNotNull(c[2]);
        assertEquals("khách trả lại", c[3]);
        AuditEntry e = about(AuditTarget.ORDER, paid.getId(), AuditAction.ORDER_REFUNDED).get(0);
        assertEquals("100000", e.oldValue());
        assertEquals("khách trả lại", e.detail());
    }

    @Test
    public void aRefundThatKeepsTheStockSaysSo() {
        Order paid = paidOrder("0", null);
        orderDAO.cancelPaidOrder(paid.getId(), false, "đồ uống đã pha");
        assertEquals(1, about(AuditTarget.ORDER, paid.getId(), AuditAction.ORDER_REFUNDED_NO_STOCK).size());
        assertTrue(about(AuditTarget.ORDER, paid.getId(), AuditAction.ORDER_REFUNDED).isEmpty());
    }

    @Test
    public void aRefundWithNoReasonStillWorksForCallersThatHaveNone() throws Exception {
        Order paid = paidOrder("0", null);
        orderDAO.cancelPaidOrder(paid.getId());
        assertNull(cancellation(paid.getId())[3]);
        assertEquals(1, about(AuditTarget.ORDER, paid.getId(), AuditAction.ORDER_REFUNDED).size());
    }

    @Test
    public void theStaffRecordOfWhoRefundedIsKept() throws Exception {
        User user = account(Role.ADMIN);
        Employee employee = rowOf(user.getEmployeeId()).employee();
        Order paid = paidOrder("0", null);
        Session.start(user, employee);
        orderDAO.cancelPaidOrder(paid.getId(), true, "sai món");
        TestSession.admin();
        assertEquals(String.valueOf(employee.getId()), cancellation(paid.getId())[1]);
        AuditEntry e = about(AuditTarget.ORDER, paid.getId(), AuditAction.ORDER_REFUNDED).get(0);
        assertEquals(Integer.valueOf(employee.getId()), e.actorEmployeeId());
        assertEquals(employee.getFullName(), e.actorName());
    }

    @Test
    public void aSecondRefundOfTheSameOrderLeavesNoSecondLine() {
        Order paid = paidOrder("0", null);
        orderDAO.cancelPaidOrder(paid.getId(), true, "lần một");
        try {
            orderDAO.cancelPaidOrder(paid.getId(), true, "lần hai");
            fail("already cancelled");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(1, about(AuditTarget.ORDER, paid.getId(), AuditAction.ORDER_REFUNDED).size());
    }

    // ------------------------------------------------------------------ tables and take-away

    @Test
    public void emptyingATableWithAnOpenOrderLeavesALineAndTheColumns() throws Exception {
        int table = tableDAO.add("test-audit-" + UUID.randomUUID().toString().substring(0, 6), 2);
        Order open = new Order();
        open.setOrderDate(AppClock.now());
        open.setTableNumber(table);
        open.setStatus(OrderStatus.OPEN);
        open.setSubtotal(new BigDecimal("90000"));
        open.setDiscount(BigDecimal.ZERO);
        open.setTotal(new BigDecimal("90000"));
        open = orderDAO.saveOrUpdateOpenOrder(open, List.of(line(1)));
        tableDAO.setEmpty(table);
        AuditEntry e = about(AuditTarget.ORDER, open.getId(), AuditAction.TABLE_EMPTIED).get(0);
        assertEquals("90000", e.oldValue());
        assertEquals(String.valueOf(table), e.detail());
        String[] c = cancellation(open.getId());
        assertEquals("CANCELLED", c[0]);
        assertNotNull(c[2]);
    }

    @Test
    public void emptyingATableWithNoOpenOrderWritesNothing() {
        int table = tableDAO.add("test-audit-" + UUID.randomUUID().toString().substring(0, 6), 2);
        tableDAO.setEmpty(table);
        assertTrue("the table is new, so any line that names it would be this one", audit
                .find(new AuditDAO.Filter(null, null, AuditAction.TABLE_EMPTIED, null, null), 500, 0).stream()
                .noneMatch(e -> String.valueOf(table).equals(e.detail())));
    }

    @Test
    public void cancellingAnOpenTakeAwayOrderLeavesALine() throws Exception {
        Order open = new Order();
        open.setOrderDate(AppClock.now());
        open.setStatus(OrderStatus.OPEN);
        open.setSubtotal(new BigDecimal("50000"));
        open.setDiscount(BigDecimal.ZERO);
        open.setTotal(new BigDecimal("50000"));
        open = orderDAO.insert(open, List.of(line(1)));
        assertTrue(orderDAO.cancelOpenTakeAway(open.getId()));
        assertFalse("a second cancel changes nothing", orderDAO.cancelOpenTakeAway(open.getId()));
        List<AuditEntry> lines = about(AuditTarget.ORDER, open.getId(), AuditAction.TAKE_AWAY_CANCELLED);
        assertEquals(1, lines.size());
        assertEquals("50000", lines.get(0).oldValue());
        assertNotNull(cancellation(open.getId())[2]);
    }

    // ------------------------------------------------------------------ products

    @Test
    public void aPriceCostAndStockEditAreLoggedAndASaveThatChangesNothingIsNot() {
        Product loaded = productDAO.findById(product.getId()).orElseThrow();
        loaded.setLoadedStock(loaded.getStock());
        productDAO.update(loaded); // nothing changed
        assertTrue("a save that changes nothing writes nothing",
                about(AuditTarget.PRODUCT, product.getId(), AuditAction.PRODUCT_PRICE).isEmpty());
        int before = 0;

        loaded.setPrice(new BigDecimal("55000"));
        loaded.setCost(new BigDecimal("21000"));
        loaded.setStock(990); // typed over: a stock-take
        productDAO.update(loaded);

        AuditEntry price = about(AuditTarget.PRODUCT, product.getId(), AuditAction.PRODUCT_PRICE).get(0);
        assertEquals("50000", price.oldValue());
        assertEquals("55000", price.newValue());
        assertEquals(before + 1, about(AuditTarget.PRODUCT, product.getId(), AuditAction.PRODUCT_PRICE).size());
        AuditEntry cost = about(AuditTarget.PRODUCT, product.getId(), AuditAction.PRODUCT_COST).get(0);
        assertEquals("20000", cost.oldValue());
        assertEquals("21000", cost.newValue());
        AuditEntry stock = about(AuditTarget.PRODUCT, product.getId(), AuditAction.PRODUCT_STOCK).get(0);
        assertEquals("1000", stock.oldValue());
        assertEquals("990", stock.newValue());
        assertEquals(product.getName(), stock.targetLabel());
    }

    @Test
    public void aStockNumberThatWasNotTouchedIsNotLoggedEvenWhenSalesChangedIt() {
        Product loaded = productDAO.findById(product.getId()).orElseThrow();
        loaded.setLoadedStock(loaded.getStock());
        paidOrder("0", null); // sells 2: the stock in the database is now 998
        loaded.setName(loaded.getName() + "-x");
        productDAO.update(loaded);
        assertTrue(about(AuditTarget.PRODUCT, product.getId(), AuditAction.PRODUCT_STOCK).isEmpty());
    }

    // ------------------------------------------------------------------ accounts

    @Test
    public void anAccountIsLoggedWhenCreatedChangedLockedAndGivenANewPassword() {
        User user = account(Role.EMPLOYEE);
        AuditEntry created = about(AuditTarget.ACCOUNT, user.getId(), AuditAction.ACCOUNT_CREATED).get(0);
        assertEquals("EMPLOYEE", created.newValue());
        assertEquals("ACTIVE", created.detail());
        assertEquals(user.getUsername(), created.targetLabel());

        Employee employee = rowOf(user.getEmployeeId()).employee();
        accounts.update(user, employee, new AccountService.Form(user.getUsername(), "", Role.ADMIN, true, employee));
        AuditEntry role = about(AuditTarget.ACCOUNT, user.getId(), AuditAction.ACCOUNT_ROLE_CHANGED).get(0);
        assertEquals("EMPLOYEE", role.oldValue());
        assertEquals("ADMIN", role.newValue());
        assertTrue("no password given: no reset line", about(AuditTarget.ACCOUNT, user.getId(), AuditAction.PASSWORD_RESET).isEmpty());
        assertTrue("same role again: nothing new", about(AuditTarget.ACCOUNT, user.getId(), AuditAction.ACCOUNT_LOCKED).isEmpty());

        accounts.update(user, employee, new AccountService.Form(user.getUsername(), "Xyz98765w", Role.ADMIN, true, employee));
        assertEquals(1, about(AuditTarget.ACCOUNT, user.getId(), AuditAction.PASSWORD_RESET).size());

        User fresh = userDAO.findById(user.getId()).orElseThrow();
        accounts.setLocked(fresh, true);
        assertEquals(1, about(AuditTarget.ACCOUNT, user.getId(), AuditAction.ACCOUNT_LOCKED).size());
        accounts.setLocked(userDAO.findById(user.getId()).orElseThrow(), false);
        assertEquals(1, about(AuditTarget.ACCOUNT, user.getId(), AuditAction.ACCOUNT_UNLOCKED).size());
    }

    @Test
    public void noPasswordAndNoHashIsEverWrittenToTheLog() {
        User user = account(Role.EMPLOYEE);
        Employee employee = rowOf(user.getEmployeeId()).employee();
        accounts.update(user, employee, new AccountService.Form(user.getUsername(), "Xyz98765w", Role.EMPLOYEE, true, employee));
        String hash = userDAO.findById(user.getId()).orElseThrow().getPasswordHash();
        for (AuditEntry e : audit.find(new AuditDAO.Filter(null, null, null, null, user.getUsername()), 50, 0)) {
            for (String field : new String[]{e.targetLabel(), e.oldValue(), e.newValue(), e.detail()}) {
                if (field == null) continue;
                assertFalse(field.contains("Xyz98765w"));
                assertFalse(field.contains("Abc12345x"));
                assertFalse(field.contains(hash));
            }
        }
    }

    @Test
    public void changingYourOwnPasswordIsLogged() {
        User user = account(Role.EMPLOYEE);
        PasswordChangeService.Result result = new PasswordChangeService().change(user.getId(), "Abc12345x", "Tn58-Jv27-Kd", true);
        assertEquals(PasswordChangeService.Result.OK, result);
        assertEquals(1, about(AuditTarget.ACCOUNT, user.getId(), AuditAction.PASSWORD_CHANGED).size());
    }

    // ------------------------------------------------------------------ staff

    @Test
    public void deletingAPersonWithNoHistoryIsLogged() {
        Employee saved = staff.save(typed());
        StaffRow row = rowOf(saved.getId());
        staff.remove(row);
        AuditEntry e = about(AuditTarget.STAFF, saved.getId(), AuditAction.STAFF_REMOVED).get(0);
        assertEquals(saved.getFullName(), e.targetLabel());
    }

    @Test
    public void makingAStaffMemberAnonymousRemovesTheirNameFromEveryOlderLine() {
        User user = account(Role.ADMIN);
        Employee employee = rowOf(user.getEmployeeId()).employee();
        String oldName = employee.getFullName();
        String oldLogin = user.getUsername();
        account(Role.ADMIN); // another administrator, so this one may be locked

        Session.start(user, employee);
        Product loaded = productDAO.findById(product.getId()).orElseThrow();
        loaded.setLoadedStock(loaded.getStock());
        loaded.setPrice(new BigDecimal("52000")); // a line this person did, under their own name
        productDAO.update(loaded);
        TestSession.admin();
        assertTrue(audit.find(new AuditDAO.Filter(null, null, AuditAction.PRODUCT_PRICE, oldName, null), 10, 0).size() >= 1);

        staff.setWorking(rowOf(employee.getId()), false);
        staff.makeAnonymous(rowOf(employee.getId()));

        String newName = rowOf(employee.getId()).employee().getFullName();
        assertNotEquals(oldName, newName);
        assertTrue("nothing is left under the old name", audit.find(new AuditDAO.Filter(null, null, null, oldName, null), 10, 0).isEmpty());
        assertTrue(audit.find(new AuditDAO.Filter(null, null, AuditAction.PRODUCT_PRICE, newName, null), 10, 0).size() >= 1);
        AuditEntry made = about(AuditTarget.STAFF, employee.getId(), AuditAction.STAFF_ANONYMISED).get(0);
        assertEquals(newName, made.targetLabel());
        AuditEntry createdLine = about(AuditTarget.ACCOUNT, user.getId(), AuditAction.ACCOUNT_CREATED).get(0);
        assertNotEquals(oldLogin, createdLine.targetLabel());
        assertTrue(createdLine.targetLabel().startsWith("former-"));
        assertTrue("the role stays: it is not personal", "ADMIN".equals(createdLine.newValue()));
    }

    @Test
    public void aPersonWhoDidSomethingLoggedCannotBeDeleted() {
        User user = account(Role.ADMIN);
        Employee employee = rowOf(user.getEmployeeId()).employee();
        Session.start(user, employee);
        AuditDAO.recordQuietly(AuditAction.BACKUP_MADE, AuditTarget.BACKUP, null, "x.zip", null, "PLAIN", null);
        TestSession.admin();
        assertTrue(staff.hasHistory(rowOf(employee.getId())));
    }

    // ------------------------------------------------------------------ customers

    private Customer customer(int points) {
        Customer c = new Customer();
        c.setFullName("test-audit-customer-" + UUID.randomUUID());
        c.setPhone(phone());
        c.setLoyaltyPoints(points);
        return customerDAO.insert(c);
    }

    @Test
    public void mergingAnonymisingAndDeletingCustomersIsLoggedWithoutLeavingNames() {
        Customer keep = customer(10);
        Customer drop = customer(5);
        merges.merge(keep.getId(), drop.getId());
        AuditEntry merged = about(AuditTarget.CUSTOMER, keep.getId(), AuditAction.CUSTOMER_MERGED).get(0);
        assertEquals(keep.getFullName(), merged.targetLabel());
        assertTrue(merged.oldValue().contains(drop.getFullName()));
        assertEquals("0|5", merged.detail());

        merges.anonymise(keep.getId());
        AuditEntry scrubbed = about(AuditTarget.CUSTOMER, keep.getId(), AuditAction.CUSTOMER_MERGED).get(0);
        assertNotEquals(keep.getFullName(), scrubbed.targetLabel());
        assertNull(scrubbed.oldValue());
        assertNull(scrubbed.detail());
        assertEquals(1, about(AuditTarget.CUSTOMER, keep.getId(), AuditAction.CUSTOMER_ANONYMISED).size());
    }

    // ------------------------------------------------------------------ backups

    @Test
    public void aBackupMadeByHandIsLoggedWithTheFileNameOnly() throws Exception {
        Path scratch = Files.createTempDirectory("lunavera-audit-backup-test");
        String name = "audit-" + UUID.randomUUID().toString().substring(0, 8) + ".zip";
        try {
            Path made = BackupService.createBackup(scratch.resolve(name));
            AuditEntry e = audit.find(new AuditDAO.Filter(null, null, AuditAction.BACKUP_MADE, null, name), 5, 0).get(0);
            assertEquals(made.getFileName().toString(), e.targetLabel());
            assertFalse("the folder is not written", e.targetLabel().contains(scratch.toString()));
            assertEquals("PLAIN", e.newValue());
        } finally {
            try (var files = Files.walk(scratch)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}
