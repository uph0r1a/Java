package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;

/**
 * The "part in cash" line of the payment box (TODO 4.32): when the customer pays with cash and one other method, the cashier chooses
 * the other method with the usual buttons, ticks this box and types how much was paid in cash; the rest is for the other method.
 * Shown only while a method other than cash is chosen. The rule that is checked when the order is saved is in
 * {@code OrderDAO.checkCashPart}; this panel only helps to type it and refuses the obvious mistakes first.
 */
final class SplitPaymentPanel {

    private final CheckBox on = new CheckBox(I18n.t("pos.split.check"));
    private final TextField cashField = new TextField();
    private final Label restLabel = new Label();
    private final Label due = new Label();
    private final VBox box = new VBox(6);
    private BigDecimal total = BigDecimal.ZERO;

    SplitPaymentPanel(BigDecimal total) {
        this.total = total;
        cashField.setPromptText(I18n.t("pos.split.cash.prompt"));
        TextLengthLimiter.limit(cashField, TextLengthLimiter.NUMBER_MAX);
        cashField.visibleProperty().bind(on.selectedProperty());
        cashField.managedProperty().bind(on.selectedProperty());
        restLabel.visibleProperty().bind(on.selectedProperty());
        restLabel.managedProperty().bind(on.selectedProperty());
        box.getChildren().addAll(on, cashField, restLabel);
        box.setVisible(false);
        box.setManaged(false);
        on.selectedProperty().addListener((o, was, now) -> refresh());
        cashField.textProperty().addListener((o, was, now) -> refresh());
        refresh();
    }

    /** The block to put in the payment box. */
    Node node() {
        return box;
    }

    /**
     * What the other method has to receive: the whole total, or the total less the cash part while a valid part is typed. The QR
     * of a transfer and the e-wallet block show this label, so the customer is never asked to transfer the cash part too.
     */
    Label dueLabel() {
        return due;
    }

    /** Call when the chosen method changes: the box is for the non-cash methods only, and cash alone is never a split. */
    void methodChanged(PaymentMethod method) {
        boolean show = method != PaymentMethod.CASH;
        if (!show) on.setSelected(false);
        if (box.isVisible() != show) {
            box.setVisible(show);
            box.setManaged(show);
            Platform.runLater(() -> {
                if (box.getScene() != null && box.getScene().getWindow() != null) box.getScene().getWindow().sizeToScene();
            });
        }
    }

    /** Call when the total changes (a discount was typed). */
    void totalChanged(BigDecimal newTotal) {
        total = newTotal;
        refresh();
    }

    /** The cash part to save, or null when the box is not ticked. Call {@link #problem()} first. */
    BigDecimal cashPart() {
        return on.isSelected() ? MoneyParser.parseOptionalDong(cashField.getText()) : null;
    }

    /** What is wrong with the typed part, in words for the cashier; null when it is fine or not used. */
    String problem() {
        if (!on.isSelected()) return null;
        BigDecimal typed = MoneyParser.parseOptionalDong(cashField.getText());
        if (typed == null || typed.signum() <= 0 || typed.compareTo(total) >= 0) {
            return I18n.t("pos.split.invalid", CurrencyUtil.format(total));
        }
        return null;
    }

    private void refresh() {
        BigDecimal typed = on.isSelected() ? MoneyParser.parseOptionalDong(cashField.getText()) : null;
        boolean valid = typed != null && typed.signum() > 0 && typed.compareTo(total) < 0;
        BigDecimal rest = valid ? total.subtract(typed) : total;
        restLabel.setText(valid ? I18n.t("pos.split.rest", CurrencyUtil.format(rest)) : "");
        due.setText(CurrencyUtil.format(rest));
    }
}
