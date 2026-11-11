package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

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

    /** Collects what IdleTracker writes to the log, so a test can see the WARNING (TODO 3.79). */
    private static final class Capture extends Handler {
        final List<String> warnings = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (record.getLevel().intValue() >= Level.WARNING.intValue()) warnings.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private Capture capture;
    private Logger logger;

    @Before
    public void listenToTheLog() {
        IdleTracker.forgetWarnings();
        logger = Logger.getLogger(IdleTracker.class.getName());
        capture = new Capture();
        logger.addHandler(capture);
    }

    @After
    public void stopListening() {
        logger.removeHandler(capture);
        IdleTracker.forgetWarnings();
    }

    @Test
    public void theSettingIsReadInMinutes() {
        assertEquals(Duration.ofMinutes(15), IdleTracker.limitFromSetting(null));
        assertEquals(Duration.ofMinutes(15), IdleTracker.limitFromSetting(""));
        assertEquals(Duration.ofMinutes(15), IdleTracker.limitFromSetting("   "));
        assertEquals(Duration.ofMinutes(30), IdleTracker.limitFromSetting("30"));
        assertEquals(Duration.ofMinutes(30), IdleTracker.limitFromSetting(" 30 "));
        assertEquals(Duration.ofSeconds(90), IdleTracker.limitFromSetting("1,5"));
        assertEquals(Duration.ofSeconds(90), IdleTracker.limitFromSetting("1.5"));
        assertEquals(Duration.ofMinutes(1), IdleTracker.limitFromSetting("1"));
        assertEquals("a day is the longest", Duration.ofMinutes(1440), IdleTracker.limitFromSetting("1440"));
        assertTrue("a missing or good value writes no warning", capture.warnings.isEmpty());
    }

    @Test
    public void aValueThatMakesNoSenseGivesTheDefaultAndAWarningNamingIt() {
        String[] refused = {"NaN", "-5", "Infinity", "-Infinity", "1e9", "abc", "soon", "0,5", "0.5", "1441", "999999", "5d", "0x10", "1 5", "--1"};
        for (String value : refused) {
            capture.warnings.clear();
            IdleTracker.forgetWarnings();
            assertEquals("'" + value + "' must not turn the lock off or make it huge", Duration.ofMinutes(15), IdleTracker.limitFromSetting(value));
            assertEquals("one warning for '" + value + "'", 1, capture.warnings.size());
            assertTrue("the warning names the refused value " + value, capture.warnings.get(0).contains("\"" + value + "\""));
        }
    }

    @Test
    public void zeroTurnsTheAutoLockOffOnPurposeAndIsLoggedAsAWarning() {
        assertEquals(Duration.ZERO, IdleTracker.limitFromSetting("0"));
        assertEquals(1, capture.warnings.size());
        assertTrue(capture.warnings.get(0).contains("switched off on purpose"));
        assertEquals(Duration.ZERO, IdleTracker.limitFromSetting("0.0"));
        assertEquals(Duration.ZERO, IdleTracker.limitFromSetting("00"));
    }

    @Test
    public void theSameWarningIsNotWrittenEveryTimeTheLimitIsRead() {
        for (int i = 0; i < 50; i++) IdleTracker.limitFromSetting("abc"); // the limit is read every ten seconds
        assertEquals(1, capture.warnings.size());
        IdleTracker.limitFromSetting("xyz");
        assertEquals("another bad value is another warning", 2, capture.warnings.size());
    }

    @Test
    public void aVeryLongValueIsCutInTheLog() {
        IdleTracker.limitFromSetting("9".repeat(500));
        assertEquals(1, capture.warnings.size());
        assertTrue(capture.warnings.get(0).length() < 250);
    }

    @Test
    public void theOwnersSettingFollowsTheSameRules() {
        assertEquals(Duration.ofMinutes(45), IdleTracker.limit(null, 45));
        assertEquals(Duration.ofMinutes(1440), IdleTracker.limit(" ", 1440));
        assertTrue(capture.warnings.isEmpty());
        assertEquals("0 in Settings is off on purpose", Duration.ZERO, IdleTracker.limit(null, 0));
        assertEquals(1, capture.warnings.size());
        assertEquals("a negative value is not 'off'", Duration.ofMinutes(15), IdleTracker.limit(null, -5));
        assertEquals("a value above a day is not taken", Duration.ofMinutes(15), IdleTracker.limit("", 100_000));
        assertEquals(3, capture.warnings.size());
        assertEquals("the override wins, and is checked too", Duration.ofMinutes(15), IdleTracker.limit("NaN", 45));
    }
}
