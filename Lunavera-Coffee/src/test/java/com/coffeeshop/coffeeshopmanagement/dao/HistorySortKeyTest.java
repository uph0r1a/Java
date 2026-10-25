package com.coffeeshop.coffeeshopmanagement.dao;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HistorySortKeyTest {
    @Test
    public void theDateColumnSortsByTheTimeOfPaymentLikeTheFilterAndTheReports() {
        // TODO 2.67: the same expression as OrderDAO.EFFECTIVE_DAY and the history date filter, with the table alias
        assertEquals("COALESCE(o.paid_at, o.order_date)", OrderDAO.HistorySortKey.DATE.sql());
        assertEquals("o.id", OrderDAO.HistorySortKey.ID.sql());
    }
}
