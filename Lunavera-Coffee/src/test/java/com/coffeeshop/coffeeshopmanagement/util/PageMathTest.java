package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 6.35: the page arithmetic behind {@link PageBar}, which itself needs a JavaFX toolkit to build. */
public class PageMathTest {

    private static final int G = PageMath.GAP;

    @Test
    public void thereIsAlwaysAtLeastOnePage() {
        assertEquals(1, PageMath.totalPages(0, 10));
        assertEquals(1, PageMath.totalPages(-5, 10));
        assertEquals(1, PageMath.totalPages(1, 10));
    }

    @Test
    public void aPartlyFilledLastPageCounts() {
        assertEquals(1, PageMath.totalPages(10, 10));
        assertEquals(2, PageMath.totalPages(11, 10));
        assertEquals(3, PageMath.totalPages(25, 10));
    }

    @Test
    public void theAllOptionAndABrokenSizeNeverDivideByZero() {
        assertEquals(1, PageMath.totalPages(5_000, Integer.MAX_VALUE));
        assertEquals(5, PageMath.totalPages(5, 0));
        assertEquals(5, PageMath.totalPages(5, -3));
    }

    @Test
    public void aPageNumberIsKeptInsideTheRange() {
        assertEquals(1, PageMath.clamp(0, 5));
        assertEquals(1, PageMath.clamp(-7, 5));
        assertEquals(3, PageMath.clamp(3, 5));
        assertEquals(5, PageMath.clamp(9, 5));
        assertEquals(1, PageMath.clamp(4, 0));
    }

    @Test
    public void fewPagesAreAllShown() {
        assertEquals(Arrays.asList(1), PageMath.visiblePages(1, 1));
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), PageMath.visiblePages(3, 5));
    }

    @Test
    public void manyPagesCollapseAroundTheCurrentOne() {
        assertEquals(Arrays.asList(1, 2, 3, 4, G, 20), PageMath.visiblePages(1, 20));
        assertEquals(Arrays.asList(1, G, 4, 5, 6, G, 20), PageMath.visiblePages(5, 20));
        assertEquals(Arrays.asList(1, G, 17, 18, 19, 20), PageMath.visiblePages(20, 20));
    }

    @Test
    public void aGapIsDrawnEvenWhenOnlyOnePageIsSkipped() {
        // Behaviour as written in PageBar since the start: "1 … 3 4 5 … 7" rather than showing 2 or 6. Pinned so a change is deliberate.
        assertEquals(Arrays.asList(1, G, 3, 4, 5, G, 7), PageMath.visiblePages(4, 7));
        assertEquals(Arrays.asList(1, 2, 3, 4, G, 6), PageMath.visiblePages(3, 6));
    }

    @Test
    public void theCurrentPageAndTheFirstAndLastAreAlwaysShown() {
        for (int pages = 1; pages <= 60; pages++) {
            for (int current = 1; current <= pages; current++) {
                List<Integer> shown = PageMath.visiblePages(current, pages);
                assertTrue(shown.contains(1));
                assertTrue(shown.contains(pages));
                assertTrue(shown.contains(current));
                assertTrue("the bar must stay small: " + shown, shown.size() <= 9);
            }
        }
    }
}
