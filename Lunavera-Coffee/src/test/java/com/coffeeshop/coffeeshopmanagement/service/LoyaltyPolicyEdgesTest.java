package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.78: edges of {@link LoyaltyPolicy} and {@link DiscountPolicy} that a mutation run (tools/mutation-sweep.py) showed no test
 * noticed. Numbers are the shop's defaults (spend 1.000đ a point, at most 50% of a bill, least 20 points, silver 100, gold 500).
 */
public class LoyaltyPolicyEdgesTest {

    @Test
    public void aRedemptionOfOnePointIsStillARedemption() {
        assertFalse(new LoyaltyPolicy.Redemption(1, BigDecimal.ONE).isNone());
        assertTrue(new LoyaltyPolicy.Redemption(0, BigDecimal.ZERO).isNone());
        assertTrue(new LoyaltyPolicy.Redemption(-1, BigDecimal.ZERO).isNone());
        assertTrue(LoyaltyPolicy.redemptionOf(0).isNone());
        assertEquals(new BigDecimal("1000"), LoyaltyPolicy.redemptionOf(1).value());
    }

    @Test
    public void spendingAllThePointsYouHaveIsAllowed() {
        BigDecimal bill = new BigDecimal("100000");
        assertNull("exactly the balance", LoyaltyPolicy.redemptionProblem(20, 20, bill));
        assertEquals(I18n.t("pos.points.notenough", 20), LoyaltyPolicy.redemptionProblem(21, 20, bill));
    }

    @Test
    public void theLeastPointsAreSpendableAtTheMinimumAndNotBelow() {
        assertEquals(20, LoyaltyPolicy.maxRedeemable(20, new BigDecimal("100000")));
        assertEquals(0, LoyaltyPolicy.maxRedeemable(19, new BigDecimal("100000")));
        assertEquals("the bill's half is 19.000: under the least", 0, LoyaltyPolicy.maxRedeemable(100, new BigDecimal("39999")));
        assertEquals(20, LoyaltyPolicy.maxRedeemable(100, new BigDecimal("40000")));
    }

    @Test
    public void aSinglePointIsNamedInTheReason() {
        assertEquals(I18n.t("order.discount.reason.points", 1), LoyaltyPolicy.reasonWithPoints(null, 1));
        assertEquals("Khách quen", LoyaltyPolicy.reasonWithPoints("Khách quen", 0));
    }

    @Test
    public void theTierOfNoCustomerIsNewAndOfAGoldCustomerIsGold() {
        assertEquals(LoyaltyPolicy.Tier.NEW, LoyaltyPolicy.tierOf(null));
        Customer gold = new Customer();
        gold.setLifetimePoints(500);
        assertEquals(LoyaltyPolicy.Tier.GOLD, LoyaltyPolicy.tierOf(gold));
        Customer silver = new Customer();
        silver.setLifetimePoints(100);
        assertEquals(LoyaltyPolicy.Tier.SILVER, LoyaltyPolicy.tierOf(silver));
    }

    @Test
    public void noSubtotalAllowsNoDiscountForAnyRole() {
        assertEquals(BigDecimal.ZERO, DiscountPolicy.maxDiscount(Role.ADMIN, null));
        assertEquals(BigDecimal.ZERO, DiscountPolicy.maxDiscount(Role.EMPLOYEE, null));
        assertEquals(BigDecimal.ZERO, DiscountPolicy.maxDiscount(Role.ADMIN, new BigDecimal("-5")));
    }
}
