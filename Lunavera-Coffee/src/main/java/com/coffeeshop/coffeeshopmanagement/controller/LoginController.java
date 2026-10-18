package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.AuthenticationService;
import com.coffeeshop.coffeeshopmanagement.service.AuthenticationService.LoginResult;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.PasswordReveal;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.SessionGuard;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;


public class LoginController {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button showPasswordButton;
    @FXML
    private Button loginButton;
    @FXML
    private javafx.scene.layout.StackPane loginRoot;
    @FXML
    private javafx.scene.image.ImageView backgroundImage;
    @FXML
    private javafx.scene.control.Label dataLocationLabel;

    private final AuthenticationService authenticationService = new AuthenticationService();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    /** The background picture always covers the whole window (cropped, not stretched), whatever its shape (TODO 2.38). */
    private void coverBackground() {
        if (loginRoot == null || backgroundImage == null || backgroundImage.getImage() == null) return;
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(loginRoot.widthProperty());
        clip.heightProperty().bind(loginRoot.heightProperty());
        loginRoot.setClip(clip);
        Runnable fit = () -> {
            double w = loginRoot.getWidth(), h = loginRoot.getHeight();
            double iw = backgroundImage.getImage().getWidth(), ih = backgroundImage.getImage().getHeight();
            if (w <= 0 || h <= 0 || iw <= 0 || ih <= 0) return;
            double k = Math.max(w / iw, h / ih);
            backgroundImage.setFitWidth(iw * k);
            backgroundImage.setFitHeight(ih * k);
        };
        loginRoot.widthProperty().addListener((o, a, b) -> fit.run());
        loginRoot.heightProperty().addListener((o, a, b) -> fit.run());
        fit.run();
    }

    /** The small line that says which data this is (TODO 5.25). A failure to work it out must never stop the login screen. */
    private void showDataLocation() {
        if (dataLocationLabel == null) return;
        try {
            dataLocationLabel.setText(DatabaseConfig.locationSummary());
        } catch (RuntimeException e) {
            dataLocationLabel.setVisible(false);
        }
    }

    @FXML
    private void initialize() {
        coverBackground();
        showDataLocation();
        if (usernameField != null && passwordField != null) {
            usernameField.setOnAction(event -> passwordField.requestFocus());
        }
        TextLengthLimiter.limit(usernameField, TextLengthLimiter.USERNAME_MAX);
        if (passwordField != null) {
            TextLengthLimiter.limit(passwordField, TextLengthLimiter.PASSWORD_MAX);
            passwordField.setOnAction(event -> handleLogin());
        }
        if (loginButton != null) loginButton.setOnAction(event -> handleLogin());
        PasswordReveal.install(passwordField, showPasswordButton);
    }

    @FXML
    public void handleLogin() {
        String username = usernameField != null ? usernameField.getText() : "";
        String password = passwordField != null ? passwordField.getText() : "";
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            AlertUtil.warning("Thông báo", "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
            return;
        }

        // Password verification (PBKDF2, ~120,000 iterations) is deliberately slow to resist
        // brute-forcing, which means it's slow enough to freeze the UI if run directly on the
        // FX thread. Run it in the background and only touch the UI again once it's done -
        // Async guarantees these callbacks land back on the FX thread.
        setLoginInProgress(true);
        String trimmedUsername = username.trim();
        boolean usedDefaultPassword = DatabaseConfig.DEFAULT_ADMIN_PASSWORD.equals(password);
        Async.run(
                () -> authenticationService.login(trimmedUsername, password),
                result -> onLoginFinished(result, usedDefaultPassword),
                error -> {
                    setLoginInProgress(false);
                    AlertUtil.error("Lỗi đăng nhập",
                            "Đã xảy ra lỗi khi kiểm tra tài khoản. Vui lòng thử lại.");
                }
        );
    }

    private void onLoginFinished(LoginResult result, boolean usedDefaultPassword) {
        setLoginInProgress(false);
        switch (result.status()) {
            case INVALID_CREDENTIALS -> AlertUtil.error("Đăng nhập thất bại",
                    "Tên đăng nhập hoặc mật khẩu không chính xác.");
            case TOO_MANY_ATTEMPTS -> AlertUtil.error("Thử lại sau",
                    "Đã nhập sai quá nhiều lần. Vui lòng thử lại sau " + result.retryAfterSeconds() + " giây.");
            case INACTIVE -> AlertUtil.error("Tài khoản bị khóa",
                    "Tài khoản này đã bị khóa. Vui lòng liên hệ quản trị viên.");
            case SUCCESS -> onLoginSuccess(result.user(), usedDefaultPassword);
        }
    }

    /** Disables the button and swaps its label while a login attempt is in flight, so the
     *  window still feels responsive instead of appearing stuck. */
    private void setLoginInProgress(boolean inProgress) {
        Button active = loginButton;
        if (active == null) {
            return;
        }
        active.setDisable(inProgress);
        active.setText(inProgress ? "ĐANG ĐĂNG NHẬP..." : "ĐĂNG NHẬP");
    }

    private void onLoginSuccess(User user, boolean usedDefaultPassword) {
        if (user.getRole() == Role.CUSTOMER) {
            // No customer-facing screen exists yet in this build; documented in progress.md.
            AlertUtil.info("Chưa hỗ trợ",
                    "Tài khoản khách hàng hiện chưa có màn hình riêng. Vui lòng đăng nhập bằng " +
                            "tài khoản nhân viên hoặc quản trị viên.");
            return;
        }

        if (user.getEmployeeId() == null) {
            enterApplication(user, null, usedDefaultPassword);
            return;
        }
        // The employee lookup is a DB call, so it runs off the FX thread like the password check.
        setLoginInProgress(true);
        Async.run(
                () -> employeeDAO.findById(user.getEmployeeId()).orElse(null),
                employee -> {
                    setLoginInProgress(false);
                    enterApplication(user, employee, usedDefaultPassword);
                },
                error -> {
                    setLoginInProgress(false);
                    AlertUtil.error("Lỗi đăng nhập",
                            "Không thể tải thông tin nhân viên. Vui lòng thử lại.");
                }
        );
    }

    private void enterApplication(User user, Employee employee, boolean usedDefaultPassword) {
        Session.start(user, employee);

        Stage stage = currentStage();
        if (stage == null) {
            return;
        }
        String target = user.getRole() == Role.ADMIN
                ? "/fxml/admin-trangchu.fxml"
                : "/fxml/employee-trangchu.fxml";
        SessionGuard.startWatching(stage);
        SceneNavigator.switchScene(stage, target);
        if (usedDefaultPassword || user.isMustChangePassword()) {
            javafx.application.Platform.runLater(() -> PasswordChangeDialog.showForced(user, stage));
        }
    }

    private Stage currentStage() {
        Node anchor = loginButton != null ? loginButton
                : usernameField;
        if (anchor == null || anchor.getScene() == null) {
            return null;
        }
        return (Stage) anchor.getScene().getWindow();
    }
}
