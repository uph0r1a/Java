package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.TimeClockDAO;
import com.coffeeshop.coffeeshopmanagement.service.TimeClockService;
import com.coffeeshop.coffeeshopmanagement.model.TimeClockEntry;
import com.coffeeshop.coffeeshopmanagement.service.TimeClockRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * Administrator: correct the start or end of a finished stretch of work, or add one that was missed (TODO 3.67). A clock-out
 * entered late, or by mistake a minute after clocking in, used to stay as it was in the hours report. The rules are in
 * {@link TimeClockRules}, the checks and the audit line in {@link TimeClockDAO#correct} / {@link TimeClockDAO#addMissing}; this class
 * shows the finished stretches and asks for the new times and the reason. Database calls run off the screen thread.
 */
public final class TimeClockCorrectionDialog {

    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private TimeClockCorrectionDialog() {
    }

    /** @param afterChange run after a stretch was corrected or added (the caller refreshes its hours) */
    public static void show(Runnable afterChange) {
        TimeClockService dao = new TimeClockService();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("time.clock.fix.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        DatePicker from = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker(AppClock.today().withDayOfMonth(1));
        DatePicker to = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker(AppClock.today());
        Button show = new Button(I18n.t("time.clock.show"));
        Label message = new Label();
        message.setWrapText(true);
        TableView<TimeClockEntry> table = new TableView<>();
        table.setPrefHeight(260);
        table.setPlaceholder(new Label(I18n.t("time.clock.fix.empty")));
        table.getColumns().add(column(I18n.t("time.clock.col.name"), 170, e -> e.userName()));
        table.getColumns().add(column(I18n.t("time.clock.col.in"), 150, e -> SHOWN.format(e.clockIn())));
        table.getColumns().add(column(I18n.t("time.clock.col.out"), 150, e -> SHOWN.format(e.clockOut())));
        table.getColumns().add(column(I18n.t("time.clock.col.length"), 80,
                e -> TimeClockRules.hoursText(Duration.between(e.clockIn(), e.clockOut()).toMinutes())));
        Button correct = new Button(I18n.t("time.clock.fix.correct"));
        correct.setDisable(true);
        Button add = new Button(I18n.t("time.clock.fix.add"));
        table.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> correct.setDisable(now == null));

        Runnable refresh = () -> {
            if (from.getValue() == null || to.getValue() == null || to.getValue().isBefore(from.getValue())) {
                message.setText(I18n.t("time.clock.val.range"));
                return;
            }
            LocalDate f = from.getValue();
            LocalDate t = to.getValue();
            Async.run(() -> dao.findClosedBetween(f, t), list -> {
                table.getItems().setAll(list);
                message.setText("");
            }, e -> message.setText(e.getMessage()));
        };
        Runnable changed = () -> {
            refresh.run();
            if (afterChange != null) afterChange.run();
        };
        show.setOnAction(ev -> refresh.run());
        correct.setOnAction(ev -> {
            TimeClockEntry entry = table.getSelectionModel().getSelectedItem();
            if (entry != null) askAndSave(dao, entry, changed, message);
        });
        add.setOnAction(ev -> askAndSave(dao, null, changed, message));

        Label hint = new Label(I18n.t("time.clock.fix.hint"));
        hint.setWrapText(true);
        VBox box = new VBox(10, hint, new HBox(8, from, to, show), table, new HBox(8, correct, add), message);
        box.setPadding(new Insets(12));
        box.setPrefWidth(620);
        dialog.getDialogPane().setContent(box);
        refresh.run();
        dialog.showAndWait();
    }

    /**
     * The box for new times and a reason: for {@code entry} when one is given (a correction), else for a missed stretch of a person
     * chosen in the box. A refusal from the database is shown in the box, which stays open.
     */
    private static void askAndSave(TimeClockService dao, TimeClockEntry entry, Runnable changed, Label outerMessage) {
        Dialog<ButtonType> ask = new Dialog<>();
        ask.setTitle(entry != null ? I18n.t("time.clock.fix.correct.title", entry.userName()) : I18n.t("time.clock.fix.add.title"));
        AlertUtil.configure(ask);
        ButtonType save = new ButtonType(I18n.t("common.save"), javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        ask.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<TimeClockDAO.Person> person = new ComboBox<>();
        person.setPrefWidth(300);
        TextField in = new TextField(entry != null ? TimeClockRules.typed(entry.clockIn()) : "");
        TextField out = new TextField(entry != null ? TimeClockRules.typed(entry.clockOut()) : "");
        in.setPromptText("2026-10-30 08:00");
        out.setPromptText("2026-10-30 17:30");
        TextField reason = new TextField();
        reason.setPrefWidth(300);
        Label problem = new Label();
        problem.setWrapText(true);
        problem.setStyle("-fx-text-fill: #c0392b;");
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        int row = 0;
        if (entry == null) {
            grid.add(new Label(I18n.t("time.clock.fix.person")), 0, row);
            grid.add(person, 1, row++);
            Async.run(dao::people, list -> person.getItems().setAll(list), e -> problem.setText(e.getMessage()));
        }
        grid.add(new Label(I18n.t("time.clock.fix.in")), 0, row);
        grid.add(in, 1, row++);
        grid.add(new Label(I18n.t("time.clock.fix.out")), 0, row);
        grid.add(out, 1, row++);
        grid.add(new Label(I18n.t("time.clock.fix.reason")), 0, row);
        grid.add(reason, 1, row++);
        grid.add(problem, 0, row, 2, 1);
        ask.getDialogPane().setContent(grid);

        javafx.scene.Node saveButton = ask.getDialogPane().lookupButton(save);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume(); // the box closes only when the save went through
            LocalDateTime newIn = TimeClockRules.parseTyped(in.getText());
            LocalDateTime newOut = TimeClockRules.parseTyped(out.getText());
            if (newIn == null || newOut == null) {
                problem.setText(I18n.t("time.clock.val.time"));
                return;
            }
            if (entry == null && person.getValue() == null) {
                problem.setText(I18n.t("time.clock.val.person"));
                return;
            }
            saveButton.setDisable(true);
            String why = reason.getText();
            Async.run(() -> {
                if (entry != null) dao.correct(entry.id(), newIn, newOut, why);
                else dao.addMissing(person.getValue().userId(), newIn, newOut, why);
                return Boolean.TRUE;
            }, ok -> {
                ask.close();
                changed.run();
            }, e -> {
                saveButton.setDisable(false);
                problem.setText(e.getMessage());
            });
        });
        ask.showAndWait();
    }

    private static <T> TableColumn<T, String> column(String title, double width, Function<T, String> text) {
        TableColumn<T, String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(cell -> new SimpleStringProperty(text.apply(cell.getValue())));
        return c;
    }
}
