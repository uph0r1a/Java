package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/** TODO 2A.46: wrong PINs are counted for the whole till as well as for each name. No database, no screen. */
public class TillPinCounterTest {

    private final AtomicLong clock = new AtomicLong(5_000_000);
    private final TillPinCounter till = new TillPinCounter(clock::get);

    private void wrong(int times) {
        for (int i = 0; i < times; i++) {
            till.recordWrong();
            clock.addAndGet(1_000);
        }
    }

    @Test
    public void aFreshTillIsOpen() {
        assertFalse(till.isClosed());
        assertEquals(0, till.wrongInARow());
    }

    @Test
    public void theTenthWrongPinInARowClosesThePadAndOnlyTheTenthSaysSo() {
        for (int i = 1; i < TillPinCounter.MAX_WRONG_ON_A_TILL; i++) {
            assertFalse("wrong PIN " + i, till.recordWrong());
            assertFalse(till.isClosed());
        }
        assertTrue("the tenth closes it", till.recordWrong());
        assertTrue(till.isClosed());
        assertFalse("the eleventh is not announced again", till.recordWrong());
    }

    @Test
    public void aRightPinStartsTheCountAgain() {
        wrong(9);
        till.recordRight();
        assertEquals(0, till.wrongInARow());
        wrong(9);
        assertFalse(till.isClosed());
    }

    @Test
    public void aClosedPadOpensByItselfAfterTheClosedTime() {
        wrong(TillPinCounter.MAX_WRONG_ON_A_TILL);
        assertTrue(till.isClosed());
        clock.addAndGet(TillPinCounter.CLOSED_MINUTES * 60_000L - 5_000);
        assertTrue("still closed just before the time is up", till.isClosed());
        clock.addAndGet(10_000);
        assertFalse(till.isClosed());
    }

    @Test
    public void aQuietQuarterOfAnHourForgetsEarlierWrongPins() {
        wrong(9);
        clock.addAndGet(TillPinCounter.CLOSED_MINUTES * 60_000L + 1);
        assertFalse("the tenth wrong PIN is now only the first", till.recordWrong());
        assertEquals(1, till.wrongInARow());
        assertFalse(till.isClosed());
    }

    @Test
    public void tenIsMoreThanOneNamesLimitSoOneBadTypistIsNeverClosedOut() {
        assertTrue(TillPinCounter.MAX_WRONG_ON_A_TILL > PinRules.MAX_WRONG_IN_A_ROW);
        assertTrue("but at most two names can be switched off from one till", TillPinCounter.MAX_WRONG_ON_A_TILL <= 2 * PinRules.MAX_WRONG_IN_A_ROW);
    }
}
