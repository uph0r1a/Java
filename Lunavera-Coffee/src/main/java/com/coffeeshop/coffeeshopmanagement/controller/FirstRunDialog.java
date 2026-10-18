package com.coffeeshop.coffeeshopmanagement.controller;

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
        dialog.setTitle("Thiết lập lần đầu");
        AlertUtil.configure(dialog);
        ButtonType createType = new ButtonType("Tạo tài khoản", ButtonData.OK_DONE);
        // OTHER, not CANCEL_CLOSE: Esc and the window's X must not silently skip the setup.
        ButtonType exitType = new ButtonType("Thoát", ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(createType, exitType);
        AlertUtil.setDefaultButton(dialog, createType);

        Label intro = new Label("Chương trình chưa có tài khoản nào, nên đây là lần dùng đầu tiên với dữ liệu tại:\n"
                + DataPaths.dataDir() + "\n\n"
                + "Nếu quán đã dùng Lunavera trước đây và bạn không thấy dữ liệu cũ, hãy bấm Thoát và mở lại chương trình "
                + "từ đúng thư mục (hoặc đặt biến LUNAVERA_DATA_DIR).\n\n"
                + "Nếu đây đúng là lần đầu, hãy tạo tài khoản quản trị viên của quán. Chương trình không có mật khẩu mặc định: "
                + "hãy tự chọn và nhớ mật khẩu này.");
        intro.setWrapText(true);
        intro.setMaxWidth(430);

        TextField nameField = new TextField();
        nameField.setPromptText("Họ tên chủ quán");
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
        grid.addRow(1, new Label("Họ tên:"), nameField);
        grid.addRow(2, new Label("Tên đăng nhập:"), userField);
        grid.addRow(3, new Label("Mật khẩu:"), passwordField);
        grid.addRow(4, new Label("Nhập lại:"), confirmField);
        dialog.getDialogPane().setContent(grid);

        boolean[] created = {false};
        dialog.getDialogPane().lookupButton(createType).addEventFilter(ActionEvent.ACTION, event -> {
            String problem = problemWith(nameField.getText(), userField.getText(), passwordField.getText(), confirmField.getText());
            if (problem != null) {
                AlertUtil.warning("Chưa hợp lệ", problem);
                event.consume();
                return;
            }
            try {
                Employee owner = new Employee();
                owner.setFullName(nameField.getText().trim());
                owner.setPosition("Chủ quán");
                owner.setHireDate(LocalDate.now());
                new AccountService().createFirstAdmin(new AccountService.Form(
                        userField.getText().trim(), passwordField.getText(), Role.ADMIN, true, owner));
                created[0] = true;
            } catch (DataAccessException e) {
                AlertUtil.error("Không tạo được tài khoản", e.getMessage());
                event.consume();
            }
        });
        dialog.showAndWait();
        if (created[0]) {
            AlertUtil.info("Đã tạo tài khoản", "Tài khoản quản trị viên đã được tạo. Hãy đăng nhập bằng tên đăng nhập và mật khẩu vừa chọn.");
        }
        return created[0];
    }

    /** The first thing wrong with what was typed, or null. Kept separate so it can be tested without a window. */
    static String problemWith(String fullName, String username, String password, String confirm) {
        if (ValidationUtil.isBlank(fullName)) return "Vui lòng nhập họ tên.";
        String name = username == null ? "" : username.trim();
        if (!ValidationUtil.isValidUsername(name)) {
            return "Tên đăng nhập gồm 4-32 ký tự (chữ, số, dấu chấm hoặc gạch dưới) và không được giống một số điện thoại.";
        }
        if (password == null || !ValidationUtil.isValidPassword(password)
                || ValidationUtil.containsUsername(password, name)
                || DatabaseConfig.DEFAULT_ADMIN_PASSWORD.equals(password)) {
            return "Mật khẩu phải có ít nhất 8 ký tự, gồm cả chữ và số, không chứa tên đăng nhập hay tên quán, "
                    + "và không phải mật khẩu quá phổ biến (như 12345678).";
        }
        if (!password.equals(confirm)) return "Mật khẩu nhập lại không khớp.";
        return null;
    }
}
