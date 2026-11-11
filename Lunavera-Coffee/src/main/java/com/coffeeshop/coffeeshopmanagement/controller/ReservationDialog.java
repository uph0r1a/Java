package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.ReservationService;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Reservation;
import com.coffeeshop.coffeeshopmanagement.service.ReservationRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
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
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * "Đặt bàn" (TODO 4.43, rule D11): write a booking down (table, name, phone, day and time, people, note), see the waiting ones, cancel
 * one, mark "khách không đến", or press "Nhận bàn" to seat the guests: the window closes and the sales screen opens the table's order
 * with the customer on it. The rules are in {@link ReservationRules}; {@link ReservationService} enforces them.
 */
final class ReservationDialog {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm dd/MM");

    private ReservationDialog() {
    }

    /** What adding a booking came to: the reason it was refused, or the booking that was written. */
    private record Added(String problem, Reservation made) {
    }

    static void show(List<DiningTable> tables, Consumer<Reservation> onSeat) {
        ReservationService dao = new ReservationService();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("reservation.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        ComboBox<DiningTable> tableBox = new ComboBox<>(FXCollections.observableArrayList(tables));
        tableBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(DiningTable t) {
                return t == null ? "" : t.displayName() + " (" + I18n.t("pos.capacity", t.getCapacity()) + ")";
            }

            @Override
            public DiningTable fromString(String text) {
                return null;
            }
        });
        tableBox.setPromptText(I18n.t("reservation.table.prompt"));
        TextField nameField = new TextField();
        nameField.setPromptText(I18n.t("reservation.name.prompt"));
        TextLengthLimiter.limit(nameField, ReservationRules.NAME_MAX);
        TextField phoneField = new TextField();
        phoneField.setPromptText(I18n.t("reservation.phone.prompt"));
        TextLengthLimiter.limit(phoneField, TextLengthLimiter.PHONE_MAX);
        DatePicker dayField = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker(AppClock.today());
        TextField timeField = new TextField("18:00");
        timeField.setPromptText("HH:mm");
        TextField peopleField = new TextField("2");
        TextField noteField = new TextField();
        noteField.setPromptText(I18n.t("reservation.note.prompt"));
        TextLengthLimiter.limit(noteField, ReservationRules.NOTE_MAX);
        Label status = new Label();
        status.setWrapText(true);
        Button addButton = new Button(I18n.t("reservation.add"));
        addButton.setDefaultButton(true);
        Label hint = new Label(I18n.t("reservation.hint", ReservationRules.slotMinutes(), ReservationRules.REMINDER_MINUTES));
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #6d4c41;");

