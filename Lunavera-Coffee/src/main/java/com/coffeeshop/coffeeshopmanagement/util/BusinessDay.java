package com.coffeeshop.coffeeshopmanagement.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * The shop's day (TODO 4.50). A cafe that stays open until 1 a.m. wants the evening's sales on ONE day, so the owner can say at what
 * time of day the day starts (for example 04:00): everything from 04:00 to 03:59 the next morning belongs to the day it started on.
 * The default is 00:00, which is the calendar day, exactly as before.
 *
 * <p>Every "today", every day range of a report, the history filters, the dashboard, the take-away number of the day, the clocked
 * hours and the day shown or grouped by in SQL go through this class and nothing else, so they cannot disagree. A calendar date
 * that means "the date on the wall" (a birthday, a hire date, a voucher's last day, the audit log) does not.
 *
 * <p>The start is kept in whole minutes after midnight, 0 up to {@link #MAX_START_MINUTES} (less than 12 hours: a day that starts in
 * the afternoon is not a day start). {@link ShopSettings} sets it when the program starts and when the owner saves the settings.
 * Changing it re-cuts the old orders as well: the reports read the times that were stored, nothing is rewritten.
 */
public final class BusinessDay {

    /** The latest start that can be set: 11:59. */
    public static final int MAX_START_MINUTES = 12 * 60 - 1;

    private static volatile int startMinutes = 0;

    private BusinessDay() {
    }

    /** Minutes after midnight at which the shop's day starts: 0 means the calendar day. */
    public static int startMinutes() {
        return startMinutes;
    }

    /** The time of day at which the shop's day starts. */
    public static LocalTime startTime() {
        return LocalTime.ofSecondOfDay(startMinutes * 60L);
    }

    /** True when the shop's day is the calendar day (no start set). */
    public static boolean isCalendarDay() {
        return startMinutes == 0;
    }

    /** Sets the start of the shop's day. @throws IllegalArgumentException for a value outside 0 to {@link #MAX_START_MINUTES}. */
    public static void setStartMinutes(int minutes) {
        if (minutes < 0 || minutes > MAX_START_MINUTES) {
            throw new IllegalArgumentException("the day start must be between 00:00 and 11:59, not " + minutes + " minutes");
        }
        startMinutes = minutes;
    }

    /** For tests: back to the calendar day. */
    public static void resetForTests() {
        startMinutes = 0;
    }

    /** The shop's day right now. */
    public static LocalDate today() {
        return dayOf(AppClock.now());
    }

    /** The shop's day that {@code time} belongs to: 00:40 with a day start of 04:00 still belongs to the day before. */
    public static LocalDate dayOf(LocalDateTime time) {
        return time.minusMinutes(startMinutes).toLocalDate();
    }

    /** The moment the shop's day {@code day} starts. */
    public static LocalDateTime startOf(LocalDate day) {
        return day.atStartOfDay().plusMinutes(startMinutes);
    }

    /**
     * The text to compare a stored time with to say "from the start of {@code day}" (or, with the next day, "before the end of
     * {@code day}"). With the calendar day it is the plain date, as it always was; otherwise it is the full time, written the way
     * the database in use stores times ({@link DbTime#write}), so a time of exactly 04:00 is on the right side of the bound.
     */
    public static String boundText(LocalDate day) {
        return isCalendarDay() ? day.toString() : DbTime.write(startOf(day));
    }

    /** {@code 04:00} for 240 minutes. */
    public static String format(int minutes) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    /** Minutes for {@code 04:00} (also {@code 4:00} and {@code 4:30}); null when the text is not a time from 00:00 to 11:59. */
    public static Integer parse(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (!t.matches("[0-9]{1,2}:[0-9]{2}")) return null;
        int hours = Integer.parseInt(t.substring(0, t.indexOf(':')));
        int minutes = Integer.parseInt(t.substring(t.indexOf(':') + 1));
        if (minutes > 59) return null;
        int total = hours * 60 + minutes;
        return total > MAX_START_MINUTES ? null : total;
    }
}
