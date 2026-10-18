package com.coffeeshop.coffeeshopmanagement.controller;

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
        show(user, true, setByAdmin ? "Đặt mật khẩu của bạn" : "Đổi mật khẩu mặc định",
                setByAdmin
                        ? "Mật khẩu hiện tại do quản trị viên đặt nên quản trị viên biết nó. Hãy đặt mật khẩu của riêng bạn trước khi tiếp tục."
                        : "Bạn đang dùng mật khẩu mặc định (ai cũng biết) nên phải đặt mật khẩu mới trước khi tiếp tục.",
                logOut);
        if (logOut[0] && stage != null) {
            Session.clear();
            SceneNavigator.switchScene(stage, "/fxml/dangnhap.fxml");
        }
    }

    public static void showVoluntary(User user) {
        show(user, false, "Đổi mật khẩu", "Nhập mật khẩu hiện tại và mật khẩu mới.", new boolean[1]);
    }

    private static void show(User user, boolean forced, String title, String intro, boolean[] logOut) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        AlertUtil.configure(dialog);
        ButtonType saveType = new ButtonType("Đổi mật khẩu", ButtonData.OK_DONE);
        if (forced) {
            // OTHER, not CANCEL_CLOSE: a dialog with no cancel-type button ignores Esc and the window's X.
            ButtonType logoutType = new ButtonType("Đăng xuất", ButtonData.OTHER);
            dialog.getDialogPane().getButtonTypes().addAll(saveType, logoutType);
            dialog.getDialogPane().lookupButton(logoutType).addEventFilter(ActionEvent.ACTION, e -> logOut[0] = true);
        } else {
            ButtonType cancelType = new ButtonType("Hủy", ButtonData.CANCEL_CLOSE);
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
            grid.addRow(row++, new Label("Mật khẩu hiện tại:"), currentField);
        }
        grid.addRow(row++, new Label("Mật khẩu mới:"), newField);
        grid.addRow(row, new Label("Nhập lại:"), confirmField);
        dialog.getDialogPane().setContent(grid);

        PasswordChangeService service = new PasswordChangeService();
        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, event -> {
            String password = newField.getText();
            if (!password.equals(confirmField.getText())) {
                AlertUtil.warning("Không khớp", "Mật khẩu nhập lại không khớp.");
                event.consume();
                return;
            }
            try {
                Result result = service.change(user.getId(), currentField.getText(), password, !forced);
                String problem = switch (result) {
                    case OK -> null;
                    case WRONG_CURRENT -> "Mật khẩu hiện tại không đúng.";
                    case WEAK -> "Mật khẩu phải có ít nhất 8 ký tự, gồm cả chữ và số, không chứa tên đăng nhập hay tên quán, và không phải mật khẩu quá phổ biến (như 12345678).";
                    case IS_DEFAULT -> "Vui lòng chọn mật khẩu khác mật khẩu mặc định.";
                    case SAME_AS_CURRENT -> "Mật khẩu mới phải khác mật khẩu hiện tại.";
                    case NO_SUCH_USER -> "Tài khoản không còn tồn tại.";
                    case TOO_MANY_ATTEMPTS -> "Nhập sai mật khẩu hiện tại quá nhiều lần. Vui lòng thử lại sau ít phút.";
                };
                if (problem != null) {
                    AlertUtil.warning("Không thể đổi mật khẩu", problem);
                    event.consume();
                } else if (!forced) {
                    AlertUtil.info("Đã đổi mật khẩu", "Mật khẩu mới đã được lưu và có hiệu lực ngay (lần đăng nhập sau hãy dùng mật khẩu mới).");
                }
            } catch (DataAccessException e) {
                AlertUtil.error("Không thể đổi mật khẩu", e.getMessage());
                event.consume();
            }
        });
        dialog.showAndWait();
    }
}
