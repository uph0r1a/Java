package com.coffeeshop.coffeeshopmanagement.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * The one place the data code reads "now" and "today" from (TODO 6.18, 6.39). In the app it is the system clock. A test can
 * freeze the <em>date</em> ({@link #pinDate}) so a run that crosses midnight still sees one single day everywhere, or fix the
 * whole instant ({@link #fix}). The time of day keeps moving when only the date is pinned, so rows made one after the other
 * still sort in the order they were made.
 *
 * <p>Not used for things that must follow the real clock: the check that the PC's clock is sane, idle timers, file names.
 */
public final class AppClock {

    private static volatile LocalDate pinnedDate;
    private static volatile LocalDateTime fixed;

    private AppClock() {
    }

    public static LocalDateTime now() {
        LocalDateTime fixedNow = fixed;
        if (fixedNow != null) return fixedNow;
        LocalDate day = pinnedDate;
        return day == null ? LocalDateTime.now() : LocalDateTime.of(day, LocalTime.now());
    }

    public static LocalDate today() {
        return now().toLocalDate();
    }

    /** From now on {@link #today()} is {@code day} and the time of day is the real one. For tests. */
    public static void pinDate(LocalDate day) {
        fixed = null;
        pinnedDate = day;
    }

    /** From now on {@link #now()} always returns {@code instant}. For tests that need an exact moment. */
    public static void fix(LocalDateTime instant) {
        pinnedDate = null;
        fixed = instant;
    }

    /** Back to the system clock. */
    public static void reset() {
        pinnedDate = null;
        fixed = null;
    }
}
