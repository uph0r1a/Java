package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditEntry;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.CheckoutService;
import com.coffeeshop.coffeeshopmanagement.service.ComplimentaryRules.Give;
import com.coffeeshop.coffeeshopmanagement.service.ComplimentaryRules.Kind;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 4.61 (rule D26) on a real database: a free bill is saved paid at 0 with its kind, person and menu price; the stock is used; it is not a
 * discount; a cashier's daily limit is enforced inside the payment (and a refused one saves nothing); an administrator has no limit; a cancelled
 * free bill gives the allowance back; the report counts cups, cost and value; the audit line says who got it. The clock is pinned to a day of
 * its own so other tests' bills never count.
 */
public class ComplimentaryDatabaseTest {

    private final CheckoutService checkout = new CheckoutService();
    private final ComplimentaryDAO free = new ComplimentaryDAO();
    private LocalDate savedToday;
    private LocalDate today;
    private Product product;
    private Employee mai;
    private Employee linh;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @AfterClass
    public static void signOut() {
        TestSession.clear();
    }

    @Before
    public void setUp() {
        TestSession.admin();
        savedToday = AppClock.today();
        today = LocalDate.of(2400, 1, 1).plusDays(new Random().nextInt(30_000));
        AppClock.fix(today.atTime(12, 0));
        Category category = new Category();
        category.setName("test-free-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        product = new Product();
        product.setName("test-free-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("40000"));
        product.setCost(new BigDecimal("12000"));
        product.setStock(100000);
        product.setActive(true);
        new ProductDAO().insert(product);
        mai = staff("test-free-mai");
        linh = staff("test-free-linh");
    }

    @After
    public void restoreClock() {
        AppClock.pinDate(savedToday);
    }

    private Employee staff(String name) {
        Employee e = new Employee();
        e.setFullName(name + "-" + UUID.randomUUID());
        e.setActive(true);
        return new EmployeeDAO().insert(e);
    }

    private OrderItem line(int qty) {
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(qty);
        item.setUnitPrice(new BigDecimal("40000"));
        item.setLineTotal(new BigDecimal("40000").multiply(BigDecimal.valueOf(qty)));
        return item;
    }

    private Order give(Give give, int cups, Role role) {
        BigDecimal subtotal = new BigDecimal("40000").multiply(BigDecimal.valueOf(cups));
        return checkout.pay(null, List.of(line(cups)), subtotal, null, "", "", null, CheckoutService.Payment.free(give), null, role).order();
    }

    private String text(String sql, int id) throws Exception {
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private int stock() {
        return new ProductDAO().findById(product.getId()).orElseThrow().getStock();
    }

    @Test
    public void aStaffDrinkIsSavedPaidAtZeroAndMarked() throws Exception {
        Order paid = give(new Give(Kind.STAFF, mai.getId(), "closing shift"), 2, Role.EMPLOYEE);
        assertEquals(0, paid.getTotal().signum());
        assertEquals(0, paid.getDiscount().signum());
        assertEquals(PaymentMethod.COMPLIMENTARY, paid.getPaymentMethod());
        assertEquals("STAFF", text("SELECT complimentary_kind FROM orders WHERE id = ?", paid.getId()));
        assertEquals(String.valueOf(mai.getId()), text("SELECT complimentary_for FROM orders WHERE id = ?", paid.getId()));
        assertEquals("the menu price of two cups is kept", "80000", text("SELECT complimentary_value FROM orders WHERE id = ?", paid.getId()));
        assertEquals("closing shift", text("SELECT complimentary_note FROM orders WHERE id = ?", paid.getId()));
        assertEquals("the lines are at 0", "0", text("SELECT COALESCE(SUM(line_total), 0) FROM order_items WHERE order_id = ?", paid.getId()));
        assertEquals("the drink's cost was recorded on the line", "24000", text("SELECT SUM(unit_cost * quantity) FROM order_items WHERE order_id = ?", paid.getId()));
        assertEquals("the stock was used", 100000 - 2, stock());
    }

    @Test
    public void aCashierIsHeldToTheDailyLimitPerPersonAndARefusedBillSavesNothing() throws Exception {
        give(new Give(Kind.STAFF, mai.getId(), null), 1, Role.EMPLOYEE);
        give(new Give(Kind.STAFF, mai.getId(), null), 1, Role.EMPLOYEE); // the limit is 2 until the owner changes it
        int stockBefore = stock();
        try {
            give(new Give(Kind.STAFF, mai.getId(), null), 1, Role.EMPLOYEE);
            fail("a third free cup for the same person in one day");
        } catch (DataAccessException expected) {
            assertFalse(expected.getMessage().startsWith("complimentary."));
        }
        assertEquals("nothing was saved and the stock was not taken", stockBefore, stock());
        assertEquals(2, free.cupsGivenToday(Kind.STAFF, mai.getId()));
        give(new Give(Kind.STAFF, linh.getId(), null), 1, Role.EMPLOYEE); // another person has their own allowance
        assertEquals(1, free.cupsGivenToday(Kind.STAFF, linh.getId()));
    }

    @Test
    public void theOwnerIsOnePersonAndAnAdministratorHasNoLimit() {
        give(new Give(Kind.OWNER, null, null), 2, Role.EMPLOYEE);
        try {
            give(new Give(Kind.OWNER, null, null), 1, Role.EMPLOYEE);
            fail("a third cup for the owner from a cashier");
        } catch (DataAccessException expected) {
            // refused
        }
        give(new Give(Kind.OWNER, null, null), 5, Role.ADMIN);
        give(new Give(Kind.STAFF, mai.getId(), null), 9, Role.ADMIN);
        assertEquals(7, free.cupsGivenToday(Kind.OWNER, null));
    }

    @Test
    public void aTastingIsRefusedForACashierAndAcceptedForAnAdministratorWithANote() {
        try {
            give(new Give(Kind.TASTING, null, "milk supplier"), 1, Role.EMPLOYEE);
            fail("a cashier gave a tasting");
        } catch (DataAccessException expected) {
            // refused
        }
        Order paid = give(new Give(Kind.TASTING, null, "milk supplier"), 3, Role.ADMIN);
        assertEquals(0, paid.getTotal().signum());
    }

    @Test
    public void aPersonWhoIsNotActiveCannotBeGivenADrink() {
        new EmployeeDAO().setActive(linh.getId(), false);
        int before = stock();
        try {
            give(new Give(Kind.STAFF, linh.getId(), null), 1, Role.ADMIN);
            fail("a drink for someone who no longer works here");
        } catch (DataAccessException expected) {
            // refused
        }
        assertEquals(before, stock());
    }

    @Test
    public void aCancelledFreeBillGivesTheAllowanceAndTheStockBack() {
        Order first = give(new Give(Kind.STAFF, mai.getId(), null), 2, Role.EMPLOYEE);
        assertEquals(2, free.cupsGivenToday(Kind.STAFF, mai.getId()));
        new OrderDAO().cancelPaidOrder(first.getId(), true, "wrong person, a mistake");
        assertEquals(0, free.cupsGivenToday(Kind.STAFF, mai.getId()));
        assertEquals(100000, stock());
        give(new Give(Kind.STAFF, mai.getId(), null), 2, Role.EMPLOYEE);
    }

    @Test
    public void aLineOfAFreeBillCannotBeRefunded() {
        Order paid = give(new Give(Kind.OWNER, null, null), 1, Role.ADMIN);
        int itemId = new OrderDAO().findItemsByOrderId(paid.getId()).get(0).getId();
        try {
            new RefundDAO().refund(paid.getId(), java.util.Map.of(itemId, 1), true, PaymentMethod.CASH, "no money was paid");
            fail("refunded a free bill");
        } catch (DataAccessException expected) {
            assertFalse(expected.getMessage().startsWith("complimentary."));
        }
    }

    @Test
    public void nothingElseCanBeOnAFreeBill() {
        CheckoutService.Payment withDiscount = new CheckoutService.Payment(PaymentMethod.COMPLIMENTARY, new BigDecimal("1000"), "x", BigDecimal.ZERO,
                null, null, 0, null, null, null, null, false, new Give(Kind.OWNER, null, null));
        try {
            checkout.pay(null, List.of(line(1)), new BigDecimal("40000"), null, "", "", null, withDiscount, null, Role.ADMIN);
            fail("a discount on a free bill");
        } catch (DataAccessException expected) {
            // refused before anything was written
        }
    }

    @Test
    public void theReportCountsCupsCostAndValueAndKeepsDiscountsOut() {
        give(new Give(Kind.STAFF, mai.getId(), null), 2, Role.ADMIN);
        give(new Give(Kind.OWNER, null, null), 1, Role.ADMIN);
        ComplimentaryDAO.Totals totals = free.totals(today, today);
        assertEquals(2, totals.bills());
        assertEquals(3, totals.cups());
        assertEquals(0, new BigDecimal("36000").compareTo(totals.cost()));
        assertEquals(0, new BigDecimal("120000").compareTo(totals.value()));
        assertEquals(0, totals.cupsWithoutCost());
        List<ComplimentaryDAO.PersonLine> people = free.byPerson(today, today);
        assertEquals(2, people.size());
        assertEquals("the biggest cost first", Kind.STAFF, people.get(0).kind());
        assertEquals(mai.getId(), people.get(0).employeeId().intValue());
        ReportDAO.Summary summary = new ReportDAO().summary(today, today);
        assertEquals("no money and no discount", 0, summary.revenue().signum() + summary.discounts().signum());
        assertEquals(2, summary.free().bills());
        assertTrue("no discount line for free drinks", new ReportDAO().discountsByCashier(today, today).isEmpty());
        assertTrue("the next day has none", free.totals(today.plusDays(1), today.plusDays(1)).isNone());
    }

    @Test
    public void theAuditLineSaysWhoGotIt() {
        Order paid = give(new Give(Kind.STAFF, mai.getId(), "late shift"), 1, Role.EMPLOYEE);
        List<AuditEntry> found = new AuditDAO().find(new AuditDAO.Filter(today, today, AuditAction.COMPLIMENTARY_GIVEN, null, null), 10, 0);
        assertEquals(1, found.size());
        AuditEntry entry = found.get(0);
        assertEquals("STAFF", entry.targetType());
        assertEquals(mai.getId(), entry.targetId().intValue());
        assertEquals(mai.getFullName(), entry.targetLabel());
        assertEquals("STAFF|" + paid.getId() + "|1|late shift", entry.detail());
        assertEquals("40000", entry.newValue());
    }
}
