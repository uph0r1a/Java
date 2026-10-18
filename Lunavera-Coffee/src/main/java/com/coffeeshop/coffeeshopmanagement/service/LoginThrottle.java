package com.coffeeshop.coffeeshopmanagement.service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Slows down password guessing without ever locking anybody out: the first {@value #FREE_FAILURES}
 * wrong attempts in a row for a username are free (typos), then every further wrong attempt makes
 * the next one wait longer: 1, 2, 4, 8, 16 and then 30 seconds at most. A correct login resets it,
 * and so does {@value #FORGET_AFTER_MILLIS} ms without any wrong attempt. State lives in memory
 * for the running app (surviving a restart needs a table, planned after the MariaDB merge).
 *
 * <p>Usernames that do not exist are throttled exactly like real ones, so the throttle itself does
 * not reveal which usernames exist.
 */
public final class LoginThrottle {

    static final int FREE_FAILURES = 2;
    private static final long BASE_DELAY_MILLIS = 1_000;
    private static final long MAX_DELAY_MILLIS = 30_000;
    private static final long FORGET_AFTER_MILLIS = 15 * 60_000;
    private static final int MAX_TRACKED = 1000;

    private static final class State {
        int failures;
        long lastFailure;
        long blockedUntil;
    }

    private final LongSupplier clock;
    private final Map<String, State> states = new HashMap<>();

    public LoginThrottle() {
        this(System::currentTimeMillis);
    }

    public LoginThrottle(LongSupplier clockMillis) {
        this.clock = clockMillis;
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    /** Seconds the caller must still wait for this username, or 0 when an attempt is allowed. */
    public synchronized long secondsUntilAllowed(String username) {
        State state = states.get(key(username));
        if (state == null) return 0;
        long remaining = state.blockedUntil - clock.getAsLong();
        return remaining <= 0 ? 0 : (remaining + 999) / 1000;
    }

    public synchronized void recordFailure(String username) {
        if (states.size() >= MAX_TRACKED) {
            states.clear(); // bounded memory; an attacker flooding names only resets everyone's counters
        }
        State state = states.computeIfAbsent(key(username), k -> new State());
        long now = clock.getAsLong();
        if (now - state.lastFailure > FORGET_AFTER_MILLIS) state.failures = 0;
        state.lastFailure = now;
        state.failures++;
        int extra = state.failures - FREE_FAILURES;
        if (extra > 0) {
            long delay = Math.min(MAX_DELAY_MILLIS, BASE_DELAY_MILLIS << Math.min(extra - 1, 5));
            state.blockedUntil = now + delay;
        }
    }

    public synchronized void recordSuccess(String username) {
        states.remove(key(username));
    }
}
