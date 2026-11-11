package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.ReservationService;
import com.coffeeshop.coffeeshopmanagement.model.Reservation;
import com.coffeeshop.coffeeshopmanagement.service.ReservationRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.util.List;
import java.util.Map;

/**
 * The waiting bookings as the table map last loaded them, and the question asked before an order is put on a table that is about to be
 * used (TODO 3.74): by a move to another table and by a new order on a table. Seating the guests who booked does not ask.
 */
final class BookedTables {

    private static volatile List<Reservation> waiting = List.of();

    private BookedTables() {
    }

    /** Remembers the list the map was drawn from and returns the badges of it (what {@link ReservationService#badgesOf} gives). */
    static Map<Integer, String> badges(List<Reservation> loaded) {
        waiting = loaded == null ? List.of() : List.copyOf(loaded);
        return ReservationService.badgesOf(loaded, AppClock.now());
    }

    /** True when the order may go on the table: nothing is booked soon, or the cashier said to carry on (Enter says no). */
    static boolean confirm(Integer tableNumber, String tableName) {
        if (tableNumber == null) return true; // a take-away order
        return confirm(ReservationRules.bookedNear(tableNumber, waiting, AppClock.now()).orElse(null), tableName);
    }

    static boolean confirm(Reservation booking, String tableName) {
        return booking == null || AlertUtil.confirm(I18n.t("reservation.title"), ReservationRules.bookedWarning(tableName, booking));
    }
}
