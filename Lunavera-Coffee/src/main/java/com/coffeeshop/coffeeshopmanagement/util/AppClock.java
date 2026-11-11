package com.coffeeshop.coffeeshopmanagement.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * The one place the data code reads "now" and "today" from (TODO 6.18, 6.39). In the app it is the system clock. A test can
 * freeze the <em>date</em> ({@link #pinDate}) so a run that crosses midnight still sees one single day everywhere, or fix the
 * whole instant ({@link #fix}). The time of day keeps moving when only the date is pinned, so rows made one after the other
 * still sort in the order they were made.
 *
 * <p><b>The zone (TODO 3.68).</b> Every date in the database is zone-less text, so what it means depends on the zone it was read in.
 * The program therefore tells this class the shop's zone at start-up ({@link #setZone}, from {@code ShopZone}) and every
 * {@link #now()} is the wall-clock time in <em>that</em> zone, whatever zone the PC is set to: a computer left on UTC or on the wrong
 * country still writes Vietnamese time, and two tills of one shop never write two clocks into the same table. Until a zone is set
 * (in tests) it is the PC's own zone, as before. This assumes the PC's clock is right as a moment in time, which is what the
 * operating system's automatic time keeps even when its zone is wrong.
 *
 * <p>Not used for things that must follow the real clock (TODO 6.50). The list of exceptions is short and closed:
 * <ul>
 *   <li>the check that the PC's clock is sane ({@code ClockCheck} in {@code LunaveraApplication}): it exists to catch a wrong clock;</li>
 *   <li>times read from or compared with <em>file</em> times, which the operating system sets from the real clock:
 *       automatic-backup scheduling and pruning, {@code BackupService.pendingIsOlderThan}, {@code DatabaseHealth}, the snapshot
 *       service and the dashboard's backup line;</li>
 *   <li>names and notes written into files and file dialogs: backup and snapshot names, the suggested names of CSV exports,
 *       image names, {@code backup-info.txt}, the legacy-copy note, the {@code MoneyMigration} backup name;</li>
 *   <li>timers that measure how long ago ({@link MonotonicClock}: idle time, login delay, crash-dialog spacing).</li>
 * </ul>
 * Anything else that saves a date, defaults a date picker or builds a date range uses this class. {@code AppClockUsageTest}
 * reads the sources and fails when a file outside the list above reads the system clock for a date.
 */
public final class AppClock {

    private static volatile LocalDate pinnedDate;
    private static volatile LocalDateTime fixed;
    private static volatile ZoneId zone;
    private static volatile Instant fixedInstant;

    private AppClock() {
    }

    public static LocalDateTime now() {
        LocalDateTime fixedNow = fixed;
        if (fixedNow != null) return fixedNow;
        ZoneId inUse = zone();
        Instant moment = fixedInstant;
        if (moment != null) return LocalDateTime.ofInstant(moment, inUse);
        LocalDate day = pinnedDate;
        return day == null ? LocalDateTime.now(inUse) : LocalDateTime.of(day, LocalTime.now(inUse));
    }

    /** The zone {@link #now()} is read in: the shop's once {@link #setZone} was called, otherwise the PC's own. */
    public static ZoneId zone() {
        ZoneId set = zone;
        return set != null ? set : ZoneId.systemDefault();
    }

    /** From now on {@link #now()} is the wall-clock time in {@code shopZone}, whatever zone the PC is in. Null goes back to the PC's zone. */
    public static void setZone(ZoneId shopZone) {
        zone = shopZone;
    }

    public static LocalDate today() {
        return now().toLocalDate();
    }

    /** From now on {@link #today()} is {@code day} and the time of day is the real one. For tests. */
    public static void pinDate(LocalDate day) {
        fixed = null;
        fixedInstant = null;
        pinnedDate = day;
    }

    /** From now on {@link #now()} always returns {@code instant}. For tests that need an exact moment. */
    public static void fix(LocalDateTime instant) {
        pinnedDate = null;
        fixedInstant = null;
        fixed = instant;
    }

    /**
     * From now on {@link #now()} is this real moment seen in {@link #zone()} (it stays still). For tests of the zone: the same moment
     * is a different date in Vietnam and in UTC.
     */
    public static void fixInstant(Instant moment) {
        pinnedDate = null;
        fixed = null;
        fixedInstant = moment;
    }

    /** Back to the system clock. */
    public static void reset() {
        pinnedDate = null;
        fixed = null;
        fixedInstant = null;
        zone = null;
    }
}
