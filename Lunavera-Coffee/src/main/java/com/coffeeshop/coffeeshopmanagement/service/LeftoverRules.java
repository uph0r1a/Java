package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Reservation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The pure rules of the short list shown at the first sign-in of the day (TODO 5.49): bookings from earlier days that nobody closed, and
 * a shift that has not been closed for more than a day. No database and no screen here. Nothing in the list is done by itself: the
 * list only asks (rule D11: a booking is never cancelled silently).
 */
public final class LeftoverRules {

    /** The key of {@code UiSettings} that holds the day the list was last looked at, so it comes once a day on a computer. */
    public static final String SHOWN_KEY = "leftovers.shown";

    /** A shift last closed this many days ago or more is mentioned: closed yesterday (1 day) is the normal rhythm of a shop. */
    public static final int SHIFT_NOTE_FROM_DAYS = 2;

    private LeftoverRules() {
    }

    /** What the list has to say; empty parts are null or empty. */
    public record Leftovers(List<Reservation> earlierBookings, Integer daysSinceShiftClose) {
        public boolean isEmpty() {
            return earlierBookings.isEmpty() && daysSinceShiftClose == null;
        }
    }

    /** True when the list was not looked at yet on {@code today} (the business day) on this computer. */
    public static boolean due(String shownOn, LocalDate today) {
        return shownOn == null || !shownOn.equals(today.toString());
    }

    /** The waiting bookings from before {@code dayStart} (the start of today's business day), soonest first as given. */
    public static List<Reservation> earlierBookings(List<Reservation> waiting, LocalDateTime dayStart) {
        return waiting.stream().filter(r -> r.isBooked() && r.reservedFor().isBefore(dayStart)).toList();
    }

    /**
     * Whole days between the end of the last shift close and {@code today}, when that is {@link #SHIFT_NOTE_FROM_DAYS} or more; null
     * otherwise, and null when no shift was ever closed (a new shop is not nagged about a close it never made).
     */
    public static Integer daysSinceClose(LocalDateTime lastCloseEnd, LocalDate today) {
        if (lastCloseEnd == null) return null;
        long days = java.time.temporal.ChronoUnit.DAYS.between(lastCloseEnd.toLocalDate(), today);
        return days >= SHIFT_NOTE_FROM_DAYS ? (int) Math.min(days, Integer.MAX_VALUE) : null;
    }

    public static Leftovers of(List<Reservation> waiting, LocalDateTime lastCloseEnd, LocalDateTime dayStart, LocalDate today) {
        return new Leftovers(earlierBookings(waiting, dayStart), daysSinceClose(lastCloseEnd, today));
    }
}
