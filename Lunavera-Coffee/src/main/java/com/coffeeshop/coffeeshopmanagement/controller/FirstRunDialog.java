package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.AccountService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;

/**
 * The very first start (TODO 2A.5): there is no built-in administrator any more, so the owner chooses the name and the
 * password of the first one here. Nothing else can be done until it exists; "Thoát" closes the program (for example when
 * the shop already has data and the program was simply started from the wrong folder).
 */
public final class FirstRunDialog {

    private FirstRunDialog() {}

    /** Shows the dialog until an administrator has been created. Returns false when the owner chose to exit. */
    public static boolean run() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("firstrun.title"));
        AlertUtil.configure(dialog);
        ButtonType createType = new ButtonType(I18n.t("firstrun.create"), ButtonData.OK_DONE);
        // OTHER, not CANCEL_CLOSE: Esc and the window's X must not silently skip the setup.
        ButtonType exitType = new ButtonType(I18n.t("common.exit"), ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(createType, exitType);
        AlertUtil.setDefaultButton(dialog, createType);

        Label intro = new Label(I18n.t("firstrun.intro", DataPaths.dataDir()));
        intro.setWrapText(true);
        intro.setMaxWidth(430);

        TextField nameField = new TextField();
        nameField.setPromptText(I18n.t("firstrun.name.prompt"));
        TextLengthLimiter.limit(nameField, TextLengthLimiter.NAME_MAX);
        TextField userField = new TextField("admin");
        TextLengthLimiter.limit(userField, TextLengthLimiter.USERNAME_MAX);
        PasswordField passwordField = new PasswordField();
        TextLengthLimiter.limit(passwordField, TextLengthLimiter.PASSWORD_MAX);
        PasswordField confirmField = new PasswordField();
        TextLengthLimiter.limit(confirmField, TextLengthLimiter.PASSWORD_MAX);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(intro, 0, 0, 2, 1);
        grid.addRow(1, new Label(I18n.t("accounts.label.fullname")), nameField);
        grid.addRow(2, new Label(I18n.t("accounts.label.username")), userField);
        grid.addRow(3, new Label(I18n.t("accounts.label.password")), passwordField);
        grid.addRow(4, new Label(I18n.t("password.label.confirm")), confirmField);
        Label capsLock = com.coffeeshop.coffeeshopmanagement.util.CapsLockWarning.newLabel(); // TODO 5.26
        grid.add(capsLock, 0, 5, 2, 1);
        com.coffeeshop.coffeeshopmanagement.util.CapsLockWarning.install(capsLock, userField, passwordField, confirmField);
        dialog.getDialogPane().setContent(grid);

        boolean[] created = {false};
        dialog.getDialogPane().lookupButton(createType).addEventFilter(ActionEvent.ACTION, event -> {
            String problem = problemWith(nameField.getText(), userField.getText(), passwordField.getText(), confirmField.getText());
            if (problem != null) {
                AlertUtil.warning(I18n.t("firstrun.invalid.title"), problem);
                event.consume();
                return;
            }
            try {
                Employee owner = new Employee();
                owner.setFullName(nameField.getText().trim());
                owner.setPosition("Chủ quán");
                owner.setHireDate(com.coffeeshop.coffeeshopmanagement.util.AppClock.today());
                new AccountService().createFirstAdmin(new AccountService.Form(
                        userField.getText().trim(), passwordField.getText(), Role.ADMIN, true, owner));
                created[0] = true;
            } catch (DataAccessException e) {
                AlertUtil.error(I18n.t("firstrun.fail.title"), e.getMessage());
                event.consume();
            }
        });
        dialog.showAndWait();
        if (created[0]) {
            try {
                com.coffeeshop.coffeeshopmanagement.config.AppFlags.mark(com.coffeeshop.coffeeshopmanagement.config.AppFlags.SETUP_PENDING); // TODO 4.15
            } catch (RuntimeException e) {
                // the setup window is a convenience; the owner can still do all of it from the dashboard
            }
            AlertUtil.info(I18n.t("firstrun.done.title"), I18n.t("firstrun.done.message"));
        }
        return created[0];
    }

    /** The first thing wrong with what was typed, or null. Kept separate so it can be tested without a window. */
    static String problemWith(String fullName, String username, String password, String confirm) {
        if (ValidationUtil.isBlank(fullName)) return I18n.t("firstrun.val.name");
        String name = username == null ? "" : username.trim();
        if (!ValidationUtil.isValidUsername(name)) {
            return I18n.t("firstrun.val.username");
        }
        if (password == null || !ValidationUtil.isValidPassword(password)
                || ValidationUtil.containsUsername(password, name)
                || DatabaseConfig.DEFAULT_ADMIN_PASSWORD.equals(password)) {
            return I18n.t("password.problem.weak");
        }
        if (!password.equals(confirm)) return I18n.t("password.mismatch.message");
        return null;
    }
}
