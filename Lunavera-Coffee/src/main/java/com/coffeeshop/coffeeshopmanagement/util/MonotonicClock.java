package com.coffeeshop.coffeeshopmanagement.util;

/**
 * Milliseconds that only ever move forward at the pace of real time, and do not follow the computer's clock (TODO 2A.37).
 *
 * <p>Anything that measures "how long ago" or "how long still" must use this and not {@code System.currentTimeMillis()} or
 * {@code Instant.now()}: somebody at the till can set the Windows clock forward (and a login delay is gone, an idle logout
 * fires at once) or back (and a 30-second wait becomes an hour, an idle logout never comes), and the clock also jumps by
 * itself when it is synchronised or when summer time changes. The value has no meaning as a date, and its zero is
 * arbitrary (it can even be negative): only the difference between two readings means anything.
 */
public final class MonotonicClock {

    private MonotonicClock() {
    }

    /** Whole milliseconds of {@link System#nanoTime()}; {@code floorDiv} so the value stays continuous when it is below zero. */
    public static long millis() {
        return Math.floorDiv(System.nanoTime(), 1_000_000L);
    }
}
