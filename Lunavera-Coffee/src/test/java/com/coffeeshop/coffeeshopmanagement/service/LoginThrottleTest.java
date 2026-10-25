package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoginThrottleTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final LoginThrottle throttle = new LoginThrottle(now::get);

    private void fail(String user, int times) {
        for (int i = 0; i < times; i++) throttle.recordFailure(user);
    }

    @Test
    public void theFirstTwoWrongAttemptsAreFree() {
        fail("admin", 2);
        assertEquals(0, throttle.secondsUntilAllowed("admin"));
    }

    @Test
    public void afterThatEachWrongAttemptWaitsLonger() {
        fail("admin", 2);
        long[] expected = {1, 2, 4, 8, 16, 30, 30, 30};
        for (long seconds : expected) {
            throttle.recordFailure("admin");
            assertEquals(seconds, throttle.secondsUntilAllowed("admin"));
            now.addAndGet(seconds * 1000);
            assertEquals(0, throttle.secondsUntilAllowed("admin"));
        }
    }

    @Test
    public void theWaitNeverLastsMoreThanThirtySecondsEvenAfterManyFailures() {
        fail("admin", 500);
        assertEquals(30, throttle.secondsUntilAllowed("admin"));
        now.addAndGet(30_000);
        assertEquals(0, throttle.secondsUntilAllowed("admin"));
    }

    @Test
    public void aQuietQuarterOfAnHourForgetsOldFailures() {
        fail("admin", 7);
        now.addAndGet(16 * 60_000);
        throttle.recordFailure("admin");
        assertEquals(0, throttle.secondsUntilAllowed("admin"));
    }

    @Test
    public void aSuccessfulLoginResetsTheCounters() {
        fail("admin", 4);
        throttle.recordSuccess("admin");
        fail("admin", 2);
        assertEquals(0, throttle.secondsUntilAllowed("admin"));
    }

    @Test
    public void usernamesAreIndependentAndCaseInsensitive() {
        fail("Admin", 3);
        assertEquals(1, throttle.secondsUntilAllowed(" admin "));
        assertEquals(0, throttle.secondsUntilAllowed("someone-else"));
    }

    @Test
    public void everySpellingOfOnePhoneNumberSharesOneCounter() {
        // TODO 2A.28: these five all sign in to the same account, so they must not each get their own free tries.
        String[] spellings = {"0901111222", "+84 901 111 222", "84901111222", "0901.111.222", "(0901) 111-222"};
        for (String spelling : spellings) throttle.recordFailure(spelling);
        // Five wrong tries in all: two free, then 1, 2 and 4 seconds, as if one text had been typed five times.
        for (String spelling : spellings) {
            assertEquals(spelling, 4, throttle.secondsUntilAllowed(spelling));
        }
    }

    @Test
    public void anAccountHasOneWaitWhateverWasTypedToReachIt() {
        throttle.recordFailureForAccount(7);
        throttle.recordFailureForAccount(7);
        assertEquals(0, throttle.secondsUntilAllowedForAccount(7));
        throttle.recordFailureForAccount(7);
        assertEquals(1, throttle.secondsUntilAllowedForAccount(7));
        assertEquals("another account is not slowed", 0, throttle.secondsUntilAllowedForAccount(8));
        throttle.recordSuccessForAccount(7);
        assertEquals(0, throttle.secondsUntilAllowedForAccount(7));
    }

    @Test
    public void aTypedTextCanNeverBeTheBucketOfAnAccount() {
        for (int i = 0; i < 6; i++) throttle.recordFailure("\0account:7");
        assertEquals(0, throttle.secondsUntilAllowedForAccount(7));
        for (int i = 0; i < 6; i++) throttle.recordFailure("account:7");
        assertEquals(0, throttle.secondsUntilAllowedForAccount(7));
    }

    // ------------------------------------------------ TODO 2A.31: a full table must not wipe the delay of the attacked name

    private void flood(int names, String prefix) {
        for (int i = 0; i < names; i++) {
            throttle.recordFailure(prefix + i);
        }
    }

    @Test
    public void typingManyDifferentNamesDoesNotWipeTheDelayOfTheNameBeingAttacked() {
        fail("admin", 6);
        long waitBefore = throttle.secondsUntilAllowed("admin");
        assertTrue(waitBefore > 0);
        flood(LoginThrottle.MAX_TRACKED * 3, "guess");
        assertEquals("admin is still waiting", waitBefore, throttle.secondsUntilAllowed("admin"));
        assertTrue(throttle.trackedNames() <= LoginThrottle.MAX_TRACKED);
    }

    @Test
    public void aFullTablePushesOutOneEntryAndNeverEverything() {
        flood(LoginThrottle.MAX_TRACKED, "n");
        assertEquals(LoginThrottle.MAX_TRACKED, throttle.trackedNames());
        throttle.recordFailure("one-more");
        assertEquals(LoginThrottle.MAX_TRACKED, throttle.trackedNames());
    }

    @Test
    public void aNameAlreadyInTheFullTableKeepsCountingInsteadOfClearingTheTable() {
        throttle.recordFailure("admin");
        throttle.recordFailure("admin");
        flood(LoginThrottle.MAX_TRACKED - 1, "n");
        assertEquals(LoginThrottle.MAX_TRACKED, throttle.trackedNames());
        throttle.recordFailure("admin"); // the third: the first delay
        assertEquals(1, throttle.secondsUntilAllowed("admin"));
        assertEquals("nothing else was lost", LoginThrottle.MAX_TRACKED, throttle.trackedNames());
    }

    @Test
    public void whenThereIsNoRoomTheNameWithFewerWrongAttemptsGoesFirst() {
        fail("medium", 2); // two free attempts used
        fail("strong", 6); // a delay is running
        flood(LoginThrottle.MAX_TRACKED - 2, "n");
        flood(LoginThrottle.MAX_TRACKED, "later"); // a thousand more names, one wrong attempt each
        assertTrue("the delay is still running", throttle.secondsUntilAllowed("strong") > 0);
        throttle.recordFailure("medium"); // its third wrong attempt: delayed only if its two earlier ones were remembered
        assertEquals(1, throttle.secondsUntilAllowed("medium"));
    }

    @Test
    public void whenEveryoneIsWaitingTheOneThatWaitsLeastGoesFirst() {
        for (int i = 0; i < LoginThrottle.MAX_TRACKED; i++) {
            fail("w" + i, 3); // all delayed 1 second
        }
        now.addAndGet(500);
        fail("newcomer", 3);
        assertEquals(LoginThrottle.MAX_TRACKED, throttle.trackedNames());
        assertTrue("the newcomer is remembered", throttle.secondsUntilAllowed("newcomer") > 0);
    }

    @Test
    public void entriesQuietForAQuarterOfAnHourAreDroppedFirst() {
        flood(LoginThrottle.MAX_TRACKED - 1, "old");
        now.addAndGet(10 * 60_000);
        throttle.recordFailure("recent");
        now.addAndGet(6 * 60_000); // the old ones have been quiet for 16 minutes, "recent" for 6
        throttle.recordFailure("newcomer");
        assertEquals("the 999 quiet entries went, the two recent ones stayed", 2, throttle.trackedNames());
    }

    // ------------------------------------------------ TODO 2A.37: the clock's zero is arbitrary, and it is only a measuring stick

    @Test
    public void aClockWhoseValuesAreNegativeStillGivesTheSameDelays() {
        now.set(-5_000_000_000L); // System.nanoTime() can be negative: its zero is arbitrary
        fail("admin", 2);
        assertEquals("free attempts set no delay", 0, throttle.secondsUntilAllowed("admin"));
        throttle.recordFailure("admin");
        assertEquals(1, throttle.secondsUntilAllowed("admin"));
        now.addAndGet(1_000);
        assertEquals(0, throttle.secondsUntilAllowed("admin"));
        fail("someone", 1);
        assertEquals(0, throttle.secondsUntilAllowed("someone"));
    }

    @Test
    public void aQuietQuarterOfAnHourIsMeasuredWithTheSameClock() {
        now.set(-1_000_000L);
        fail("admin", 7);
        now.addAndGet(16 * 60_000);
        throttle.recordFailure("admin");
        assertEquals(0, throttle.secondsUntilAllowed("admin"));
    }
}
