package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.service.PinRules;
import com.coffeeshop.coffeeshopmanagement.service.PinService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;

/** "Mã PIN" in the sidebar of a staff member (TODO 4.36): sets a new PIN after asking for the password, or removes the PIN. */
final class PinSetupDialog {

    private PinSetupDialog() {
    }

    static void show() {
        PinService service = new PinService();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("pin.set.title"));
        AlertUtil.configure(dialog);
        ButtonType saveType = new ButtonType(I18n.t("password.button.save"), ButtonData.OK_DONE);
        ButtonType removeType = new ButtonType(I18n.t("pin.set.remove"), ButtonData.OTHER);
        ButtonType cancelType = new ButtonType(I18n.t("common.cancel"), ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, removeType, cancelType);
        AlertUtil.setCancelButton(dialog, cancelType);
        AlertUtil.setDefaultButton(dialog, saveType);

        PasswordField password = new PasswordField();
        PasswordField pin = new PasswordField();
        PasswordField again = new PasswordField();
        TextLengthLimiter.limit(pin, PinRules.MAX_LENGTH);
        TextLengthLimiter.limit(again, PinRules.MAX_LENGTH);
        Label intro = new Label(I18n.t("pin.set.intro", PinRules.MIN_LENGTH, PinRules.MAX_LENGTH));
        intro.setWrapText(true);
        intro.setMaxWidth(380);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(intro, 0, 0, 2, 1);
        grid.addRow(1, new Label(I18n.t("pin.set.password.label")), password);
        grid.addRow(2, new Label(I18n.t("pin.set.new")), pin);
        grid.addRow(3, new Label(I18n.t("pin.set.again")), again);
        dialog.getDialogPane().setContent(grid);

        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            if (!pin.getText().equals(again.getText())) {
                AlertUtil.warning(I18n.t("pin.set.title"), I18n.t("pin.set.mismatch"));
                event.consume();
                return;
            }
            try {
                service.setPin(password.getText(), pin.getText());
            } catch (DataAccessException e) {
                AlertUtil.warning(I18n.t("pin.set.title"), e.getMessage());
                event.consume();
            }
        });
        dialog.getDialogPane().lookupButton(removeType).addEventFilter(ActionEvent.ACTION, event -> service.removeOwnPin());
        dialog.showAndWait().ifPresent(result -> {
            if (result == saveType) AlertUtil.info(I18n.t("pin.set.title"), I18n.t("pin.set.done"));
            else if (result == removeType) AlertUtil.info(I18n.t("pin.set.title"), I18n.t("pin.set.removed"));
        });
    }
}
