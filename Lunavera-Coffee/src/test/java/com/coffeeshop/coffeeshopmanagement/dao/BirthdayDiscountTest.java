package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.BirthdayRules;
import com.coffeeshop.coffeeshopmanagement.service.CheckoutService;
import com.coffeeshop.coffeeshopmanagement.service.CustomerMergeService;
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
 * TODO 3.66 (rule D25): the birthday discount is given once a day to a customer. Paying with the Birthday button writes the day into
 * {@code customers.birthday_used_on} in the payment's own transaction; a second payment the same day is refused and saves nothing.
 */
public class BirthdayDiscountTest {

    private final CustomerDAO customers = new CustomerDAO();
    private final CheckoutService checkout = new CheckoutService();
    private LocalDate savedToday;
    private LocalDate today;
    private Product product;

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
        category.setName("test-bday-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        product = new Product();
        product.setName("test-bday-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("40000"));
        product.setStock(100000);
        product.setActive(true);
        new ProductDAO().insert(product);
    }

    @After
    public void restoreClock() {
        AppClock.pinDate(savedToday);
    }

    /** A customer born in 1992 on the day the clock shows, or on another day when {@code onBirthday} is false. */
    private Customer customer(boolean onBirthday) {
        Customer c = new Customer();
        c.setFullName("test-bday");
        c.setPhone("09" + (10_000_000 + new Random().nextInt(89_999_999)));
        c.setBirthday((onBirthday ? today : today.plusDays(3)).withYear(1992)); // a leap year, so 29 February stays 29 February
        return customers.insert(c);
    }

    private OrderItem line() {
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("40000"));
        item.setLineTotal(new BigDecimal("40000"));
        return item;
    }

    private CheckoutService.Payment payment(String discount, boolean birthday) {
        BigDecimal d = new BigDecimal(discount);
        CheckoutService.Payment p = new CheckoutService.Payment(PaymentMethod.CASH, d, d.signum() > 0 ? "Birthday 10%" : null,
                new BigDecimal("40000").subtract(d), null, null);
        return birthday ? p.withBirthday() : p;
    }

    private Order pay(Customer c, String discount, boolean birthday) {
        return checkout.pay(null, List.of(line()), new BigDecimal("40000"), c, "", "", null, payment(discount, birthday), null, Role.ADMIN).order();
    }

