package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.AuthenticationService;
import com.coffeeshop.coffeeshopmanagement.service.AuthenticationService.LoginResult;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.LanguageSetting;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.BusyIndicator;
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
    private javafx.scene.control.Label capsLockLabel;
    @FXML
    private Button showPasswordButton;
    @FXML
    private Button loginButton;
    @FXML
    private Button languageViButton;
    @FXML
    private Button languageEnButton;
    @FXML
    private javafx.scene.layout.Region languageThumb;
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
        com.coffeeshop.coffeeshopmanagement.util.CapsLockWarning.install(capsLockLabel, usernameField, passwordField); // TODO 5.26
        markActiveLanguage();
    }

    /** Width of one language segment of the switch; the same number is in dangnhap.fxml (the thumb moves by it). */
    private static final double SEGMENT_WIDTH = 86;

    /** The language in use is drawn as selected, and the thumb sits under it without moving (TODO 1.7). */
    private void markActiveLanguage() {
        markLanguage(I18n.current());
        if (languageThumb != null) {
            languageThumb.setTranslateX(I18n.EN.equals(I18n.current()) ? SEGMENT_WIDTH : 0);
        }
    }

    /** Only the text colour: the green thumb behind the text is what shows the choice. */
    private void markLanguage(String code) {
        markIf(languageViButton, I18n.VI.equals(code));
        markIf(languageEnButton, I18n.EN.equals(code));
    }

    private static void markIf(Button button, boolean active) {
        if (button == null) return;
        button.getStyleClass().remove("language-active");
        if (active) button.getStyleClass().add("language-active");
    }

    @FXML
    private void chooseVietnamese() {
        changeLanguage(I18n.VI);
    }

    @FXML
    private void chooseEnglish() {
        changeLanguage(I18n.EN);
    }

    /**
     * The thumb slides to the chosen language (a fifth of a second), and when it arrives the language is remembered and
     * this screen is shown again in it; nothing is lost, as nobody is signed in yet. The screen is reloaded only after the
     * slide, otherwise the new screen would hide it.
     */
    private void changeLanguage(String code) {
        if (code.equals(I18n.current())) return;
        if (!I18n.LANGUAGES.contains(code)) return;
        if (languageThumb == null) {
            applyLanguage(code);
            return;
        }
        if (languageViButton != null) languageViButton.setDisable(true);
        if (languageEnButton != null) languageEnButton.setDisable(true);
        markLanguage(code);
        javafx.animation.TranslateTransition slide =
                new javafx.animation.TranslateTransition(javafx.util.Duration.millis(220), languageThumb);
        slide.setToX(I18n.EN.equals(code) ? SEGMENT_WIDTH : 0);
        slide.setInterpolator(javafx.animation.Interpolator.EASE_BOTH);
        slide.setOnFinished(e -> applyLanguage(code));
        slide.play();
    }

    private void applyLanguage(String code) {
        if (!LanguageSetting.choose(code)) {
            markActiveLanguage();
            return;
        }
        Stage stage = currentStage();
        if (stage != null) {
            SceneNavigator.reloadCurrent(stage);
        } else {
            markActiveLanguage();
        }
    }

    @FXML
    public void handleLogin() {
        String username = usernameField != null ? usernameField.getText() : "";
        String password = passwordField != null ? passwordField.getText() : "";
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            AlertUtil.warning(I18n.t("login.missing.title"), I18n.t("login.missing.message"));
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
                    AlertUtil.error(I18n.t("login.error.title"), I18n.t("login.error.check"));
                }
        );
    }

    private void onLoginFinished(LoginResult result, boolean usedDefaultPassword) {
        setLoginInProgress(false);
        switch (result.status()) {
            case INVALID_CREDENTIALS -> AlertUtil.error(I18n.t("login.invalid.title"), I18n.t("login.invalid.message"));
            case TOO_MANY_ATTEMPTS -> AlertUtil.error(I18n.t("login.throttled.title"),
                    I18n.t("login.throttled.message", result.retryAfterSeconds()));
            case INACTIVE -> AlertUtil.error(I18n.t("login.locked.title"), I18n.t("login.locked.message"));
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
        // A spinner and the waiting pointer as well as the text (TODO 5.16).
        if (inProgress) {
            if (releaseLoginBusy == null) {
                releaseLoginBusy = BusyIndicator.hold(active, I18n.t("login.button.busy"));
            }
        } else {
            if (releaseLoginBusy != null) {
                releaseLoginBusy.run();
                releaseLoginBusy = null;
            }
            active.setDisable(false);
            active.setText(I18n.t("login.button"));
        }
    }

    /** What puts the sign-in button back after {@link #setLoginInProgress}; null while nothing is running. */
    private Runnable releaseLoginBusy;

    private void onLoginSuccess(User user, boolean usedDefaultPassword) {
        if (user.getRole() == Role.CUSTOMER) {
            // No customer-facing screen exists yet in this build; documented in progress.md.
            AlertUtil.info(I18n.t("login.customer.title"), I18n.t("login.customer.message"));
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
                    AlertUtil.error(I18n.t("login.error.title"), I18n.t("login.error.employee"));
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
