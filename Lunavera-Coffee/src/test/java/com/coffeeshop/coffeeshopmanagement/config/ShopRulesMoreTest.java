package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.service.*;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.IdleTracker;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 4.49, the rest of the item: the cashier limit stops at 30 %, and the birthday suggestion, the booking slot, the idle sign-out
 * and the forgotten clock-in hours are settings too: checked, applied, saved with an audit line, and an order already paid is
 * never recalculated.
 */
public class ShopRulesMoreTest {

    private String language;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void useEnglish() {
        language = I18n.current();
        I18n.setLanguage(I18n.EN);
        ShopSettings.resetForTests();
    }

    @After
    public void putBack() {
        ShopSettings.saveAll("Lunavera Coffee", "", "", "", Integer.toString(StockLevel.DEFAULT), ShopRules.DEFAULTS);
        ShopSettings.resetForTests();
        I18n.setLanguage(language);
    }

    private static final String[] OK = {"10", "10000", "1000", "50", "20", "100", "500", "10", "90", "15", "16", "0", "208"};

    private static String[] with(int index, String value) {
        String[] boxes = OK.clone();
        boxes[index] = value;
        return boxes;
    }

    // ------------------------------------------------------------------ the limits (no database)

    @Test
    public void theCashierLimitStopsAtThirtyPercent() {
        assertNull(ShopRules.problemWith(with(0, "30")));
        assertNotNull(ShopRules.problemWith(with(0, "31")));
        assertNotNull("100 % is refused", ShopRules.problemWith(with(0, "100")));
    }

    @Test
    public void everyNewRuleHasItsRange() {
        assertNotNull(ShopRules.problemWith(with(7, "0")));
        assertNotNull(ShopRules.problemWith(with(7, "31")));
        assertNull(ShopRules.problemWith(with(7, "30")));
        assertNotNull(ShopRules.problemWith(with(8, "14")));
        assertNotNull(ShopRules.problemWith(with(8, "241")));
        assertNull(ShopRules.problemWith(with(8, "15")));
        assertNotNull(ShopRules.problemWith(with(9, "481")));
        assertNull("0 means never", ShopRules.problemWith(with(9, "0")));
        assertNotNull(ShopRules.problemWith(with(10, "3")));
        assertNotNull(ShopRules.problemWith(with(10, "25")));
        assertNull(ShopRules.problemWith(with(10, "24")));
        assertTrue("the field is named", ShopRules.problemWith(with(8, "5")).contains("booking"));
    }

    @Test
    public void blankBoxesAndTheOlderSevenBoxFormMeanTheOriginalValues() {
        assertEquals(ShopRules.DEFAULTS, ShopRules.parse("", "", "", "", "", "", "", "", "", "", "", "", ""));
        assertEquals(ShopRules.DEFAULTS, ShopRules.parse("10", "10000", "1000", "50", "20", "100", "500"));
    }

    @Test
    public void changesListsOnlyTheRulesThatDiffer() {
        ShopRules.Values after = new ShopRules.Values(20, 10_000, 1_000, 50, 20, 100, 500, 10, 120, 0, 16);
        List<ShopRules.Change> changes = ShopRules.changes(ShopRules.DEFAULTS, after);
        assertEquals(3, changes.size());
        assertEquals(new ShopRules.Change("staffDiscountPercent", 10, 20), changes.get(0));
        assertEquals(new ShopRules.Change("slotMinutes", 90, 120), changes.get(1));
        assertEquals(new ShopRules.Change("idleMinutes", 15, 0), changes.get(2));
        assertTrue(ShopRules.changes(after, after).isEmpty());
    }

    @Test
    public void thePayEstimateRulesHaveTheirRanges() {
        assertNull(ShopRules.problemWith(with(11, "0")));
        assertNull(ShopRules.problemWith(with(11, "1")));
        assertNotNull("only monthly (0) or hourly (1)", ShopRules.problemWith(with(11, "2")));
        assertNotNull(ShopRules.problemWith(with(12, "39")));
        assertNotNull(ShopRules.problemWith(with(12, "401")));
        assertNull(ShopRules.problemWith(with(12, "173")));
        assertTrue(ShopRules.problemWith(with(12, "5")).contains("month"));
        assertEquals("the older forms without them mean a monthly salary and 208 hours", 0, ShopRules.parse(OK).salaryHourly());
        assertEquals(208, ShopRules.parse(OK).monthlyHours());
    }

    @Test
    public void aChangeOfHowTheSalaryIsReadIsListedAndSavedAndAudited() {
        ShopRules.Values after = new ShopRules.Values(10, 10_000, 1_000, 50, 20, 100, 500, 10, 90, 15, 16, 1, 173);
        assertEquals(2, ShopRules.changes(ShopRules.DEFAULTS, after).size());
        ShopSettings.saveAll("Lunavera Coffee", "", "", "", "10", after);
        ShopSettings.resetForTests();
        ShopSettings.load();
        assertEquals(after, ShopRules.current());
        assertTrue(hasLine("salaryHourly", "0", "1"));
        assertTrue(hasLine("monthlyHours", "208", "173"));
    }

    // ------------------------------------------------------------------ the rules are used

