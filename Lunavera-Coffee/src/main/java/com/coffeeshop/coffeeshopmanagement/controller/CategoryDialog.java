package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Messages;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

/**
 * The add/edit category dialog, used by the category screen and by the product screen's "add category" shortcut
 * (TODO 6.4). It owns no screen state: the caller says what to do after a successful save.
 */
public final class CategoryDialog {

    private static final CategoryDAO CATEGORIES = new CategoryDAO();

    private CategoryDialog() {
    }

    /**
     * The add/edit dialog. {@code afterSave} runs only when a category was actually saved (so the calling screen can reload).
     * {@code existing} is null for a new category.
     */
    public static void show(Category existing, Runnable afterSave) {
        Dialog<Category> dialog = new Dialog<>();
        dialog.setTitle(Messages.get(existing == null ? "category.dialog.title.add" : "category.dialog.title.edit"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        AlertUtil.setDefaultButton(dialog, ButtonType.OK);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        TextField nameField = new TextField(existing != null ? existing.getName() : "");
        TextLengthLimiter.limit(nameField, TextLengthLimiter.NAME_MAX);
        TextArea descriptionField = new TextArea(existing != null ? existing.getDescription() : "");
        TextLengthLimiter.limit(descriptionField, TextLengthLimiter.DESCRIPTION_MAX);
        descriptionField.setPrefRowCount(3);
        CheckBox activeBox = new CheckBox(Messages.get("category.dialog.active"));
        activeBox.setSelected(existing == null || existing.isActive());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label(Messages.get("category.dialog.name")), nameField);
        grid.addRow(1, new Label(Messages.get("category.dialog.description")), descriptionField);
        grid.addRow(2, new Label(""), activeBox);
        dialog.getDialogPane().setContent(grid);

        // Validate and save via an event filter on the button itself, consuming the event to
        // keep the dialog open on failure. setResultConverter can't do this: returning null
        // from it still closes the dialog, silently discarding whatever the user typed - the
        // exact "known limitation" flagged in progress.md (Session 11).
        boolean[] saved = {false};
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (ValidationUtil.isBlank(nameField.getText())) {
                AlertUtil.warning(Messages.get("category.dialog.missingName.title"), Messages.get("category.dialog.missingName.text"));
                event.consume();
                return;
            }
            Category category = existing != null ? existing : new Category();
            category.setName(nameField.getText().trim());
            category.setDescription(descriptionField.getText());
            category.setActive(activeBox.isSelected());
            try {
                if (existing == null) {
                    CATEGORIES.insert(category);
                } else {
                    CATEGORIES.update(category);
                }
                saved[0] = true;
            } catch (DataAccessException e) {
                AlertUtil.error(Messages.get("category.dialog.saveFailed.title"), e.getMessage());
                event.consume();
            }
        });

        AlertUtil.confirmBeforeDiscarding(dialog, ButtonType.CANCEL);
        dialog.showAndWait();
        if (saved[0]) {
            afterSave.run();
        }
    }
}
