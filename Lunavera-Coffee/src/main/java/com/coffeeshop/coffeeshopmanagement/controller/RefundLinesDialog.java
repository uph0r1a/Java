package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.RefundDefaults;
import com.coffeeshop.coffeeshopmanagement.service.RefundService;
import com.coffeeshop.coffeeshopmanagement.dao.RefundDAO;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.service.RefundMath;
import com.coffeeshop.coffeeshopmanagement.service.StockRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Money;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "Hoàn từng dòng" (TODO 4.37): refund some pieces of some lines of a paid order instead of the whole order. The box lists the
 * lines with what is left of each, a number of pieces to give back per line, how the money goes back, whether the goods return to
 * stock (untick for a drink already made), and an optional reason; the total to give back is worked out as the numbers change
 * ({@link RefundMath}). The refund itself, with its checks, is {@link RefundService#refund}, one transaction.
 */
public final class RefundLinesDialog {

    private RefundLinesDialog() {
    }

    /** Loads the order's lines (off the screen thread) and opens the box; {@code onDone} runs after a refund was made. */
    public static void show(Order order, Runnable onDone) {
        RefundService dao = new RefundService();
        Async.run(
                () -> dao.load(order.getId()),
                state -> open(order, state, dao, onDone),
                error -> AlertUtil.error(I18n.t("refund.title", order.getId()), error.getMessage()));
    }

    private static void open(Order order, RefundDAO.State state, RefundService dao, Runnable onDone) {
        final int orderId = order.getId();
        String title = I18n.t("refund.title", orderId);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setText(I18n.t("refund.do"));
        AlertUtil.setDefaultButton(dialog, ButtonType.CANCEL); // a refund cannot be undone: Enter does not do it
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        ComboBox<PaymentMethod> methodBox = new ComboBox<>(FXCollections.observableArrayList(java.util.Arrays.stream(PaymentMethod.values()).filter(m -> m != PaymentMethod.GIFT_CARD && m != PaymentMethod.COMPLIMENTARY).toList()));
        methodBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(PaymentMethod method) {
                return method == null ? "" : method.label();
            }

            @Override
            public PaymentMethod fromString(String text) {
                return null;
            }
        });
        methodBox.setValue(order.getPaymentMethod() != null && order.getPaymentMethod() != PaymentMethod.GIFT_CARD && !order.getPaymentMethod().isComplimentary() ? order.getPaymentMethod() : PaymentMethod.CASH); // never the card: it is not in the list, and the card part goes back by itself (TODO 2.93)
        Label total = new Label();
        total.setStyle("-fx-font-weight: bold;");
        Label split = new Label(); // TODO 2.93: an order paid with a gift card gives the card its money back first
        split.setWrapText(true);
        Map<Integer, Spinner<Integer>> spinners = new LinkedHashMap<>();
        Runnable recalc = () -> {
            Map<Integer, Integer> request = requestOf(spinners);
            boolean valid = RefundMath.problem(state.mathLines(), request) == null;
            long amount = valid ? state.preview(request).amount() : 0;
            long card = state.cardPart(amount);
            total.setText(I18n.t("refund.total", CurrencyUtil.format(Money.fromDong(amount))));
            split.setText(card > 0 ? I18n.t("refund.split", CurrencyUtil.format(Money.fromDong(card)),
                    CurrencyUtil.format(Money.fromDong(amount - card))) : "");
            split.setManaged(card > 0);
            split.setVisible(card > 0);
            methodBox.setDisable(card > 0 && card == amount); // the card takes all of it: nothing is left to choose a method for
            okButton.setDisable(!valid);
        };

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        int row = 0;
        for (RefundDAO.RefundableLine line : state.lines()) {
            Label name = new Label(line.productName());
            name.setWrapText(true);
            Label sold = new Label(I18n.t("refund.line.state", line.quantity(), line.refundedQuantity(), CurrencyUtil.format(line.lineTotal())));
            Spinner<Integer> spinner = new Spinner<>(0, Math.max(0, line.remaining()), 0);
            spinner.setPrefWidth(90);
            spinner.setDisable(line.remaining() <= 0);
            spinner.valueProperty().addListener((o, was, now) -> recalc.run());
            spinners.put(line.itemId(), spinner);
            grid.add(name, 0, row);
            grid.add(sold, 1, row);
            grid.add(spinner, 2, row);
            GridPane.setHgrow(name, Priority.ALWAYS);
            row++;
        }

        CheckBox restockBox = new CheckBox(I18n.t("refund.restock"));
        restockBox.setSelected(true);
        TextField reasonField = new TextField();
        reasonField.setPromptText(I18n.t("refund.reason.prompt"));
        TextLengthLimiter.limit(reasonField, StockRules.TEXT_MAX);

        // TODO 6.90 d: made-to-order lines are thrown away, not put back; the answers follow the lines being refunded until the cashier changes them
        final boolean[] restockTouched = {false};
        final String[] suggested = {""};
        restockBox.setOnAction(e -> restockTouched[0] = true);
        Runnable defaults = () -> {
            boolean made = RefundDefaults.anyMade(state.lines(), requestOf(spinners));
            if (!restockTouched[0]) restockBox.setSelected(RefundDefaults.returnToStock(made));
            String key = RefundDefaults.reasonKey(made);
            String text = key == null ? "" : I18n.t(key);
            if (reasonField.getText().isEmpty() || reasonField.getText().equals(suggested[0])) reasonField.setText(text);
            suggested[0] = text;
        };
        spinners.values().forEach(s -> s.valueProperty().addListener((o, was, now) -> defaults.run()));

        GridPane options = new GridPane();
        options.setHgap(12);
        options.setVgap(8);
        options.addRow(0, new Label(I18n.t("refund.method")), methodBox);
        options.add(restockBox, 1, 1);
        options.addRow(2, new Label(I18n.t("refund.reason")), reasonField);
        GridPane.setHgrow(reasonField, Priority.ALWAYS);

        Label intro = new Label(I18n.t("refund.intro"));
        intro.setWrapText(true);
        VBox content = new VBox(12, intro, grid, options, total, split);
        content.setPadding(new Insets(16));
        content.setPrefWidth(620);
        dialog.getDialogPane().setContent(content);
        recalc.run();

        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume(); // the box closes by itself once the refund went through
            Map<Integer, Integer> request = requestOf(spinners);
            String problem = RefundMath.problem(state.mathLines(), request);
            if (problem != null) {
                AlertUtil.warning(title, I18n.t(problem));
                return;
            }
            RefundMath.Plan plan = state.preview(request);
            long card = state.cardPart(plan.amount());
            if (!AlertUtil.confirm(I18n.t("refund.confirm.title"), I18n.t("refund.confirm",
                    CurrencyUtil.format(Money.fromDong(plan.amount())), plan.lines().size(), orderId)
                    + (card > 0 ? "\n\n" + I18n.t("refund.confirm.card", CurrencyUtil.format(Money.fromDong(card))) : ""))) {
                return;
            }
            okButton.setDisable(true);
            final boolean returnToStock = restockBox.isSelected();
            final PaymentMethod method = methodBox.getValue();
            final String reason = reasonField.getText();
            Async.run(
                    () -> dao.refund(orderId, request, returnToStock, method, reason),
                    result -> {
                        dialog.close();
                        AlertUtil.info(title, I18n.t("refund.done", CurrencyUtil.format(result.amount()))
                                + (result.toCard().signum() > 0 ? " " + I18n.t("refund.done.card", CurrencyUtil.format(result.toCard())) : ""));
                        if (onDone != null) onDone.run();
                    },
                    error -> {
                        okButton.setDisable(false);
                        AlertUtil.error(I18n.t("refund.fail"), error.getMessage());
                    });
        });
        dialog.showAndWait();
    }

    private static Map<Integer, Integer> requestOf(Map<Integer, Spinner<Integer>> spinners) {
        Map<Integer, Integer> request = new LinkedHashMap<>();
        spinners.forEach((itemId, spinner) -> {
            Integer value = spinner.getValue();
            if (value != null && value > 0) {
                request.put(itemId, value);
            }
        });
        return request;
    }
}
