package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Role;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.76 (the same idea for the discount rule): for random bills, random discounts, both roles and random cashier limits, a
 * discount that {@link DiscountPolicy#check} accepts is never above the bill, never above the cashier's share for a cashier, and
 * always has a reason. See {@link RandomCases} to replay a failure.
 */
public class DiscountPolicyPropertyTest {

    @Before
    public void factoryRules() {
        ShopRules.resetForTests();
    }

    @After
    public void restore() {
        ShopRules.resetForTests();
    }

    @Test
    public void anAcceptedDiscountIsInsideEveryLimit() {
        RandomCases.run("DiscountPolicy.check", 10_000, random -> {
            int limit = RandomCases.between(random, 0, ShopRules.STAFF_MAX);
            ShopRules.set(new ShopRules.Values(limit, 10_000, 1_000, 50, 20, 100, 500));
            assertEquals(limit, DiscountPolicy.staffMaxPercent());

            Role role = random.nextBoolean() ? Role.ADMIN : Role.EMPLOYEE;
            BigDecimal subtotal = BigDecimal.valueOf(RandomCases.between(random, 0, 2_000_000));
            BigDecimal max = DiscountPolicy.maxDiscount(role, subtotal);
            assertTrue("the most allowed is never below 0 or above the bill", max.signum() >= 0 && max.compareTo(subtotal) <= 0);
            if (role == Role.EMPLOYEE) {
                assertTrue("a cashier gets at most " + limit + " %", max.multiply(BigDecimal.valueOf(100)).compareTo(subtotal.multiply(BigDecimal.valueOf(limit))) <= 0);
            }
            BigDecimal discount = switch (random.nextInt(3)) {
                case 0 -> max;
                case 1 -> max.add(BigDecimal.valueOf(RandomCases.between(random, -2, 2)));
                default -> BigDecimal.valueOf(RandomCases.between(random, -10, 2_100_000));
            };
            String reason = random.nextBoolean() ? "regular customer" : (random.nextBoolean() ? "  " : null);
            DiscountPolicy.Result result = DiscountPolicy.check(role, subtotal, discount, reason);
            if (result == DiscountPolicy.Result.OK) {
                assertTrue("an accepted discount is not negative", discount.signum() >= 0);
                assertTrue("an accepted discount is never above the bill: " + discount + " > " + subtotal, discount.compareTo(subtotal) <= 0);
                assertTrue("an accepted discount is never above the role's limit", discount.signum() == 0 || discount.compareTo(max) <= 0);
                assertTrue("an accepted discount above 0 has a written reason", discount.signum() == 0 || (reason != null && !reason.isBlank()));
            } else {
                assertTrue("a refusal has a reason of its own", result != null);
            }
            if (discount.signum() > 0 && discount.compareTo(max) <= 0 && reason != null && !reason.isBlank()) {
                assertEquals("inside the limits and explained: allowed", DiscountPolicy.Result.OK, result);
            }
        });
    }
}
