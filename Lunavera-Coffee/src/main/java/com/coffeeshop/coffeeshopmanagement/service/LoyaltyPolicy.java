package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One place that decides how many loyalty points a paid order earns, so the rule isn't
 * scattered across DAOs/controllers. The numbers are the owner's settings ({@link ShopRules}, TODO 4.49); until changed: 1 point per
 * full 10.000đ of the final total. The membership tiers (100 points = silver, 500 = gold by default) are decided here too, by {@link #tierFor(int)} (TODO 6.63), so the
 * customer list, the receipt and an export cannot disagree.
 */
public final class LoyaltyPolicy {

    // ---- Spending points (TODO 4.16, rule D5). The owner's call; these three numbers are the whole rule.

    /** What one point is worth when it is spent (1.000đ until the owner changes it; earned 1 point per 10.000đ paid, so 10% back). */
    public static BigDecimal vndPerPointSpent() {
        return BigDecimal.valueOf(ShopRules.current().spendDong());
    }

    /** Points may pay at most this share of the bill after any discount, so a sale is never paid for entirely with points (50 until changed). */
    public static int maxPercentOfBill() {
        return ShopRules.current().maxPercentOfBill();
    }

    /** The least that may be spent on one order (20 points until changed, the owner's answer to D5). Below it nothing is spent. */
    public static int minPoints() {
        return ShopRules.current().minPoints();
    }

    /**
     * Points spent on one order and what they were worth when they were spent. The value is saved with the order, so a later
     * change of the point's value ({@link #vndPerPointSpent()}) does not rewrite old orders.
     */
    public record Redemption(int points, BigDecimal value) {
        public static final Redemption NONE = new Redemption(0, BigDecimal.ZERO);

        public boolean isNone() {
            return points <= 0;
        }
    }

    /** {@code points} spent at today's rate. */
    public static Redemption redemptionOf(int points) {
        return points <= 0 ? Redemption.NONE : new Redemption(points, vndPerPointSpent().multiply(BigDecimal.valueOf(points)));
    }

    /**
     * The most points that can be spent on a bill of {@code amountAfterDiscount}: the share the rule allows, in whole points, and no
     * more than the customer has.
     */
    public static int maxRedeemable(int balance, BigDecimal amountAfterDiscount) {
        if (balance <= 0 || amountAfterDiscount == null || amountAfterDiscount.signum() <= 0) return 0;
        BigDecimal allowed = amountAfterDiscount.multiply(BigDecimal.valueOf(maxPercentOfBill())).divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR);
        BigDecimal whole = allowed.divide(vndPerPointSpent(), 0, RoundingMode.FLOOR);
        long byShare = whole.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0 ? Integer.MAX_VALUE : whole.longValue();
        int most = (int) Math.min(balance, byShare);
        return most < minPoints() ? 0 : most; // a bill or a balance too small for the least amount can spend nothing
    }

    /**
     * Why spending {@code points} is not allowed, as a sentence for the cashier, or null when it is. {@code balance} is the points
     * the customer has, or null when no customer is attached.
     */
    public static String redemptionProblem(int points, Integer balance, BigDecimal amountAfterDiscount) {
        if (points == 0) return null;
        if (points < 0) return I18n.t("pos.points.invalid");
        if (balance == null) return I18n.t("pos.points.nocustomer");
        if (points > balance) return I18n.t("pos.points.notenough", balance);
        if (points < minPoints()) {
            return I18n.t("pos.points.min", minPoints(), com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil.format(vndPerPointSpent().multiply(BigDecimal.valueOf(minPoints()))));
        }
        int max = maxRedeemable(balance, amountAfterDiscount);
        if (points > max) return I18n.t("pos.points.toomany", max, maxPercentOfBill());
        return null;
    }

    /**
     * The number of points typed in the payment dialog: blank is 0; digits only, up to nine of them; anything else (a minus sign, a
     * comma, a letter) is null, "not a number of points".
     */
    public static Integer parsePoints(String typed) {
        String text = typed == null ? "" : com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.plain(typed).strip(); // TODO 3.71
        if (text.isEmpty()) return 0;
        if (!text.matches("[0-9]{1,9}")) return null;
        return Integer.valueOf(text);
    }

    /** The discount reason as saved when points paid part of the bill: the cashier's reason (if any) and a note of the points. */
    public static String reasonWithPoints(String manualReason, int points) {
        if (points <= 0) return manualReason;
        String note = I18n.t("order.discount.reason.points", points);
        return manualReason == null || manualReason.isBlank() ? note : manualReason + " · " + note;
    }

    /** Points from which a customer is silver / gold (inclusive); 100 and 500 until the owner changes them. */
    public static int silverFrom() {
        return ShopRules.current().silverFrom();
    }

    public static int goldFrom() {
        return ShopRules.current().goldFrom();
    }

    /** The membership tier of a customer with this many points; a negative number counts as none. */
    public enum Tier { NEW, SILVER, GOLD }

    public static Tier tierFor(int points) {
        if (points >= goldFrom()) return Tier.GOLD;
        if (points >= silverFrom()) return Tier.SILVER;
        return Tier.NEW;
    }

    /**
     * The tier of a customer (TODO 3.80): from the points earned over time, not from the balance, so spending points on a bill does
     * not take a customer down a tier ({@link com.coffeeshop.coffeeshopmanagement.model.Customer#getTierPoints()}).
     */
    public static Tier tierOf(com.coffeeshop.coffeeshopmanagement.model.Customer customer) {
        return tierFor(customer == null ? 0 : customer.getTierPoints());
    }

    /** The tier's name in the interface language. */
    public static String tierName(int points) {
        switch (tierFor(points)) {
            case GOLD: return I18n.t("customers.tier.gold");
            case SILVER: return I18n.t("customers.tier.silver");
            default: return I18n.t("customers.tier.new");
        }
    }

    private LoyaltyPolicy() {
    }

    /**
     * The points a paid order put on its customer's account, or {@code null} when the receipt should say nothing about points
     * (no customer attached, or the order is not paid: a refund takes the points back, TODO 5.28). Same rule as the payment itself.
     */
    public static Integer pointsEarnedBy(com.coffeeshop.coffeeshopmanagement.model.Order order) {
        if (order == null || order.getCustomerId() == null
                || order.getStatus() != com.coffeeshop.coffeeshopmanagement.model.OrderStatus.PAID) {
            return null;
        }
        return pointsFor(order.getTotal());
    }

    public static int pointsFor(BigDecimal orderTotal) {
        if (orderTotal == null || orderTotal.signum() <= 0) {
            return 0;
        }
        // Not .intValue(): above 21 trillion dong that wrapped round to a negative number of points (TODO 3.59).
        BigDecimal points = orderTotal.divide(BigDecimal.valueOf(ShopRules.current().earnDong()), 0, RoundingMode.DOWN);
        return points.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0 ? Integer.MAX_VALUE : points.intValue();
    }
}
