package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.ShopSettings;
import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy;
import com.coffeeshop.coffeeshopmanagement.service.ShopRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 3.80: a customer's tier is read from the points earned over time, so spending points on a bill does not take them down a
 * tier. The balance still goes down when points are spent; the number the tier uses does not, and a refund takes back what the sale gave.
 */
public class LifetimePointsTest {

    private final CustomerDAO customers = new CustomerDAO();
    private final OrderDAO orders = new OrderDAO();
    private Product product;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void aProduct() {
        TestSession.admin();
        ShopSettings.resetForTests();
        Category category = new Category();
        category.setName("test-life-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        product = new Product();
        product.setName("test-life-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("1000000"));
        product.setStock(1000);
        product.setActive(true);
        product = new ProductDAO().insert(product);
    }

    @After
    public void reset() {
        ShopSettings.saveAll("Lunavera Coffee", "", "", "", "10", ShopRules.DEFAULTS);
        ShopSettings.resetForTests();
    }

    private Customer customer(int startingPoints) {
        Customer c = new Customer();
        c.setFullName("test-life-" + UUID.randomUUID());
        c.setLoyaltyPoints(startingPoints);
        return customers.insert(c);
    }

    private Customer reload(Customer c) {
        return customers.findById(c.getId()).orElseThrow();
    }

    /** A paid order of {@code quantity} x 1.000.000đ: 100 points per piece at the usual 10.000đ a point. */
    private Order pay(Customer c, int quantity, int pointsToSpend) {
        OrderItem line = new OrderItem();
        line.setProductId(product.getId());
        line.setProductName(product.getName());
        line.setQuantity(quantity);
        line.setUnitPrice(product.getPrice());
        line.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setCustomerId(c.getId());
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(line.getLineTotal());
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(line.getLineTotal());
        Order open = orders.insert(order, List.of(line));
        BigDecimal total = line.getLineTotal();
        com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy.Redemption redemption = pointsToSpend > 0
                ? new com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy.Redemption(pointsToSpend,
                BigDecimal.valueOf(pointsToSpend).multiply(LoyaltyPolicy.vndPerPointSpent())) : null;
        BigDecimal payable = redemption == null ? total : total.subtract(redemption.value());
        BigDecimal discount = redemption == null ? BigDecimal.ZERO : redemption.value(); // the points' worth is part of the discount
        return orders.payExistingOrder(open.getId(), c.getId(), PaymentMethod.CASH, discount, null, payable, List.of(line),
                null, null, null, redemption, null, null, null);
    }

    @Test
    public void aNewCustomerStartsWithWhatTheyAreGivenCountedAsEarned() {
        Customer c = reload(customer(120));
        assertEquals(120, c.getLoyaltyPoints());
        assertEquals(120, c.getLifetimePoints());
        assertEquals(LoyaltyPolicy.Tier.SILVER, LoyaltyPolicy.tierOf(c));
    }

    @Test
    public void payingRaisesBothTheBalanceAndThePointsEarnedInTotal() {
        Customer c = customer(0);
        pay(c, 1, 0); // 100 points
        Customer after = reload(c);
        assertEquals(100, after.getLoyaltyPoints());
        assertEquals(100, after.getLifetimePoints());
        assertEquals(LoyaltyPolicy.Tier.SILVER, LoyaltyPolicy.tierOf(after));
    }

    @Test
    public void spendingPointsLowersTheBalanceButNotTheTier() {
        Customer c = customer(0);
        pay(c, 6, 0); // 600 points: gold
        assertEquals(LoyaltyPolicy.Tier.GOLD, LoyaltyPolicy.tierOf(reload(c)));
        pay(c, 1, 590); // spends 590 points on the next bill (and earns what the rest of the bill gives)
        Customer after = reload(c);
        assertTrue("the balance fell below the gold line: " + after.getLoyaltyPoints(), after.getLoyaltyPoints() < LoyaltyPolicy.goldFrom());
        assertTrue("the points earned in total only went up", after.getLifetimePoints() >= 600);
        assertEquals("spending points does not take the customer down a tier", LoyaltyPolicy.Tier.GOLD, LoyaltyPolicy.tierOf(after));
        assertTrue("it used to be decided by the balance, which would now say less", LoyaltyPolicy.tierFor(after.getLoyaltyPoints()).ordinal()
                < LoyaltyPolicy.Tier.GOLD.ordinal());
    }

    @Test
    public void cancellingASaleTakesBackWhatItEarnedFromBothNumbers() {
        Customer c = customer(0);
        Order paid = pay(c, 2, 0); // 200 points
        assertEquals(200, reload(c).getLifetimePoints());
        orders.cancelPaidOrder(paid.getId());
        Customer after = reload(c);
        assertEquals(0, after.getLoyaltyPoints());
        assertEquals("a cancelled sale no longer counts towards the tier", 0, after.getLifetimePoints());
        assertEquals(LoyaltyPolicy.Tier.NEW, LoyaltyPolicy.tierOf(after));
    }

    @Test
    public void cancellingASaleThatSpentPointsGivesThemBackWithoutChangingWhatWasEarned() {
        Customer c = customer(0);
        pay(c, 3, 0);                     // 300 earned
        Order second = pay(c, 1, 50);     // spends 50, earns 95 for the 950.000đ paid
        Customer before = reload(c);
        orders.cancelPaidOrder(second.getId());
        Customer after = reload(c);
        assertEquals("what the second sale earned is taken back", before.getLifetimePoints() - 95, after.getLifetimePoints());
        assertEquals("the 50 spent points come back to the balance", before.getLoyaltyPoints() - 95 + 50, after.getLoyaltyPoints());
    }

    @Test
    public void aPartialRefundTakesBackItsShareOfThePointsEarned() {
        Customer c = customer(0);
        Order paid = pay(c, 2, 0); // 200 points, two pieces
        int lineId = orders.findItemsByOrderId(paid.getId()).get(0).getId();
        new RefundDAO().refund(paid.getId(), java.util.Map.of(lineId, 1), true, PaymentMethod.CASH, "one was wrong");
        Customer after = reload(c);
        assertEquals("half of the 200", 100, after.getLoyaltyPoints());
        assertEquals(100, after.getLifetimePoints());
    }

    @Test
    public void mergingTwoCustomersAddsTheirPointsEarnedInTotal() {
        Customer keep = customer(0);
        Customer duplicate = customer(0);
        pay(keep, 1, 0);       // 100
        pay(duplicate, 2, 0);  // 200
        new com.coffeeshop.coffeeshopmanagement.service.CustomerMergeService().merge(keep.getId(), duplicate.getId());
        Customer merged = reload(keep);
        assertEquals(300, merged.getLoyaltyPoints());
        assertEquals(300, merged.getLifetimePoints());
    }

    @Test
    public void anAnonymisedCustomerHasNoPointsOfEitherKind() {
        Customer c = customer(0);
        pay(c, 1, 0);
        new com.coffeeshop.coffeeshopmanagement.service.CustomerMergeService().anonymise(c.getId());
        Customer after = reload(c);
        assertEquals(0, after.getLoyaltyPoints());
        assertEquals(0, after.getLifetimePoints());
    }

    @Test
    public void theTierNeverFallsBelowWhatTheBalanceAloneWouldGive() {
        Customer c = new Customer();
        c.setLoyaltyPoints(600);
        c.setLifetimePoints(0); // a row whose total was never filled
        assertEquals(LoyaltyPolicy.Tier.GOLD, LoyaltyPolicy.tierOf(c));
        assertEquals(LoyaltyPolicy.Tier.NEW, LoyaltyPolicy.tierOf(null));
    }

    @Test
    public void theBackfillGivesAnExistingCustomerTheirBalanceAndWhatTheySpent() throws Exception {
        // The column that already exists is dropped and added again, the way an old database meets it for the first time.
        Customer c = customer(40);
        pay(c, 1, 0);          // 100 more earned: balance 140
        pay(c, 1, 30);         // spends 30, earns 97: balance 207 - 30 ... (see the assertion below)
        Customer now = reload(c);
        try (Connection connection = DatabaseConfig.getConnection()) {
            try (PreparedStatement st = connection.prepareStatement("UPDATE customers SET lifetime_points = 0 WHERE id = ?")) {
                st.setInt(1, c.getId());
                st.executeUpdate();
            }
            // what DatabaseConfig.addLaterColumn runs right after it adds the column
            try (java.sql.Statement st = connection.createStatement()) {
                st.executeUpdate("UPDATE customers SET lifetime_points = loyalty_points + COALESCE((SELECT SUM(o.points_redeemed) FROM orders o "
                        + "WHERE o.customer_id = customers.id AND o.status = 'PAID'), 0) WHERE id = " + c.getId());
            }
            try (PreparedStatement st = connection.prepareStatement("SELECT lifetime_points FROM customers WHERE id = ?")) {
                st.setInt(1, c.getId());
                try (ResultSet rs = st.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("the balance plus the 30 points that were spent", now.getLoyaltyPoints() + 30, rs.getInt(1));
                }
            }
        }
    }
}
