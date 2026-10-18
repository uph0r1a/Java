package com.coffeeshop.coffeeshopmanagement.util;

import java.time.Duration;
import java.time.Instant;

/** Remembers when the person last touched the app and decides whether they have been away too long (TODO 2.9). No JavaFX here, so it is unit-tested. */
public final class IdleTracker {

    public static final double DEFAULT_MINUTES = 15;

    private volatile Instant lastActivity;

    public IdleTracker() {
        this.lastActivity = Instant.now();
    }

    public void touch() {
        touch(Instant.now());
    }

    public void touch(Instant when) {
        lastActivity = when;
    }

    public Duration idleFor(Instant now) {
        Duration idle = Duration.between(lastActivity, now);
        return idle.isNegative() ? Duration.ZERO : idle;
    }

    /** True when the idle time has reached the limit. A zero or negative limit means "never log out". */
    public boolean isIdle(Instant now, Duration limit) {
        if (limit == null || limit.isZero() || limit.isNegative()) return false;
        return idleFor(now).compareTo(limit) >= 0;
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
