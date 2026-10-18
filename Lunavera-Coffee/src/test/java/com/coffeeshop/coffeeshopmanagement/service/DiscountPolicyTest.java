package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.DiscountPolicy.Result;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

public class DiscountPolicyTest {

    private static BigDecimal d(long v) { return BigDecimal.valueOf(v); }

    @Test
    public void noDiscountNeedsNoReasonForAnyone() {
        assertEquals(Result.OK, DiscountPolicy.check(Role.EMPLOYEE, d(100_000), d(0), null));
        assertEquals(Result.OK, DiscountPolicy.check(Role.ADMIN, d(100_000), d(0), ""));
    }

    @Test
    public void staffMayDiscountUpToTheLimitWithAReason() {
        assertEquals(d(10_000), DiscountPolicy.maxDiscount(Role.EMPLOYEE, d(100_000)));
        assertEquals(Result.OK, DiscountPolicy.check(Role.EMPLOYEE, d(100_000), d(10_000), "Khách quen"));
    }

    @Test
    public void staffCannotGoAboveTheLimit() {
        assertEquals(Result.ABOVE_STAFF_LIMIT, DiscountPolicy.check(Role.EMPLOYEE, d(100_000), d(10_001), "Khách quen"));
        assertEquals(Result.ABOVE_STAFF_LIMIT, DiscountPolicy.check(Role.EMPLOYEE, d(100_000), d(100_000), "Miễn phí"));
    }

    @Test
    public void theLimitRoundsDownToWholeDong() {
        assertEquals(d(3_333), DiscountPolicy.maxDiscount(Role.EMPLOYEE, d(33_339)));
        assertEquals(Result.ABOVE_STAFF_LIMIT, DiscountPolicy.check(Role.EMPLOYEE, d(33_339), d(3_334), "x"));
    }

    @Test
    public void adminMayDiscountEverythingButNotMoreThanTheSubtotal() {
        assertEquals(Result.OK, DiscountPolicy.check(Role.ADMIN, d(100_000), d(100_000), "Sự cố"));
        assertEquals(Result.ABOVE_SUBTOTAL, DiscountPolicy.check(Role.ADMIN, d(100_000), d(100_001), "Sự cố"));
    }

    @Test
    public void everyDiscountNeedsAReasonEvenFromTheAdmin() {
        assertEquals(Result.REASON_REQUIRED, DiscountPolicy.check(Role.ADMIN, d(100_000), d(5_000), null));
        assertEquals(Result.REASON_REQUIRED, DiscountPolicy.check(Role.EMPLOYEE, d(100_000), d(5_000), "   "));
    }

    @Test
    public void aReasonThatIsTooLongIsRefused() {
        String longReason = "x".repeat(DiscountPolicy.REASON_MAX_LENGTH + 1);
        assertEquals(Result.REASON_TOO_LONG, DiscountPolicy.check(Role.ADMIN, d(100_000), d(5_000), longReason));
        assertEquals(Result.OK, DiscountPolicy.check(Role.ADMIN, d(100_000), d(5_000), "x".repeat(DiscountPolicy.REASON_MAX_LENGTH)));
    }

    @Test
    public void negativeAndMissingDiscountsAreInvalid() {
        assertEquals(Result.NEGATIVE, DiscountPolicy.check(Role.ADMIN, d(100_000), d(-1), "x"));
        assertEquals(Result.NEGATIVE, DiscountPolicy.check(Role.ADMIN, d(100_000), null, "x"));
    }

    @Test
    public void aCustomerRoleIsTreatedLikeStaff() {
        assertEquals(Result.ABOVE_STAFF_LIMIT, DiscountPolicy.check(Role.CUSTOMER, d(100_000), d(50_000), "x"));
        assertEquals(Result.ABOVE_STAFF_LIMIT, DiscountPolicy.check(null, d(100_000), d(50_000), "x"));
    }

    @Test
    public void reasonIsOnlyStoredWithADiscountAndTrimmed() {
        assertNull(DiscountPolicy.reasonToStore(d(0), "Khách quen"));
        assertNull(DiscountPolicy.reasonToStore(d(1000), "  "));
        assertEquals("Khách quen", DiscountPolicy.reasonToStore(d(1000), "  Khách quen "));
    }

    // ---- TODO 6.45: messageFor and require, used by the payment service ----

    @Test
    public void everyRefusalHasAMessageAndOkHasNone() {
        for (Result r : Result.values()) {
            if (r == Result.OK) {
                assertThrows(IllegalArgumentException.class, () -> DiscountPolicy.messageFor(r, Role.EMPLOYEE, d(100_000)));
            } else {
                String message = DiscountPolicy.messageFor(r, Role.EMPLOYEE, d(100_000));
                assertFalse(r.name(), message == null || message.isBlank());
            }
        }
    }

    @Test
    public void theStaffLimitMessageNamesThePercentage() {
        String message = DiscountPolicy.messageFor(Result.ABOVE_STAFF_LIMIT, Role.EMPLOYEE, d(100_000));
        assertTrue(message, message.contains(DiscountPolicy.STAFF_MAX_PERCENT + "%"));
    }

    @Test
    public void requireDoesNothingWhenTheDiscountIsAllowed() {
        DiscountPolicy.require(Role.EMPLOYEE, d(100_000), d(10_000), "Khách quen");
        DiscountPolicy.require(Role.ADMIN, d(100_000), d(100_000), "Quà tặng");
        DiscountPolicy.require(Role.EMPLOYEE, d(100_000), d(0), null);
    }

    @Test
    public void requireRefusesWithTheSameWordingTheDialogShows() {
        com.coffeeshop.coffeeshopmanagement.dao.DataAccessException e = assertThrows(
                com.coffeeshop.coffeeshopmanagement.dao.DataAccessException.class,
                () -> DiscountPolicy.require(Role.EMPLOYEE, d(100_000), d(10_001), "x"));
        assertEquals(DiscountPolicy.messageFor(Result.ABOVE_STAFF_LIMIT, Role.EMPLOYEE, d(100_000)), e.getMessage());
    }

    @Test
    public void aMissingRoleOrSubtotalFailsClosed() {
        assertEquals(Result.ABOVE_STAFF_LIMIT, DiscountPolicy.check(null, d(100_000), d(50_000), "x"));
        assertEquals(Result.ABOVE_SUBTOTAL, DiscountPolicy.check(Role.ADMIN, null, d(1), "x"));
    }
}

