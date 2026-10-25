package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/** TODO 2.9: the "has this person been away too long" rule; TODO 2A.37: measured with a clock that only moves forward. */
public class IdleTrackerTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final IdleTracker tracker = new IdleTracker(now::get);

    private void pass(Duration time) {
        now.addAndGet(time.toMillis());
    }

    @Test
    public void notIdleBeforeTheLimitIdleAtIt() {
        Duration limit = Duration.ofMinutes(15);
        pass(Duration.ofMinutes(14).plusSeconds(59));
        assertFalse(tracker.isIdle(limit));
        pass(Duration.ofSeconds(1));
        assertTrue(tracker.isIdle(limit));
        pass(Duration.ofHours(3));
        assertTrue(tracker.isIdle(limit));
    }

    @Test
    public void activityRestartsTheClock() {
        pass(Duration.ofMinutes(14));
        tracker.touch();
        pass(Duration.ofMinutes(6));
        assertFalse(tracker.isIdle(Duration.ofMinutes(15)));
        pass(Duration.ofMinutes(9));
        assertTrue(tracker.isIdle(Duration.ofMinutes(15)));
    }

    @Test
    public void aZeroLimitNeverLogsOut() {
        pass(Duration.ofDays(30));
        assertFalse(tracker.isIdle(Duration.ZERO));
        assertFalse(tracker.isIdle(null));
        assertFalse(tracker.isIdle(Duration.ofMinutes(-5)));
    }

    @Test
    public void aReadingThatIsBehindTheLastTouchIsNotIdle() {
        now.addAndGet(-60_000);
        assertEquals(Duration.ZERO, tracker.idleFor());
        assertFalse(tracker.isIdle(Duration.ofSeconds(1)));
    }

    @Test
    public void aClockWhoseValuesAreNegativeWorksLikeAnyOther() {
        now.set(-9_000_000_000L); // System.nanoTime() can be negative: its zero is arbitrary
        IdleTracker t = new IdleTracker(now::get);
        pass(Duration.ofMinutes(14));
        assertFalse(t.isIdle(Duration.ofMinutes(15)));
        pass(Duration.ofMinutes(1));
        assertTrue(t.isIdle(Duration.ofMinutes(15)));
        assertEquals(Duration.ofMinutes(15), t.idleFor());
    }

    @Test
    public void theTrackerStartsNotIdleWhateverTheClockSays() {
        now.set(Long.MIN_VALUE / 4);
        assertEquals(Duration.ZERO, new IdleTracker(now::get).idleFor());
        now.set(Long.MAX_VALUE / 4);
        assertEquals(Duration.ZERO, new IdleTracker(now::get).idleFor());
    }

    @Test
    public void theDefaultTrackerStartsNotIdleAndGetsIdleWithRealTime() throws InterruptedException {
        IdleTracker real = new IdleTracker();
        assertFalse(real.isIdle(Duration.ofMinutes(1)));
        Thread.sleep(30);
        assertTrue("30 ms of real time is more than a 10 ms limit", real.isIdle(Duration.ofMillis(10)));
        real.touch();
        assertFalse(real.isIdle(Duration.ofSeconds(5)));
    }

    @Test
    public void theSettingIsReadInMinutes() {
        assertEquals(Duration.ofMinutes(15), IdleTracker.limitFromSetting(null));
        assertEquals(Duration.ofMinutes(15), IdleTracker.limitFromSetting(""));
        assertEquals(Duration.ofMinutes(15), IdleTracker.limitFromSetting("soon"));
        assertEquals(Duration.ofMinutes(30), IdleTracker.limitFromSetting("30"));
        assertEquals(Duration.ofSeconds(30), IdleTracker.limitFromSetting("0.5"));
        assertEquals(Duration.ofSeconds(90), IdleTracker.limitFromSetting("1,5"));
        assertEquals(Duration.ZERO, IdleTracker.limitFromSetting("0"));
        assertEquals(Duration.ZERO, IdleTracker.limitFromSetting("-5"));
    }
}
