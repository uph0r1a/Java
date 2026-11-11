package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Reservation;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** TODO 3.74: which booking should stop a cashier from putting an order on a table, and where the question is asked. */
public class BookedTableWarningTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 18, 0);
    private static final String ROOT = "src/main/java/com/coffeeshop/coffeeshopmanagement/controller/";

    private static Reservation booking(int id, int table, LocalDateTime when, Reservation.Status status) {
        return new Reservation(id, table, "Nam", null, when, 4, null, status, null, NOW.minusDays(1), null);
    }

    @Test
    public void aBookingInsideTheSlotIsFound() {
        Reservation soon = booking(1, 5, NOW.plusMinutes(30), Reservation.Status.BOOKED);
        assertEquals(soon, ReservationRules.bookedNear(5, List.of(soon), NOW).orElseThrow());
        Reservation edge = booking(2, 5, NOW.plusMinutes(ReservationRules.slotMinutes()), Reservation.Status.BOOKED);
        assertTrue("exactly at the slot length counts", ReservationRules.bookedNear(5, List.of(edge), NOW).isPresent());
    }

    @Test
    public void aLateButNotForgottenBookingIsFound() {
        Reservation late = booking(1, 5, NOW.minusMinutes(20), Reservation.Status.BOOKED);
        assertTrue(ReservationRules.bookedNear(5, List.of(late), NOW).isPresent());
    }

    @Test
    public void farOffForgottenOtherTableAndClosedBookingsAreNotFound() {
        assertFalse(ReservationRules.bookedNear(5, List.of(booking(1, 5, NOW.plusMinutes(ReservationRules.slotMinutes() + 1), Reservation.Status.BOOKED)), NOW).isPresent());
        assertFalse("forgotten", ReservationRules.bookedNear(5, List.of(booking(2, 5, NOW.minusHours(ReservationRules.STALE_HOURS + 1), Reservation.Status.BOOKED)), NOW).isPresent());
        assertFalse("another table", ReservationRules.bookedNear(6, List.of(booking(3, 5, NOW.plusMinutes(10), Reservation.Status.BOOKED)), NOW).isPresent());
        assertFalse("seated", ReservationRules.bookedNear(5, List.of(booking(4, 5, NOW.plusMinutes(10), Reservation.Status.SEATED)), NOW).isPresent());
        assertFalse("cancelled", ReservationRules.bookedNear(5, List.of(booking(5, 5, NOW.plusMinutes(10), Reservation.Status.CANCELLED)), NOW).isPresent());
        assertFalse(ReservationRules.bookedNear(5, List.of(), NOW).isPresent());
        assertFalse(ReservationRules.bookedNear(5, null, NOW).isPresent());
    }

    @Test
    public void theEarliestBookingIsTheOneShown() {
        Reservation later = booking(1, 5, NOW.plusMinutes(60), Reservation.Status.BOOKED);
        Reservation earlier = booking(2, 5, NOW.plusMinutes(10), Reservation.Status.BOOKED);
        assertEquals(earlier, ReservationRules.bookedNear(5, List.of(later, earlier), NOW).orElseThrow());
    }

    @Test
    public void theWarningNamesTheTimeThePersonAndThePartySize() {
        String text = ReservationRules.bookedWarning("Table 5", booking(1, 5, NOW.plusMinutes(30), Reservation.Status.BOOKED));
        assertTrue(text, text.contains("Table 5") && text.contains("18:30") && text.contains("Nam") && text.contains("4"));
    }

    @Test
    public void theQuestionIsAskedOnBothWaysOntoATableButNotWhenSeatingTheGuests() throws IOException {
        String order = Files.readString(Path.of(ROOT + "OrderController.java"));
        assertEquals("the add-product and start-order entrances", 2, order.split("BookedTables\\.confirm\\(selectedTableNumber", -1).length - 1);
        assertTrue(Files.readString(Path.of(ROOT + "TableMovePanel.java")).contains("BookedTables.confirm(to.getTableNumber()"));
        assertTrue("the map remembers the bookings it drew", Files.readString(Path.of(ROOT + "TableMapView.java")).contains("BookedTables.badges(loaded.waiting())")); // the map moved out of the till (TODO 6.54)
        int seat = order.indexOf("startOrderForTable(booking.tableNumber()");
        assertTrue(seat > 0 && !order.substring(seat - 40, seat).contains("BookedTables"));
    }
}
