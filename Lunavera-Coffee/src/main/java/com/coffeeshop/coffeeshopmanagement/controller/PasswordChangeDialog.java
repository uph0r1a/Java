package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.PasswordChangeService;
import com.coffeeshop.coffeeshopmanagement.service.PasswordChangeService.Result;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

/**
 * Change-my-own-password dialog (TODO 2.2), in two forms:
 * <ul>
 *   <li>{@link #showForced}: right after someone logged in with the well-known seeded password (Admin@123). It
 *       cannot be dismissed (no "later", Esc and the window's X do nothing); the only ways out are to set a new
 *       password or to log out. Before this, "Để sau" let the default password stay in use for ever.</li>
 *   <li>{@link #showVoluntary}: the "Đổi mật khẩu" button in every screen's sidebar; asks for the current
 *       password first and can be cancelled.</li>
 * </ul>
 */
public final class PasswordChangeDialog {

    private PasswordChangeDialog() {
    }

    public static void showForced(User user, Stage stage) {
        boolean[] logOut = {false};
        boolean setByAdmin = user.isMustChangePassword();
        show(user, true, I18n.t(setByAdmin ? "password.forced.admin.title" : "password.forced.default.title"),
                I18n.t(setByAdmin ? "password.forced.admin.intro" : "password.forced.default.intro"),
                logOut);
        if (logOut[0] && stage != null) {
            Session.clear();
            SceneNavigator.switchScene(stage, "/fxml/dangnhap.fxml");
        }
    }

    public static void showVoluntary(User user) {
        show(user, false, I18n.t("password.title"), I18n.t("password.intro"), new boolean[1]);
    }

    private static void show(User user, boolean forced, String title, String intro, boolean[] logOut) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        AlertUtil.configure(dialog);
        ButtonType saveType = new ButtonType(I18n.t("password.button.save"), ButtonData.OK_DONE);
        if (forced) {
            // OTHER, not CANCEL_CLOSE: a dialog with no cancel-type button ignores Esc and the window's X.
            ButtonType logoutType = new ButtonType(I18n.t("password.button.logout"), ButtonData.OTHER);
            dialog.getDialogPane().getButtonTypes().addAll(saveType, logoutType);
            dialog.getDialogPane().lookupButton(logoutType).addEventFilter(ActionEvent.ACTION, e -> logOut[0] = true);
        } else {
            ButtonType cancelType = new ButtonType(I18n.t("common.cancel"), ButtonData.CANCEL_CLOSE);
            dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);
            AlertUtil.setCancelButton(dialog, cancelType);
        }
        AlertUtil.setDefaultButton(dialog, saveType);

        PasswordField currentField = new PasswordField();
        PasswordField newField = new PasswordField();
        PasswordField confirmField = new PasswordField();
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        Label introLabel = new Label(intro);
        introLabel.setWrapText(true);
        introLabel.setMaxWidth(380);
        grid.add(introLabel, 0, 0, 2, 1);
        int row = 1;
        if (!forced) {
            grid.addRow(row++, new Label(I18n.t("password.label.current")), currentField);
        }
        grid.addRow(row++, new Label(I18n.t("password.label.new")), newField);
        grid.addRow(row, new Label(I18n.t("password.label.confirm")), confirmField);
        Label capsLock = com.coffeeshop.coffeeshopmanagement.util.CapsLockWarning.newLabel(); // TODO 5.26
        grid.add(capsLock, 0, row + 1, 2, 1);
        com.coffeeshop.coffeeshopmanagement.util.CapsLockWarning.install(capsLock, currentField, newField, confirmField);
        dialog.getDialogPane().setContent(grid);

        PasswordChangeService service = new PasswordChangeService();
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            String password = newField.getText();
            if (!password.equals(confirmField.getText())) {
                AlertUtil.warning(I18n.t("password.mismatch.title"), I18n.t("password.mismatch.message"));
                event.consume();
                return;
            }
            try {
                Result result = service.change(user.getId(), currentField.getText(), password, !forced);
                String problem = switch (result) {
                    case OK -> null;
                    case WRONG_CURRENT -> I18n.t("password.problem.wrongcurrent");
                    case WEAK -> I18n.t("password.problem.weak");
                    case IS_DEFAULT -> I18n.t("password.problem.isdefault");
                    case SAME_AS_CURRENT -> I18n.t("password.problem.same");
                    case NO_SUCH_USER -> I18n.t("password.problem.nouser");
                    case TOO_MANY_ATTEMPTS -> I18n.t("password.problem.throttled");
                };
                if (problem != null) {
                    AlertUtil.warning(I18n.t("password.fail.title"), problem);
                    event.consume();
                } else if (!forced) {
                    AlertUtil.info(I18n.t("password.done.title"), I18n.t("password.done.message"));
                }
            } catch (DataAccessException e) {
                AlertUtil.error(I18n.t("password.fail.title"), e.getMessage());
                event.consume();
            }
        });
        dialog.showAndWait();
    }
}
