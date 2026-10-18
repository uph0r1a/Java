package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

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
}
