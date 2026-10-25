package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One place that decides how many loyalty points a paid order earns, so the rule isn't
 * scattered across DAOs/controllers. Currently: 1 point per full 10.000đ of the final total.
 * The membership tiers (100 points = silver, 500 = gold) are decided here too, by {@link #tierFor(int)} (TODO 6.63), so the
 * customer list, the receipt and an export cannot disagree.
 */
public final class LoyaltyPolicy {

    private static final BigDecimal VND_PER_POINT = new BigDecimal("10000");

    /** Points from which a customer is silver / gold (inclusive). */
    public static final int SILVER_FROM = 100;
    public static final int GOLD_FROM = 500;

    /** The membership tier of a customer with this many points; a negative number counts as none. */
    public enum Tier { NEW, SILVER, GOLD }

    public static Tier tierFor(int points) {
        if (points >= GOLD_FROM) return Tier.GOLD;
        if (points >= SILVER_FROM) return Tier.SILVER;
        return Tier.NEW;
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
        BigDecimal points = orderTotal.divide(VND_PER_POINT, 0, RoundingMode.DOWN);
        return points.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0 ? Integer.MAX_VALUE : points.intValue();
    }
}