    @Test
    public void thePlacesThatUsedConstantsFollowTheRules() {
        ShopRules.set(new ShopRules.Values(10, 10_000, 1_000, 50, 20, 100, 500, 15, 60, 30, 12));
        assertEquals(15, BirthdayRules.suggestedPercent());
        Customer c = new Customer();
        c.setFullName("An");
        c.setBirthday(LocalDate.of(1990, 5, 5));
        assertTrue(BirthdayRules.badge(c, LocalDate.of(2026, 5, 5)).contains("15"));

        LocalDateTime noon = LocalDateTime.of(2026, 5, 5, 12, 0);
        assertTrue("59 minutes apart clash with a 60 minute slot", ReservationRules.tooClose(noon, noon.plusMinutes(59)));
        assertFalse(ReservationRules.tooClose(noon, noon.plusMinutes(60)));

        assertEquals(12, TimeClockRules.forgottenAfterHours());
        assertTrue(TimeClockRules.looksForgotten(noon, noon.plusHours(12)));
        assertFalse(TimeClockRules.looksForgotten(noon, noon.plusHours(11)));
        assertEquals(12 * 60L, TimeClockRules.minutesWithin(noon, null, noon.minusDays(1), noon.plusDays(3), noon.plusDays(2)));
    }

    @Test
    public void theIdleLimitComesFromTheRuleUnlessTheDevelopersOverrideIsGiven() {
        assertEquals(Duration.ofMinutes(15), IdleTracker.limit(null, 15));
        assertEquals(Duration.ofMinutes(45), IdleTracker.limit("", 45));
        assertEquals(Duration.ZERO, IdleTracker.limit(null, 0));
        assertEquals("-Dlunavera.idleMinutes wins", Duration.ofMinutes(5), IdleTracker.limit("5", 45));
        assertEquals(Duration.ZERO, IdleTracker.limit("0", 45));
    }

    // ------------------------------------------------------------------ the database

    @Test
    public void allElevenRulesAreSavedAndReadBackByAFreshStart() {
        ShopRules.Values mine = new ShopRules.Values(20, 20_000, 2_000, 40, 5, 150, 600, 20, 120, 45, 12);
        ShopSettings.saveAll("Quán Mai", "", "", "", "10", mine);
        ShopSettings.resetForTests();
        ShopSettings.load();
        assertEquals(mine, ShopRules.current());
    }

    @Test
    public void aHandEditedNewFlagFallsBackToTheOriginals() {
        AppFlags.replaceValues(Map.of(ShopSettings.RULE_SLOT_PREFIX, "5", ShopSettings.RULE_IDLE_PREFIX, "9999"));
        ShopSettings.resetForTests();
        ShopSettings.load();
        assertEquals(ShopRules.DEFAULTS, ShopRules.current());
    }

    @Test
    public void everyChangedRuleGetsItsOwnAuditLineWithTheOldAndNewNumber() throws Exception {
        int before = lines("staffDiscountPercent") + lines("idleMinutes");
        ShopSettings.saveAll("Lunavera Coffee", "", "", "", "10", new ShopRules.Values(20, 10_000, 1_000, 50, 20, 100, 500, 10, 90, 30, 16));
        assertEquals("one line per changed rule, none for the rest", before + 2, lines("staffDiscountPercent") + lines("idleMinutes"));
        assertTrue(hasLine("staffDiscountPercent", "10", "20"));
        assertTrue(hasLine("idleMinutes", "15", "30"));
        int again = lines("staffDiscountPercent");
        ShopSettings.saveAll("Lunavera Coffee", "", "", "", "10", ShopRules.current());
        assertEquals("a save that changes no rule writes no line", again, lines("staffDiscountPercent"));
    }

    @Test
    public void anOrderAlreadyPaidIsNotRecalculatedWhenTheRulesChange() {
        CustomerDAO customers = new CustomerDAO();
        Customer customer = new Customer();
        customer.setFullName("test-rules-customer-" + UUID.randomUUID());
        customer.setLoyaltyPoints(0);
        customer = customers.insert(customer);
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setCustomerId(customer.getId());
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(new BigDecimal("100000"));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("100000"));
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaidAt(AppClock.now());
        Category category = new Category();
        category.setName("test-rules-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        Product product = new Product();
        product.setName("test-rules-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("50000"));
        product.setStock(10);
        product.setActive(true);
        product = new ProductDAO().insert(product);
        OrderItem line = new OrderItem();
        line.setProductId(product.getId());
        line.setProductName(product.getName());
        line.setQuantity(2);
        line.setUnitPrice(product.getPrice());
        line.setLineTotal(new BigDecimal("100000"));
        OrderDAO orders = new OrderDAO();
        Order saved = orders.insert(order, List.of(line));
        assertEquals("10.000đ a point: 100.000đ earns 10", Integer.valueOf(10), orders.findById(saved.getId()).orElseThrow().getPointsEarned());

        ShopSettings.saveAll("Lunavera Coffee", "", "", "", "10", new ShopRules.Values(10, 5_000, 1_000, 50, 20, 100, 500));
        assertEquals("new bills earn by the new rule", 20, LoyaltyPolicy.pointsFor(new BigDecimal("100000")));
        assertEquals("the paid order keeps what it earned", Integer.valueOf(10), orders.findById(saved.getId()).orElseThrow().getPointsEarned());
        assertEquals("the customer's points are not touched", 10, customers.findById(customer.getId()).orElseThrow().getLoyaltyPoints());

        orders.cancelPaidOrder(saved.getId());
        assertEquals("a cancel takes back exactly what the sale gave, not what today's rule would give", 0,
                customers.findById(customer.getId()).orElseThrow().getLoyaltyPoints());
    }

    private static int lines(String ruleName) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM audit_log WHERE action = 'RULE_CHANGED' AND target_label = ?")) {
            st.setString(1, ruleName);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean hasLine(String ruleName, String oldValue, String newValue) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement(
                     "SELECT COUNT(*) FROM audit_log WHERE action = 'RULE_CHANGED' AND target_label = ? AND old_value = ? AND new_value = ?")) {
            st.setString(1, ruleName);
            st.setString(2, oldValue);
            st.setString(3, newValue);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
