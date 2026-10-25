package com.coffeeshop.coffeeshopmanagement.dao;

/**
 * Where {@code LoginThrottle} keeps the wrong-password counters so they survive a restart of the app (TODO 2A.19).
 *
 * <p>The throttle counts time with a monotonic clock (TODO 2A.37), whose zero is different in every run of the program, so
 * it cannot write its own times to a file. It hands the store its times in <b>its</b> clock together with the current reading
 * of that clock ({@code nowMono}); the store turns them into calendar times for the table and, when it reads them back
 * (possibly in another run), into the new run's clock. The throttle itself never looks at the calendar.
 *
 * <p>One row per {@code (scope, key)}: the scope separates the sign-in screen from the "current password" check of a
 * password change; the key is the throttle's bucket name (a typed name, or an account).
 */
public interface LoginAttemptStore {

    /** How many buckets the table keeps at most; more are pushed out, the least important ones first. */
    int MAX_TRACKED = 1000;

    /** A bucket with no wrong attempt for this long is forgotten. */
    long FORGET_AFTER_MILLIS = 15 * 60_000L;

    /** No stored delay is ever longer than this, whatever the table says (TODO 2A.27). */
    long MAX_DELAY_MILLIS = 30_000L;

    /**
     * The counters of one bucket, with times in the clock of the caller. {@code delayed} tells whether {@code blockedUntil}
     * means anything (the first free failures set no delay).
     */
    record Attempts(int failures, long lastFailure, boolean delayed, long blockedUntil) {
    }

    /** The counters of this bucket, translated to the clock whose reading is {@code nowMono}, or {@code null} when there are none. */
    Attempts load(String scope, String key, long nowMono);

    /** Stores the counters (times in the clock whose reading is {@code nowMono}), replacing earlier ones, and keeps the table small. */
    void save(String scope, String key, Attempts attempts, long nowMono);

    /** Forgets this bucket (a correct login). */
    void delete(String scope, String key);
}
