package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.*;

/** TODO 2.9: the "has this person been away too long" rule. */
public class IdleTrackerTest {

    private static final Instant T0 = Instant.parse("2026-10-11T08:00:00Z");

    @Test
    public void notIdleBeforeTheLimitIdleAtIt() {
        IdleTracker t = new IdleTracker();
        t.touch(T0);
        Duration limit = Duration.ofMinutes(15);
        assertFalse(t.isIdle(T0.plus(Duration.ofMinutes(14)).plusSeconds(59), limit));
        assertTrue(t.isIdle(T0.plus(Duration.ofMinutes(15)), limit));
        assertTrue(t.isIdle(T0.plus(Duration.ofHours(3)), limit));
    }

    @Test
    public void activityRestartsTheClock() {
        IdleTracker t = new IdleTracker();
        t.touch(T0);
        t.touch(T0.plus(Duration.ofMinutes(14)));
        assertFalse(t.isIdle(T0.plus(Duration.ofMinutes(20)), Duration.ofMinutes(15)));
        assertTrue(t.isIdle(T0.plus(Duration.ofMinutes(29)), Duration.ofMinutes(15)));
    }

    @Test
    public void aZeroLimitNeverLogsOut() {
        IdleTracker t = new IdleTracker();
        t.touch(T0);
        assertFalse(t.isIdle(T0.plus(Duration.ofDays(30)), Duration.ZERO));
        assertFalse(t.isIdle(T0.plus(Duration.ofDays(30)), null));
    }

    @Test
    public void aClockThatMovedBackIsNotIdle() {
        IdleTracker t = new IdleTracker();
        t.touch(T0);
        assertEquals(Duration.ZERO, t.idleFor(T0.minusSeconds(60)));
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
