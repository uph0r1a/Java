package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** TODO 2A.37: what measures an interval must not follow the computer's clock. */
public class MonotonicClockTest {

    private static final Path MAIN = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement");

    @Test
    public void theValueNeverGoesBack() {
        long previous = MonotonicClock.millis();
        for (int i = 0; i < 100_000; i++) {
            long current = MonotonicClock.millis();
            assertTrue("went back from " + previous + " to " + current, current >= previous);
            previous = current;
        }
    }

    @Test
    public void itMovesAtTheSpeedOfRealTime() throws InterruptedException {
        long before = MonotonicClock.millis();
        Thread.sleep(60);
        long passed = MonotonicClock.millis() - before;
        assertTrue("60 ms of sleep showed as " + passed + " ms", passed >= 50 && passed < 5_000);
    }

    /**
     * The point of 2A.37 is a choice of clock, and a choice is easy to undo by accident: this test fails the day someone
     * puts {@code System.currentTimeMillis()} or {@code Instant.now()} back into one of the classes that decide "how long ago".
     */
    @Test
    public void theClassesThatMeasureAnIntervalDoNotReadTheComputersClock() throws IOException {
        for (String file : new String[]{"service/LoginThrottle.java", "util/IdleTracker.java", "util/LogRateLimiter.java"}) {
            String source = Files.readString(MAIN.resolve(file));
            assertFalse(file + " must use MonotonicClock, not System.currentTimeMillis()", source.contains("currentTimeMillis"));
            assertFalse(file + " must use MonotonicClock, not Instant.now()", source.contains("Instant.now"));
            assertFalse(file + " must use MonotonicClock, not LocalDateTime.now()", source.contains("LocalDateTime.now"));
        }
        assertTrue("IdleLogout asks the tracker, not the clock", !Files.readString(MAIN.resolve("util/IdleLogout.java")).contains("Instant.now"));
    }
}
