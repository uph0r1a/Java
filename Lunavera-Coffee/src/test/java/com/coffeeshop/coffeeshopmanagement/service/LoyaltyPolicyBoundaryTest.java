package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy.Redemption;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

/** TODO 6.78: tests that came from a mutation run on {@link LoyaltyPolicy}. The shop rules are the factory ones. No database. */
public class LoyaltyPolicyBoundaryTest {

    @Before
    public void factoryRules() {
        ShopRules.resetForTests();
    }

    @After
    public void restore() {
        ShopRules.resetForTests();
    }

    @Test
    public void aRedemptionIsNoneOnlyWhenNoPointsWereSpent() {
        assertTrue(new Redemption(0, BigDecimal.ZERO).isNone());
        assertTrue(Redemption.NONE.isNone());
        assertFalse("one point spent is a redemption", new Redemption(1, new BigDecimal("1000")).isNone());
        assertFalse(LoyaltyPolicy.redemptionOf(20).isNone());
        assertTrue(LoyaltyPolicy.redemptionOf(0).isNone());
        assertTrue(LoyaltyPolicy.redemptionOf(-5).isNone());
    }

    @Test
    public void aBillTooBigForALongToHoldStillOnlyLimitsTheSpendingToTheBalance() {
        // the share the rule allows is far above Integer.MAX_VALUE, and above what a long can hold
        assertEquals(1000, LoyaltyPolicy.maxRedeemable(1000, new BigDecimal("1000000000000000000000000000000")));
        assertEquals(1000, LoyaltyPolicy.maxRedeemable(1000, new BigDecimal("100000000000000000000000000000000000000")));
    }

    @Test
    public void spendingExactlyTheBalanceIsAllowed() {
        assertNull(LoyaltyPolicy.redemptionProblem(100, 100, new BigDecimal("1000000")));
        assertNotNull(LoyaltyPolicy.redemptionProblem(101, 100, new BigDecimal("1000000")));
        assertEquals(I18n.t("pos.points.notenough", 100), LoyaltyPolicy.redemptionProblem(101, 100, new BigDecimal("1000000")));
    }

    @Test
    public void aNoteOfThePointsIsAddedEvenForASinglePoint() {
        assertEquals("the cashier's reason is kept and the points are added to it",
                "Regular · " + I18n.t("order.discount.reason.points", 1), LoyaltyPolicy.reasonWithPoints("Regular", 1));
        assertEquals("Regular", LoyaltyPolicy.reasonWithPoints("Regular", 0));
        assertEquals(I18n.t("order.discount.reason.points", 1), LoyaltyPolicy.reasonWithPoints(null, 1));
    }

    @Test
    public void theTierOfACustomerComesFromTheirPointsAndNobodyIsNew() {
        assertEquals(LoyaltyPolicy.Tier.NEW, LoyaltyPolicy.tierOf(null));
        Customer silver = new Customer();
        silver.setLifetimePoints(100);
        assertEquals(LoyaltyPolicy.Tier.SILVER, LoyaltyPolicy.tierOf(silver));
        Customer gold = new Customer();
        gold.setLifetimePoints(500);
        assertEquals(LoyaltyPolicy.Tier.GOLD, LoyaltyPolicy.tierOf(gold));
        Customer fresh = new Customer();
        fresh.setLifetimePoints(99);
        assertEquals(LoyaltyPolicy.Tier.NEW, LoyaltyPolicy.tierOf(fresh));
    }

    @Test
    public void aShareOfTheBillThatDoesNotFitALongStillLimitsTheSpendingToTheBalance() {
        // 2^63 whole points would wrap round to a negative number if it were forced into a long before it was compared
        BigDecimal bill = new BigDecimal("9223372036854775808").multiply(new BigDecimal("2000"));
        assertEquals(1000, LoyaltyPolicy.maxRedeemable(1000, bill));
    }

    @Test
    public void withTheLeastSetToOnePointOnePointCanBeSpent() {
        ShopRules.set(new ShopRules.Values(10, 10_000, 1_000, 50, 1, 100, 500));
        assertEquals(1, LoyaltyPolicy.maxRedeemable(1, new BigDecimal("100000")));
        assertNull(LoyaltyPolicy.redemptionProblem(1, 1, new BigDecimal("100000")));
        assertEquals(0, LoyaltyPolicy.maxRedeemable(0, new BigDecimal("100000")));
    }

    @Test
    public void noCustomerIsNewEvenWhenTheOwnerLetsOnePointMakeASilverCustomer() {
        ShopRules.set(new ShopRules.Values(10, 10_000, 1_000, 50, 20, 1, 500));
        assertEquals(LoyaltyPolicy.Tier.NEW, LoyaltyPolicy.tierFor(0));
        assertEquals(LoyaltyPolicy.Tier.SILVER, LoyaltyPolicy.tierFor(1));
        assertEquals("no customer has no points, not one", LoyaltyPolicy.Tier.NEW, LoyaltyPolicy.tierOf(null));
    }
}
