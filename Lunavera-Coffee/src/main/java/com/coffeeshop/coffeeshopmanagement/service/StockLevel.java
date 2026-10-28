package com.coffeeshop.coffeeshopmanagement.service;

/**
 * When a product counts as "low on stock" (TODO 3.21 + 4.19). A product has its own reorder level (a fast seller needs
 * 40 in the fridge, a slow one 3); a product with none uses the shop default {@value #DEFAULT}. Low means
 * {@code stock <= level}; "very low" (the red badge) is half the level or less. One place, used by the dashboards, the
 * product table, the cashier's product cards and the "need to reorder" filter, so they cannot disagree.
 */
public final class StockLevel {

    /** The reorder level of a product that has none of its own, until the owner sets another one (TODO 4.7). It was the one shop-wide number before. */
    public static final int DEFAULT = 10;

    private static volatile int shopDefault = DEFAULT;

    private StockLevel() {
    }

    /** The shop-wide level the owner chose in the settings screen ({@link #DEFAULT} until then). */
    public static int defaultLevel() {
        return shopDefault;
    }

    /** Called by {@code ShopSettings} when the level is loaded or saved. A value outside 0..{@link Limits#MAX_STOCK} is ignored. */
    public static void setDefaultLevel(int level) {
        if (level >= 0 && level <= Limits.MAX_STOCK) shopDefault = level;
    }

    /** For tests: the factory level again. */
    public static void resetForTests() {
        shopDefault = DEFAULT;
    }

    /** The level that applies: the product's own, or the shop-wide level ({@link #defaultLevel()}) when it has none. */
    public static int effective(Integer own) {
        return own == null ? shopDefault : own;
    }

    public static boolean isLow(int stock, Integer own) {
        return stock <= effective(own);
    }

    /** Red badge: out of stock, or half the level or less. With the default level this is the old "5 or fewer". */
    public static boolean isCritical(int stock, Integer own) {
        return stock <= 0 || (long) stock * 2 <= effective(own);
    }

    /** What the box of the product dialog may hold: empty (use the default) or a whole number from 0 to the stock ceiling. */
    public static String problem(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return parse(text) == null ? "products.val.reorder" : null;
    }

    /** The number typed, or null for an empty or invalid text (use {@link #problem} to tell which). */
    public static Integer parse(String text) {
        if (text == null) {
            return null;
        }
        String clean = text.trim();
        if (clean.isEmpty() || clean.length() > 7) {
            return null;
        }
        for (int i = 0; i < clean.length(); i++) {
            if (clean.charAt(i) < '0' || clean.charAt(i) > '9') {
                return null;
            }
        }
        int value = Integer.parseInt(clean);
        return value <= Limits.MAX_STOCK ? value : null;
    }
}
