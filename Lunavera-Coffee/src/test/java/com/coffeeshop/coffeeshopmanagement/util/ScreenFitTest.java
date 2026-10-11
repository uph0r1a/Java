package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 2.4: the arithmetic that fits a 1600x1000 layout to the shop's screen (no window needed). */
public class ScreenFitTest {

    @Test
    public void aBigEnoughWindowKeepsTheDesignSize() {
        assertEquals(1.0, ScreenFitMath.scaleFor(1600, 1000, 1600, 1000), 1e-9);
    }

    @Test
    public void aSmallerWindowShrinksToTheTighterSide() {
        // 1366x768 laptop: height is the tighter side
        assertEquals(735.0 / 1000, ScreenFitMath.scaleFor(1356, 735, 1600, 1000), 1e-9);
        assertEquals(0.5, ScreenFitMath.scaleFor(800, 800, 1600, 1000), 1e-9);
    }

    @Test
    public void theZoomIsKeptInARange() {
        assertEquals(ScreenFitMath.MIN_SCALE, ScreenFitMath.scaleFor(10, 10, 1600, 1000), 1e-9);
        assertEquals(ScreenFitMath.MAX_SCALE, ScreenFitMath.scaleFor(9000, 9000, 1600, 1000), 1e-9);
    }

    @Test
    public void nonsenseSizesFallBackToNoZoom() {
        assertEquals(1.0, ScreenFitMath.scaleFor(0, 0, 1600, 1000), 1e-9);
        assertEquals(1.0, ScreenFitMath.scaleFor(Double.NaN, 500, 1600, 1000), 1e-9);
    }

    @Test
    public void theWindowIsAskedForTheDesignSizeOrWhatTheScreenAllows() {
        assertEquals(1600, ScreenFitMath.fitLength(1600, 1920, 40), 1e-9);   // fits: design size
        assertEquals(1040 - 40, ScreenFitMath.fitLength(1600, 1040, 40), 1e-9); // too big: free area minus the window's own frame
        assertEquals(1000, ScreenFitMath.fitLength(1000, 0, 40), 1e-9);      // unknown screen: keep the design
    }
}
