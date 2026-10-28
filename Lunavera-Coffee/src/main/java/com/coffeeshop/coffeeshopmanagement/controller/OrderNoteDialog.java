package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.OrderNote;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * The small box on the sales screen where the cashier types what the customer asked for on one line ("less sugar, no ice", TODO 4.9).
 * The usual requests are buttons that add to the text, so most notes are one or two taps. Nothing here touches the database: the
 * caller puts the answer on the cart line ({@code Cart.setNote}) and the normal autosave keeps it with the order.
 */
public final class OrderNoteDialog {

    /** The language keys of the quick buttons, in the order shown; the text they add is kept as typed on the order. */
    static final String[] QUICK_KEYS = {"order.note.q.lesssugar", "order.note.q.nosugar", "order.note.q.lessice",
            "order.note.q.noice", "order.note.q.extraice", "order.note.q.takeaway"};

    private OrderNoteDialog() {
    }

    /**
     * Asks for the note of one line.
     *
     * @param productName the name shown in the title
     * @param current the note the line has now, or null
     * @return the typed text (empty means "no note") when the person pressed Save; empty when they cancelled
     */
    public static Optional<String> show(String productName, String current) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("order.note.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        AlertUtil.setDefaultButton(dialog, ButtonType.OK);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        Label heading = new Label(I18n.t("order.note.heading", productName));
        heading.setWrapText(true);
        TextField field = new TextField(current == null ? "" : current);
        field.setPromptText(I18n.t("order.note.prompt"));
        TextLengthLimiter.limit(field, OrderNote.MAX_LENGTH);
        Button clear = new Button(I18n.t("order.note.clear"));
        clear.setOnAction(e -> {
            field.clear();
            field.requestFocus();
        });
        HBox typed = new HBox(8, field, clear);
        HBox.setHgrow(field, Priority.ALWAYS);

        FlowPane quick = new FlowPane(8, 8);
        for (String key : QUICK_KEYS) {
            String text = I18n.t(key);
            Button button = new Button(text);
            button.setFocusTraversable(false);
            button.setOnAction(e -> {
                field.setText(OrderNote.append(field.getText(), text));
                field.requestFocus();
                field.positionCaret(field.getText().length());
            });
            quick.getChildren().add(button);
        }
        quick.setPrefWrapLength(420);

        VBox content = new VBox(12, heading, typed, quick);
        content.setPadding(new Insets(16));
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);
        javafx.application.Platform.runLater(() -> {
            field.requestFocus();
            field.positionCaret(field.getText().length());
        });

        Optional<ButtonType> answer = dialog.showAndWait();
        if (answer.isPresent() && answer.get() == ButtonType.OK) {
            return Optional.of(field.getText() == null ? "" : field.getText());
        }
        return Optional.empty();
    }
}
