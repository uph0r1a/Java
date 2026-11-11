package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.MonotonicClock;

import java.util.function.LongSupplier;

/**
 * Counts unknown gift-card codes typed on <b>this till</b> and pauses the lookups when somebody is guessing (TODO 2A.45).
 *
 * <p>A card is a bearer instrument: whoever knows the code can spend it. The code space (about 31^8) makes guessing by hand
 * hopeless, but nothing noticed it being tried, unlike logins and PINs. Here {@value #MAX_WRONG} unknown codes within
 * {@value #WINDOW_SECONDS} seconds start a pause of {@value #PAUSE_SECONDS} seconds in which every lookup answers "wait" without
 * touching the database. A <em>right</em> code does not clear the count (otherwise a known card typed between guesses would),
 * and a mistyped code counts like any other: a cashier who mistypes five times waits a minute, which is the price of the rule.
 * The counter is in memory (one program is one till) and reads {@link MonotonicClock}, so changing the Windows clock changes
 * nothing. Pure, so it is tested without a database.
 */
public final class GiftCardLookupGuard {

    public static final int MAX_WRONG = 5;
    public static final int WINDOW_SECONDS = 60;
    public static final int PAUSE_SECONDS = 60;

    private final LongSupplier clock;
    private int wrong;
    private long windowStart;
    private long pausedUntil;
    private boolean paused;

    public GiftCardLookupGuard() {
        this(MonotonicClock::millis);
    }

    public GiftCardLookupGuard(LongSupplier clockMillis) {
        this.clock = clockMillis;
    }

    /** Seconds a lookup must still wait, or 0 when it may go ahead. */
    public synchronized long secondsToWait() {
        if (!paused) return 0;
        long left = pausedUntil - clock.getAsLong();
        if (left <= 0) {
            paused = false;
            return 0;
        }
        return (left + 999) / 1000;
    }

    /** Counts one unknown code. True exactly when this one started a pause, so the caller writes one audit line. */
    public synchronized boolean recordWrong() {
        long now = clock.getAsLong();
        if (wrong == 0 || now - windowStart > WINDOW_SECONDS * 1000L) {
            wrong = 0;
            windowStart = now;
        }
        wrong++;
        if (wrong < MAX_WRONG) return false;
        wrong = 0;
        paused = true;
        pausedUntil = now + PAUSE_SECONDS * 1000L;
        return true;
    }

    /** Unknown codes counted in the current window (for tests). */
    public synchronized int wrongInWindow() {
        return wrong;
    }
}
