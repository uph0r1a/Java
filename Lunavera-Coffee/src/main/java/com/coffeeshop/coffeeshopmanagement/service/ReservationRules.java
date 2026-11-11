package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Reservation;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The rules of table reservations (TODO 4.43, rule D11, my choice until the owner decides):
 * <ul>
 *   <li>a booking is for one table, at a time that is not before the current MINUTE (the time picker has no seconds, so a booking for
 *       the minute the guests are walking in is fine, TODO 3.85) and not more than {@link #DAYS_AHEAD_MAX} days away, for 1 to
 *       {@link #PEOPLE_MAX} people; a party larger than the table is accepted when the OTHER tables that have no booking near that time
 *       can seat the rest, and the booking then says which tables to push together (TODO 3.85, tables can be merged at the till, 4.10);
 *       the name is required, the phone is optional but must be a real number when given;</li>
 *   <li>a table holds one booking per {@link #slotMinutes()} minutes: two bookings of the same table closer than that are refused;</li>
 *   <li>the reminder is {@link #REMINDER_MINUTES} minutes before; a booking stays "late" (never cancelled by itself) until staff seat
 *       it, cancel it or mark it as not come; the map shows a booking from {@link #BADGE_HOURS} hours before until it is closed;</li>
 *   <li>a booking more than {@link #STALE_HOURS} hours late is "not closed" (TODO 2.94): somebody forgot it. It stays in the list,
 *       in a block of its own at the top of the booking window, but is kept off the map and gives no reminder, so it cannot hide
 *       tonight's real booking or nag after every restart; the administrator can mark all of them as not come in one go;</li>
 *   <li>seating needs the table to be free and opens its order with the customer on it.</li>
 * </ul>
 */
public final class ReservationRules {

    /** The minutes a booking holds its table: a setting of the owner (TODO 4.49), 90 until it is changed. */
    public static int slotMinutes() {
        return ShopRules.current().slotMinutes();
    }

    public static final int REMINDER_MINUTES = 15;
    public static final int BADGE_HOURS = 3;
    /** A waiting booking later than this is "not closed" (TODO 2.94). */
    public static final int STALE_HOURS = 3;
    public static final int DAYS_AHEAD_MAX = 60;
    public static final int NAME_MAX = 100;
    public static final int NOTE_MAX = 200;
    public static final int PEOPLE_MAX = 99;

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    /** Between what the cashier typed and the tables to join. */
    private static final String NOTE_JOIN = " \u00b7 ";

    /** Where a waiting booking stands at a moment. STALE: more than {@link #STALE_HOURS} hours late, nobody closed it (TODO 2.94). */
    public enum Phase { FAR, SOON, REMIND, LATE, STALE }

    private ReservationRules() {
    }

    /** What is wrong with a booking as typed, as a sentence; null when it is fine. No other table is known to be free here. */
    public static String problem(Integer tableCapacity, String name, String phone, LocalDateTime when, int people, String note, LocalDateTime now) {
        return problem(tableCapacity, name, phone, when, people, note, now, Map.of());
    }

    /**
     * The same, for a booking whose party may be larger than the table: {@code otherFreeTables} are the other tables (number -> seats)
     * that have no booking near that time. A party that does not fit the table is accepted when those tables can seat the rest, and
     * refused with the number of seats that ARE free when they cannot (TODO 3.85).
     */
    public static String problem(Integer tableCapacity, String name, String phone, LocalDateTime when, int people, String note, LocalDateTime now,
                                 Map<Integer, Integer> otherFreeTables) {
        if (tableCapacity == null) return I18n.t("reservation.val.table");
        if (name == null || name.isBlank()) return I18n.t("reservation.val.name");
        if (name.trim().length() > NAME_MAX) return I18n.t("reservation.val.namelong", NAME_MAX);
        if (phone != null && !phone.isBlank() && !com.coffeeshop.coffeeshopmanagement.util.ValidationUtil.isValidPhone(phone.trim())) {
            return I18n.t("reservation.val.phone");
        }
        if (when == null) return I18n.t("reservation.val.time");
        // The picker has minutes and "now" has seconds: a booking for this very minute is on time (TODO 3.85).
        if (when.isBefore(now.truncatedTo(ChronoUnit.MINUTES))) return I18n.t("reservation.val.past");
        if (when.isAfter(now.plusDays(DAYS_AHEAD_MAX))) return I18n.t("reservation.val.far", DAYS_AHEAD_MAX);
        if (people < 1 || people > PEOPLE_MAX) return I18n.t("reservation.val.people", PEOPLE_MAX);
        int noteLimit = NOTE_MAX;
        if (people > tableCapacity) {
            List<Integer> extra = extraTables(people - tableCapacity, otherFreeTables);
            if (extra == null) {
                int free = tableCapacity + otherFreeTables.values().stream().mapToInt(Integer::intValue).sum();
                return I18n.t("reservation.val.capacity", tableCapacity, free);
            }
            noteLimit = NOTE_MAX - NOTE_JOIN.length() - mergeNote(extra).length(); // the note gets the tables added to it
        }
        if (note != null && note.trim().length() > noteLimit) return I18n.t("reservation.val.notelong", noteLimit);
        return null;
    }

    /**
     * The other tables to push together with the booked one so that {@code missingSeats} more people can sit: the biggest first, the
     * lower number first among equals, no more than needed; null when all of them together are not enough (TODO 3.85).
     */
    public static List<Integer> extraTables(int missingSeats, Map<Integer, Integer> otherFreeTables) {
        List<Map.Entry<Integer, Integer>> candidates = new ArrayList<>(otherFreeTables.entrySet());
        candidates.sort(Map.Entry.<Integer, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        List<Integer> chosen = new ArrayList<>();
        int left = missingSeats;
        for (Map.Entry<Integer, Integer> table : candidates) {
            if (left <= 0) break;
            if (table.getValue() <= 0) continue;
            chosen.add(table.getKey());
            left -= table.getValue();
        }
        if (left > 0) return null;
        Collections.sort(chosen);
        return chosen;
    }

    /** The words that go with a booking that needs more tables: "Join table(s) 5, 6". */
    public static String mergeNote(List<Integer> tables) {
        return I18n.t("reservation.merge.note", tables.stream().map(String::valueOf).collect(Collectors.joining(", ")));
    }

    /** The note as it is saved: what the cashier typed, then the tables to join, when the party is bigger than the table. */
    public static String noteWithMerge(String note, List<Integer> extraTables) {
        String typed = note == null ? "" : note.trim();
        if (extraTables == null || extraTables.isEmpty()) return note;
        return typed.isEmpty() ? mergeNote(extraTables) : typed + NOTE_JOIN + mergeNote(extraTables);
    }

    /** Two bookings of one table are too close when they start less than {@link #slotMinutes()} apart. */
    public static boolean tooClose(LocalDateTime a, LocalDateTime b) {
        return Math.abs(Duration.between(a, b).toMinutes()) < slotMinutes();
    }

    /** Where a booking stands now: far off, soon (inside the badge window), to be reminded (inside the reminder), or late. */
    public static Phase phase(Reservation reservation, LocalDateTime now) {
        long minutes = Duration.between(now, reservation.reservedFor()).toMinutes();
        if (reservation.reservedFor().isBefore(now.minusHours(STALE_HOURS))) return Phase.STALE;
        if (reservation.reservedFor().isBefore(now) || minutes < 0) return Phase.LATE;
        if (minutes <= REMINDER_MINUTES) return Phase.REMIND;
        if (minutes <= BADGE_HOURS * 60L) return Phase.SOON;
        return Phase.FAR;
    }

    /** True when the booking is waiting and more than {@link #STALE_HOURS} hours past its time: "not closed" (TODO 2.94). */
    public static boolean isStale(Reservation reservation, LocalDateTime now) {
        return reservation.isBooked() && phase(reservation, now) == Phase.STALE;
    }

    /**
     * The booking the map shows for one table: the waiting one that comes first, inside the badge window or late. A forgotten one
     * ({@link Phase#STALE}) is left out, so it cannot take the badge from tonight's real booking (TODO 2.94).
     */
    public static Optional<Reservation> forBadge(List<Reservation> ofOneTable, LocalDateTime now) {
        return ofOneTable.stream().filter(Reservation::isBooked)
                .filter(r -> phase(r, now) != Phase.FAR && phase(r, now) != Phase.STALE)
                .min(java.util.Comparator.comparing(Reservation::reservedFor));
    }

    /**
     * The waiting booking that makes it unwise to put an order on {@code tableNumber} now (TODO 3.74): it starts within
     * {@link #slotMinutes()} minutes, or it is already late but not forgotten ({@link Phase#STALE}). The earliest one; empty when there is none.
     */
    public static Optional<Reservation> bookedNear(int tableNumber, List<Reservation> waiting, LocalDateTime now) {
        if (waiting == null || now == null) return Optional.empty();
        return waiting.stream()
                .filter(r -> r.isBooked() && r.tableNumber() == tableNumber)
                .filter(r -> phase(r, now) != Phase.STALE && !r.reservedFor().isAfter(now.plusMinutes(slotMinutes())))
                .min(java.util.Comparator.comparing(Reservation::reservedFor));
    }

    /** "Bàn 5 có khách đặt lúc 18:30 (Nam, 4 người). Vẫn tiếp tục?" */
    public static String bookedWarning(String tableName, Reservation booking) {
        return I18n.t("reservation.warn.booked", tableName, HOUR.format(booking.reservedFor()), booking.customerName(), booking.people());
    }

    /** The line on the table's button: "Đặt 18:30 · Nam · 4", or "Quá giờ" in front when it is late. */
    public static String badge(Reservation reservation, LocalDateTime now) {
        String text = I18n.t("reservation.badge", HOUR.format(reservation.reservedFor()), reservation.customerName(), reservation.people());
        return phase(reservation, now) == Phase.LATE ? I18n.t("reservation.badge.late") + " " + text : text;
    }

    /** The reminder sentence for a booking that is due. */
    public static String reminder(Reservation reservation, String tableName, LocalDateTime now) {
        long minutes = Duration.between(now, reservation.reservedFor()).toMinutes();
        return minutes >= 0
                ? I18n.t("reservation.remind.soon", reservation.customerName(), reservation.people(), tableName,
                        HOUR.format(reservation.reservedFor()), minutes)
                : I18n.t("reservation.remind.late", reservation.customerName(), reservation.people(), tableName,
                        HOUR.format(reservation.reservedFor()), -minutes);
    }

    /** True when the reminder should be shown now (inside the reminder window or late) for a waiting booking. */
    public static boolean needsReminder(Reservation reservation, LocalDateTime now) {
        if (!reservation.isBooked()) return false;
        Phase phase = phase(reservation, now);
        return phase == Phase.REMIND || phase == Phase.LATE;
    }
}
