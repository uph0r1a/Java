package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.BusinessDay;
import com.coffeeshop.coffeeshopmanagement.dao.TimeClockDAO;
import com.coffeeshop.coffeeshopmanagement.service.TimeClockService;
import com.coffeeshop.coffeeshopmanagement.model.TimeClockEntry;
import com.coffeeshop.coffeeshopmanagement.service.EarningsRules;
import com.coffeeshop.coffeeshopmanagement.service.ShopRules;
import com.coffeeshop.coffeeshopmanagement.service.TimeClockRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Function;

/**
 * "Chấm công" (TODO 7, clock-in): every signed-in person sees whether they are clocked in and has one button to clock in or out.
 * An administrator also sees the hours of everybody in a range of days and who is still clocked in, and can close a forgotten
 * stretch. All the rules are in {@link TimeClockRules} and {@link TimeClockDAO}; this class only shows and asks. Database calls
 * run off the screen thread.
 */
public final class TimeClockDialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final DateTimeFormatter TYPED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private TimeClockDialog() {
    }

    public static void show() {
        TimeClockService dao = new TimeClockService();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("time.clock.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        Label status = new Label();
        status.setWrapText(true);
        Label message = new Label();
        message.setWrapText(true);
        Button toggle = new Button();
        toggle.setDefaultButton(true);
        toggle.setDisable(true);
        VBox box = new VBox(10, status, toggle, message);
        box.setPadding(new Insets(12));
        box.setPrefWidth(560);

        TableView<TimeClockDAO.Hours> hoursTable = new TableView<>();
        TableView<TimeClockEntry> openTable = new TableView<>();
        DatePicker from = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker(BusinessDay.today().withDayOfMonth(1));
        DatePicker to = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker(BusinessDay.today());

        Runnable[] refreshAll = new Runnable[1];
        Runnable refreshMine = () -> Async.run(dao::openForMe, open -> {
            toggle.setDisable(false);
            if (open.isPresent()) {
                status.setText(I18n.t("time.clock.status.in", TIME.format(open.get().clockIn())));
                toggle.setText(I18n.t("time.clock.out"));
            } else {
                status.setText(I18n.t("time.clock.status.out"));
                toggle.setText(I18n.t("time.clock.in"));
            }
        }, e -> message.setText(e.getMessage()));
        toggle.setOnAction(ev -> {
            toggle.setDisable(true);
            boolean clockingOut = toggle.getText().equals(I18n.t("time.clock.out"));
            Async.run(() -> clockingOut ? dao.clockOut() : dao.clockIn(), entry -> {
                message.setText(clockingOut
                        ? I18n.t("time.clock.done.out", TimeClockRules.hoursText(TimeClockRules.minutesWithin(entry.clockIn(), entry.clockOut(),
                                entry.clockIn(), entry.clockOut(), entry.clockOut())))
                        : I18n.t("time.clock.done.in"));
                refreshMine.run();
                if (refreshAll[0] != null) refreshAll[0].run();
            }, e -> {
                message.setText(e.getMessage());
                refreshMine.run();
            });
        });

        if (Session.isAdmin()) {
            box.setPrefWidth(840); // the hours table now has the rate and the pay beside the hours (TODO 4.60)
            hoursTable.setPrefHeight(200);
            // The hourly rate worked out from each person's salary and the shop's reading of it, shown as an estimate (TODO 4.60).
            final java.util.Map<Integer, java.math.BigDecimal> salaryByUser = new java.util.HashMap<>();
            final java.util.function.Function<Integer, java.math.BigDecimal> rateOf =
                    userId -> EarningsRules.hourlyRate(salaryByUser.get(userId), ShopRules.current());
            hoursTable.setPlaceholder(new Label(I18n.t("time.clock.empty")));
            hoursTable.getColumns().add(column(I18n.t("time.clock.col.name"), 190, h -> h.userName()));
            hoursTable.getColumns().add(column(I18n.t("time.clock.col.hours"), 80, h -> TimeClockRules.hoursText(h.minutes())));
            hoursTable.getColumns().add(column(I18n.t("time.clock.col.rate"), 170, h -> {
                java.math.BigDecimal rate = rateOf.apply(h.userId());
                return rate == null ? "-" : CurrencyUtil.format(rate);
            }));
            hoursTable.getColumns().add(column(I18n.t("time.clock.col.pay"), 150, h -> {
                java.math.BigDecimal pay = EarningsRules.estimate(h.minutes(), rateOf.apply(h.userId()));
                return pay == null ? "-" : CurrencyUtil.format(pay);
            }));
            hoursTable.getColumns().add(column(I18n.t("time.clock.col.state"), 200, h -> h.forgotten() ? I18n.t("time.clock.state.forgotten")
                    : h.clockedIn() ? I18n.t("time.clock.state.in") : ""));
            openTable.setPrefHeight(110);
            openTable.setPlaceholder(new Label(I18n.t("time.clock.open.empty")));
            openTable.getColumns().add(column(I18n.t("time.clock.col.name"), 200, e -> e.userName()));
            openTable.getColumns().add(column(I18n.t("time.clock.col.since"), 160, e -> TIME.format(e.clockIn())));
            Button close = new Button(I18n.t("time.clock.close"));
            close.setDisable(true);
            openTable.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> close.setDisable(now == null));
            close.setOnAction(ev -> {
                TimeClockEntry entry = openTable.getSelectionModel().getSelectedItem();
                if (entry == null) return;
                TextInputDialog ask = new TextInputDialog(AppClock.now().withSecond(0).withNano(0).format(TYPED));
                ask.setTitle(I18n.t("time.clock.close"));
                ask.setHeaderText(I18n.t("time.clock.close.ask", entry.userName()));
                AlertUtil.configure(ask);
                ask.showAndWait().ifPresent(text -> {
                    LocalDateTime at;
                    try {
                        at = LocalDateTime.parse(text.trim(), TYPED);
                    } catch (DateTimeParseException ex) {
                        message.setText(I18n.t("time.clock.val.time"));
                        return;
                    }
                    Async.run(() -> {
                        dao.closeForgotten(entry.id(), at);
                        return Boolean.TRUE;
                    }, ok -> refreshAll[0].run(), e -> message.setText(e.getMessage()));
                });
            });
            Button show = new Button(I18n.t("time.clock.show"));
            Button export = new Button(I18n.t("common.exportcsv")); // the hours for whoever does the pay (TODO 4.46)
            export.setOnAction(ev -> TimeClockExport.run(export, dao, from.getValue(), to.getValue(), message));
            refreshAll[0] = () -> {
                if (from.getValue() == null || to.getValue() == null || to.getValue().isBefore(from.getValue())) {
                    message.setText(I18n.t("time.clock.val.range"));
                    return;
                }
                LocalDate f = from.getValue();
                LocalDate t = to.getValue();
                Async.run(() -> new Object[]{dao.hours(f, t), dao.findOpen(), dao.salaries()}, data -> {
                    @SuppressWarnings("unchecked") var hours = (java.util.List<TimeClockDAO.Hours>) data[0];
                    @SuppressWarnings("unchecked") var open = (java.util.List<TimeClockEntry>) data[1];
                    @SuppressWarnings("unchecked") var salaries = (java.util.Map<Integer, java.math.BigDecimal>) data[2];
                    salaryByUser.clear();
                    salaryByUser.putAll(salaries);
                    hoursTable.getItems().setAll(hours);
                    openTable.getItems().setAll(open);
                }, e -> message.setText(e.getMessage()));
            };
            show.setOnAction(ev -> refreshAll[0].run());
            Button fix = new Button(I18n.t("time.clock.fix.open")); // correct a finished stretch or add a missed one (TODO 3.67)
            fix.setOnAction(ev -> TimeClockCorrectionDialog.show(() -> refreshAll[0].run()));
            Label estimateNote = new Label(I18n.t("time.clock.pay.note", I18n.t(ShopRules.current().salaryHourly() == 1
                    ? "time.clock.pay.basis.hourly" : "time.clock.pay.basis.monthly", ShopRules.current().monthlyHours())));
            estimateNote.setWrapText(true);
            box.getChildren().addAll(new Label(I18n.t("time.clock.admin.title")), new HBox(8, from, to, show, export), hoursTable, estimateNote,
                    new Label(I18n.t("time.clock.open.title")), openTable, new HBox(8, close, fix));
            refreshAll[0].run();
        }
        refreshMine.run();
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait();
    }

    private static <T> TableColumn<T, String> column(String title, double width, Function<T, String> text) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(cell -> new SimpleStringProperty(text.apply(cell.getValue())));
        return c;
    }
}
