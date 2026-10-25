package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 5.34: the rules behind the report window that do not need a screen. */
public class ReportRefreshTest {

    private static List<Integer> numbers(int n) {
        List<Integer> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) list.add(i);
        return list;
    }

    @Test
    public void theScreenShowsTheFirstTwentyAndTheCsvKeepsEverything() {
        List<Integer> all = numbers(60);
        List<Integer> shown = ReportRefresh.top(all, 20);
        assertEquals(20, shown.size());
        assertEquals(Integer.valueOf(1), shown.get(0));
        assertEquals(Integer.valueOf(20), shown.get(19));
        assertEquals("the full list is not cut", 60, all.size());
    }

    @Test
    public void aShortListIsShownWhole() {
        List<Integer> few = numbers(7);
        assertEquals(few, ReportRefresh.top(few, 20));
        assertEquals(List.of(), ReportRefresh.top(List.of(), 20));
        assertEquals(List.of(), ReportRefresh.top(numbers(5), 0));
    }

    @Test
    public void aWindowReloadsOnFocusOnlyWhenItsFiguresAreOldAndNothingIsRunning() {
        long loaded = 1_000_000;
        assertFalse("never loaded: the first load is the button's", ReportRefresh.shouldRefreshOnFocus(false, -1, loaded + 10 * 60_000));
        assertFalse("a quick alt-tab", ReportRefresh.shouldRefreshOnFocus(false, loaded, loaded + 5_000));
        assertFalse("just under the limit", ReportRefresh.shouldRefreshOnFocus(false, loaded, loaded + ReportRefresh.MIN_AGE_MILLIS - 1));
        assertTrue("old enough", ReportRefresh.shouldRefreshOnFocus(false, loaded, loaded + ReportRefresh.MIN_AGE_MILLIS));
        assertTrue("left open for hours", ReportRefresh.shouldRefreshOnFocus(false, loaded, loaded + 3 * 3_600_000L));
        assertFalse("a load is already running", ReportRefresh.shouldRefreshOnFocus(true, loaded, loaded + 3 * 3_600_000L));
    }

    @Test
    public void theLoadTimeIsShownAsHoursAndMinutes() {
        String text = ReportRefresh.loadedText(LocalDateTime.of(2026, 10, 24, 9, 5, 59));
        assertTrue(text, text.contains("09:05"));
        assertFalse("no seconds", text.contains("09:05:"));
    }
}
