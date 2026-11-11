package com.coffeeshop.coffeeshopmanagement.service;

/**
 * How much of a recipe one piece of a size takes (TODO 4.47, with the recipes of TODO 4.41): a Large with 130 takes 30% more of every
 * ingredient than the recipe says. Whole units only, rounded to the nearest, and never to nothing while the recipe takes something.
 */
public final class SizeScale {

    /** The percentage that leaves the recipe as it is. */
    public static final int NORMAL = 100;

    private SizeScale() {
    }

    /** The percentage to use: a line with no size (null) or a damaged value (zero or less) is a normal serving. */
    public static int percentOf(Integer percent) {
        return percent == null || percent <= 0 ? NORMAL : percent;
    }

    /** {@code amount} (one recipe line, per piece) scaled to the size; the amount itself when the size is normal. */
    public static long scale(long amount, Integer percent) {
        int p = percentOf(percent);
        if (amount <= 0 || p == NORMAL) return amount;
        return Math.max(1, (amount * p + 50) / 100);
    }
}
