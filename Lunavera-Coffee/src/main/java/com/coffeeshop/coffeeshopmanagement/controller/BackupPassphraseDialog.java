package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.BackupCrypto;
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

import java.util.Optional;

/**
 * The two questions about a backup passphrase (TODO 2A.4): "lock this new backup with a passphrase?" and "this backup is
 * locked, what is its passphrase?". The passphrase is handed back as a {@code char[]} so the caller can wipe it.
 */
public final class BackupPassphraseDialog {

    private BackupPassphraseDialog() {}

    /**
     * Asks whether the new backup should be locked. Both boxes empty means "no lock". Returns an empty Optional when the
     * person cancelled, an array of length 0 for "no lock", otherwise the passphrase.
     */
    public static Optional<char[]> askForNew() {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("admin.backup.title"));
        AlertUtil.configure(dialog);
        ButtonType continueType = new ButtonType(I18n.t("backup.pass.continue"), ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(continueType, ButtonType.CANCEL);
        AlertUtil.setDefaultButton(dialog, continueType);

        Label intro = new Label(I18n.t("backup.pass.intro"));
        intro.setWrapText(true);
        intro.setMaxWidth(430);
        PasswordField phraseField = new PasswordField();
        TextLengthLimiter.limit(phraseField, TextLengthLimiter.PASSWORD_MAX);
        phraseField.setPromptText(I18n.t("backup.pass.prompt", BackupCrypto.MIN_PASSPHRASE_LENGTH));
        PasswordField confirmField = new PasswordField();
        TextLengthLimiter.limit(confirmField, TextLengthLimiter.PASSWORD_MAX);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(intro, 0, 0, 2, 1);
        grid.addRow(1, new Label(I18n.t("backup.pass.label")), phraseField);
        grid.addRow(2, new Label(I18n.t("backup.pass.again")), confirmField);
        Label capsLock = com.coffeeshop.coffeeshopmanagement.util.CapsLockWarning.newLabel(); // TODO 5.26
        grid.add(capsLock, 0, 3, 2, 1);
        com.coffeeshop.coffeeshopmanagement.util.CapsLockWarning.install(capsLock, phraseField, confirmField);
        dialog.getDialogPane().setContent(grid);

        char[][] result = {null};
        dialog.getDialogPane().lookupButton(continueType).addEventFilter(ActionEvent.ACTION, event -> {
            String phrase = phraseField.getText();
            String confirm = confirmField.getText();
            if (phrase.isEmpty() && confirm.isEmpty()) {
                result[0] = new char[0];
                return;
            }
            String problem = problemWith(phrase, confirm);
            if (problem != null) {
                AlertUtil.warning(I18n.t("firstrun.invalid.title"), problem);
                event.consume();
                return;
            }
            result[0] = phrase.toCharArray();
        });
        dialog.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    /** Asks for the passphrase of a locked backup. Returns an empty Optional when the person cancelled. */
    public static Optional<char[]> askToOpen(String fileName) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("backup.pass.open.title"));
        AlertUtil.configure(dialog);
        ButtonType openType = new ButtonType(I18n.t("backup.pass.open"), ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(openType, ButtonType.CANCEL);
        AlertUtil.setDefaultButton(dialog, openType);

        Label intro = new Label(I18n.t("backup.pass.open.intro", fileName));
        intro.setWrapText(true);
        intro.setMaxWidth(400);
        PasswordField phraseField = new PasswordField();
        TextLengthLimiter.limit(phraseField, TextLengthLimiter.PASSWORD_MAX);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.add(intro, 0, 0, 2, 1);
        grid.addRow(1, new Label(I18n.t("backup.pass.open.label")), phraseField);
        Label capsLock = com.coffeeshop.coffeeshopmanagement.util.CapsLockWarning.newLabel(); // TODO 5.26
        grid.add(capsLock, 0, 2, 2, 1);
        com.coffeeshop.coffeeshopmanagement.util.CapsLockWarning.install(capsLock, phraseField);
        dialog.getDialogPane().setContent(grid);

        char[][] result = {null};
        dialog.getDialogPane().lookupButton(openType).addEventFilter(ActionEvent.ACTION, event -> {
            if (phraseField.getText().isEmpty()) {
                AlertUtil.warning(I18n.t("backup.pass.open.empty.title"), I18n.t("backup.pass.open.empty"));
                event.consume();
                return;
            }
            result[0] = phraseField.getText().toCharArray();
        });
        dialog.showAndWait();
        return Optional.ofNullable(result[0]);
    }

    /** The first thing wrong with a new passphrase, or null. Kept separate so it can be tested without a window. */
    static String problemWith(String phrase, String confirm) {
        String problem = BackupCrypto.passphraseProblem(phrase == null ? null : phrase.toCharArray());
        if (problem != null) return problem;
        if (!phrase.equals(confirm)) return I18n.t("backup.pass.mismatch");
        return null;
    }
}
