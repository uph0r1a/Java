package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.ShiftService;
import com.coffeeshop.coffeeshopmanagement.service.ReservationService;
import com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO;
import com.coffeeshop.coffeeshopmanagement.model.Reservation;
import com.coffeeshop.coffeeshopmanagement.service.LeftoverRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.BusinessDay;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.UiSettings;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The short list at the first sign-in of the day (TODO 5.49): bookings from earlier days that are still waiting, and "the last shift was
 * closed N days ago". It only asks: "close them as no-shows" (administrator, one audit line) and "open the shift close" are buttons, and
 * "Not now" (Esc) leaves everything as it was. Shown once a day on a computer, and never when there is nothing to say. The rules are
 * in {@code LeftoverRules}; the database reads run off the screen thread.
 */
public final class FirstSignInLeftovers {

    private static final Logger LOGGER = Logger.getLogger(FirstSignInLeftovers.class.getName());
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private static final int NAMED = 5;

    private FirstSignInLeftovers() {
    }

    /** Called right after a sign-in. Does nothing when nobody is signed in or the list was already looked at today. */
    public static void afterSignIn() {
        if (Session.getCurrentUser() == null) return;
        LocalDate today = BusinessDay.today();
        if (!LeftoverRules.due(UiSettings.get(LeftoverRules.SHOWN_KEY, null), today)) return;
        int userId = Session.getCurrentUser().getId();
        Async.run(
                () -> LeftoverRules.of(new ReservationService().findWaiting(), new ShiftService().lastCloseEnd().orElse(null),
                        BusinessDay.startOf(today), today),
                leftovers -> {
                    if (Session.getCurrentUser() == null || Session.getCurrentUser().getId() != userId) return; // signed out meanwhile
                    UiSettings.put(LeftoverRules.SHOWN_KEY, today.toString());
                    if (!leftovers.isEmpty()) show(leftovers);
                },
                error -> LOGGER.log(Level.WARNING, "Could not read the leftovers at sign-in; no list shown", error));
    }

    private static void show(LeftoverRules.Leftovers leftovers) {
        List<ButtonType> buttons = new ArrayList<>();
        ButtonType close = null;
        ButtonType shift = null;
        boolean canClose = Session.isAdmin() && !leftovers.earlierBookings().isEmpty();
        if (canClose) {
            close = new ButtonType(I18n.t("leftovers.button.close"), ButtonData.OK_DONE);
            buttons.add(close);
        }
        if (leftovers.daysSinceShiftClose() != null) {
            shift = new ButtonType(I18n.t("leftovers.button.shift"), ButtonData.APPLY);
            buttons.add(shift);
        }
        ButtonType notNow = new ButtonType(I18n.t("clock.offer.notnow"), ButtonData.CANCEL_CLOSE);
        buttons.add(notNow);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text(leftovers), buttons.toArray(new ButtonType[0]));
        alert.setTitle(I18n.t("leftovers.title"));
        alert.setHeaderText(null);
        AlertUtil.configure(alert);
        AlertUtil.setCancelButton(alert, notNow);
        ButtonType chosen = alert.showAndWait().orElse(notNow);
        if (chosen == close) {
            LocalDateTime now = AppClock.now();
            Async.run(() -> new ReservationService().closeBefore(BusinessDay.startOf(BusinessDay.today()), now),
                    n -> AlertUtil.info(I18n.t("leftovers.title"), I18n.t("leftovers.closed", n)),
                    error -> AlertUtil.error(I18n.t("leftovers.title"), error.getMessage()));
        } else if (chosen == shift) {
            ShiftCloseDialog.show();
        }
    }

    /** The text of the box: the earlier bookings (the first few by name) and the shift note. */
    static String text(LeftoverRules.Leftovers leftovers) {
        List<String> parts = new ArrayList<>();
        List<Reservation> bookings = leftovers.earlierBookings();
        if (!bookings.isEmpty()) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < Math.min(NAMED, bookings.size()); i++) {
                Reservation r = bookings.get(i);
                names.append("\n  \u2022 ").append(r.reservedFor().format(WHEN)).append("  ").append(r.customerName());
            }
            if (bookings.size() > NAMED) names.append("\n  ").append(I18n.t("leftovers.more", bookings.size() - NAMED));
            parts.add(I18n.t("leftovers.bookings", bookings.size()) + names);
        }
        if (leftovers.daysSinceShiftClose() != null) parts.add(I18n.t("leftovers.shift", leftovers.daysSinceShiftClose()));
        return String.join("\n\n", parts);
    }
}
