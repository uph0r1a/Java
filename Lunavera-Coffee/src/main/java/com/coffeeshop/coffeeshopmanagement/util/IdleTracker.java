package com.coffeeshop.coffeeshopmanagement.util;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Remembers when the person last touched the app and decides whether they have been away too long (TODO 2.9). No JavaFX
 * here, so it is unit-tested.
 *
 * <p>Time comes from {@link MonotonicClock}, not from the computer's clock (TODO 2A.37): setting the Windows clock forward
 * must not log the person out at once, and setting it back must not keep the till open for ever.
 */
public final class IdleTracker {

    public static final double DEFAULT_MINUTES = 15;

    private final LongSupplier clockMillis;
    private volatile long lastActivity;

    public IdleTracker() {
        this(MonotonicClock::millis);
    }

    /** @param clockMillis a clock that only moves forward (a test passes its own) */
    public IdleTracker(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
        this.lastActivity = clockMillis.getAsLong();
    }

    public void touch() {
        lastActivity = clockMillis.getAsLong();
    }

    public Duration idleFor() {
        long idle = clockMillis.getAsLong() - lastActivity;
        return idle <= 0 ? Duration.ZERO : Duration.ofMillis(idle);
    }

    /** True when the idle time has reached the limit. A zero or negative limit means "never log out". */
    public boolean isIdle(Duration limit) {
        if (limit == null || limit.isZero() || limit.isNegative()) return false;
        return idleFor().compareTo(limit) >= 0;
    }

    /** The limit from a setting such as "15" or "0.5" (minutes). Missing or unreadable gives the default; 0 or less turns it off. */
    public static Duration limitFromSetting(String setting) {
        double minutes = DEFAULT_MINUTES;
        if (setting != null && !setting.isBlank()) {
            try {
                minutes = Double.parseDouble(setting.trim().replace(',', '.'));
            } catch (NumberFormatException ignored) {
                minutes = DEFAULT_MINUTES;
            }
        }
        if (Double.isNaN(minutes) || minutes <= 0) return Duration.ZERO;
        return Duration.ofMillis(Math.round(minutes * 60_000));
    }
}
