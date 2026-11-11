package com.coffeeshop.coffeeshopmanagement.service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Staff clock-in and hours (TODO 7, clock-in). The pure arithmetic: how many whole minutes of a stretch fall inside a window, and
 * when an open stretch is taken to have been forgotten. No database and no screen here, so it is tested on its own.
 */
public final class TimeClockRules {

    /** A stretch still open after this many hours is a forgotten clock-out: it is counted only up to this length and flagged (a setting, TODO 4.49; 16 until changed). */
    public static int forgottenAfterHours() {
        return ShopRules.current().forgottenHours();
    }

    private TimeClockRules() {
    }

    /** True when a stretch that began at {@code clockIn} and is still open at {@code now} has been open too long. */
    public static boolean looksForgotten(LocalDateTime clockIn, LocalDateTime now) {
        return clockIn != null && now != null && Duration.between(clockIn, now).toHours() >= forgottenAfterHours();
    }

    /**
     * The whole minutes of {@code [clockIn, clockOut)} that lie inside {@code [from, to)}. An open stretch ({@code clockOut} null) ends
     * at {@code now}, but never later than {@link #forgottenAfterHours()} after it began. Never negative.
     */
    public static long minutesWithin(LocalDateTime clockIn, LocalDateTime clockOut, LocalDateTime from, LocalDateTime to, LocalDateTime now) {
        if (clockIn == null || from == null || to == null) return 0;
        LocalDateTime end = clockOut != null ? clockOut : now;
        if (end == null) return 0;
        if (clockOut == null) {
            LocalDateTime cap = clockIn.plusHours(forgottenAfterHours());
            if (end.isAfter(cap)) end = cap;
        }
        LocalDateTime start = clockIn.isAfter(from) ? clockIn : from;
        LocalDateTime stop = end.isBefore(to) ? end : to;
        if (!stop.isAfter(start)) return 0;
        return Duration.between(start, stop).toMinutes();
    }

    /** "7:30" for 450 minutes; hours are not cut at 24. */
    public static String hoursText(long minutes) {
        long m = Math.max(0, minutes);
        return (m / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", m % 60);
    }

    /** The longest stretch an administrator may write in by hand: nobody works longer, so a longer one is a typing slip. */
    public static final int MAX_STRETCH_HOURS = 24;
    public static final int MAX_REASON_LENGTH = 200;
    private static final java.time.format.DateTimeFormatter TYPED = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** A time typed as "2026-10-30 17:30", or null when it is not one. */
    public static LocalDateTime parseTyped(String text) {
        try {
            return text == null ? null : LocalDateTime.parse(text.trim(), TYPED);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /** The time as it is typed and written in the audit log: "2026-10-30 17:30". */
    public static String typed(LocalDateTime time) {
        return time == null ? "" : TYPED.format(time);
    }

    /**
     * What is wrong with a finished stretch written or corrected by hand (TODO 3.67), or null when it is fine: "TIME" (a time is
     * missing), "BEFORE" (it does not end after it starts), "FUTURE" (it starts or ends after now) or "LONG" (more than
     * {@link #MAX_STRETCH_HOURS} hours).
     */
    public static String stretchProblem(LocalDateTime clockIn, LocalDateTime clockOut, LocalDateTime now) {
        if (clockIn == null || clockOut == null) return "TIME";
        if (!clockOut.isAfter(clockIn)) return "BEFORE";
        if (clockIn.isAfter(now) || clockOut.isAfter(now)) return "FUTURE";
        if (Duration.between(clockIn, clockOut).compareTo(Duration.ofHours(MAX_STRETCH_HOURS)) > 0) return "LONG";
        return null;
    }

    /** True when two stretches of one person share any time; {@code null} for an end means "still open". Ends touching is not an overlap. */
    public static boolean overlaps(LocalDateTime inA, LocalDateTime outA, LocalDateTime inB, LocalDateTime outB) {
        boolean aStartsBeforeBEnds = outB == null || inA.isBefore(outB);
        boolean bStartsBeforeAEnds = outA == null || inB.isBefore(outA);
        return aStartsBeforeBEnds && bStartsBeforeAEnds;
    }

    /** The problem with the reason written for a correction: "REASON" (blank) or "LONG", else null. */
    public static String reasonProblem(String reason) {
        if (reason == null || reason.isBlank()) return "REASON";
        return reason.trim().length() > MAX_REASON_LENGTH ? "LONG" : null;
    }

    /** The problem with correcting a stretch's end to {@code at}, or null when it is fine: "TIME" (no time), "BEFORE" (not after the clock-in) or "FUTURE" (administrator's correction) or "LONG" (more than {@link #MAX_STRETCH_HOURS} hours after the clock-in). */
    public static String correctionProblem(LocalDateTime clockIn, LocalDateTime at, LocalDateTime now) {
        if (at == null) return "TIME";
        if (!at.isAfter(clockIn)) return "BEFORE";
        if (at.isAfter(now)) return "FUTURE";
        if (Duration.between(clockIn, at).compareTo(Duration.ofHours(MAX_STRETCH_HOURS)) > 0) return "LONG"; // TODO 3.76: a 45-hour shift is a slip
        return null;
    }
}
