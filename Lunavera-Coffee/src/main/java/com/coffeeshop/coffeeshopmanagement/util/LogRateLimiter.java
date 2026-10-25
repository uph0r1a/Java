package com.coffeeshop.coffeeshopmanagement.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Keeps a repeating event from filling a log (TODO 2A.25): a wrong password typed 100 times a minute, or Enter held down on the
 * login screen, must leave one line, not a hundred thousand.
 *
 * <p>Two limits apply together:
 * <ul>
 *   <li><b>per key</b> (for example "failed login for admin"): at most one line per window (a minute);</li>
 *   <li><b>overall</b>: at most {@code globalPerWindow} lines per window whatever the keys are, so typing a different user
 *       name each time cannot flood the log either.</li>
 * </ul>
 * What was left out is counted and reported on the next line that is written ({@code (+12 similar not written)}), so the
 * log still shows that a flood happened and how big it was.
 *
 * <p>Thread-safe. The clock is a parameter so a test can move time without sleeping.
 */
public final class LogRateLimiter {

    /** Most keys remembered at once; when full, entries whose window is over are dropped, and if that is not enough all are. */
    static final int MAX_KEYS = 1000;

    private static final class Entry {
        long windowStart;
        long suppressed;
    }

    private final LongSupplier clockMillis;
    private final long windowMillis;
    private final int globalPerWindow;
    private final Map<String, Entry> entries = new HashMap<>();
    private long globalWindowStart;
    private int globalWritten;
    private long globalSuppressed;

    public LogRateLimiter(LongSupplier clockMillis, long windowMillis, int globalPerWindow) {
        if (windowMillis <= 0 || globalPerWindow <= 0) {
            throw new IllegalArgumentException("window and global limit must be positive");
        }
        this.clockMillis = clockMillis;
        this.windowMillis = windowMillis;
        this.globalPerWindow = globalPerWindow;
        this.globalWindowStart = clockMillis.getAsLong();
    }

    /** A limiter with the app's settings: one line per key per minute, 30 lines per minute in all. */
    public static LogRateLimiter perMinute() {
        return new LogRateLimiter(MonotonicClock::millis, 60_000, 30);
    }

    /**
     * Asks whether a line for {@code key} may be written now.
     *
     * @return {@code null} when it must NOT be written; otherwise the text to append to the line: empty, or a note such as
     *         {@code " (+12 similar not written)"} that says how many lines were left out since the last one
     */
    public synchronized String admit(String key) {
        long now = clockMillis.getAsLong();
        if (now - globalWindowStart >= windowMillis || now < globalWindowStart) { // a clock that went back starts a new window
            globalWindowStart = now;
            globalWritten = 0;
        }
        if (globalWritten >= globalPerWindow) {
            globalSuppressed++;
            return null;
        }
        Entry entry = entries.get(key);
        if (entry != null && now >= entry.windowStart && now - entry.windowStart < windowMillis) {
            entry.suppressed++;
            return null;
        }
        long left = (entry == null ? 0 : entry.suppressed) + globalSuppressed;
        if (entry == null) {
            if (entries.size() >= MAX_KEYS) {
                prune(now);
            }
            entry = new Entry();
            entries.put(key, entry);
        }
        entry.windowStart = now;
        entry.suppressed = 0;
        globalSuppressed = 0;
        globalWritten++;
        return left == 0 ? "" : " (+" + left + " similar not written)";
    }

    private void prune(long now) {
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Entry e = it.next().getValue();
            if (now < e.windowStart || now - e.windowStart >= windowMillis) {
                it.remove();
            }
        }
        if (entries.size() >= MAX_KEYS) {
            entries.clear(); // the overall limit still protects the log
        }
    }

    /** Number of keys remembered (for tests). */
    synchronized int trackedKeys() {
        return entries.size();
    }
}
