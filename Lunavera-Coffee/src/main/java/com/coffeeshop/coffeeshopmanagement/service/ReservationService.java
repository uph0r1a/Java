package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.ReservationDAO;
import com.coffeeshop.coffeeshopmanagement.model.Reservation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * What the booking screens ask for (TODO 6.21): the waiting bookings, the line on each table's button, making a booking, closing one,
 * and closing the ones that are long past. What is a valid booking is {@link ReservationDAO#problemFor}'s (the same check runs again
 * inside {@link #add}); the badge text is {@link ReservationDAO#badgesOf}. {@code ReservationDialog}, {@code ReservationPanel} and
 * {@code BookedTables} hold no DAO of their own. Meant to be called from a background thread (TODO 6.14).
 */
public class ReservationService {

    private final ReservationDAO reservations;

    public ReservationService() {
        this(new ReservationDAO());
    }

    ReservationService(ReservationDAO reservations) {
        this.reservations = reservations;
    }

    public List<Reservation> findWaiting() {
        return reservations.findWaiting();
    }

    /** The line to show on each table's button, by table number. */
    public Map<Integer, String> badgesByTable() {
        return reservations.badgesByTable();
    }

    /** {@link #badgesByTable} for a list already loaded; needs no database. */
    public static Map<Integer, String> badgesOf(List<Reservation> waiting, LocalDateTime now) {
        return ReservationDAO.badgesOf(waiting, now);
    }

    /** What is wrong with a booking as typed, in words, or null when it can be made. */
    public String problemFor(int tableNumber, String name, String phone, LocalDateTime when, int people, String note) {
        return reservations.problemFor(tableNumber, name, phone, when, people, note);
    }

    public Reservation add(int tableNumber, String name, String phone, LocalDateTime when, int people, String note) {
        return reservations.add(tableNumber, name, phone, when, people, note);
    }

    /** Closes a booking as cancelled / no-show / seated; false when somebody closed it first. */
    public boolean close(int id, Reservation.Status status) {
        return reservations.close(id, status);
    }

    /** Closes the bookings that are long past; how many. */
    public int closeStale(LocalDateTime now) {
        return reservations.closeStale(now);
    }

    public int closeBefore(LocalDateTime cutoff, LocalDateTime now) {
        return reservations.closeBefore(cutoff, now);
    }
}
