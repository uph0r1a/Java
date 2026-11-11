package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.AuditRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The one question asked before a paid order is cancelled as a refund (TODO 2.66 and 4.2): do the goods go back into stock, and why.
 * It replaces the three-button question the history window used to ask, so the reason is asked in the same step and the answer
 * "do not return to stock" is still the one that is chosen when nothing is touched. The reason is required: it is kept on the order
 * ({@code cancel_reason}) and in the audit log, where it is the thing an owner looks for later. Nothing here touches the database.
 */
public final class RefundDialog {

    /** What the person decided. */
    public record Choice(boolean returnToStock, String reason) {
    }

    private RefundDialog() {
    }

    /** @return the choice, or empty when the person kept the order */
    public static Optional<Choice> ask(int orderId, BigDecimal total) {
        return ask(orderId, total, null);
    }

    /** @param suggestedReasonKey the i18n key of a reason to fill in (an order with something made to order in it), or null (TODO 6.90 d) */
    public static Optional<Choice> ask(int orderId, BigDecimal total, String suggestedReasonKey) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("history.cancel.title"));
        AlertUtil.configure(dialog);
        ButtonType refund = new ButtonType(I18n.t("history.cancel"), ButtonData.OK_DONE);
        ButtonType stop = new ButtonType(I18n.t("history.cancel.stop"), ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(refund, stop);
        AlertUtil.setCancelButton(dialog, stop); // no default button: Enter in the reason box must not refund by accident

        Label message = new Label(I18n.t("history.cancel.message", orderId, CurrencyUtil.format(total)));
        message.setWrapText(true);
        ToggleGroup group = new ToggleGroup();
        RadioButton keep = new RadioButton(I18n.t("history.cancel.keep"));
        RadioButton back = new RadioButton(I18n.t("history.cancel.back"));
        keep.setToggleGroup(group);
        back.setToggleGroup(group);
        keep.setSelected(true);
        TextField reason = new TextField(suggestedReasonKey == null ? "" : I18n.t(suggestedReasonKey)); // the cashier may change it
        reason.setPromptText(I18n.t("history.cancel.reason.prompt"));
        TextLengthLimiter.limit(reason, AuditRules.REASON_MAX);
        Label problem = new Label();
        problem.setStyle("-fx-text-fill: #c62828;");
        problem.setWrapText(true);

        VBox content = new VBox(10, message, keep, back, new Label(I18n.t("history.cancel.reason.label")), reason, problem);
        content.setPadding(new Insets(16));
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().lookupButton(refund).addEventFilter(ActionEvent.ACTION, event -> {
            String why = AuditRules.reasonProblem(reason.getText());
            if (why != null) {
                problem.setText(I18n.t(why, AuditRules.REASON_MIN));
                event.consume();
                reason.requestFocus();
            }
        });
        javafx.application.Platform.runLater(reason::requestFocus);
        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isPresent() && answer.get() == refund) {
            return Optional.of(new Choice(back.isSelected(), AuditRules.clip(reason.getText(), AuditRules.REASON_MAX)));
        }
        return Optional.empty();
    }
}
