package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Reservation;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

/** TODO 2.94: a booking nobody closed does not nag, does not take the badge, and is not reminded again after every restart. */
public class ForgottenBookingRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 4, 20, 0);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static Reservation booking(int id, LocalDateTime when, Reservation.Status status) {
        return new Reservation(id, 1, "Khách " + id, null, when, 2, null, status, "x", NOW.minusDays(5), null);
    }

    private static Reservation waiting(int id, LocalDateTime when) {
        return booking(id, when, Reservation.Status.BOOKED);
    }

    // ---- the phases ----

    @Test
    public void aBookingMoreThanThreeHoursLateIsStale() {
        assertEquals(ReservationRules.Phase.STALE, ReservationRules.phase(waiting(1, NOW.minusMinutes(181)), NOW));
        assertEquals(ReservationRules.Phase.STALE, ReservationRules.phase(waiting(2, NOW.minusDays(2)), NOW));
        assertTrue(ReservationRules.isStale(waiting(3, NOW.minusHours(4)), NOW));
    }

    @Test
    public void justInsideThreeHoursIsStillLate() {
        assertEquals(ReservationRules.Phase.LATE, ReservationRules.phase(waiting(1, NOW.minusMinutes(179)), NOW));
        assertEquals("exactly three hours is not yet over it", ReservationRules.Phase.LATE, ReservationRules.phase(waiting(2, NOW.minusHours(3)), NOW));
        assertFalse(ReservationRules.isStale(waiting(3, NOW.minusMinutes(179)), NOW));
    }

    @Test
    public void onlyAWaitingBookingCanBeStale() {
        for (Reservation.Status s : List.of(Reservation.Status.SEATED, Reservation.Status.CANCELLED, Reservation.Status.NO_SHOW)) {
            assertFalse(s.name(), ReservationRules.isStale(booking(1, NOW.minusDays(1), s), NOW));
        }
    }

    @Test
    public void aStaleBookingGivesNoReminder() {
        assertFalse(ReservationRules.needsReminder(waiting(1, NOW.minusHours(5)), NOW));
        assertTrue("a booking that is merely late still does", ReservationRules.needsReminder(waiting(2, NOW.minusMinutes(30)), NOW));
        assertTrue(ReservationRules.needsReminder(waiting(3, NOW.plusMinutes(10)), NOW));
    }

    // ---- the badge on the table map ----

    @Test
    public void aForgottenBookingDoesNotHideTonightsRealOne() {
        Reservation forgotten = waiting(1, NOW.minusDays(3));
        Reservation tonight = waiting(2, NOW.plusMinutes(40));
        Optional<Reservation> shown = ReservationRules.forBadge(List.of(forgotten, tonight), NOW);
        assertTrue(shown.isPresent());
        assertEquals(2, shown.get().id());
    }

    @Test
    public void aTableWithOnlyAForgottenBookingShowsNoBadge() {
        assertTrue(ReservationRules.forBadge(List.of(waiting(1, NOW.minusHours(6))), NOW).isEmpty());
    }

    @Test
    public void aBookingThatIsOnlyLateStillShowsItsBadge() {
        Optional<Reservation> shown = ReservationRules.forBadge(List.of(waiting(1, NOW.minusHours(6)), waiting(2, NOW.minusMinutes(30))), NOW);
        assertEquals(2, shown.orElseThrow().id());
    }

    // ---- the reminder log ----

    @Test
    public void aBookingIsRemindedOncePerDay() {
        ReminderLog log = new ReminderLog();
        LocalDate day = LocalDate.of(2026, 11, 4);
        assertTrue(log.firstTimeToday(7, day));
        assertFalse(log.firstTimeToday(7, day));
        assertTrue("another booking", log.firstTimeToday(8, day));
        assertTrue("the next day it may remind again", log.firstTimeToday(7, day.plusDays(1)));
    }

    @Test
    public void aRestartDoesNotBringTheReminderBack() throws IOException {
        Path file = folder.getRoot().toPath().resolve("reservation-reminders.txt");
        LocalDate day = LocalDate.of(2026, 11, 4);
        assertTrue(new ReminderLog(file).firstTimeToday(7, day));
        ReminderLog afterRestart = new ReminderLog(file); // the program was closed and opened again
        assertFalse("already shown today", afterRestart.firstTimeToday(7, day));
        assertTrue("a booking not shown yet is", afterRestart.firstTimeToday(9, day));
        assertFalse(new ReminderLog(file).firstTimeToday(9, day));
    }

    @Test
    public void oldLinesAreDroppedAndDamagedOnesIgnored() throws IOException {
        Path file = folder.getRoot().toPath().resolve("reminders.txt");
        LocalDate day = LocalDate.of(2026, 11, 10);
        Files.write(file, List.of("1 2026-11-01", "garbage", "2 not-a-date", "3 2026-11-10", "", "4 5 6"), StandardCharsets.UTF_8);
        ReminderLog log = new ReminderLog(file);
        assertFalse("a line of today is kept", log.firstTimeToday(3, day));
        assertTrue("a damaged line is no memory", log.firstTimeToday(2, day));
        List<String> saved = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertTrue(saved.toString(), saved.stream().noneMatch(l -> l.startsWith("1 ")));
        assertTrue(saved.toString(), saved.contains("3 2026-11-10"));
    }

    @Test
    public void aFileThatCannotBeWrittenNeverStopsTheReminders() {
        Path nowhere = folder.getRoot().toPath().resolve("no-such-folder").resolve("x.txt");
        ReminderLog log = new ReminderLog(nowhere);
        LocalDate day = LocalDate.of(2026, 11, 4);
        assertTrue(log.firstTimeToday(1, day));
        assertFalse("it still remembers in memory", log.firstTimeToday(1, day));
    }
}
