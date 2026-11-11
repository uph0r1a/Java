package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 2.99: how big a dialog may be on a given screen (no JavaFX needed). */
public class DialogFitMathTest {

    @Test
    public void aShortScreenLimitsTheHeightToAShareOfTheFreeArea() {
        assertEquals(675, DialogFitMath.maxHeight(768 - 1), 60); // a 1366x768 till: about 88 % of what the taskbar leaves
        assertTrue(DialogFitMath.maxHeight(728) < 728);
        assertEquals(Math.floor(1040 * DialogFitMath.HEIGHT_SHARE), DialogFitMath.maxHeight(1040), 0.0);
    }

    @Test
    public void aDialogIsNeverSqueezedBelowTheMinimumUnlessTheScreenIsSmaller() {
        assertEquals(DialogFitMath.MIN_HEIGHT, DialogFitMath.maxHeight(280), 0.0);
        assertEquals(200, DialogFitMath.maxHeight(200), 0.0);
    }

    @Test
    public void anUnknownScreenMeansNoLimitNotAWrongOne() {
        assertEquals(Double.MAX_VALUE, DialogFitMath.maxHeight(0), 0.0);
        assertEquals(Double.MAX_VALUE, DialogFitMath.maxHeight(Double.NaN), 0.0);
        assertEquals(Double.MAX_VALUE, DialogFitMath.maxWidth(-5), 0.0);
    }

    @Test
    public void theWidthIsLimitedToo() {
        assertEquals(Math.floor(1366 * DialogFitMath.WIDTH_SHARE), DialogFitMath.maxWidth(1366), 0.0);
        assertEquals(300, DialogFitMath.maxWidth(300), 0.0);
    }

    @Test
    public void onlyALongMessageGetsAScrollBox() {
        assertFalse(DialogFitMath.isLongText(null));
        assertFalse(DialogFitMath.isLongText("Saved."));
        assertTrue(DialogFitMath.isLongText("x".repeat(501)));
        assertTrue(DialogFitMath.isLongText("a\n".repeat(14)));
        assertFalse(DialogFitMath.isLongText("a\n".repeat(5)));
    }
}
