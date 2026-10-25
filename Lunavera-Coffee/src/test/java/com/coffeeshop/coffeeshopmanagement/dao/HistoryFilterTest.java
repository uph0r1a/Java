package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO.HistoryFilter;
import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.*;

/** TODO 3.58: a date range with "from" after "to" is recognised, so the screens can warn instead of showing an empty list. */
public class HistoryFilterTest {

    @Test
    public void fromAfterToIsReversed() {
        assertTrue(new HistoryFilter(null, null, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 1)).hasReversedDates());
    }

    @Test
    public void everyOtherCombinationIsFine() {
        LocalDate d = LocalDate.of(2026, 10, 5);
        assertFalse(new HistoryFilter(null, null, d, d).hasReversedDates());
        assertFalse(new HistoryFilter(null, null, d, d.plusDays(1)).hasReversedDates());
        assertFalse(new HistoryFilter(null, null, d, null).hasReversedDates());
        assertFalse(new HistoryFilter(null, null, null, d).hasReversedDates());
        assertFalse(HistoryFilter.NONE.hasReversedDates());
    }
}
