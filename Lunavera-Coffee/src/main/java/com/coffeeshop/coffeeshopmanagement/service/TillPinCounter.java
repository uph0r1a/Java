package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.MonotonicClock;

import java.util.function.LongSupplier;

/**
 * Wrong PINs in a row on <b>this till</b>, whoever the PIN was typed for (TODO 2A.46).
 *
 * <p>Each name has its own limit ({@link PinRules#MAX_WRONG_IN_A_ROW}), and that limit is what lets a colleague's PIN be switched off
 * by somebody else. This counter looks at the till instead: after {@value #MAX_WRONG_ON_A_TILL} wrong PINs in a row, for any names,
 * the PIN pad is closed on this till for {@value #CLOSED_MINUTES} minutes (the lock screen then asks for the password of the person
 * signed in). Ten is two names' worth, so a person who mistypes a few times is never closed out, while somebody going from name to
 * name cannot switch off the whole shift's PINs from one till.
 *
 * <p>A right PIN starts the count again, and so does a quiet quarter of an hour. The closing is worked out from the time of the last
 * wrong PIN, so it ends by itself and no stored state can lock a till for ever. The counter lives in the program's memory: one
 * program is one till, so each till counts its own (a restart needs a password sign-in anyway). Time comes from
 * {@link MonotonicClock}, so setting the Windows clock changes nothing. Pure, so it is tested without a database.
 */
public final class TillPinCounter {

    public static final int MAX_WRONG_ON_A_TILL = 10;
    public static final int CLOSED_MINUTES = 15;
    private static final long CLOSED_MILLIS = CLOSED_MINUTES * 60_000L;

    private final LongSupplier clock;
    private int wrong;
    private long lastWrong;

    public TillPinCounter() {
        this(MonotonicClock::millis);
    }

    public TillPinCounter(LongSupplier clockMillis) {
        this.clock = clockMillis;
    }

    /** Counts one wrong PIN. True exactly when this one closed the pad (the tenth in a row), so the caller writes one audit line. */
    public synchronized boolean recordWrong() {
        long now = clock.getAsLong();
        if (wrong > 0 && now - lastWrong > CLOSED_MILLIS) wrong = 0; // a quiet quarter of an hour forgets them
        wrong++;
        lastWrong = now;
        return wrong == MAX_WRONG_ON_A_TILL;
    }

    /** A right PIN: the till is in good hands again. */
    public synchronized void recordRight() {
        wrong = 0;
    }

    /** True while the PIN pad of this till is closed. */
    public synchronized boolean isClosed() {
        return wrong >= MAX_WRONG_ON_A_TILL && clock.getAsLong() - lastWrong <= CLOSED_MILLIS;
    }

    /** Wrong PINs in a row so far (for the audit line and for tests). */
    public synchronized int wrongInARow() {
        return wrong;
    }
}
