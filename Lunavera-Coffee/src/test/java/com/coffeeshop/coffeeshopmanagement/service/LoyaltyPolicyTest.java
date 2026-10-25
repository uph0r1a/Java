package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class LoyaltyPolicyTest {

    @Test
    public void oneEarnedPointPerFullTenThousand() {
        assertEquals(1, LoyaltyPolicy.pointsFor(new BigDecimal("10000")));
        assertEquals(5, LoyaltyPolicy.pointsFor(new BigDecimal("50000")));
    }

    @Test
    public void roundsDownRatherThanUp() {
        // 19.999đ must NOT earn a 2nd point - rounding the wrong way here would be giving
        // away loyalty points the order didn't actually qualify for.
        assertEquals(1, LoyaltyPolicy.pointsFor(new BigDecimal("19999")));
    }

    @Test
    public void belowThresholdEarnsNothing() {
        assertEquals(0, LoyaltyPolicy.pointsFor(new BigDecimal("9999")));
        assertEquals(0, LoyaltyPolicy.pointsFor(BigDecimal.ZERO));
    }

    @Test
    public void negativeOrNullNeverEarnsPoints() {
        // A cancelled/refunded order total, or a missing total, must not produce negative or
        // fabricated points.
        assertEquals(0, LoyaltyPolicy.pointsFor(new BigDecimal("-5000")));
        assertEquals(0, LoyaltyPolicy.pointsFor(null));
    }

    /** TODO 5.28: the receipt names the points only for a paid order that has a customer. */
    @Test
    public void receiptPointsOnlyForAPaidOrderWithACustomer() {
        com.coffeeshop.coffeeshopmanagement.model.Order order = new com.coffeeshop.coffeeshopmanagement.model.Order();
        order.setTotal(new BigDecimal("55000"));
        order.setStatus(com.coffeeshop.coffeeshopmanagement.model.OrderStatus.PAID);
        org.junit.Assert.assertNull("no customer attached: the receipt says nothing about points", LoyaltyPolicy.pointsEarnedBy(order));
        order.setCustomerId(7);
        assertEquals(Integer.valueOf(5), LoyaltyPolicy.pointsEarnedBy(order));
        order.setTotal(new BigDecimal("9999"));
        assertEquals("a customer on a small order earns 0, and the line says so", Integer.valueOf(0), LoyaltyPolicy.pointsEarnedBy(order));
        order.setStatus(com.coffeeshop.coffeeshopmanagement.model.OrderStatus.CANCELLED);
        org.junit.Assert.assertNull("a refund took the points back", LoyaltyPolicy.pointsEarnedBy(order));
        order.setStatus(com.coffeeshop.coffeeshopmanagement.model.OrderStatus.OPEN);
        org.junit.Assert.assertNull(LoyaltyPolicy.pointsEarnedBy(order));
        org.junit.Assert.assertNull(LoyaltyPolicy.pointsEarnedBy(null));
    }

    @Test
    public void receiptPointsLineIsEnglishAndHasNoThousandsSeparator() {
        assertEquals("Points earned: +5, total: 1200", com.coffeeshop.coffeeshopmanagement.util.ReceiptText.points(5, 1200));
        assertEquals("Points earned: +5", com.coffeeshop.coffeeshopmanagement.util.ReceiptText.points(5, null));
    }

    @Test
    public void tiersChangeExactlyAtTheThresholds() {
        assertEquals(LoyaltyPolicy.Tier.NEW, LoyaltyPolicy.tierFor(0));
        assertEquals(LoyaltyPolicy.Tier.NEW, LoyaltyPolicy.tierFor(99));
        assertEquals(LoyaltyPolicy.Tier.SILVER, LoyaltyPolicy.tierFor(100));
        assertEquals(LoyaltyPolicy.Tier.SILVER, LoyaltyPolicy.tierFor(499));
        assertEquals(LoyaltyPolicy.Tier.GOLD, LoyaltyPolicy.tierFor(500));
        assertEquals(LoyaltyPolicy.Tier.GOLD, LoyaltyPolicy.tierFor(Integer.MAX_VALUE));
        assertEquals("a negative balance is still a new member", LoyaltyPolicy.Tier.NEW, LoyaltyPolicy.tierFor(-5));
    }

    @Test
    public void tierNamesComeFromTheLanguageFile() {
        assertEquals(com.coffeeshop.coffeeshopmanagement.util.I18n.t("customers.tier.new"), LoyaltyPolicy.tierName(0));
        assertEquals(com.coffeeshop.coffeeshopmanagement.util.I18n.t("customers.tier.silver"), LoyaltyPolicy.tierName(100));
        assertEquals(com.coffeeshop.coffeeshopmanagement.util.I18n.t("customers.tier.gold"), LoyaltyPolicy.tierName(500));
    }

    /** TODO 3.59: the numbers that wrapped round to a negative count. */
    @Test
    public void hugeTotalsClampInsteadOfWrappingNegative() {
        assertEquals(Integer.MAX_VALUE, LoyaltyPolicy.pointsFor(new BigDecimal("25000000000000")));
        assertEquals(Integer.MAX_VALUE, LoyaltyPolicy.pointsFor(new BigDecimal("21474836480000")));
        assertEquals(Integer.MAX_VALUE, LoyaltyPolicy.pointsFor(new BigDecimal("999999999999999")));
        assertEquals("just below the clamp is exact", Integer.MAX_VALUE - 1, LoyaltyPolicy.pointsFor(new BigDecimal("21474836460000")));
        assertEquals(5, LoyaltyPolicy.pointsFor(new BigDecimal("50000")));
    }
}
