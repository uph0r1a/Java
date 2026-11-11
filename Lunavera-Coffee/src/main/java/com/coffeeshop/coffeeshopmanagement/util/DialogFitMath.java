package com.coffeeshop.coffeeshopmanagement.util;

/**
 * The arithmetic behind {@link DialogFit}, free of JavaFX classes so it can be tested without a toolkit (TODO 2.99): how tall and how
 * wide a dialog may be on a given screen. The room is taken from the screen's free area (without the taskbar), and a little is left
 * for the window's own title bar and borders, which the dialog's size does not include.
 */
public final class DialogFitMath {

    /** The share of the free height a dialog may use; the rest is the title bar, the borders and some air. */
    public static final double HEIGHT_SHARE = 0.88;
    public static final double WIDTH_SHARE = 0.96;
    /** A dialog is never squeezed below this height (title, one line of content, buttons), unless the whole screen is smaller. */
    public static final double MIN_HEIGHT = 260;
    public static final double MIN_WIDTH = 320;

    private DialogFitMath() {
    }

    /** The tallest the dialog's content area plus buttons may be on a screen whose free area is {@code freeHeight} high. */
    public static double maxHeight(double freeHeight) {
        if (Double.isNaN(freeHeight) || freeHeight <= 0) return Double.MAX_VALUE; // unknown screen: no limit rather than a wrong one
        return Math.min(freeHeight, Math.max(MIN_HEIGHT, Math.floor(freeHeight * HEIGHT_SHARE)));
    }

    public static double maxWidth(double freeWidth) {
        if (Double.isNaN(freeWidth) || freeWidth <= 0) return Double.MAX_VALUE;
        return Math.min(freeWidth, Math.max(MIN_WIDTH, Math.floor(freeWidth * WIDTH_SHARE)));
    }

    /** True when a long message (an alert's text) should be shown in a scrolling box instead of being cut off. */
    public static boolean isLongText(String text) {
        return text != null && (text.length() > 500 || text.chars().filter(c -> c == '\n').count() > 12);
    }
}