    private int orderCount(int customerId) throws Exception {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM orders WHERE customer_id = ?")) {
            st.setInt(1, customerId);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private LocalDate usedOn(int customerId) {
        return customers.findById(customerId).orElseThrow().getBirthdayUsedOn();
    }

    @Test
    public void payingWithTheBirthdayDiscountRecordsTheDay() {
        Customer c = customer(true);
        assertNull(usedOn(c.getId()));
        assertTrue(BirthdayRules.isOffered(customers.findById(c.getId()).orElseThrow(), today));
        Order paid = pay(c, "4000", true);
        assertEquals(0, new BigDecimal("4000").compareTo(paid.getDiscount()));
        assertEquals(today, usedOn(c.getId()));
        assertFalse("the badge no longer offers it", BirthdayRules.isOffered(customers.findById(c.getId()).orElseThrow(), today));
        assertTrue(BirthdayRules.usedToday(customers.findById(c.getId()).orElseThrow(), today));
    }

    @Test
    public void itCannotBeGivenTwiceTheSameDayAndTheSecondPaymentSavesNothing() throws Exception {
        Customer c = customer(true);
        pay(c, "4000", true);
        assertEquals(1, orderCount(c.getId()));
        try {
            pay(customers.findById(c.getId()).orElseThrow(), "4000", true);
            fail("the birthday discount was given twice on one day");
        } catch (DataAccessException expected) {
            // refused
        }
        assertEquals("no second order was saved", 1, orderCount(c.getId()));
        assertEquals(today, usedOn(c.getId()));
    }

    @Test
    public void aStaleScreenCannotGiveItAgainEither() throws Exception {
        Customer c = customer(true);
        Customer stale = customers.findById(c.getId()).orElseThrow(); // what a second till still shows: not given yet
        pay(c, "4000", true);
        try {
            pay(stale, "4000", true);
            fail("the day is checked in the database, not on the screen");
        } catch (DataAccessException expected) {
            // refused
        }
        assertEquals(1, orderCount(c.getId()));
    }

    @Test
    public void anOrdinaryDiscountIsNotTrackedAndRepeatsAsBefore() throws Exception {
        Customer c = customer(true);
        pay(c, "4000", false);
        pay(c, "4000", false);
        assertEquals(2, orderCount(c.getId()));
        assertNull("only the Birthday button counts", usedOn(c.getId()));
    }

    @Test
    public void itIsRefusedWhenTodayIsNotTheirBirthday() throws Exception {
        Customer c = customer(false);
        try {
            pay(c, "4000", true);
            fail("the birthday discount was given on another day");
        } catch (DataAccessException expected) {
            // refused
        }
        assertEquals("the order was not kept", 0, orderCount(c.getId()));
        assertNull(usedOn(c.getId()));
    }

    @Test
    public void itNeedsACustomerAndExactlyTheButtonsAmount() throws Exception {
        Customer c = customer(true);
        try {
            checkout.pay(null, List.of(line()), new BigDecimal("40000"), null, "", "", null, payment("4000", true), null, Role.ADMIN);
            fail("no customer on the bill");
        } catch (DataAccessException expected) {
            // refused
        }
        try {
            pay(c, "4001", true);
            fail("a typed amount is not the birthday discount");
        } catch (DataAccessException expected) {
            // refused
        }
        assertEquals(0, orderCount(c.getId()));
        assertNull(usedOn(c.getId()));
    }

    @Test
    public void itIsGivenAgainOnTheNextBirthday() {
        Customer c = customer(true);
        pay(c, "4000", true);
        try (Connection db = DatabaseConfig.getConnection();
             PreparedStatement st = db.prepareStatement("UPDATE customers SET birthday_used_on = ? WHERE id = ?")) {
            st.setString(1, today.minusYears(1).toString()); // as if it had been given a year ago
            st.setInt(2, c.getId());
            st.executeUpdate();
        } catch (java.sql.SQLException e) {
            throw new AssertionError(e);
        }
        pay(customers.findById(c.getId()).orElseThrow(), "4000", true);
        assertEquals(today, usedOn(c.getId()));
    }

    @Test
    public void payingAnOpenOrderRecordsItToo() {
        Customer c = customer(true);
        Order open = new Order();
        open.setOrderDate(AppClock.now());
        open.setStatus(OrderStatus.OPEN);
        open.setSubtotal(new BigDecimal("40000"));
        open.setDiscount(BigDecimal.ZERO);
        open.setTotal(new BigDecimal("40000"));
        open = new OrderDAO().insert(open, List.of(line()));
        CheckoutService.PaidOrder paid = checkout.pay(null, List.of(line()), new BigDecimal("40000"), c, "", "", open,
                payment("4000", true), null, Role.ADMIN);
        assertEquals(OrderStatus.PAID, paid.order().getStatus());
        assertEquals(today, usedOn(c.getId()));
    }

    @Test
    public void everyUseIsOneLineInTheAuditTrail() {
        Customer c = customer(true);
        Order paid = pay(c, "4000", true);
        var lines = new AuditDAO().find(new AuditDAO.Filter(null, null, AuditAction.BIRTHDAY_DISCOUNT_USED, null, null), 500, 0);
        assertTrue(lines.stream().anyMatch(l -> Integer.valueOf(c.getId()).equals(l.targetId()) && ("order #" + paid.getId()).equals(l.detail())));
        assertTrue("the date of birth is not written in it",
                lines.stream().noneMatch(l -> String.valueOf(l.detail()).contains("1992") || String.valueOf(l.newValue()).contains("1992")));
    }

    @Test
    public void mergingKeepsTheLaterDayAndAnonymisingClearsIt() {
        Customer kept = customer(true);
        Customer other = customer(true);
        pay(other, "4000", true); // given to the one that is merged away
        assertNull(usedOn(kept.getId()));
        new CustomerMergeService().merge(kept.getId(), other.getId());
        assertEquals("a merge cannot give it again", today, usedOn(kept.getId()));
        new CustomerMergeService().anonymise(kept.getId());
        assertNull(usedOn(kept.getId()));
    }
}
