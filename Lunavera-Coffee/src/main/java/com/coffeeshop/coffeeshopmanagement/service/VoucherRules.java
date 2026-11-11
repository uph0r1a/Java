package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Voucher;
import com.coffeeshop.coffeeshopmanagement.util.DateLimits;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The rules of promotions (TODO 4.11, rule D6, my choice until the owner decides):
 * <ul>
 *   <li><b>Percentage buttons</b> ({@link #PERCENT_BUTTONS}) only fill the usual discount box; the cashier's limit
 *       ({@link DiscountPolicy}) and the written reason still apply, so they are a shortcut, not a new way round the rule.</li>
 *   <li><b>A voucher code</b> is created by an administrator only. It is the shop's gift, so it is not held to the cashier's limit; one
 *       voucher per order; it can be combined with the cashier's own discount and with spent loyalty points, and what it takes off can
 *       never push the total below 0 (the part above the bill is simply not used). It is checked at the till and again, with its use
 *       counted, inside the payment transaction; a refund gives the use back.</li>
 * </ul>
 * Everything here is whole dong, rounded down (the shop never gives a fraction of a dong away).
 */
public final class VoucherRules {

    public static final List<Integer> PERCENT_BUTTONS = List.of(5, 10, 15, 20);
    public static final int CODE_MIN = 3;
    public static final int CODE_MAX = 40;
    public static final long VALUE_MAX = 1_000_000_000L;
    public static final int USES_MAX = 1_000_000;

    /** What is saved on an order that used a voucher: its code and what it took off. */
    public record Use(String code, BigDecimal value) {
        public static final Use NONE = new Use(null, BigDecimal.ZERO);

        public boolean isNone() {
            return code == null || value == null || value.signum() <= 0;
        }
    }

    public enum Problem { OK, NOT_FOUND, INACTIVE, NOT_YET, EXPIRED, USED_UP, TOO_SMALL, NEEDS_CUSTOMER, ONCE_ONLY }

    private VoucherRules() {
    }

    /** The code as it is kept: trimmed, capitals, only letters, digits, "-" and "_"; null when empty or not allowed. */
    public static String normalise(String typed) {
        if (typed == null) return null;
        // A code is pasted from a Zalo message: a trailing non-breaking space or a zero-width character must not make it "not found" (TODO 3.71).
        String code = com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.plain(typed).trim().toUpperCase(Locale.ROOT);
        if (code.length() < CODE_MIN || code.length() > CODE_MAX) return null;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_')) return null;
        }
        return code;
    }

    /** Whether {@code voucher} may be used on a bill of {@code subtotal} on {@code today}. A missing voucher is NOT_FOUND. */
    public static Problem check(Voucher voucher, BigDecimal subtotal, LocalDate today) {
        if (voucher == null) return Problem.NOT_FOUND;
        if (!voucher.active()) return Problem.INACTIVE;
        if (voucher.validFrom() != null && today.isBefore(voucher.validFrom())) return Problem.NOT_YET;
        if (voucher.validTo() != null && today.isAfter(voucher.validTo())) return Problem.EXPIRED;
        if (voucher.maxUses() != null && voucher.usedCount() >= voucher.maxUses()) return Problem.USED_UP;
        if (subtotal == null || voucher.minSubtotal() != null && subtotal.compareTo(voucher.minSubtotal()) < 0) return Problem.TOO_SMALL;
        return Problem.OK;
    }

    /**
     * The "once per customer" part of the rule (TODO 4.54), for a voucher that passed {@link #check}: such a voucher needs a customer on
     * the bill, and refuses one who already has a paid bill that used it. {@code alreadyUsedByCustomer} is looked up by the caller.
     */
    public static Problem checkCustomer(Voucher voucher, Integer customerId, boolean alreadyUsedByCustomer) {
        if (voucher == null || !voucher.oncePerCustomer()) return Problem.OK;
        if (customerId == null) return Problem.NEEDS_CUSTOMER;
        return alreadyUsedByCustomer ? Problem.ONCE_ONLY : Problem.OK;
    }

    /**
     * What the voucher takes off a bill: a percentage of {@code subtotal} (rounded down) or the fixed amount, but never more than what
     * is still to pay, {@code subtotal - alreadyDiscounted}.
     */
    public static BigDecimal discountFor(Voucher voucher, BigDecimal subtotal, BigDecimal alreadyDiscounted) {
        BigDecimal left = subtotal.subtract(alreadyDiscounted == null ? BigDecimal.ZERO : alreadyDiscounted);
        if (left.signum() <= 0) return BigDecimal.ZERO;
        BigDecimal wanted = Voucher.PERCENT.equals(voucher.kind()) ? percentOf(subtotal, (int) voucher.value())
                : BigDecimal.valueOf(voucher.value());
        return wanted.min(left);
    }

    /** {@code percent}% of {@code amount}, in whole dong, rounded down. */
    public static BigDecimal percentOf(BigDecimal amount, int percent) {
        return amount.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR);
    }

    /**
     * The result of typing a code at the payment box (TODO 6.5, it was inside {@code PromotionPanel.apply}): the voucher that may be used
     * and the line shown under the field, or no voucher and the sentence saying why not.
     *
     * @param voucher the voucher to apply, or {@code null} when it cannot be used (or nothing was typed)
     * @param status  the line for the cashier; empty when nothing was typed
     */
    public record Lookup(Voucher voucher, String status) {
    }

    /**
     * What typing {@code typed} does, given what was found for it ({@code found} is {@code null} when no voucher has that code): nothing typed
     * clears the field; a voucher that fails {@link #check} against {@code payable} is refused with its sentence; a good one is applied.
     */
    public static Lookup lookup(String typed, Voucher found, BigDecimal payable, LocalDate today) {
        if (typed == null || typed.isBlank()) return new Lookup(null, "");
        Problem problem = check(found, payable, today);
        if (problem != Problem.OK) return new Lookup(null, message(problem, found));
        return new Lookup(found, I18n.t("pos.voucher.applied", found.code(), worth(found))
                + (found.oncePerCustomer() ? " " + I18n.t("pos.voucher.once") : "")); // TODO 4.54
    }

    /** The sentence for a refused voucher; {@code problem} must not be OK. */
    public static String message(Problem problem, Voucher voucher) {
        return switch (problem) {
            case OK -> throw new IllegalArgumentException("OK has no problem to describe");
            case NOT_FOUND -> I18n.t("voucher.problem.notfound");
            case INACTIVE -> I18n.t("voucher.problem.inactive");
            case NOT_YET -> I18n.t("voucher.problem.notyet", voucher.validFrom());
            case EXPIRED -> I18n.t("voucher.problem.expired", voucher.validTo());
            case USED_UP -> I18n.t("voucher.problem.usedup");
            case TOO_SMALL -> I18n.t("voucher.problem.toosmall", CurrencyUtil.format(voucher.minSubtotal()));
            case NEEDS_CUSTOMER -> I18n.t("voucher.problem.needscustomer");
            case ONCE_ONLY -> I18n.t("voucher.problem.onceonly");
        };
    }

    /** What an administrator typed to make a voucher, checked; null when it is fine, else a sentence to show. */
    public static String newVoucherProblem(String code, String kind, Long value, BigDecimal minSubtotal, LocalDate from, LocalDate to,
                                           Integer maxUses) {
        if (normalise(code) == null) return I18n.t("voucher.val.code", CODE_MIN, CODE_MAX);
        if (!Voucher.PERCENT.equals(kind) && !Voucher.AMOUNT.equals(kind)) return I18n.t("voucher.val.kind");
        if (value == null || value <= 0) return I18n.t("voucher.val.value");
        if (Voucher.PERCENT.equals(kind) && value > 100) return I18n.t("voucher.val.percent");
        if (value > VALUE_MAX) return I18n.t("voucher.val.toobig");
        if (minSubtotal != null && (minSubtotal.signum() < 0 || minSubtotal.longValue() > VALUE_MAX)) return I18n.t("voucher.val.min");
        if (from != null && from.isBefore(DateLimits.EARLIEST)
                || to != null && to.isBefore(DateLimits.EARLIEST)) {
            return I18n.t("date.val.early", DateLimits.EARLIEST.getYear()); // TODO 3.78
        }
        if (from != null && to != null && to.isBefore(from)) return I18n.t("voucher.val.range");
        if (maxUses != null && (maxUses <= 0 || maxUses > USES_MAX)) return I18n.t("voucher.val.uses");
        return null;
    }

    /** The note added to the discount reason of an order that used a voucher. */
    public static String reasonWithVoucher(String reason, String code) {
        if (code == null) return reason;
        String note = I18n.t("order.discount.reason.voucher", code);
        return reason == null || reason.isBlank() ? note : reason + " · " + note;
    }

    /** The label of a voucher's worth: "10%" or "50.000đ". */
    public static String worth(Voucher voucher) {
        return Voucher.PERCENT.equals(voucher.kind()) ? voucher.value() + "%" : CurrencyUtil.format(BigDecimal.valueOf(voucher.value()));
    }
}
