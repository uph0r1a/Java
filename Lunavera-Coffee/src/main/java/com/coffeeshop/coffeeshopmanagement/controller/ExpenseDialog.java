package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.ExpenseDAO;
import com.coffeeshop.coffeeshopmanagement.model.Expense;
import com.coffeeshop.coffeeshopmanagement.service.ExpenseRules;
import com.coffeeshop.coffeeshopmanagement.service.StockRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SessionGuard;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * "Chi phí" (TODO 4.38): the expenses of the shop. {@link #show()} is the administrator's window: write an expense down (day, amount,
 * category, note, and whether it came out of the cash drawer), see the expenses of a range of days with their total, and delete one
 * written by mistake. {@link #showCashOut} is the small box for any signed-in person to write down cash taken out of the drawer, used
 * from "Chốt ca" so the drawer's expected cash is right. The rules are in {@link ExpenseRules}; {@link ExpenseDAO} enforces them and
 * who may do what, so this class only collects the typing.
 */
public final class ExpenseDialog {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private ExpenseDialog() {
    }

    private static ComboBox<String> categoryBox() {
        ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(ExpenseRules.CATEGORIES));
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(String code) {
                return code == null ? "" : ExpenseRules.categoryName(code);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        box.setPromptText(I18n.t("expense.category.prompt"));
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    // ------------------------------------------------------------------ the administrator's window

    /** The expenses window (administrators only: the account is read again first, as for the report). */
    public static void show() {
        if (!SessionGuard.validateNow()) {
            SessionGuard.forceLogout();
            return;
        }
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("expense.adminonly"));
            return;
        }
        ExpenseDAO dao = new ExpenseDAO();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("expense.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        DatePicker dayField = new DatePicker(AppClock.today());
        TextField amountField = new TextField();
        amountField.setPromptText(I18n.t("expense.amount.prompt"));
        ComboBox<String> categoryField = categoryBox();
        CheckBox drawerBox = new CheckBox(I18n.t("expense.drawer"));
        TextField noteField = new TextField();
        noteField.setPromptText(I18n.t("expense.note.prompt"));
        TextLengthLimiter.limit(noteField, StockRules.TEXT_MAX);
        Label status = new Label();
        status.setWrapText(true);
        Button addButton = new Button(I18n.t("expense.add"));
        addButton.setDefaultButton(true);
        Label hint = new Label(I18n.t("expense.hint"));
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #6d4c41;");

        // the list: a range of days (this month by default) with its total
        DatePicker fromField = new DatePicker(AppClock.today().withDayOfMonth(1));
        DatePicker toField = new DatePicker(AppClock.today());
        Button showButton = new Button(I18n.t("expense.show"));
        Label totalLabel = new Label();
        totalLabel.setStyle("-fx-font-weight: bold;");
        TableView<Expense> table = new TableView<>();
        table.setPrefHeight(280);
        table.setPlaceholder(new Label(I18n.t("expense.empty")));
        table.getColumns().add(column(I18n.t("expense.col.day"), 90, e -> DAY.format(e.spentOn())));
        table.getColumns().add(column(I18n.t("expense.col.category"), 130, e -> ExpenseRules.categoryName(e.category())));
        table.getColumns().add(column(I18n.t("expense.col.amount"), 120, e -> CurrencyUtil.format(e.amount())));
        table.getColumns().add(column(I18n.t("expense.col.drawer"), 80, e -> e.fromDrawer() ? I18n.t("expense.yes") : ""));
        table.getColumns().add(column(I18n.t("expense.col.note"), 200, e -> e.note() == null ? "" : e.note()));
        table.getColumns().add(column(I18n.t("expense.col.by"), 120, e -> e.recordedByName() == null ? "" : e.recordedByName()));
        table.getColumns().add(column(I18n.t("expense.col.written"), 130, e -> e.createdAt() == null ? "" : TIME.format(e.createdAt())));
        Button deleteButton = new Button(I18n.t("expense.delete"));
        deleteButton.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> deleteButton.setDisable(now == null));

        Runnable reload = () -> {
            LocalDate from = fromField.getValue();
            LocalDate to = toField.getValue();
            if (from == null || to == null || to.isBefore(from)) {
                status.setText(I18n.t("expense.val.range"));
                return;
            }
            try {
                table.getItems().setAll(dao.find(from, to, ExpenseDAO.DEFAULT_LIMIT));
                BigDecimal total = BigDecimal.ZERO;
                for (Expense e : table.getItems()) total = total.add(e.amount());
                totalLabel.setText(I18n.t("expense.total", table.getItems().size(), CurrencyUtil.format(total)));
            } catch (DataAccessException ex) {
                AlertUtil.error(I18n.t("expense.title"), ex.getMessage());
            }
        };
        reload.run();
        showButton.setOnAction(e -> reload.run());

        addButton.setOnAction(e -> {
            BigDecimal amount = MoneyParser.parseOptionalDong(amountField.getText());
            String problem = ExpenseRules.problem(dayField.getValue(), amount, categoryField.getValue(), AppClock.today());
            if (problem != null) {
                status.setText(problem);
                return;
            }
            try {
                dao.add(dayField.getValue(), amount, categoryField.getValue(), noteField.getText(), drawerBox.isSelected());
                status.setText(I18n.t("expense.saved", CurrencyUtil.format(amount)));
                amountField.clear();
                noteField.clear();
                drawerBox.setSelected(false);
                reload.run();
                amountField.requestFocus();
            } catch (DataAccessException ex) {
                status.setText(ex.getMessage());
            }
        });
        deleteButton.setOnAction(e -> {
            Expense selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            if (!AlertUtil.confirm(I18n.t("expense.delete.title"), I18n.t("expense.delete.confirm",
                    ExpenseRules.categoryName(selected.category()), CurrencyUtil.format(selected.amount()), DAY.format(selected.spentOn())))) {
                return;
            }
            try {
                dao.delete(selected.id());
                reload.run();
            } catch (DataAccessException ex) {
                AlertUtil.error(I18n.t("expense.title"), ex.getMessage());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        int row = 0;
        form.addRow(row++, new Label(I18n.t("expense.label.day")), dayField);
        form.addRow(row++, new Label(I18n.t("expense.label.amount")), amountField);
        form.addRow(row++, new Label(I18n.t("expense.label.category")), categoryField);
        form.add(drawerBox, 1, row++);
        form.addRow(row++, new Label(I18n.t("expense.label.note")), noteField);
        form.add(addButton, 1, row++);
        GridPane.setHgrow(amountField, Priority.ALWAYS);
        GridPane.setHgrow(noteField, Priority.ALWAYS);

        HBox range = new HBox(8, new Label(I18n.t("expense.label.from")), fromField, new Label(I18n.t("expense.label.to")), toField,
                showButton, totalLabel);
        range.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox content = new VBox(12, hint, form, status, range, table, deleteButton);
        content.setPadding(new Insets(16));
        content.setPrefWidth(900);
        dialog.getDialogPane().setContent(content);
        javafx.application.Platform.runLater(amountField::requestFocus);
        dialog.showAndWait();
    }

    // ------------------------------------------------------------------ cash out of the drawer, for anyone signed in

    /**
     * The small box for cash taken out of the drawer (amount, category, note; dated today). Any signed-in person may use it.
     *
     * @param onSaved called after the line was saved, so the caller (the shift close) can total the drawer again
     */
    public static void showCashOut(Runnable onSaved) {
        ExpenseDAO dao = new ExpenseDAO();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("expense.cashout.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText(I18n.t("expense.cashout.save"));
        AlertUtil.setDefaultButton(dialog, ButtonType.OK);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        TextField amountField = new TextField();
        amountField.setPromptText(I18n.t("expense.amount.prompt"));
        ComboBox<String> categoryField = categoryBox();
        categoryField.setValue(ExpenseRules.OTHER);
        TextField noteField = new TextField();
        noteField.setPromptText(I18n.t("expense.note.prompt"));
        TextLengthLimiter.limit(noteField, StockRules.TEXT_MAX);
        Label info = new Label(I18n.t("expense.cashout.info"));
        info.setWrapText(true);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        int row = 0;
        form.addRow(row++, new Label(I18n.t("expense.label.amount")), amountField);
        form.addRow(row++, new Label(I18n.t("expense.label.category")), categoryField);
        form.addRow(row++, new Label(I18n.t("expense.label.note")), noteField);
        GridPane.setHgrow(amountField, Priority.ALWAYS);
        VBox content = new VBox(10, info, form);
        content.setPadding(new Insets(16));
        content.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);
        javafx.application.Platform.runLater(amountField::requestFocus);

        final boolean[] saved = new boolean[1];
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, event -> {
            BigDecimal amount = MoneyParser.parseOptionalDong(amountField.getText());
            String problem = ExpenseRules.problem(AppClock.today(), amount, categoryField.getValue(), AppClock.today());
            if (problem != null) {
                AlertUtil.warning(I18n.t("expense.cashout.title"), problem);
                event.consume();
                return;
            }
            try {
                dao.add(AppClock.today(), amount, categoryField.getValue(), noteField.getText(), true);
                saved[0] = true;
            } catch (DataAccessException ex) {
                AlertUtil.error(I18n.t("expense.cashout.title"), ex.getMessage());
                event.consume();
            }
        });
        dialog.showAndWait();
        if (saved[0] && onSaved != null) onSaved.run();
    }

    private static TableColumn<Expense, String> column(String title, double width, java.util.function.Function<Expense, String> text) {
        TableColumn<Expense, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellValueFactory(data -> new SimpleStringProperty(text.apply(data.getValue())));
        return column;
    }
}