        TableView<Reservation> list = bookingList(tables, 260);
        list.setPlaceholder(new Label(I18n.t("reservation.empty")));
        // The forgotten bookings (more than STALE_HOURS late) have a block of their own at the top (TODO 2.94): kept off the map and
        // out of the reminder, but never hidden from the people who can close them.
        TableView<Reservation> staleList = bookingList(tables, 120);
        Label staleTitle = new Label();
        staleTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #b71c1c;");
        Label staleHint = new Label(I18n.t("reservation.toclose.hint"));
        staleHint.setWrapText(true);
        Button closeOldButton = new Button(I18n.t("reservation.toclose.button"));
        closeOldButton.setVisible(Session.isAdmin()); // the database refuses anybody else as well
        closeOldButton.setManaged(Session.isAdmin());
        VBox staleBox = new VBox(6, staleTitle, staleHint, staleList, closeOldButton);
        staleBox.setVisible(false);
        staleBox.setManaged(false);
        Button seatButton = new Button(I18n.t("reservation.seat"));
        Button cancelButton = new Button(I18n.t("reservation.cancel"));
        Button noShowButton = new Button(I18n.t("reservation.noshow"));
        for (Button b : List.of(seatButton, cancelButton, noShowButton)) b.setDisable(true);
        // The buttons act on the selected line of either list; selecting in one clears the other.
        Supplier<Reservation> selected = () -> list.getSelectionModel().getSelectedItem() != null
                ? list.getSelectionModel().getSelectedItem() : staleList.getSelectionModel().getSelectedItem();
        list.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            if (now != null) staleList.getSelectionModel().clearSelection();
            for (Button b : List.of(seatButton, cancelButton, noShowButton)) b.setDisable(selected.get() == null);
        });
        staleList.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> {
            if (now != null) list.getSelectionModel().clearSelection();
            for (Button b : List.of(seatButton, cancelButton, noShowButton)) b.setDisable(selected.get() == null);
        });

        Runnable reload = () -> Async.run(() -> dao.findWaiting(), waiting -> { // TODO 6.14: read in the background
            LocalDateTime now = AppClock.now();
            list.getItems().setAll(waiting.stream().filter(r -> !ReservationRules.isStale(r, now)).toList());
            List<Reservation> forgotten = waiting.stream().filter(r -> ReservationRules.isStale(r, now)).toList();
            staleList.getItems().setAll(forgotten);
            staleList.setPrefHeight(Math.min(150, 34 + 26.0 * forgotten.size()));
            staleTitle.setText(I18n.t("reservation.toclose.title", ReservationRules.STALE_HOURS, forgotten.size()));
            staleBox.setVisible(!forgotten.isEmpty());
            staleBox.setManaged(!forgotten.isEmpty());
        }, error -> AlertUtil.error(I18n.t("reservation.title"), error.getMessage()));
        reload.run();

        addButton.setOnAction(e -> {
            DiningTable table = tableBox.getValue();
            LocalDateTime when = parseWhen(dayField.getValue(), timeField.getText());
            int people = parsePeople(peopleField.getText());
            // the boxes are read here, a background step never touches a text box
            String name = nameField.getText();
            String phone = phoneField.getText();
            String note = noteField.getText();
            Async.run(() -> {
                // The rules judge a party bigger than the table on the seats of the other free tables (TODO 3.85), so the database is asked
                String problem = table == null ? I18n.t("reservation.val.table")
                        : when == null ? I18n.t("reservation.val.time")
                        : dao.problemFor(table.getTableNumber(), name, phone, when, people, note);
                if (problem != null) return new Added(problem, null);
                return new Added(null, dao.add(table.getTableNumber(), name, phone, when, people, note));
            }, added -> {
                if (added.problem() != null) {
                    status.setText(added.problem());
                    return;
                }
                Reservation made = added.made();
                status.setText(I18n.t("reservation.saved", made.customerName(), WHEN.format(made.reservedFor()))
                        + (people > table.getCapacity() ? "  " + made.note() : "")); // which tables to push together
                nameField.clear();
                phoneField.clear();
                noteField.clear();
                reload.run();
                nameField.requestFocus();
            }, error -> status.setText(error.getMessage()), addButton);
        });
        cancelButton.setOnAction(e -> closeSelected(selected, dao, Reservation.Status.CANCELLED, "reservation.cancel.confirm", reload, status));
        noShowButton.setOnAction(e -> closeSelected(selected, dao, Reservation.Status.NO_SHOW, "reservation.noshow.confirm", reload, status));
        seatButton.setOnAction(e -> {
            Reservation chosen = selected.get();
            if (chosen == null) return;
            dialog.close(); // the sales screen takes over: it opens the order and then marks the booking as seated
            onSeat.accept(chosen);
        });
        closeOldButton.setOnAction(e -> {
            int count = staleList.getItems().size();
            if (count == 0 || !AlertUtil.confirm(I18n.t("reservation.title"), I18n.t("reservation.toclose.confirm", count))) return;
            Async.run(() -> dao.closeStale(AppClock.now()), closed -> {
                status.setText(I18n.t("reservation.toclose.done", closed));
                reload.run();
            }, error -> AlertUtil.error(I18n.t("reservation.title"), error.getMessage()), closeOldButton); // a refused permission is shown the same way
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        int row = 0;
        form.addRow(row++, new Label(I18n.t("reservation.label.table")), tableBox);
        form.addRow(row++, new Label(I18n.t("reservation.label.name")), nameField);
        form.addRow(row++, new Label(I18n.t("reservation.label.phone")), phoneField);
        form.addRow(row++, new Label(I18n.t("reservation.label.when")), new HBox(8, dayField, timeField));
        form.addRow(row++, new Label(I18n.t("reservation.label.people")), peopleField);
        form.addRow(row++, new Label(I18n.t("reservation.label.note")), noteField);
        form.add(addButton, 1, row);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(noteField, Priority.ALWAYS);

        VBox content = new VBox(12, hint, staleBox, form, status, list, new HBox(8, seatButton, cancelButton, noShowButton));
        content.setPadding(new Insets(16));
        content.setPrefWidth(820);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private static void closeSelected(Supplier<Reservation> chosen, ReservationService dao, Reservation.Status to, String confirmKey,
                                      Runnable reload, Label status) {
        Reservation selected = chosen.get();
        if (selected == null) return;
        if (!AlertUtil.confirm(I18n.t("reservation.title"), I18n.t(confirmKey, selected.customerName(), WHEN.format(selected.reservedFor())))) return;
        Async.run(() -> dao.close(selected.id(), to), closed -> {
            if (!closed) status.setText(I18n.t("reservation.closed.already"));
            reload.run();
        }, error -> AlertUtil.error(I18n.t("reservation.title"), error.getMessage()));
    }

    /** The day and the typed "HH:mm" as one moment; null when either is missing or not a time. */
    static LocalDateTime parseWhen(LocalDate day, String typed) {
        if (day == null || typed == null) return null;
        try {
            return day.atTime(LocalTime.parse(typed.trim().length() == 4 && typed.trim().charAt(1) == ':' ? "0" + typed.trim() : typed.trim()));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static int parsePeople(String typed) {
        try {
            return Integer.parseInt(typed.trim());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String tableName(List<DiningTable> tables, int number) {
        for (DiningTable t : tables) if (t.getTableNumber() == number) return t.displayName();
        return I18n.t("orders.table", number);
    }

    /** A list of bookings with the columns of the booking window. */
    private static TableView<Reservation> bookingList(List<DiningTable> tables, double height) {
        TableView<Reservation> list = new TableView<>();
        list.setPrefHeight(height);
        list.getColumns().add(column(I18n.t("reservation.col.when"), 110, r -> WHEN.format(r.reservedFor())));
        list.getColumns().add(column(I18n.t("reservation.col.table"), 90, r -> tableName(tables, r.tableNumber())));
        list.getColumns().add(column(I18n.t("reservation.col.name"), 160, Reservation::customerName));
        list.getColumns().add(column(I18n.t("reservation.col.phone"), 110, r -> r.phone() == null ? "" : r.phone()));
        list.getColumns().add(column(I18n.t("reservation.col.people"), 60, r -> String.valueOf(r.people())));
        list.getColumns().add(column(I18n.t("reservation.col.note"), 200, r -> r.note() == null ? "" : r.note()));
        return list;
    }

    private static TableColumn<Reservation, String> column(String title, double width, java.util.function.Function<Reservation, String> text) {
        TableColumn<Reservation, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellValueFactory(data -> new SimpleStringProperty(text.apply(data.getValue())));
        return column;
    }
}
