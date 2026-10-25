package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;

import java.math.BigDecimal;
import java.util.logging.Logger;

/**
 * Who may give how much discount (TODO 2.3). Before, any cashier could type a discount of up to 100% and nothing
 * recorded why. Now:
 * <ul>
 *   <li>an <b>admin</b> may discount any amount up to the subtotal;</li>
 *   <li>anyone else (employee) may discount at most {@link #STAFF_MAX_PERCENT}% of the subtotal;</li>
 *   <li>every discount above 0, from anyone, needs a written <b>reason</b>, which is saved with the order.</li>
 * </ul>
 * {@link #STAFF_MAX_PERCENT} is the owner's call; 10 is a starting guess (see TODO 2.3).
 *
 * <p>The payment dialog asks {@link #check} so the cashier sees a message at once, and {@code CheckoutService.pay} asks
 * {@link #require} again before anything is written (TODO 6.45), so a screen that forgets the dialog's check cannot give a
 * discount the rules do not allow.
 */
public final class DiscountPolicy {

    private static final Logger LOGGER = Logger.getLogger(DiscountPolicy.class.getName());

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
        if (subtotal == null || discount.compareTo(subtotal) > 0) return Result.ABOVE_SUBTOTAL;
        if (discount.signum() == 0) return Result.OK; // no discount: no reason needed
        if (discount.compareTo(maxDiscount(role, subtotal)) > 0) return Result.ABOVE_STAFF_LIMIT;
        String trimmed = reason == null ? "" : reason.trim();
        if (trimmed.isEmpty()) return Result.REASON_REQUIRED;
        if (trimmed.length() > REASON_MAX_LENGTH) return Result.REASON_TOO_LONG;
        return Result.OK;
    }

    /**
     * The sentence that tells a cashier why {@code result} was refused; the same text the payment dialog and
     * {@link #require} use. {@code result} must not be {@link Result#OK}.
     */
    public static String messageFor(Result result, Role role, BigDecimal subtotal) {
        return switch (result) {
            case OK -> throw new IllegalArgumentException("OK has no problem to describe");
            case NEGATIVE -> I18n.t("pos.discount.invalid.number");
            case ABOVE_SUBTOTAL -> I18n.t("discount.problem.abovesubtotal");
            case ABOVE_STAFF_LIMIT -> I18n.t("discount.problem.stafflimit", STAFF_MAX_PERCENT,
                    CurrencyUtil.format(maxDiscount(role, subtotal)));
            case REASON_REQUIRED -> I18n.t("discount.problem.reasonrequired");
            case REASON_TOO_LONG -> I18n.t("discount.problem.reasonlong", REASON_MAX_LENGTH);
        };
    }

    /**
     * {@link #check} that refuses instead of returning: throws a {@link DataAccessException} (every screen already shows its
     * message) when the discount is not allowed for this role. A missing role is treated as an ordinary employee, so the
     * stricter rule applies when nobody can be identified.
     */
    public static void require(Role role, BigDecimal subtotal, BigDecimal discount, String reason) {
        Result result = check(role, subtotal, discount, reason);
        if (result == Result.OK) {
            return;
        }
        // Ids and amounts only: never a name or a reason text (TODO 6.28).
        LOGGER.warning("A payment with a discount the rules do not allow was refused: " + result);
        throw new DataAccessException(messageFor(result, role, subtotal));
    }

    /** The reason as it is saved: trimmed, and null when there is no discount. */
    public static String reasonToStore(BigDecimal discount, String reason) {
        if (discount == null || discount.signum() == 0 || reason == null) return null;
        String trimmed = reason.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
