package com.coffeeshop.coffeeshopmanagement.util;

/** The arithmetic behind {@link ScreenFit}, kept free of JavaFX classes so it can be unit-tested without a toolkit. */
public final class ScreenFitMath {

    public static final double MIN_SCALE = 0.4;
    public static final double MAX_SCALE = 1.5;

    private ScreenFitMath() {
    }

    /** The zoom that makes a design of the given size fit a window area, kept between MIN_SCALE and MAX_SCALE. */
    public static double scaleFor(double areaWidth, double areaHeight, double designWidth, double designHeight) {
        double fit = Math.min(areaWidth / designWidth, areaHeight / designHeight);
        if (Double.isNaN(fit) || fit <= 0) return 1.0;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, fit));
    }

    /**
     * The scene size to ask for: the design size, or the biggest size that still fits the free screen area
     * (after the window's own title bar and borders, {@code chrome}).
     */
    public static double fitLength(double designLength, double freeLength, double chrome) {
        double room = freeLength - chrome;
        return room <= 0 ? designLength : Math.min(designLength, room);
    }
}
