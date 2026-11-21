package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.78, mutation sweep 2026-11-16 (first batch): the exact moments at which the two wrong-entry counters forget or close. The
 * earlier tests used times far from the edges, so {@code >} against {@code >=} went unnoticed.
 */
public class CounterEdgesTest {

    private static final long MINUTE = 60_000L;

    @Test
    public void aGiftCardCodeCountsInTheSameWindowUpToExactlyASecondMinute() {
        AtomicLong now = new AtomicLong(1_000);
        GiftCardLookupGuard guard = new GiftCardLookupGuard(now::get);
        for (int i = 0; i < GiftCardLookupGuard.MAX_WRONG - 1; i++) assertFalse(guard.recordWrong());
        now.addAndGet(GiftCardLookupGuard.WINDOW_SECONDS * 1000L);
        assertTrue("exactly 60 seconds after the first: still the same window, so this is the fifth", guard.recordWrong());
    }

    @Test
    public void aGiftCardCodeOneMillisecondLaterStartsANewWindow() {
        AtomicLong now = new AtomicLong(1_000);
        GiftCardLookupGuard guard = new GiftCardLookupGuard(now::get);
        for (int i = 0; i < GiftCardLookupGuard.MAX_WRONG - 1; i++) assertFalse(guard.recordWrong());
        now.addAndGet(GiftCardLookupGuard.WINDOW_SECONDS * 1000L + 1);
        assertFalse("the window is over, this is the first of a new one", guard.recordWrong());
        assertEquals(1, guard.wrongInWindow());
    }

    @Test
    public void theGiftCardPauseEndsByItselfAndNeverEarly() {
        AtomicLong now = new AtomicLong(5_000);
        GiftCardLookupGuard guard = new GiftCardLookupGuard(now::get);
        for (int i = 0; i < GiftCardLookupGuard.MAX_WRONG; i++) guard.recordWrong();
        assertEquals(GiftCardLookupGuard.PAUSE_SECONDS, guard.secondsToWait());
        now.addAndGet(GiftCardLookupGuard.PAUSE_SECONDS * 1000L - 1);
        assertEquals("one millisecond left rounds up to one second", 1, guard.secondsToWait());
        now.addAndGet(1);
        assertEquals(0, guard.secondsToWait());
    }

    @Test
    public void theTillPadForgetsTheWrongPinsAfterMoreThanAQuarterOfAnHourOnly() {
        AtomicLong now = new AtomicLong(1_000_000);
        TillPinCounter counter = new TillPinCounter(now::get);
        for (int i = 0; i < 5; i++) counter.recordWrong();
        now.addAndGet(TillPinCounter.CLOSED_MINUTES * MINUTE);
        counter.recordWrong();
        assertEquals("exactly a quarter of an hour is not yet quiet enough to forget", 6, counter.wrongInARow());
        now.addAndGet(TillPinCounter.CLOSED_MINUTES * MINUTE + 1);
        counter.recordWrong();
        assertEquals("one millisecond more and they are forgotten", 1, counter.wrongInARow());
    }

    @Test
    public void theTillPadClosesOnTheTenthWrongPinAndOpensAfterAQuarterOfAnHour() {
        AtomicLong now = new AtomicLong(1_000_000);
        TillPinCounter counter = new TillPinCounter(now::get);
        for (int i = 0; i < TillPinCounter.MAX_WRONG_ON_A_TILL - 1; i++) assertFalse(counter.recordWrong());
        assertFalse("nine wrong PINs: still open", counter.isClosed());
        assertTrue("the tenth closes it", counter.recordWrong());
        assertTrue(counter.isClosed());
        now.addAndGet(TillPinCounter.CLOSED_MINUTES * MINUTE);
        assertTrue("exactly a quarter of an hour later it is still closed", counter.isClosed());
        now.addAndGet(1);
        assertFalse("one millisecond later it is open", counter.isClosed());
    }

    @Test
    public void aRightPinStartsTheCountAgain() {
        TillPinCounter counter = new TillPinCounter(() -> 1_000L);
        counter.recordWrong();
        counter.recordWrong();
        counter.recordRight();
        assertEquals(0, counter.wrongInARow());
    }
}
