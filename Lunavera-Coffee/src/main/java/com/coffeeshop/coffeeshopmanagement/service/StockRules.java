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

    /**
     * The number of pieces typed, or -1 when it is not a whole number from 1 to the stock ceiling. The reading is {@link WholeNumber}'s
     * (TODO 2.95): {@code 1.500} is 1500, but {@code 1.5}, {@code 2,5} and {@code 7.5} are refused instead of becoming 15, 25 and 75.
     */
    public static int parseQuantity(String typed) {
        java.util.OptionalLong value = WholeNumber.parse(typed);
        if (value.isEmpty()) return -1;
        return value.getAsLong() >= 1 && value.getAsLong() <= Limits.MAX_STOCK ? (int) value.getAsLong() : -1;
    }

    /** True when adding {@code quantity} to {@code currentStock} would pass {@link Limits#MAX_STOCK}. */
    public static boolean passesCeiling(int currentStock, int quantity) {
        return (long) currentStock + quantity > Limits.MAX_STOCK;
    }

    /** Free text kept as one short line: line breaks and runs of spaces become one space, cut at {@link #TEXT_MAX}; null for nothing. */
    public static String cleanText(String typed) {
        if (typed == null) return null;
        // composed accents first (TODO 3.18): the same word typed two ways must be stored one way
        String one = java.text.Normalizer.normalize(com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.clean(typed), java.text.Normalizer.Form.NFC).replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
        if (one.length() > TEXT_MAX) one = com.coffeeshop.coffeeshopmanagement.util.TextClip.clip(one, TEXT_MAX).stripTrailing();
        return one.isEmpty() ? null : one;
    }
}
