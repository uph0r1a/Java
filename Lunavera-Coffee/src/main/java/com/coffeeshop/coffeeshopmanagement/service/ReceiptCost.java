package com.coffeeshop.coffeeshopmanagement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What receiving stock at a price does to the product's cost (TODO 4.52). The profit report costs every sale at the product's cost at
 * the time it was paid, so a supplier's new price has to reach the product or sales stay costed at the old one. Nothing changes unless
 * the person says so: {@link Mode#KEEP} is the default.
 */
public final class ReceiptCost {

    /** What to do with the product's cost when goods arrive at a price. */
    public enum Mode {
        /** Leave the product's cost as it is (the price is only written in the stock log). */
        KEEP,
        /** The product's cost becomes the price of this delivery. */
        REPLACE,
        /** The product's cost becomes the weighted average of what is in stock (at the old cost) and what arrived (at the new price). */
        AVERAGE
    }

    private ReceiptCost() {
    }

    /** True when a price was typed and the product has a different cost now (or none). */
    public static boolean differs(BigDecimal productCost, BigDecimal receiptCost) {
        return receiptCost != null && (productCost == null || productCost.compareTo(receiptCost) != 0);
    }

    /**
     * The weighted average in whole dong, rounded half up: {@code (oldStock * oldCost + quantity * newCost) / (oldStock + quantity)}.
     * Stock at or below zero, or no old cost, counts for nothing (the new price is the cost).
     */
    public static BigDecimal average(int oldStock, BigDecimal oldCost, int quantity, BigDecimal newCost) {
        if (quantity <= 0 || oldStock <= 0 || oldCost == null) return newCost;
        BigDecimal value = oldCost.multiply(BigDecimal.valueOf(oldStock)).add(newCost.multiply(BigDecimal.valueOf(quantity)));
        return value.divide(BigDecimal.valueOf((long) oldStock + quantity), 0, RoundingMode.HALF_UP);
    }

    /** The product's cost after a delivery: the old one when {@code mode} is KEEP, nothing was typed or nothing arrived. */
    public static BigDecimal resulting(Mode mode, int oldStock, BigDecimal oldCost, int quantity, BigDecimal receiptCost) {
        if (mode == null || mode == Mode.KEEP || receiptCost == null || quantity <= 0) return oldCost;
        return mode == Mode.REPLACE ? receiptCost : average(oldStock, oldCost, quantity, receiptCost);
    }
}
