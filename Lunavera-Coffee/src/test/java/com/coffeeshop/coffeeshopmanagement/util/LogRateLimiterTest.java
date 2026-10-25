package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TODO 2A.25: a repeating event leaves one line a minute, and the log says how much was left out. */
public class LogRateLimiterTest {

    private final long[] now = {1_000_000};

    private LogRateLimiter limiter(int globalPerWindow) {
        return new LogRateLimiter(() -> now[0], 60_000, globalPerWindow);
    }

    @Test
    public void theSameKeyIsWrittenOnceAWindow() {
        LogRateLimiter limiter = limiter(30);
        assertEquals("", limiter.admit("wrongpw:admin"));
        for (int i = 0; i < 100; i++) {
            assertNull("repeat " + i, limiter.admit("wrongpw:admin"));
        }
    }

    @Test
    public void theLineAfterTheWindowSaysHowManyWereLeftOut() {
        LogRateLimiter limiter = limiter(30);
        limiter.admit("k");
        for (int i = 0; i < 12; i++) {
            limiter.admit("k");
        }
        now[0] += 59_999;
        assertNull("still inside the window", limiter.admit("k")); // the 13th left out
        now[0] += 1;
        assertEquals(" (+13 similar not written)", limiter.admit("k"));
        // and the count starts again
        assertNull(limiter.admit("k"));
        now[0] += 60_000;
        assertEquals(" (+1 similar not written)", limiter.admit("k"));
        now[0] += 60_000;
        assertEquals("a quiet window leaves no note", "", limiter.admit("k"));
    }

    @Test
    public void differentKeysDoNotHideEachOther() {
        LogRateLimiter limiter = limiter(30);
        assertEquals("", limiter.admit("failed:admin"));
        assertEquals("", limiter.admit("failed:staff1"));
        assertEquals("", limiter.admit("blocked:admin"));
        assertNull(limiter.admit("failed:admin"));
    }

    @Test
    public void aDifferentNameEveryTimeIsStoppedByTheOverallLimit() {
        LogRateLimiter limiter = limiter(3);
        int written = 0;
        for (int i = 0; i < 100; i++) {
            if (limiter.admit("nouser:name" + i) != null) written++;
        }
        assertEquals(3, written);
    }

    @Test
    public void whatTheOverallLimitHeldBackIsReportedInTheNextWindow() {
        LogRateLimiter limiter = limiter(2);
        limiter.admit("a");
        limiter.admit("b");
        assertNull(limiter.admit("c"));
        assertNull(limiter.admit("d"));
        assertNull(limiter.admit("e"));
        now[0] += 60_000;
        assertEquals(" (+3 similar not written)", limiter.admit("f"));
        assertEquals("", limiter.admit("g"));
    }

    @Test
    public void aKeyHeldBackByTheOverallLimitIsNotPenalisedForIt() {
        LogRateLimiter limiter = limiter(1);
        limiter.admit("a");
        assertNull(limiter.admit("b")); // overall limit; "b" never had a line, so it has no window of its own
        now[0] += 60_000;
        assertTrue(limiter.admit("b") != null);
    }

    @Test
    public void theTableOfKeysStaysBounded() {
        LogRateLimiter limiter = new LogRateLimiter(() -> now[0], 60_000, 1_000_000);
        for (int i = 0; i < LogRateLimiter.MAX_KEYS * 3; i++) {
            limiter.admit("k" + i);
            assertTrue(limiter.trackedKeys() <= LogRateLimiter.MAX_KEYS);
        }
        // entries whose window is over are what goes first
        now[0] += 60_000;
        limiter.admit("fresh");
        assertTrue(limiter.trackedKeys() <= LogRateLimiter.MAX_KEYS);
    }

    @Test
    public void aClockThatGoesBackDoesNotSilenceTheLogForGood() {
        LogRateLimiter limiter = limiter(30);
        assertEquals("", limiter.admit("k"));
        now[0] -= 500_000; // somebody set the computer's clock back
        assertTrue("a window that starts in the future is treated as over", limiter.admit("k") != null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aWindowOfZeroIsRefused() {
        new LogRateLimiter(() -> 0, 0, 5);
    }

    @Test(expected = IllegalArgumentException.class)
    public void anOverallLimitOfZeroIsRefused() {
        new LogRateLimiter(() -> 0, 1000, 0);
    }
}
