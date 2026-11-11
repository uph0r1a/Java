package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.76: the points rules for random bills, random balances AND random shop rules (the owner may change the earn rate, the value
 * of a point, the share of a bill points may pay, the least points and the tiers in Settings, TODO 4.49), so a rule that only holds at
 * the factory numbers is found. See {@link RandomCases} to replay a failure.
 */
public class LoyaltyPolicyPropertyTest {

    @Before
    public void factoryRules() {
        ShopRules.resetForTests();
    }

    @After
    public void restore() {
        ShopRules.resetForTests();
    }

    /** Puts a random set of rules, inside what Settings allows, in force. */
    static ShopRules.Values randomRules(Random random) {
        int silver = RandomCases.between(random, 1, 2000);
        int gold = silver + RandomCases.between(random, 1, 5000);
        ShopRules.Values values = new ShopRules.Values(RandomCases.between(random, 0, ShopRules.STAFF_MAX),
                RandomCases.between(random, ShopRules.EARN_MIN, 200_000L), RandomCases.between(random, ShopRules.SPEND_MIN, 5_000L),
                RandomCases.between(random, 0, ShopRules.BILL_MAX), RandomCases.between(random, 1, 100), silver, gold);
        ShopRules.set(values);
        assertEquals("the random rules are inside what the settings accept", values, ShopRules.current());
        return values;
    }

    private static BigDecimal randomBill(Random random) {
        return switch (random.nextInt(4)) {
            case 0 -> BigDecimal.valueOf(RandomCases.between(random, 0, 50_000));
            case 1 -> BigDecimal.valueOf(RandomCases.between(random, 1, 5_000_000));
            case 2 -> BigDecimal.valueOf(RandomCases.between(random, -1000, 1));
            default -> BigDecimal.valueOf(RandomCases.between(random, 1, 10_000_000_000L));
        };
    }

    @Test
    public void pointsEarnedAreNeverBelowZeroAndNeverMoreThanTheBillAllows() {
        RandomCases.run("LoyaltyPolicy.pointsFor", 5000, random -> {
            ShopRules.Values rules = randomRules(random);
            BigDecimal bill = randomBill(random);
            int points = LoyaltyPolicy.pointsFor(bill);
            assertTrue("never below zero: " + points, points >= 0);
            if (bill.signum() <= 0) {
                assertEquals("a bill of nothing earns nothing", 0, points);
            } else {
                assertTrue("a point costs at least the earn rate", bill.compareTo(BigDecimal.valueOf(points).multiply(BigDecimal.valueOf(rules.earnDong()))) >= 0);
                BigDecimal more = BigDecimal.valueOf(points + 1L).multiply(BigDecimal.valueOf(rules.earnDong()));
                assertTrue("whole points only: one more would need more money", points == Integer.MAX_VALUE || bill.compareTo(more) < 0);
            }
            assertEquals("a bigger bill never earns fewer points", true, LoyaltyPolicy.pointsFor(bill.add(BigDecimal.valueOf(RandomCases.between(random, 0, 100_000)))) >= points);
        });
    }

    @Test
    public void theMostThatCanBeSpentIsNeverMoreThanTheShareOfTheBillOrTheBalance() {
        RandomCases.run("LoyaltyPolicy.maxRedeemable", 5000, random -> {
            ShopRules.Values rules = randomRules(random);
            BigDecimal bill = randomBill(random);
            int balance = random.nextInt(4) == 0 ? RandomCases.between(random, -10, 0) : RandomCases.between(random, 0, 100_000);
            int most = LoyaltyPolicy.maxRedeemable(balance, bill);
            assertTrue("never below zero", most >= 0);
            assertTrue("never more than the balance: " + most + " > " + balance, most <= Math.max(balance, 0));
            BigDecimal worth = BigDecimal.valueOf(most).multiply(BigDecimal.valueOf(rules.spendDong()));
            BigDecimal allowed = bill.max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(rules.maxPercentOfBill())).divide(BigDecimal.valueOf(100)); // a bill of nothing or less allows nothing
            assertTrue("never more than " + rules.maxPercentOfBill() + " % of the bill: paid " + worth + " of " + bill, worth.compareTo(allowed) <= 0);
            assertTrue("never the whole bill", most == 0 || worth.compareTo(bill) < 0);
            assertTrue("either nothing or at least the least number of points", most == 0 || most >= rules.minPoints());
            if (most > 0) { // the maximum itself is allowed, one more is not
                assertNull("the most is allowed", LoyaltyPolicy.redemptionProblem(most, balance, bill));
                assertNotNull("one more is not", LoyaltyPolicy.redemptionProblem(most + 1, balance, bill));
            }
        });
    }

    @Test
    public void whatEveryAllowedRedemptionIsWorthStaysInsideTheBill() {
        RandomCases.run("LoyaltyPolicy.redemptionProblem", 5000, random -> {
            ShopRules.Values rules = randomRules(random);
            BigDecimal bill = BigDecimal.valueOf(RandomCases.between(random, 0, 3_000_000));
            int balance = RandomCases.between(random, 0, 5_000);
            int points = RandomCases.between(random, -5, 6_000);
            String problem = LoyaltyPolicy.redemptionProblem(points, balance, bill);
            if (problem == null && points != 0) {
                LoyaltyPolicy.Redemption redemption = LoyaltyPolicy.redemptionOf(points);
                assertTrue("an allowed redemption is for points the customer has", points > 0 && points <= balance);
                assertTrue("worth less than the bill", redemption.value().compareTo(bill) < 0);
                assertEquals(BigDecimal.valueOf(points).multiply(BigDecimal.valueOf(rules.spendDong())), redemption.value());
            }
            if (points < 0) {
                assertNotNull("a negative number of points is never allowed", problem);
            }
        });
    }

    @Test
    public void aCustomerWithMorePointsIsNeverInALowerTier() {
        RandomCases.run("LoyaltyPolicy.tierFor", 3000, random -> {
            randomRules(random);
            int a = RandomCases.between(random, -50, 10_000);
            int b = a + RandomCases.between(random, 0, 10_000);
            assertTrue("tier of " + a + " vs " + b, LoyaltyPolicy.tierFor(b).compareTo(LoyaltyPolicy.tierFor(a)) >= 0);
        });
    }
}
