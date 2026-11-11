package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO;
import com.coffeeshop.coffeeshopmanagement.model.ShiftClose;
import com.coffeeshop.coffeeshopmanagement.service.ShiftCash;
import com.coffeeshop.coffeeshopmanagement.service.ShiftService;
import com.coffeeshop.coffeeshopmanagement.service.StockRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.OffScreenThread;
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
 * "Chốt ca" (TODO 4.6; TODO 5.48 warns about tables that are still unpaid): any signed-in person counts the cash in the drawer at the end of a shift. The box shows what was sold since the
 * previous close, how much of it was cash, and who took it; the cashier types what the drawer started with (filled with what was
 * counted last time) and what is in it now, sees the difference as they type, and confirms. Cash taken out of the drawer for expenses
 * during the shift (TODO 4.38) is taken off the cash expected; "Ghi chi từ két" writes down one more while the box is open. The line
 * is kept in {@code shift_closes} and the next shift starts where this one ended.
 */
public final class ShiftCloseDialog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private ShiftCloseDialog() {
    }

    /** What the box shows when it opens: read in the background, the box is built when it is here (TODO 6.14). */
    private record Opening(LocalDateTime from, ShiftDAO.Summary summary, BigDecimal startingCash, BigDecimal drawerOut,
                           String giftOpen, // TODO 4.45: what the shop still owes on gift cards
                           String byMethod, // TODO 4.30: cash, card and transfer of the shift, to match the bank statement
                           String unpaid) { // TODO 5.48: the tables and take-away orders that are still open: their money falls into the next shift
    }

    @OffScreenThread
    private static Opening read() {
        ShiftService dao = new ShiftService();
        LocalDateTime from = dao.currentShiftStart();
        return new Opening(from, dao.summarize(from, AppClock.now()), dao.lastCountedCash().orElse(BigDecimal.ZERO),
                dao.cashOut(from, AppClock.now()), // expenses already taken out of the drawer in this shift (TODO 4.38)
                com.coffeeshop.coffeeshopmanagement.util.GiftCardText.line(dao.giftCardsOpenBalance()),
                com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService.methodsText(dao.paymentSplit(from, AppClock.now())),
                com.coffeeshop.coffeeshopmanagement.service.ShiftOpenOrders.text(dao.openOrders()));
    }

    public static void show() {
        Async.run(ShiftCloseDialog::read, ShiftCloseDialog::open,
                error -> AlertUtil.error(I18n.t("shift.load.error"), error.getMessage()));
    }

    private static void open(Opening loaded) {
        ShiftService dao = new ShiftService();
        final LocalDateTime from = loaded.from();
        final BigDecimal startingCash = loaded.startingCash();
        final String giftOpen = loaded.giftOpen();
        final String byMethod = loaded.byMethod();
        final String unpaid = loaded.unpaid();
        final LocalDateTime shiftStart = from;
        final ShiftDAO.Summary totals = loaded.summary();
        final BigDecimal[] cashOut = {loaded.drawerOut()}; // grows when the cashier writes down more cash taken out while this box is open


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
        Label methods = new Label(byMethod.isEmpty() ? "" : I18n.t("shift.bymethod") + "\n" + byMethod);
        methods.setWrapText(true);
        methods.setVisible(!byMethod.isEmpty());
        methods.setManaged(!byMethod.isEmpty());
        Label giftLabel = new Label(giftOpen);
        giftLabel.setWrapText(true);
        giftLabel.setVisible(!giftOpen.isEmpty());
        giftLabel.setManaged(!giftOpen.isEmpty());
        Label unpaidLabel = new Label(unpaid); // a warning only: the cashier can still close
        unpaidLabel.setWrapText(true);
        unpaidLabel.setStyle("-fx-text-fill: #8a5a00; -fx-font-weight: bold;");
        unpaidLabel.setVisible(!unpaid.isEmpty());
        unpaidLabel.setManaged(!unpaid.isEmpty());
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
        // After the cash-out box saved a line, the total is read again in the background (TODO 6.14); the button waits meanwhile.
        cashOutButton.setOnAction(e -> ExpenseDialog.showCashOut(() -> Async.run(
                () -> dao.cashOut(shiftStart, AppClock.now()),
                freshCashOut -> {
                    cashOut[0] = freshCashOut;
                    cashOutLabel.setText(CurrencyUtil.format(cashOut[0]));
                    recalc.run();
                },
                failure -> AlertUtil.error(I18n.t("shift.load.error"), failure.getMessage()),
                cashOutButton)));

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

        VBox content = new VBox(10, period, sold, methods, giftLabel, unpaidLabel);
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
            ShiftSheetActions.offerAfterClose(stored[0]); // says it is closed and offers the sheet to print or save (TODO 4.53)
        }
    }
}
