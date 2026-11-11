package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.ReservationService;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Reservation;
import com.coffeeshop.coffeeshopmanagement.service.ReminderLog;
import com.coffeeshop.coffeeshopmanagement.service.ReservationRules;
import com.coffeeshop.coffeeshopmanagement.service.TableLayout;
import com.coffeeshop.coffeeshopmanagement.util.OffScreenThread;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.BusinessDay;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The reservation side of the sales screen (TODO 4.43, rule D11), kept out of {@code OrderController} (TODO 6.54): the "Đặt bàn" button
 * after the take-away ones, the badges for the table buttons, and the reminder. The reminder is shown once per booking per day (TODO 2.94), when the
 * sales screen refreshes its tables and the booking is within {@link ReservationRules#REMINDER_MINUTES} minutes (or already late).
 */
final class ReservationPanel {

    private static final Logger LOGGER = Logger.getLogger(ReservationPanel.class.getName());

    /** Once per booking per day, kept across program starts in this PC's data folder (TODO 2.94). */
    private final ReminderLog reminded = new ReminderLog(DataPaths.dataDir().resolve("reservation-reminders.txt"));

    /** The booking lines by table number; empty when they cannot be read (a booking must never stop the map from showing). */
    @OffScreenThread
    static Map<Integer, String> badgesOrNone() {
        try {
            return new ReservationService().badgesByTable();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not read the reservations", e);
            return Map.of();
        }
    }

    /** The waiting bookings, or none when they cannot be read. */
    @OffScreenThread
    static List<Reservation> waitingOrNone() {
        try {
            return new ReservationService().findWaiting();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not read the reservations", e);
            return List.of();
        }
    }

    /** Adds "Đặt bàn" at grid position {@code index}, with the number of waiting bookings. */
    void addButton(GridPane grid, int index, int waiting, Runnable onClick) {
        Button button = new Button("📅  " + I18n.t("reservation.open") + "\n" + I18n.t("reservation.waiting", waiting));
        TakeAwayPanel.style(button, "table-button-empty", false);
        button.setOnAction(e -> onClick.run());
        TableLayout.Cell cell = TableLayout.cellAt(index);
        grid.add(button, cell.column(), cell.row());
    }

    /** Shows the reminder once for each waiting booking that is due, naming its table. */
    void remind(List<Reservation> waiting, Map<Integer, DiningTable> tables) {
        var now = AppClock.now();
        for (Reservation r : waiting) {
            if (!ReservationRules.needsReminder(r, now) || !reminded.firstTimeToday(r.id(), BusinessDay.today())) continue;
            DiningTable table = tables.get(r.tableNumber());
            String name = table != null ? table.displayName() : I18n.t("orders.table", r.tableNumber());
            com.coffeeshop.coffeeshopmanagement.util.AlertPlayer.play(com.coffeeshop.coffeeshopmanagement.util.AlertSounds.Cue.BOOKING_DUE); // TODO 5.47
            AlertUtil.info(I18n.t("reservation.remind.title"), ReservationRules.reminder(r, name, now));
        }
    }

    /** Opens the booking window; {@code onSeat} is called with the booking the cashier chose to seat. */
    static void open(List<DiningTable> tables, Consumer<Reservation> onSeat) {
        ReservationDialog.show(tables, onSeat);
    }
}
