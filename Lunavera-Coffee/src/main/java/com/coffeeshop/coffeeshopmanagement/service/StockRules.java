package com.coffeeshop.coffeeshopmanagement.service;

/**
 * What may be typed when goods come in (TODO 4.5): a whole number of pieces, at least one, and the stock after it must stay
 * within {@link Limits#MAX_STOCK}. No screen, no database, so the dialog and the DAO say the same thing.
 */
public final class StockRules {

    /** Free text on a receipt line: the supplier's name, a note. */
    public static final int TEXT_MAX = 100;

    private StockRules() {
    }

    /** The number of pieces typed, or -1 when it is not a whole number from 1 to the stock ceiling. */
    public static int parseQuantity(String typed) {
        if (typed == null) return -1;
        String digits = typed.strip().replace(".", "").replace(",", "").replace(" ", "");
        if (digits.isEmpty() || digits.length() > 9) return -1;
        for (int i = 0; i < digits.length(); i++) {
            if (digits.charAt(i) < '0' || digits.charAt(i) > '9') return -1;
        }
        int value = Integer.parseInt(digits);
        return value >= 1 && value <= Limits.MAX_STOCK ? value : -1;
    }

    /** True when adding {@code quantity} to {@code currentStock} would pass {@link Limits#MAX_STOCK}. */
    public static boolean passesCeiling(int currentStock, int quantity) {
        return (long) currentStock + quantity > Limits.MAX_STOCK;
    }

    /** Free text kept as one short line: line breaks and runs of spaces become one space, cut at {@link #TEXT_MAX}; null for nothing. */
    public static String cleanText(String typed) {
        if (typed == null) return null;
        String one = typed.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
        if (one.length() > TEXT_MAX) one = one.substring(0, TEXT_MAX).stripTrailing();
        return one.isEmpty() ? null : one;
    }
}
