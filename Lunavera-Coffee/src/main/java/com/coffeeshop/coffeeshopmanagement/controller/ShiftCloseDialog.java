package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO;
import com.coffeeshop.coffeeshopmanagement.model.ShiftClose;
import com.coffeeshop.coffeeshopmanagement.service.ShiftCash;
import com.coffeeshop.coffeeshopmanagement.service.StockRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * "Chốt ca" (TODO 4.6): any signed-in person counts the cash in the drawer at the end of a shift. The box shows what was sold since the
 * previous close, how much of it was cash, and who took it; the cashier types what the drawer started with (filled with what was
 * counted last time) and what is in it now, sees the difference as they type, and confirms. Cash taken out of the drawer for expenses
 * during the shift (TODO 4.38) is taken off the cash expected; "Ghi chi từ két" writes down one more while the box is open. The line
 * is kept in {@code shift_closes} and the next shift starts where this one ended.
 */
public final class ShiftCloseDialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private ShiftCloseDialog() {
    }

    public static void show() {
        ShiftDAO dao = new ShiftDAO();
        LocalDateTime from;
        ShiftDAO.Summary summary;
        BigDecimal startingCash;
        BigDecimal drawerOut;
        try {
            from = dao.currentShiftStart();
            summary = dao.summarize(from, AppClock.now());
            startingCash = dao.lastCountedCash().orElse(BigDecimal.ZERO);
            drawerOut = dao.cashOut(from, AppClock.now()); // expenses already taken out of the drawer in this shift (TODO 4.38)
        } catch (DataAccessException e) {
            AlertUtil.error(I18n.t("shift.load.error"), e.getMessage());
            return;
        }
        final LocalDateTime shiftStart = from;
        final ShiftDAO.Summary totals = summary;
        final BigDecimal[] cashOut = {drawerOut}; // grows when the cashier writes down more cash taken out while this box is open

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("shift.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((javafx.scene.control.Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText(I18n.t("shift.open"));
        AlertUtil.setDefaultButton(dialog, ButtonType.OK);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        Label period = new Label(I18n.t("shift.period", TIME.format(shiftStart)));
        period.setWrapText(true);
        Label sold = new Label(I18n.t("shift.summary", totals.paidOrders(), CurrencyUtil.format(totals.revenue()),
                CurrencyUtil.format(totals.cashSales())));
        sold.setWrapText(true);
        sold.setStyle("-fx-font-weight: bold;");
        VBox people = new VBox(2);
        for (ShiftDAO.EmployeeShare share : totals.byEmployee()) {
            String name = share.employeeName() == null || share.employeeName().isBlank() ? I18n.t("report.employee.unknown") : share.employeeName();
            people.getChildren().add(new Label("   " + name + ": " + share.orders() + " / " + CurrencyUtil.format(share.revenue())
                    + " (" + CurrencyUtil.format(share.cash()) + ")"));
        }

        TextField openingField = new TextField(startingCash.toPlainString());
        TextField countedField = new TextField();
        countedField.setPromptText(I18n.t("shift.label.counted"));
        TextField noteField = new TextField();
        TextLengthLimiter.limit(noteField, StockRules.TEXT_MAX);
        Label expectedLabel = new Label();
        Label differenceLabel = new Label();
        differenceLabel.setWrapText(true);
        Runnable recalc = () -> {
            BigDecimal opening = MoneyParser.parseOptionalDong(openingField.getText());
            BigDecimal expected = opening == null ? null : ShiftCash.expected(opening, totals.cashSales(), cashOut[0]);
            expectedLabel.setText(expected == null ? "" : CurrencyUtil.format(expected));
            BigDecimal counted = countedField.getText() == null || countedField.getText().isBlank()
                    ? null : MoneyParser.parseOptionalDong(countedField.getText());
            if (expected == null || counted == null) {
                differenceLabel.setText("");
                return;
            }
            BigDecimal difference = ShiftCash.difference(counted, expected);
            differenceLabel.setText(difference.signum() == 0 ? I18n.t("shift.diff.ok")
                    : difference.signum() > 0 ? I18n.t("shift.diff.over", CurrencyUtil.format(difference))
                    : I18n.t("shift.diff.short", CurrencyUtil.format(difference.negate())));
            differenceLabel.setStyle(difference.signum() == 0 ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828; -fx-font-weight: bold;");
        };
        openingField.textProperty().addListener((o, a, b) -> recalc.run());
        countedField.textProperty().addListener((o, a, b) -> recalc.run());
        recalc.run();

        Label cashOutLabel = new Label(CurrencyUtil.format(cashOut[0]));
        Button cashOutButton = new Button(I18n.t("shift.cashout.add"));
        cashOutButton.setOnAction(e -> ExpenseDialog.showCashOut(() -> {
            try {
                cashOut[0] = dao.cashOut(shiftStart, AppClock.now());
            } catch (DataAccessException ex) {
                AlertUtil.error(I18n.t("shift.load.error"), ex.getMessage());
            }
            cashOutLabel.setText(CurrencyUtil.format(cashOut[0]));
            recalc.run();
        }));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        int row = 0;
        grid.addRow(row++, new Label(I18n.t("shift.label.opening")), openingField);
        grid.addRow(row++, new Label(I18n.t("shift.label.cashout")), new HBox(8, cashOutLabel, cashOutButton));
        grid.addRow(row++, new Label(I18n.t("shift.label.counted")), countedField);
        grid.addRow(row++, new Label(I18n.t("shift.label.expected")), expectedLabel);
        grid.addRow(row++, new Label(I18n.t("shift.label.difference")), differenceLabel);
        grid.addRow(row++, new Label(I18n.t("shift.label.note")), noteField);

        VBox content = new VBox(10, period, sold);
        if (!totals.byEmployee().isEmpty()) {
            content.getChildren().addAll(new Label(I18n.t("shift.byemployee")), people);
        }
        content.getChildren().add(grid);
        content.setPadding(new Insets(16));
        content.setPrefWidth(480);
        dialog.getDialogPane().setContent(content);
        javafx.application.Platform.runLater(countedField::requestFocus);

        final ShiftClose[] stored = new ShiftClose[1];
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, event -> {
            BigDecimal opening = MoneyParser.parseOptionalDong(openingField.getText());
            BigDecimal counted = countedField.getText() == null || countedField.getText().isBlank()
                    ? null : MoneyParser.parseOptionalDong(countedField.getText());
            if (opening == null || counted == null) {
                AlertUtil.warning(I18n.t("shift.title"), I18n.t("shift.val.amount"));
                event.consume();
                return;
            }
            BigDecimal difference = ShiftCash.difference(counted, ShiftCash.expected(opening, totals.cashSales(), cashOut[0]));
            if (!AlertUtil.confirm(I18n.t("shift.confirm.title"),
                    I18n.t("shift.confirm.message", CurrencyUtil.format(counted), CurrencyUtil.format(difference)))) {
                event.consume();
                return;
            }
            try {
                stored[0] = dao.close(shiftStart, opening, counted, noteField.getText());
            } catch (DataAccessException e) {
                AlertUtil.error(I18n.t("shift.title"), e.getMessage());
                event.consume();
            }
        });
        dialog.showAndWait();
        if (stored[0] != null) {
            AlertUtil.info(I18n.t("shift.title"), I18n.t("shift.closed", CurrencyUtil.format(stored[0].difference())));
        }
    }
}
