package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Reservation;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 5.49: what the first-sign-in list has to say. */
public class LeftoverRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    private static final LocalDateTime DAY_START = TODAY.atStartOfDay();

    private static Reservation booking(int id, LocalDateTime when, Reservation.Status status) {
        return new Reservation(id, 1, "Guest " + id, null, when, 2, null, status, "x", when.minusDays(1), null);
    }

    @Test
    public void itComesOnceADayOnAComputer() {
        assertTrue(LeftoverRules.due(null, TODAY));
        assertTrue(LeftoverRules.due("2026-11-05", TODAY));
        assertTrue(LeftoverRules.due("garbage", TODAY));
        assertFalse(LeftoverRules.due("2026-11-06", TODAY));
    }

    @Test
    public void onlyWaitingBookingsOfEarlierDaysAreListed() {
        Reservation lastNight = booking(1, DAY_START.minusHours(2), Reservation.Status.BOOKED);
        Reservation lastWeek = booking(2, DAY_START.minusDays(6), Reservation.Status.BOOKED);
        Reservation tonight = booking(3, DAY_START.plusHours(19), Reservation.Status.BOOKED);
        Reservation seated = booking(4, DAY_START.minusDays(1), Reservation.Status.SEATED);
        Reservation atStart = booking(5, DAY_START, Reservation.Status.BOOKED);
        List<Reservation> found = LeftoverRules.earlierBookings(List.of(lastWeek, lastNight, tonight, seated, atStart), DAY_START);
        assertEquals(List.of(lastWeek, lastNight), found);
    }

    @Test
    public void aShiftClosedYesterdayIsNormalButTwoDaysIsMentioned() {
        assertNull(LeftoverRules.daysSinceClose(TODAY.minusDays(1).atTime(22, 0), TODAY));
        assertNull("closed today", LeftoverRules.daysSinceClose(TODAY.atTime(1, 0), TODAY));
        assertEquals(Integer.valueOf(2), LeftoverRules.daysSinceClose(TODAY.minusDays(2).atTime(23, 59), TODAY));
        assertEquals(Integer.valueOf(9), LeftoverRules.daysSinceClose(TODAY.minusDays(9).atTime(8, 0), TODAY));
    }

    @Test
    public void aShopThatNeverClosedAShiftIsNotNagged() {
        assertNull(LeftoverRules.daysSinceClose(null, TODAY));
    }

    @Test
    public void nothingToSayMeansAnEmptyList() {
        assertTrue(LeftoverRules.of(List.of(), TODAY.minusDays(1).atTime(21, 0), DAY_START, TODAY).isEmpty());
        assertFalse(LeftoverRules.of(List.of(booking(1, DAY_START.minusDays(1), Reservation.Status.BOOKED)), null, DAY_START, TODAY).isEmpty());
        assertFalse(LeftoverRules.of(List.of(), TODAY.minusDays(3).atTime(21, 0), DAY_START, TODAY).isEmpty());
    }
}
