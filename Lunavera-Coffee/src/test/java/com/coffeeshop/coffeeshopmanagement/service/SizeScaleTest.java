package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 4.47: a size takes more or less of every recipe line, in whole units. */
public class SizeScaleTest {

    @Test
    public void aNormalOrMissingSizeTakesTheRecipeAsItIs() {
        assertEquals(18, SizeScale.scale(18, null));
        assertEquals(18, SizeScale.scale(18, 100));
        assertEquals(18, SizeScale.scale(18, 0));
        assertEquals(18, SizeScale.scale(18, -5));
    }

    @Test
    public void aBiggerOrSmallerSizeScalesAndRoundsToTheNearestUnit() {
        assertEquals(23, SizeScale.scale(18, 130));   // 23.4
        assertEquals(195, SizeScale.scale(150, 130));
        assertEquals(14, SizeScale.scale(18, 80));    // 14.4
        assertEquals(9, SizeScale.scale(5, 170));     // 8.5 rounds up
        assertEquals(36, SizeScale.scale(18, 200));
    }

    @Test
    public void aSizeNeverTakesNothingWhileTheRecipeTakesSomething() {
        assertEquals(1, SizeScale.scale(1, 10));
        assertEquals(1, SizeScale.scale(3, 10));
        assertEquals(0, SizeScale.scale(0, 130));
    }

    @Test
    public void thePercentToUseIsHundredWhenThereIsNone() {
        assertEquals(100, SizeScale.percentOf(null));
        assertEquals(100, SizeScale.percentOf(0));
        assertEquals(130, SizeScale.percentOf(130));
    }
}
