package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.AuthenticationService;
import com.coffeeshop.coffeeshopmanagement.service.PinRules;
import com.coffeeshop.coffeeshopmanagement.service.PinService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SessionGuard;
import com.coffeeshop.coffeeshopmanagement.util.TillLock;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * The lock screen of the till (TODO 4.36). {@link TillLock} hides the windows and calls {@link #show()}; the dialog stays until the
 * till is opened or the person chose "Đăng xuất". Nothing here closes a screen or clears a cart.
 *
 * <ul>
 *   <li><b>PIN mode</b>: the person who locked is staff and has a PIN. A list of the cashiers who have a PIN (the locker first)
 *       and a PIN box: the same person unlocks, or another cashier takes over, and from then on sales are theirs ({@code paid_by}).</li>
 *   <li><b>Password mode</b>: an administrator locked it, or the staff member has no PIN. One box for that person's own password.</li>
 * </ul>
 * The dialog has no cancel button, so Esc and the window's X do nothing; the only ways out are a right answer and "Đăng xuất".
 * The checks are in {@link PinService}; this class cannot be seen on a screen from the build machine, see progress.md.
 */
public final class LockScreen {

    private LockScreen() {
    }

    /** Hands the lock screen to {@link TillLock}; called once at start-up. */
    public static void install() {
        TillLock.setPresenter(LockScreen::show);
    }

    /** The button "Khóa" of the sidebar. */
    public static void lockNow() {
        if (!TillLock.lock()) {
            AlertUtil.info(I18n.t("pin.lock.title"), I18n.t("pin.lock.cannot"));
        }
    }

    static void show() {
        User locker = Session.getCurrentUser();
        if (locker == null) return;
        PinService service = new PinService();
        List<UserDAO.PinHolder> holders = PinService.mayHavePin(locker) && service.hasPin(locker.getId())
                ? service.cashiersWithPin() : List.of();
        boolean pinMode = !holders.isEmpty();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("pin.lock.title"));
        AlertUtil.configure(dialog);
        ButtonType openType = new ButtonType(I18n.t("pin.lock.open"), ButtonData.OK_DONE);
        // OTHER, not CANCEL_CLOSE: a dialog with no cancel-type button ignores Esc and the window's X.
        ButtonType logoutType = new ButtonType(I18n.t("pin.lock.logout"), ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(openType, logoutType);
        AlertUtil.setDefaultButton(dialog, openType);

        Label intro = new Label(I18n.t(pinMode ? "pin.lock.intro.pin" : "pin.lock.intro.password", Session.getDisplayName()));
        intro.setWrapText(true);
        intro.setMaxWidth(340);
        ComboBox<UserDAO.PinHolder> who = new ComboBox<>();
        who.getItems().addAll(holders);
        holders.stream().filter(h -> h.userId() == locker.getId()).findFirst().ifPresentOrElse(who::setValue,
                () -> who.getSelectionModel().selectFirst());
        who.setMaxWidth(Double.MAX_VALUE);
        who.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(UserDAO.PinHolder holder) { return holder == null ? "" : holder.name(); }
            @Override public UserDAO.PinHolder fromString(String text) { return null; }
        });
        PasswordField secret = new PasswordField();
        secret.setPromptText(I18n.t(pinMode ? "pin.lock.prompt.pin" : "pin.lock.prompt.password"));
        if (pinMode) com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter.limit(secret, PinRules.MAX_LENGTH);
        Label message = new Label();
        message.setWrapText(true);
        message.setMaxWidth(340);
        message.setStyle("-fx-text-fill: #b33939; -fx-font-weight: bold;");
        VBox box = new VBox(10, intro);
        if (pinMode) box.getChildren().add(who);
        box.getChildren().addAll(secret, message);
        box.setPadding(new Insets(20));
        dialog.getDialogPane().setContent(box);
        javafx.application.Platform.runLater(secret::requestFocus);

        boolean[] logOut = {false};
        dialog.getDialogPane().lookupButton(logoutType).addEventFilter(ActionEvent.ACTION, e -> logOut[0] = true);
        dialog.getDialogPane().lookupButton(openType).addEventFilter(ActionEvent.ACTION, event -> {
            String problem = pinMode ? tryPin(service, who.getValue(), secret.getText()) : tryPassword(service, secret.getText());
            if (problem != null) {
                message.setText(problem);
                secret.clear();
                secret.requestFocus();
                event.consume(); // stay locked
            }
        });
        dialog.showAndWait();
        if (logOut[0]) {
            SessionGuard.endSession();
        } else if (Session.getCurrentUser() != null) {
            TillLock.refreshNames(); // another cashier may have taken over
        }
    }

    /** Null when the PIN was right (the person is signed in), else what to tell the person. */
    private static String tryPin(PinService service, UserDAO.PinHolder who, String pin) {
        if (who == null) return I18n.t("pin.lock.nobody");
        PinService.Outcome outcome = service.signInByPin(who.userId(), pin);
        return switch (outcome.status()) {
            case OK -> null;
            case WRONG -> I18n.t("pin.lock.wrong", outcome.remaining());
            case WAIT -> I18n.t("pin.lock.wait", outcome.waitSeconds());
            case NO_PIN, DISABLED_NOW -> I18n.t("pin.lock.off", who.name());
            case NOT_ALLOWED -> I18n.t("pin.lock.notallowed");
        };
    }

    private static String tryPassword(PinService service, String password) {
        AuthenticationService.LoginResult result = service.unlockWithPassword(password);
        return switch (result.status()) {
            case SUCCESS -> null;
            case TOO_MANY_ATTEMPTS -> I18n.t("pin.lock.wait", result.retryAfterSeconds());
            case INACTIVE -> I18n.t("pin.lock.notallowed");
            case INVALID_CREDENTIALS -> I18n.t("pin.lock.wrong.password");
        };
    }
}
