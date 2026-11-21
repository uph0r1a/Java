package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

/**
 * TODO 6.78, mutation sweep 2026-11-16 (first batch): the exact edges of the login delay. The first two wrong passwords are free, a
 * name is forgotten only after MORE than a quarter of an hour, and a full table pushes out the weakest name by a fixed order. The
 * earlier tests used values far from every edge.
 */
public class LoginThrottleEdgesTest {

    private static final long FORGET_AFTER = 15 * 60_000L; // LoginThrottle.FORGET_AFTER_MILLIS

    private final AtomicLong now = new AtomicLong(10_000_000L);
    private final LoginThrottle throttle = new LoginThrottle(now::get);

    private void fail(String name, int times) {
        for (int i = 0; i < times; i++) throttle.recordFailure(name);
    }

    @Test
    public void theFirstTwoWrongPasswordsAreFreeAndTheThirdWaitsOneSecond() {
        fail("alice", LoginThrottle.FREE_FAILURES);
        assertEquals(0, throttle.secondsUntilAllowed("alice"));
        fail("alice", 1);
        assertEquals(1, throttle.secondsUntilAllowed("alice"));
    }

    @Test
    public void aWaitEndsAtItsLastMillisecondAndNotBefore() {
        fail("alice", LoginThrottle.FREE_FAILURES + 1);
        now.addAndGet(999);
        assertEquals("one millisecond is left and counts as a second", 1, throttle.secondsUntilAllowed("alice"));
        now.addAndGet(1);
        assertEquals(0, throttle.secondsUntilAllowed("alice"));
    }

    @Test
    public void aNameIsNotForgottenAtExactlyAQuarterOfAnHour() {
        fail("alice", 2);
        now.addAndGet(FORGET_AFTER);
        fail("alice", 1);
        assertEquals("the third wrong password still counts", 1, throttle.secondsUntilAllowed("alice"));
    }

    @Test
    public void aNameIsForgottenOneMillisecondAfterThat() {
        fail("alice", 2);
        now.addAndGet(FORGET_AFTER + 1);
        fail("alice", 1);
        assertEquals("a new first wrong password", 0, throttle.secondsUntilAllowed("alice"));
    }

    private void fillWithQuietNames() {
        for (int i = 0; i < LoginThrottle.MAX_TRACKED; i++) fail("name" + i, 1);
        assertEquals(LoginThrottle.MAX_TRACKED, throttle.trackedNames());
    }

    @Test
    public void aFullTableKeepsEveryNameThatFailedExactlyAQuarterOfAnHourAgo() {
        fillWithQuietNames();
        now.addAndGet(FORGET_AFTER);
        fail("new", 1);
        assertEquals("nothing is quiet long enough, so one weakest name made room", LoginThrottle.MAX_TRACKED, throttle.trackedNames());
    }

    @Test
    public void aFullTableDropsEveryNameQuietForMoreThanAQuarterOfAnHour() {
        fillWithQuietNames();
        now.addAndGet(FORGET_AFTER + 1);
        fail("new", 1);
        assertEquals("only the new name is left", 1, throttle.trackedNames());
    }

    @Test
    public void ofTwoEqualNamesTheOneThatFailedLongestAgoIsPushedOut() {
        for (int i = 0; i < LoginThrottle.MAX_TRACKED; i++) {
            now.addAndGet(10);
            fail("name" + i, 2);
        }
        now.addAndGet(10);
        fail("new", 1);
        assertEquals(LoginThrottle.MAX_TRACKED, throttle.trackedNames());
        fail("name0", 1);
        assertEquals("name0 was the oldest and is gone: this is its first failure again", 0, throttle.secondsUntilAllowed("name0"));
        fail("name999", 1);
        assertEquals("name999 is the newest and stayed: this is its third failure", 1, throttle.secondsUntilAllowed("name999"));
    }

    @Test
    public void aNameWithFewerWrongPasswordsIsPushedOutBeforeOneWithMoreEvenIfItFailedLater() {
        for (int i = 0; i < LoginThrottle.MAX_TRACKED - 1; i++) {
            now.addAndGet(10);
            fail("name" + i, 2);
        }
        now.addAndGet(10);
        fail("few", 1);
        now.addAndGet(10);
        fail("new", 1);
        assertEquals(LoginThrottle.MAX_TRACKED, throttle.trackedNames());
        fail("few", 2);
        assertEquals("few was pushed out: two failures are free again", 0, throttle.secondsUntilAllowed("few"));
        fail("name0", 1);
        assertEquals("name0 has two failures and stayed: this is its third", 1, throttle.secondsUntilAllowed("name0"));
    }

    @Test
    public void aNameThatIsWaitingRightNowIsPushedOutLast() {
        fail("target", 4);
        for (int i = 0; i < LoginThrottle.MAX_TRACKED - 1; i++) fail("name" + i, 1);
        assertEquals(LoginThrottle.MAX_TRACKED, throttle.trackedNames());
        for (int i = 0; i < 5; i++) fail("flood" + i, 1);
        assertEquals(LoginThrottle.MAX_TRACKED, throttle.trackedNames());
        assertEquals("the wait of target still runs (2 seconds for the fourth failure)", 2, throttle.secondsUntilAllowed("target"));
    }
}
