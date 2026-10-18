package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.*;

public class ClockAndTablePolicyTest {

    private static final LocalDateTime NEWEST = LocalDateTime.of(2026, 10, 12, 18, 0);

    @Test
    public void aClockBehindTheNewestOrderIsReported() {
        assertNotNull(ClockCheck.problem(NEWEST.minusDays(3), NEWEST));
        assertNotNull(ClockCheck.problem(NEWEST.minusHours(1), NEWEST));
    }

    @Test
    public void aNormalClockIsNotReported() {
        assertNull(ClockCheck.problem(NEWEST.plusHours(14), NEWEST));
        assertNull("a few minutes of difference is normal", ClockCheck.problem(NEWEST.minusMinutes(5), NEWEST));
        assertNull("no orders yet", ClockCheck.problem(NEWEST, null));
    }

    @Test
    public void onlyTheOpeningCashierOrAnAdminMayEmptyATableWithAnOrder() {
        assertTrue(TablePolicy.canEmptyTableWithOpenOrder(true, null, 7));
        assertTrue(TablePolicy.canEmptyTableWithOpenOrder(false, 7, 7));
        assertFalse(TablePolicy.canEmptyTableWithOpenOrder(false, 8, 7));
        assertFalse("an order with no recorded cashier is admin-only", TablePolicy.canEmptyTableWithOpenOrder(false, 8, null));
        assertFalse(TablePolicy.canEmptyTableWithOpenOrder(false, null, 7));
    }
}
