package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.service.SalesPatterns.PaidOrder;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.40: busiest hours, weekdays and the change against the period before, on a fixed day. */
public class SalesPatternsTest {

    // 2026-10-19 is a Monday
    private static PaidOrder at(int day, int hour, int minute, long total) {
        return new PaidOrder(LocalDate.of(2026, 10, day).atTime(hour, minute), BigDecimal.valueOf(total));
    }

    @Test
    public void ordersAreGroupedByTheHourTheyWerePaid() {
        List<SalesPatterns.Slot> hours = SalesPatterns.byHour(List.of(
                at(19, 9, 59, 30_000), at(19, 10, 0, 40_000), at(20, 10, 30, 50_000), at(20, 23, 59, 10_000)));
        assertEquals(3, hours.size());
        assertEquals(9, hours.get(0).key());
        assertEquals(1, hours.get(0).orders());
        assertEquals(10, hours.get(1).key());
        assertEquals(2, hours.get(1).orders());
        assertEquals(0, BigDecimal.valueOf(90_000).compareTo(hours.get(1).revenue()));
        assertEquals(23, hours.get(2).key());
    }

    @Test
    public void anEmptyListGivesNoHoursAndNoBusiestSlot() {
        assertTrue(SalesPatterns.byHour(List.of()).isEmpty());
        assertNull(SalesPatterns.busiest(List.of()));
        assertNull(SalesPatterns.bestWeekday(SalesPatterns.byWeekday(List.of(), LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25))));
    }

    @Test
    public void theBusiestHourHasTheMostOrdersAndRevenueBreaksATie() {
        List<SalesPatterns.Slot> hours = SalesPatterns.byHour(List.of(
                at(19, 8, 0, 20_000), at(19, 8, 5, 20_000),            // 2 orders, 40.000
                at(19, 15, 0, 90_000), at(19, 15, 5, 10_000),          // 2 orders, 100.000
                at(19, 12, 0, 500_000)));                              // 1 order, big
        assertEquals(15, SalesPatterns.busiest(hours).key());
    }

    @Test
    public void weekdaysCountTheDaysOfThatKindInTheRange() {
        LocalDate monday = LocalDate.of(2026, 10, 19);
        assertEquals(1, SalesPatterns.countWeekday(monday, monday, DayOfWeek.MONDAY));
        assertEquals(0, SalesPatterns.countWeekday(monday, monday, DayOfWeek.TUESDAY));
        assertEquals(1, SalesPatterns.countWeekday(monday, monday.plusDays(6), DayOfWeek.SUNDAY));
        assertEquals(2, SalesPatterns.countWeekday(monday, monday.plusDays(7), DayOfWeek.MONDAY)); // both ends included
        assertEquals(4, SalesPatterns.countWeekday(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), DayOfWeek.MONDAY)); // 5, 12, 19, 26
        assertEquals(5, SalesPatterns.countWeekday(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), DayOfWeek.THURSDAY)); // 1, 8, 15, 22, 29
        assertEquals(0, SalesPatterns.countWeekday(monday, monday.minusDays(1), DayOfWeek.MONDAY));
    }

    @Test
    public void theWeekdayAverageIsRevenuePerDayOfThatKind() {
        // two weeks: Monday 19 and Monday 26 October; 100.000 on the first, nothing on the second; 60.000 on a Tuesday
        List<SalesPatterns.WeekdaySlot> days = SalesPatterns.byWeekday(List.of(
                at(19, 10, 0, 100_000), at(20, 10, 0, 60_000)), LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 27));
        assertEquals(7, days.size());
        SalesPatterns.WeekdaySlot monday = days.get(0);
        assertEquals(DayOfWeek.MONDAY, monday.day());
        assertEquals(2, monday.daysInRange());
        assertEquals(1, monday.orders());
        assertEquals(0, BigDecimal.valueOf(50_000).compareTo(monday.average()));
        SalesPatterns.WeekdaySlot tuesday = days.get(1);
        assertEquals(2, tuesday.daysInRange());
        assertEquals(0, BigDecimal.valueOf(30_000).compareTo(tuesday.average()));
        assertEquals(DayOfWeek.WEDNESDAY, days.get(2).day());
        assertEquals(0, days.get(2).orders());
        assertEquals(DayOfWeek.MONDAY, SalesPatterns.bestWeekday(days).day());
    }

    @Test
    public void aOneDayRangeGivesOneWeekdayRow() {
        LocalDate day = LocalDate.of(2026, 10, 24); // Saturday
        List<SalesPatterns.WeekdaySlot> days = SalesPatterns.byWeekday(List.of(at(24, 9, 0, 10_000)), day, day);
        assertEquals(1, days.size());
        assertEquals(DayOfWeek.SATURDAY, days.get(0).day());
    }

    @Test
    public void thePreviousRangeHasTheSameLengthAndEndsTheDayBefore() {
        SalesPatterns.Range month = SalesPatterns.previousRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
        assertEquals(LocalDate.of(2026, 8, 31), month.from());
        assertEquals(LocalDate.of(2026, 9, 30), month.to());
        SalesPatterns.Range oneDay = SalesPatterns.previousRange(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 24));
        assertEquals(LocalDate.of(2026, 10, 23), oneDay.from());
        assertEquals(LocalDate.of(2026, 10, 23), oneDay.to());
        SalesPatterns.Range week = SalesPatterns.previousRange(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25));
        assertEquals(LocalDate.of(2026, 10, 12), week.from());
        assertEquals(LocalDate.of(2026, 10, 18), week.to());
    }

    @Test
    public void theChangeIsAPercentOfTheEarlierFigure() {
        assertEquals(Integer.valueOf(15), SalesPatterns.change(BigDecimal.valueOf(115), BigDecimal.valueOf(100)).percent());
        assertEquals(Integer.valueOf(-50), SalesPatterns.change(BigDecimal.valueOf(50), BigDecimal.valueOf(100)).percent());
        assertEquals(Integer.valueOf(0), SalesPatterns.change(BigDecimal.valueOf(100), BigDecimal.valueOf(100)).percent());
        assertEquals(Integer.valueOf(1), SalesPatterns.change(BigDecimal.valueOf(1005), BigDecimal.valueOf(1000)).percent()); // 0.5 rounds up
        assertEquals(Integer.valueOf(-100), SalesPatterns.change(BigDecimal.ZERO, BigDecimal.valueOf(40)).percent());
        assertNull("a percentage of nothing is no number", SalesPatterns.change(BigDecimal.valueOf(40), BigDecimal.ZERO).percent());
        assertTrue(SalesPatterns.change(BigDecimal.valueOf(2), BigDecimal.ONE).direction() > 0);
        assertTrue(SalesPatterns.change(BigDecimal.ONE, BigDecimal.valueOf(2)).direction() < 0);
    }

    @Test
    public void barLengthIsProportionalAndNeverZeroForARealValue() {
        assertEquals(20, SalesPatterns.barLength(BigDecimal.TEN, BigDecimal.TEN, 20));
        assertEquals(10, SalesPatterns.barLength(BigDecimal.valueOf(5), BigDecimal.TEN, 20));
        assertEquals("a tiny value still shows one block", 1, SalesPatterns.barLength(BigDecimal.ONE, BigDecimal.valueOf(1000), 20));
        assertEquals(0, SalesPatterns.barLength(BigDecimal.ZERO, BigDecimal.TEN, 20));
        assertEquals(0, SalesPatterns.barLength(BigDecimal.TEN, BigDecimal.ZERO, 20));
    }
}
