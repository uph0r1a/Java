package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.Assert.*;

/** TODO 4.50: the shop's day, with a day start of 04:00 and with none. */
public class BusinessDayTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 10);

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady(); // DbTime asks which database is in use
    }

    @Before
    @After
    public void calendarDay() {
        BusinessDay.resetForTests();
        AppClock.reset();
    }

    @Test
    public void withNoDayStartTheShopDayIsTheCalendarDay() {
        assertTrue(BusinessDay.isCalendarDay());
        assertEquals(D, BusinessDay.dayOf(D.atTime(0, 0)));
        assertEquals(D, BusinessDay.dayOf(D.atTime(23, 59, 59)));
        assertEquals(D.atStartOfDay(), BusinessDay.startOf(D));
        assertEquals("the bound is the plain date, as before", "2026-10-10", BusinessDay.boundText(D));
    }

    @Test
    public void anEveningStaysOnTheDayItStartedOn() {
        BusinessDay.setStartMinutes(4 * 60);
        assertEquals(D, BusinessDay.dayOf(D.atTime(23, 50)));
        assertEquals("00:40 is still the evening before", D, BusinessDay.dayOf(D.plusDays(1).atTime(0, 40)));
        assertEquals(D, BusinessDay.dayOf(D.plusDays(1).atTime(3, 59, 59)));
        assertEquals("04:00 sharp starts the new day", D.plusDays(1), BusinessDay.dayOf(D.plusDays(1).atTime(4, 0)));
        assertEquals(D.plusDays(1), BusinessDay.dayOf(D.plusDays(1).atTime(4, 1)));
        assertEquals(D, BusinessDay.dayOf(D.atTime(4, 0)));
        assertEquals("03:59 on the 10th still belongs to the 9th", D.minusDays(1), BusinessDay.dayOf(D.atTime(3, 59)));
    }

    @Test
    public void todayFollowsTheClockAndTheDayStart() {
        BusinessDay.setStartMinutes(4 * 60);
        AppClock.fix(D.plusDays(1).atTime(0, 30));
        assertEquals(D, BusinessDay.today());
        AppClock.fix(D.plusDays(1).atTime(4, 30));
        assertEquals(D.plusDays(1), BusinessDay.today());
    }

    @Test
    public void aDayRunsFromItsStartToTheNextStart() {
        BusinessDay.setStartMinutes(4 * 60 + 30);
        assertEquals(D.atTime(4, 30), BusinessDay.startOf(D));
        assertEquals(D.plusDays(1).atTime(4, 30), BusinessDay.startOf(D.plusDays(1)));
        assertEquals(LocalTime.of(4, 30), BusinessDay.startTime());
        assertFalse(BusinessDay.isCalendarDay());
    }

    @Test
    public void theBoundIsAFullTimeThatEqualsAStoredStartExactly() {
        BusinessDay.setStartMinutes(4 * 60);
        // SQLite keeps 04:00:00 as "2026-10-11T04:00" and compares text, so the bound must be written the same way (no ":00" more);
        // MariaDB compares real times and is given "2026-10-11 04:00:00"
        String expected = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.isSqlite() ? "2026-10-11T04:00" : "2026-10-11 04:00:00";
        assertEquals(expected, BusinessDay.boundText(D.plusDays(1)));
    }

    @Test
    public void theStartMustBeAtMostElevenFiftyNine() {
        BusinessDay.setStartMinutes(0);
        BusinessDay.setStartMinutes(BusinessDay.MAX_START_MINUTES);
        assertThrows(IllegalArgumentException.class, () -> BusinessDay.setStartMinutes(-1));
        assertThrows(IllegalArgumentException.class, () -> BusinessDay.setStartMinutes(12 * 60));
        assertEquals(BusinessDay.MAX_START_MINUTES, BusinessDay.startMinutes());
    }

    @Test
    public void timesAreWrittenAndReadAsHoursAndMinutes() {
        assertEquals("04:00", BusinessDay.format(240));
        assertEquals("00:00", BusinessDay.format(0));
        assertEquals("11:59", BusinessDay.format(BusinessDay.MAX_START_MINUTES));
        assertEquals(Integer.valueOf(240), BusinessDay.parse("04:00"));
        assertEquals(Integer.valueOf(270), BusinessDay.parse(" 4:30 "));
        assertEquals(Integer.valueOf(0), BusinessDay.parse("00:00"));
        assertNull(BusinessDay.parse("12:00"));
        assertNull(BusinessDay.parse("04:60"));
        assertNull(BusinessDay.parse("4"));
        assertNull(BusinessDay.parse("abc"));
        assertNull(BusinessDay.parse(null));
    }

    @Test
    public void everyEdgeOfADayIsOnItsOwnSide() {
        // the property the reports rely on: dayOf(t) == d exactly when startOf(d) <= t < startOf(d + 1)
        for (int start : new int[]{0, 30, 240, 270, 719}) {
            BusinessDay.setStartMinutes(start);
            LocalDateTime t = D.minusDays(1).atTime(20, 0);
            for (int step = 0; step < 4 * 24 * 4; step++) {
                LocalDate day = BusinessDay.dayOf(t);
                assertFalse("start " + start + " at " + t, t.isBefore(BusinessDay.startOf(day)));
                assertTrue("start " + start + " at " + t, t.isBefore(BusinessDay.startOf(day.plusDays(1))));
                t = t.plusMinutes(15);
            }
        }
    }
}
