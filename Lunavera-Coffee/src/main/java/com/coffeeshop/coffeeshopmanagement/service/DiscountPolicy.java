package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Role;

import java.math.BigDecimal;

/**
 * Who may give how much discount (TODO 2.3). Before, any cashier could type a discount of up to 100% and nothing
 * recorded why. Now:
 * <ul>
 *   <li>an <b>admin</b> may discount any amount up to the subtotal;</li>
 *   <li>anyone else (employee) may discount at most {@link #STAFF_MAX_PERCENT}% of the subtotal;</li>
 *   <li>every discount above 0, from anyone, needs a written <b>reason</b>, which is saved with the order.</li>
 * </ul>
 * {@link #STAFF_MAX_PERCENT} is the owner's call; 10 is a starting guess (see TODO 2.3).
 */
public final class DiscountPolicy {

    public static final int STAFF_MAX_PERCENT = 10;
    public static final int REASON_MAX_LENGTH = 200;

    public enum Result { OK, NEGATIVE, ABOVE_SUBTOTAL, ABOVE_STAFF_LIMIT, REASON_REQUIRED, REASON_TOO_LONG }

    private DiscountPolicy() {
    }

    /** The most this role may discount on this subtotal, in whole dong. */
    public static BigDecimal maxDiscount(Role role, BigDecimal subtotal) {
        if (subtotal == null || subtotal.signum() <= 0) return BigDecimal.ZERO;
        if (role == Role.ADMIN) return subtotal;
        return subtotal.multiply(BigDecimal.valueOf(STAFF_MAX_PERCENT)).divide(BigDecimal.valueOf(100), 0, java.math.RoundingMode.FLOOR);
    }

    public static Result check(Role role, BigDecimal subtotal, BigDecimal discount, String reason) {
        if (discount == null || discount.signum() < 0) return Result.NEGATIVE;
        if (discount.compareTo(subtotal) > 0) return Result.ABOVE_SUBTOTAL;
        if (discount.signum() == 0) return Result.OK; // no discount: no reason needed
        if (discount.compareTo(maxDiscount(role, subtotal)) > 0) return Result.ABOVE_STAFF_LIMIT;
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) return Result.REASON_REQUIRED;
        if (trimmed.length() > REASON_MAX_LENGTH) return Result.REASON_TOO_LONG;
        return Result.OK;
    }

    /** The reason as it is saved: trimmed, and null when there is no discount. */
    public static String reasonToStore(BigDecimal discount, String reason) {
        if (discount == null || discount.signum() == 0 || reason == null) return null;
        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
