package com.coffeeshop.coffeeshopmanagement.util;

import java.time.Duration;
import java.time.Instant;

/**
 * The reminder to make a backup when the administrator logs out or closes the program (TODO 5.18).
 *
 * <p>What counts is the last backup made <b>by hand and saved outside the data folder</b> on this computer: the daily
 * automatic backups stay on the same disk as the data, so they say nothing about what is left if that disk fails. The time
 * of the last such backup is kept in the small per-computer settings file. The person is asked at most once each time the
 * program is opened.
 */
public final class BackupReminder {

    /** A hand-made backup older than this (or none) triggers the question. */
    public static final int DAYS = 7;

    static final String KEY = "backup.lastManual";
    private static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(10);
    private static volatile boolean askedThisRun;

    private BackupReminder() {}

    /** "Now" for the reminder, from {@link AppClock} so a test that pins the date reaches it too (TODO 6.50). */
    public static Instant now() {
        return AppClock.now().atZone(java.time.ZoneId.systemDefault()).toInstant();
    }

    /** When a backup was last saved by hand outside the data folder on this computer, or null. */
    public static Instant lastManual() {
        String text = UiSettings.get(KEY, null);
        if (text == null || text.isBlank()) return null;
        try {
            return Instant.parse(text.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Remembers that a backup was saved by hand at {@code when}. Never throws. */
    public static void recordManual(Instant when) {
        if (when != null) UiSettings.put(KEY, when.toString());
    }

    /** True when there is no hand-made backup, it is more than {@value #DAYS} days old, or its date is in the future (a wrong clock). */
    public static boolean due(Instant lastManual, Instant now) {
        if (lastManual == null) return true;
        if (lastManual.isAfter(now.plus(CLOCK_TOLERANCE))) return true;
        return Duration.between(lastManual, now).compareTo(Duration.ofDays(DAYS)) > 0;
    }

    /** Whole days since {@code lastManual} (never negative). */
    public static long daysSince(Instant lastManual, Instant now) {
        long days = Duration.between(lastManual, now).toDays();
        return Math.max(days, 0);
    }

    /** True when the question would be asked now: due, and not asked yet in this run. Does not use up the question. */
    public static boolean wouldAsk(Instant now) {
        return !askedThisRun && due(lastManual(), now);
    }

    /** Like {@link #wouldAsk} but uses the question up: after a true answer the person is not asked again until the next start. */
    public static synchronized boolean shouldAskNow(Instant now) {
        if (!wouldAsk(now)) return false;
        askedThisRun = true;
        return true;
    }

    /** For tests: forget that the question was asked in this run. */
    static void resetForTests() {
        askedThisRun = false;
    }
}
