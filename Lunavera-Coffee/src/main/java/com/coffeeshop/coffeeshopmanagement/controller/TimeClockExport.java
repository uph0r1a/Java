package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.TimeClockDAO;
import com.coffeeshop.coffeeshopmanagement.service.TimeClockService;
import com.coffeeshop.coffeeshopmanagement.model.TimeClockEntry;
import com.coffeeshop.coffeeshopmanagement.service.ShopRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.BusyIndicator;
import com.coffeeshop.coffeeshopmanagement.util.ExportCsv;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import com.coffeeshop.coffeeshopmanagement.util.StaffHoursCsv;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/**
 * The "Xuất CSV" button of the staff hours (TODO 4.46), the same way the order history and the customer list do it: ask what to save, ask where,
 * write the file off the screen thread with the shared {@link ExportCsv#write}, say where it went. Administrators only (the hours are read through
 * {@link TimeClockDAO#hours}, which refuses everybody else). The text of the file is made by {@link StaffHoursCsv}.
 */
final class TimeClockExport {

    private TimeClockExport() {
    }

    /** Asks "one line per person" or "per person per day", then the file, then saves the hours of {@code from} to {@code to}. */
    static void run(Button button, TimeClockService dao, LocalDate from, LocalDate to, Label message) {
        if (from == null || to == null || to.isBefore(from)) {
            message.setText(I18n.t("time.clock.val.range"));
            return;
        }
        ButtonType person = new ButtonType(I18n.t("time.clock.export.person"), ButtonData.OK_DONE);
        ButtonType day = new ButtonType(I18n.t("time.clock.export.day"), ButtonData.OTHER);
        Alert ask = new Alert(Alert.AlertType.NONE, I18n.t("time.clock.export.ask"), person, day, ButtonType.CANCEL);
        ask.setTitle(I18n.t("time.clock.export.title"));
        AlertUtil.configure(ask);
        Optional<ButtonType> choice = ask.showAndWait();
        if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) return;
        final boolean perDay = choice.get() == day;

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("time.clock.export.title"));
        chooser.setInitialFileName("gio-cong-" + from + "-" + to + (perDay ? "-theo-ngay" : "") + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(button.getScene() == null ? null : button.getScene().getWindow());
        if (file == null) return;
        Runnable release = BusyIndicator.hold(button, I18n.t("busy.export"));
        Async.run(() -> {
            List<TimeClockDAO.Hours> hours = dao.hours(from, to); // refuses anybody but an administrator
            int people;
            List<String> lines;
            if (perDay) {
                List<TimeClockEntry> entries = dao.findBetween(from, to);
                lines = StaffHoursCsv.perDay(entries, from, to, AppClock.now());
                HashSet<Integer> who = new HashSet<>();
                for (TimeClockEntry e : entries) who.add(e.userId());
                people = lines.size() > 1 ? who.size() : 0;
            } else {
                lines = StaffHoursCsv.perPerson(hours, dao.salaries(), ShopRules.current());
                people = hours.size();
            }
            if (people == 0) return 0; // nothing to save: no file is written
            ExportCsv.write(file.toPath(), lines);
            SecurityLog.event("exported the staff hours (" + (perDay ? "per day" : "per person") + ") to CSV");
            return people;
        }, count -> {
            release.run();
            if (count == 0) {
                AlertUtil.info(I18n.t("time.clock.export.title"), I18n.t("time.clock.export.none"));
            } else {
                AlertUtil.info(I18n.t("time.clock.export.title"), I18n.t("time.clock.export.done", count, from, to, file.getAbsolutePath()));
            }
        }, error -> {
            release.run();
            AlertUtil.error(I18n.t("common.file.savefail"), error.getMessage());
        });
    }
}
