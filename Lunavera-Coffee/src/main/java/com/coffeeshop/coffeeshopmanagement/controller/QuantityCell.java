package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.service.QuantityInput;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * Renders "[-]  qty  [+]" inline in the quantity column of the cart; a click on the number turns it into a small box to type a
 * quantity (TODO 5.27). Moved out of {@code OrderController} (TODO 6.54) unchanged: the stock limit is applied by the two callbacks.
 */
final class QuantityCell extends TableCell<OrderItem, Void> {

    private final BiConsumer<OrderItem, Integer> change;
    private final BooleanSupplier paymentInFlight;
    private final Button minus = new Button("−");
    private final Button plus = new Button("+");
    private final Label qtyLabel = new Label();
    private final TextField qtyField = new TextField();
    private final HBox box = new HBox(4, minus, qtyLabel, plus);
    private boolean typing;

    /** {@code adjust}: add or take one (+1 / -1); {@code change}: set the typed quantity; {@code paymentInFlight}: a payment is being saved. */
    QuantityCell(BiConsumer<OrderItem, Integer> adjust, BiConsumer<OrderItem, Integer> change, BooleanSupplier paymentInFlight) {
        this.change = change;
        this.paymentInFlight = paymentInFlight;
        box.setAlignment(Pos.CENTER);
        minus.setOnAction(e -> adjust.accept(getTableView().getItems().get(getIndex()), -1));
        plus.setOnAction(e -> adjust.accept(getTableView().getItems().get(getIndex()), 1));
        qtyLabel.setCursor(javafx.scene.Cursor.HAND);
        qtyLabel.setTooltip(new javafx.scene.control.Tooltip(I18n.t("pos.qty.type.hint")));
        qtyLabel.setOnMouseClicked(e -> startTyping());
        qtyField.setPrefColumnCount(3);
        qtyField.setMaxWidth(54);
        // digits only, at most three (QuantityInput); the stock limit is applied by changeQuantity, as for "+"
        qtyField.setTextFormatter(new javafx.scene.control.TextFormatter<String>(c ->
                QuantityInput.sanitize(c.getControlNewText()).equals(c.getControlNewText()) ? c : null));
        qtyField.setOnAction(e -> finishTyping(true));
        qtyField.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                finishTyping(false);
            }
        });
        qtyField.focusedProperty().addListener((o, was, now) -> {
            if (!now) {
                finishTyping(true);
            }
        });
    }

    private void startTyping() {
        if (typing || isEmpty() || paymentInFlight.getAsBoolean()) {
            return;
        }
        typing = true;
        qtyField.setText(qtyLabel.getText());
        box.getChildren().set(1, qtyField);
        qtyField.requestFocus();
        qtyField.selectAll();
    }

    /** {@code apply}: use what was typed; otherwise (Esc) leave the quantity as it was. */
    private void finishTyping(boolean apply) {
        if (!typing) {
            return;
        }
        typing = false; // first, so the focus change below cannot run this twice
        box.getChildren().set(1, qtyLabel);
        if (apply && getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
            QuantityInput.parse(qtyField.getText()).ifPresent(n -> change.accept(getTableView().getItems().get(getIndex()), n));
        }
    }

    @Override
    protected void updateItem(Void item, boolean empty) {
        super.updateItem(item, empty);
        if (empty) {
            typing = false;
            setGraphic(null);
        } else {
            if (typing) {
                typing = false;
                box.getChildren().set(1, qtyLabel);
            }
            qtyLabel.setText(String.valueOf(getTableView().getItems().get(getIndex()).getQuantity()));
            setGraphic(box);
        }
    }
}
