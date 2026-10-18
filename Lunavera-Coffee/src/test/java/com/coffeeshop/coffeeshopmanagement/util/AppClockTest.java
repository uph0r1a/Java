package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.Assert.*;

/** TODO 6.39: the clock the data code reads, and the two ways a test can hold it still. */
public class AppClockTest {

    private LocalDate pinnedAtStart;

    @After
    public void backToTheRealClock() {
        AppClock.reset();
        // The database tests pin the date for the whole run (TestDatabaseSupport); put that back if this JVM had it.
        if (pinnedAtStart != null) AppClock.pinDate(pinnedAtStart);
    }

    private void remember() {
        pinnedAtStart = AppClock.today().equals(LocalDate.now()) ? null : AppClock.today();
    }

    @Test
    public void withoutAnyPinItFollowsTheSystemClock() {
        remember();
        AppClock.reset();
        Duration gap = Duration.between(LocalDateTime.now(), AppClock.now()).abs();
        assertTrue("AppClock.now() is " + gap + " away from the system clock", gap.toSeconds() < 5);
    }

    @Test
    public void aPinnedDateStaysWhileTheTimeOfDayKeepsMoving() {
        remember();
        LocalDate day = LocalDate.of(2001, 2, 3);
        AppClock.pinDate(day);
        assertEquals(day, AppClock.today());
        assertEquals(day, AppClock.now().toLocalDate());
        Duration gap = Duration.between(LocalDateTime.now().toLocalTime().atDate(day), AppClock.now()).abs();
        assertTrue(gap.toSeconds() < 5 || gap.toHours() >= 23); // 23h+: the test crossed midnight between the two reads
    }

    @Test
    public void aFixedInstantDoesNotMoveAtAll() throws Exception {
        remember();
        LocalDateTime moment = LocalDateTime.of(2030, 12, 31, 23, 59, 58);
        AppClock.fix(moment);
        assertEquals(moment, AppClock.now());
        Thread.sleep(20);
        assertEquals(moment, AppClock.now());
        assertEquals(LocalDate.of(2030, 12, 31), AppClock.today());
    }

    @Test
    public void fixingAfterPinningAndPinningAfterFixingEachReplaceTheOther() {
        remember();
        AppClock.fix(LocalDateTime.of(2030, 1, 1, 10, 0));
        AppClock.pinDate(LocalDate.of(2040, 5, 6));
        assertEquals(LocalDate.of(2040, 5, 6), AppClock.today());
        AppClock.fix(LocalDateTime.of(2050, 7, 8, 9, 0));
        assertEquals(LocalDate.of(2050, 7, 8), AppClock.today());
    }

    @Test
    public void resetGivesTheSystemClockBack() {
        remember();
        AppClock.fix(LocalDateTime.of(1999, 1, 1, 0, 0));
        AppClock.reset();
        assertEquals(LocalDate.now().getYear(), AppClock.today().getYear());
    }
}
