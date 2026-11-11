package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Reservation;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** TODO 4.43, rule D11: the pure rules of table reservations. */
public class ReservationRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 17, 0);

    private static Reservation booking(int id, int table, LocalDateTime when, Reservation.Status status) {
        return new Reservation(id, table, "Nam", "0912345678", when, 4, null, status, "cashier", NOW.minusDays(1), null);
    }

    private static String problem(Integer capacity, String name, String phone, LocalDateTime when, int people) {
        return ReservationRules.problem(capacity, name, phone, when, people, null, NOW);
    }

    @Test
    public void aGoodBookingHasNoProblem() {
        assertNull(problem(4, "Nam", "0912345678", NOW.plusHours(2), 4));
        assertNull("the phone is optional", problem(4, "Nam", null, NOW.plusHours(2), 2));
        assertNull(problem(4, "Nam", "  ", NOW.plusHours(2), 1));
    }

    @Test
    public void eachBadBookingIsRefusedWithItsOwnSentence() {
        assertEquals(I18n.t("reservation.val.table"), problem(null, "Nam", null, NOW.plusHours(2), 2));
        assertEquals(I18n.t("reservation.val.name"), problem(4, " ", null, NOW.plusHours(2), 2));
        assertEquals(I18n.t("reservation.val.name"), problem(4, null, null, NOW.plusHours(2), 2));
        assertEquals(I18n.t("reservation.val.namelong", 100), problem(4, "x".repeat(101), null, NOW.plusHours(2), 2));
        assertEquals(I18n.t("reservation.val.phone"), problem(4, "Nam", "12ab", NOW.plusHours(2), 2));
        assertEquals(I18n.t("reservation.val.time"), problem(4, "Nam", null, null, 2));
        assertEquals(I18n.t("reservation.val.past"), problem(4, "Nam", null, NOW.minusMinutes(1), 2));
        assertEquals(I18n.t("reservation.val.far", 60), problem(4, "Nam", null, NOW.plusDays(61), 2));
        assertNull("the last allowed day", problem(4, "Nam", null, NOW.plusDays(60), 2));
        assertEquals(I18n.t("reservation.val.people", 99), problem(4, "Nam", null, NOW.plusHours(2), 0));
        assertEquals("no other table is free: only this table's 4 seats", I18n.t("reservation.val.capacity", 4, 4), problem(4, "Nam", null, NOW.plusHours(2), 5));
        assertEquals(I18n.t("reservation.val.notelong", 200),
                ReservationRules.problem(4, "Nam", null, NOW.plusHours(2), 2, "n".repeat(201), NOW));
    }

    @Test
    public void twoBookingsOfOneTableMustBeAFullSlotApart() {
        assertTrue(ReservationRules.tooClose(NOW, NOW.plusMinutes(89)));
        assertTrue(ReservationRules.tooClose(NOW.plusMinutes(30), NOW));
        assertFalse(ReservationRules.tooClose(NOW, NOW.plusMinutes(90)));
        assertTrue(ReservationRules.tooClose(NOW, NOW));
    }

    @Test
    public void aBookingMovesFromFarToSoonToReminderToLate() {
        assertEquals(ReservationRules.Phase.FAR, ReservationRules.phase(booking(1, 1, NOW.plusHours(3).plusMinutes(1), Reservation.Status.BOOKED), NOW));
        assertEquals(ReservationRules.Phase.SOON, ReservationRules.phase(booking(1, 1, NOW.plusHours(3), Reservation.Status.BOOKED), NOW));
        assertEquals(ReservationRules.Phase.SOON, ReservationRules.phase(booking(1, 1, NOW.plusMinutes(16), Reservation.Status.BOOKED), NOW));
        assertEquals(ReservationRules.Phase.REMIND, ReservationRules.phase(booking(1, 1, NOW.plusMinutes(15), Reservation.Status.BOOKED), NOW));
        assertEquals(ReservationRules.Phase.REMIND, ReservationRules.phase(booking(1, 1, NOW, Reservation.Status.BOOKED), NOW));
        assertEquals(ReservationRules.Phase.LATE, ReservationRules.phase(booking(1, 1, NOW.minusMinutes(1), Reservation.Status.BOOKED), NOW));
    }

    @Test
    public void theReminderIsDueOnlyForAWaitingBookingInsideTheWindowOrLate() {
        assertFalse(ReservationRules.needsReminder(booking(1, 1, NOW.plusHours(1), Reservation.Status.BOOKED), NOW));
        assertTrue(ReservationRules.needsReminder(booking(1, 1, NOW.plusMinutes(10), Reservation.Status.BOOKED), NOW));
        assertTrue(ReservationRules.needsReminder(booking(1, 1, NOW.minusMinutes(40), Reservation.Status.BOOKED), NOW));
        assertFalse("a seated booking is not reminded", ReservationRules.needsReminder(booking(1, 1, NOW.plusMinutes(10), Reservation.Status.SEATED), NOW));
        assertFalse(ReservationRules.needsReminder(booking(1, 1, NOW.plusMinutes(10), Reservation.Status.CANCELLED), NOW));
    }

    @Test
    public void theMapShowsTheNearestWaitingBookingInsideTheWindow() {
        Reservation far = booking(1, 3, NOW.plusHours(5), Reservation.Status.BOOKED);
        Reservation later = booking(2, 3, NOW.plusHours(2), Reservation.Status.BOOKED);
        Reservation sooner = booking(3, 3, NOW.plusMinutes(30), Reservation.Status.BOOKED);
        Reservation done = booking(4, 3, NOW.plusMinutes(10), Reservation.Status.CANCELLED);
        assertEquals(3, ReservationRules.forBadge(List.of(far, later, sooner, done), NOW).orElseThrow().id());
        assertTrue("only a far booking: nothing to show", ReservationRules.forBadge(List.of(far), NOW).isEmpty());
        assertTrue(ReservationRules.forBadge(List.of(done), NOW).isEmpty());
    }

    @Test
    public void theBadgeAndTheReminderNameTheGuestsAndSayWhenLate() {
        Reservation soon = booking(1, 3, LocalDateTime.of(2026, 10, 29, 18, 30), Reservation.Status.BOOKED);
        assertEquals(I18n.t("reservation.badge", "18:30", "Nam", 4), ReservationRules.badge(soon, NOW));
        Reservation late = booking(2, 3, LocalDateTime.of(2026, 10, 29, 16, 30), Reservation.Status.BOOKED);
        assertTrue(ReservationRules.badge(late, NOW).startsWith(I18n.t("reservation.badge.late")));
        assertEquals(I18n.t("reservation.remind.soon", "Nam", 4, "Bàn 3", "17:10", 10),
                ReservationRules.reminder(booking(3, 3, NOW.plusMinutes(10).withMinute(10).withHour(17), Reservation.Status.BOOKED), "Bàn 3", NOW));
        assertEquals(I18n.t("reservation.remind.late", "Nam", 4, "Bàn 3", "16:30", 30), ReservationRules.reminder(late, "Bàn 3", NOW));
    }

    // ---- TODO 3.85: the current minute, and a party bigger than the table

    private static String problemAt(LocalDateTime when, LocalDateTime now) {
        return ReservationRules.problem(4, "Nam", null, when, 2, null, now);
    }

    @Test
    public void aBookingForTheCurrentMinuteIsOnTimeWhateverTheSeconds() {
        LocalDateTime minute = LocalDateTime.of(2026, 10, 29, 17, 30);
        for (LocalDateTime now : new LocalDateTime[]{minute, minute.plusSeconds(1), minute.plusSeconds(30), minute.plusSeconds(59).plusNanos(999_000_000)}) {
            assertNull("the time picker gives 17:30:00, now is " + now, problemAt(minute, now));
        }
    }

    @Test
    public void aBookingForTheMinuteBeforeIsStillInThePast() {
        LocalDateTime minute = LocalDateTime.of(2026, 10, 29, 17, 30);
        assertEquals(I18n.t("reservation.val.past"), problemAt(minute.minusMinutes(1), minute.plusSeconds(5)));
        assertEquals(I18n.t("reservation.val.past"), problemAt(minute.minusMinutes(1), minute));
        assertEquals(I18n.t("reservation.val.past"), problemAt(minute, minute.plusMinutes(1)));
        assertNull(problemAt(minute.plusMinutes(1), minute.plusSeconds(59)));
    }

    private static String oversize(int people, Map<Integer, Integer> others, String note) {
        return ReservationRules.problem(4, "Nam", null, NOW.plusHours(2), people, note, NOW, others);
    }

    @Test
    public void aPartyBiggerThanTheTableIsAcceptedWhenTheOtherFreeTablesCanSeatTheRest() {
        assertNull("8 people: this table 4 and table 5 with 4", oversize(8, Map.of(5, 4), null));
        assertNull("8 people: 2 + 2 more from smaller tables", oversize(8, Map.of(5, 2, 6, 2), null));
        assertNull("a party that fits the table needs no other", oversize(4, Map.of(), null));
    }

    @Test
    public void aPartyThatEvenAllTheFreeTablesCannotSeatIsRefusedWithTheSeatsThatAreFree() {
        assertEquals(I18n.t("reservation.val.capacity", 4, 8), oversize(9, Map.of(5, 4), null));
        assertEquals(I18n.t("reservation.val.capacity", 4, 4), oversize(5, Map.of(), null));
        assertEquals("a table with no seats adds none", I18n.t("reservation.val.capacity", 4, 4), oversize(5, Map.of(5, 0), null));
        assertEquals(I18n.t("reservation.val.people", 99), oversize(100, Map.of(5, 200), null));
        assertEquals(I18n.t("reservation.val.people", 99), oversize(0, Map.of(5, 4), null));
    }

    @Test
    public void theTablesToPushTogetherAreTheBiggestFirstAndNoMoreThanNeeded() {
        assertEquals(List.of(5), ReservationRules.extraTables(4, Map.of(5, 4, 6, 2, 7, 2)));
        assertEquals(List.of(5, 6), ReservationRules.extraTables(5, Map.of(5, 4, 6, 2, 7, 2)));
        assertEquals("equal tables: the lower number first", List.of(6), ReservationRules.extraTables(2, Map.of(7, 2, 6, 2)));
        assertEquals(List.of(3, 8), ReservationRules.extraTables(6, Map.of(8, 4, 3, 2, 9, 1)));
        assertEquals(List.of(), ReservationRules.extraTables(0, Map.of(5, 4)));
        assertNull(ReservationRules.extraTables(9, Map.of(5, 4, 6, 4)));
        assertNull(ReservationRules.extraTables(1, Map.of()));
    }

    @Test
    public void theNoteGetsTheTablesToJoinAndTheNoteLimitCountsThem() {
        assertEquals(I18n.t("reservation.merge.note", "5"), ReservationRules.noteWithMerge(null, List.of(5)));
        assertEquals(I18n.t("reservation.merge.note", "5"), ReservationRules.noteWithMerge("  ", List.of(5)));
        assertEquals("by the window \u00b7 " + I18n.t("reservation.merge.note", "5, 6"), ReservationRules.noteWithMerge(" by the window ", List.of(5, 6)));
        assertEquals("a booking that fits its table keeps its note as typed", " by the window ", ReservationRules.noteWithMerge(" by the window ", List.of()));
        int room = 200 - " \u00b7 ".length() - ReservationRules.mergeNote(List.of(5)).length();
        assertNull(oversize(8, Map.of(5, 4), "n".repeat(room)));
        assertEquals(I18n.t("reservation.val.notelong", room), oversize(8, Map.of(5, 4), "n".repeat(room + 1)));
        assertNull("without a merge the whole 200 characters are the cashier's", oversize(4, Map.of(5, 4), "n".repeat(200)));
    }
}
