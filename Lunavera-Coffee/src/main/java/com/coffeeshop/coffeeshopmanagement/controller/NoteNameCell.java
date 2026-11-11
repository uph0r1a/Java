package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * The name column of the cart (TODO 4.9): the product name, under it the line's note in a smaller italic when it has one, and a
 * pencil button that opens the note box. The note is part of the line, so it is saved with the order by the same autosave as the quantity.
 */
final class NoteNameCell extends TableCell<OrderItem, String> {

    private final Label nameLabel = new Label();
    private final Label noteLabel = new Label();
    private final VBox texts = new VBox(1, nameLabel, noteLabel);
    private final HBox box;

    NoteNameCell(Consumer<OrderItem> onEdit) {
        Button editButton = new Button("✎");
        box = new HBox(6, texts, editButton);
        nameLabel.setWrapText(true);
        noteLabel.setWrapText(true);
        noteLabel.setStyle("-fx-text-fill: #8a5a00; " + com.coffeeshop.coffeeshopmanagement.util.TextSize.size(11) + " -fx-font-style: italic;");
        texts.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(texts, Priority.ALWAYS);
        editButton.setFocusTraversable(false);
        editButton.setTooltip(new javafx.scene.control.Tooltip(I18n.t("pos.note.tip")));
        editButton.setOnAction(e -> {
            if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                onEdit.accept(getTableView().getItems().get(getIndex()));
            }
        });
        box.setAlignment(Pos.CENTER_LEFT);
    }

    @Override
    protected void updateItem(String name, boolean empty) {
        super.updateItem(name, empty);
        if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
            setText(null);
            setGraphic(null);
            return;
        }
        com.coffeeshop.coffeeshopmanagement.model.OrderItem line = getTableView().getItems().get(getIndex());
        // The size and extras (TODO 4.47) and the note (TODO 4.9) share the second line: "↳ L, +Extra shot · less sugar".
        String options = com.coffeeshop.coffeeshopmanagement.service.LineOptions.describe(line);
        String note = options == null ? line.getNote() : line.getNote() == null ? options : options + " · " + line.getNote();
        nameLabel.setText(name);
        noteLabel.setText(note == null ? "" : "↳ " + note);
        noteLabel.setVisible(note != null);
        noteLabel.setManaged(note != null);
        setText(null);
        setGraphic(box);
    }
}
