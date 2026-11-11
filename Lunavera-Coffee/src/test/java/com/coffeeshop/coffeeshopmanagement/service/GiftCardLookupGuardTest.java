package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/** TODO 2A.45: unknown gift-card codes are counted per till and the lookups pause when somebody is guessing. No database. */
public class GiftCardLookupGuardTest {

    private final AtomicLong clock = new AtomicLong(9_000_000);
    private final GiftCardLookupGuard guard = new GiftCardLookupGuard(clock::get);

    @Test
    public void aFreshTillAllowsLookups() {
        assertEquals(0, guard.secondsToWait());
    }

    @Test
    public void theFifthUnknownCodeStartsAPauseAndOnlyTheFifthSaysSo() {
        for (int i = 1; i < GiftCardLookupGuard.MAX_WRONG; i++) {
            assertFalse(guard.recordWrong());
            assertEquals("no pause yet after " + i, 0, guard.secondsToWait());
            clock.addAndGet(1_000);
        }
        assertTrue(guard.recordWrong());
        assertEquals("the sixth lookup has to wait", GiftCardLookupGuard.PAUSE_SECONDS, guard.secondsToWait());
    }

    @Test
    public void thePauseCountsDownAndEnds() {
        for (int i = 0; i < GiftCardLookupGuard.MAX_WRONG; i++) guard.recordWrong();
        clock.addAndGet(20_000);
        assertEquals(GiftCardLookupGuard.PAUSE_SECONDS - 20, guard.secondsToWait());
        clock.addAndGet(GiftCardLookupGuard.PAUSE_SECONDS * 1000L - 20_000);
        assertEquals(0, guard.secondsToWait());
    }

    @Test
    public void afterThePauseFiveMoreAreAllowedBeforeTheNextOne() {
        for (int i = 0; i < GiftCardLookupGuard.MAX_WRONG; i++) guard.recordWrong();
        clock.addAndGet(GiftCardLookupGuard.PAUSE_SECONDS * 1000L + 1);
        for (int i = 1; i < GiftCardLookupGuard.MAX_WRONG; i++) assertFalse(guard.recordWrong());
        assertEquals(0, guard.secondsToWait());
        assertTrue(guard.recordWrong());
        assertTrue(guard.secondsToWait() > 0);
    }

    @Test
    public void slowGuessesAreForgottenWhenTheWindowHasPassed() {
        for (int i = 0; i < GiftCardLookupGuard.MAX_WRONG - 1; i++) guard.recordWrong();
        clock.addAndGet(GiftCardLookupGuard.WINDOW_SECONDS * 1000L + 1);
        assertFalse("the fifth in all is only the first of a new window", guard.recordWrong());
        assertEquals(1, guard.wrongInWindow());
        assertEquals(0, guard.secondsToWait());
    }
}
