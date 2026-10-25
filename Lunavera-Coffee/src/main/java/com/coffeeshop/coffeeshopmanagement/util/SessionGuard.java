package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.User;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Account status used to be checked only at login, so an admin locking (or deleting) an account
 * did nothing to that person's already-open session (TODO.md item 4). This re-reads the account
 * from the database and enforces it at the moments that matter:
 * <ul>
 *   <li>every screen switch ({@link SceneNavigator}),</li>
 *   <li>taking a payment and admin-only actions (their controllers call {@link #validateNow()}),</li>
 *   <li>a periodic background check, so someone idling on one screen is still caught.</li>
 * </ul>
 * It also refreshes the session's role, so demoting an admin takes effect without a re-login.
 *
 * Deliberately <b>fails open</b> on a database error: a transient SQLite hiccup shouldn't throw
 * a cashier out mid-sale. Only a definite "locked" or "account no longer exists" answer does.
 */
public final class SessionGuard {

    private static final Logger LOGGER = Logger.getLogger(SessionGuard.class.getName());
    private static final Duration CHECK_INTERVAL = Duration.seconds(30);

    private enum Check { OK, INVALID, PASSWORD_CHANGED, UNKNOWN }

    private record Snapshot(Check check, User fresh) {
    }

    /** Set when the session ended because the password was changed elsewhere; picks the message of {@link #forceLogout()}. */
    private static volatile boolean endedByPasswordChange;

    private static Stage primaryStage;
    private static Timeline watcher;

    private SessionGuard() {
    }

    /** Pure database read - safe to run off the FX thread. */
    private static Snapshot fetch() {
        User current = Session.getCurrentUser();
        if (current == null) {
            return new Snapshot(Check.INVALID, null);
        }
        try {
            Optional<User> fresh = new UserDAO().findById(current.getId());
            if (fresh.isEmpty() || fresh.get().getStatus() == AccountStatus.LOCKED) {
                return new Snapshot(Check.INVALID, null);
            }
            String baseline = Session.getPasswordBaseline();
            if (baseline != null && !baseline.equals(fresh.get().getPasswordHash())) {
                // An admin reset this account's password (or it was changed elsewhere): this session ends (TODO 2A.10).
                return new Snapshot(Check.PASSWORD_CHANGED, null);
            }
            return new Snapshot(Check.OK, fresh.get());
        } catch (DataAccessException e) {
            LOGGER.log(Level.WARNING, "Could not re-validate the session; leaving it as is", e);
            return new Snapshot(Check.UNKNOWN, null);
        }
    }

    /** Must run on the FX thread (it updates {@link Session}). Returns false if the account
     *  is locked or gone. */
    private static boolean apply(Snapshot snapshot) {
        if (snapshot.check() == Check.PASSWORD_CHANGED) {
            endedByPasswordChange = true;
        }
        if (snapshot.check() == Check.OK) {
            Session.refresh(snapshot.fresh(), Session.getCurrentEmployee());
        }
        return snapshot.check() != Check.INVALID && snapshot.check() != Check.PASSWORD_CHANGED;
    }

    /** Synchronous check: one indexed single-row read, cheap enough for a click handler. */
    public static boolean validateNow() {
        return apply(fetch());
    }

    /** Ends the session and returns to the login screen. */
    public static void forceLogout() {
        Session.clear();
        stopWatching();
        Stage stage = primaryStage;
        primaryStage = null;
        if (stage != null) {
            // The POS screen installs a "you have an unpaid cart" close guard on the window;
            // the session is already over, so it must not block or outlive this.
            stage.setOnCloseRequest(null);
        }
        SceneNavigator.closeOtherWindows(stage);
        boolean passwordReset = endedByPasswordChange;
        endedByPasswordChange = false;
        AlertUtil.warning(I18n.t("session.ended.title"),
                I18n.t(passwordReset ? "session.ended.passwordchanged" : "session.ended.locked")
                        + I18n.t("session.ended.tologin"));
        if (stage != null) {
            SceneNavigator.switchScene(stage, "/fxml/dangnhap.fxml");
        }
    }

    /**
     * Ends the session because nobody touched the app for {@code idle} (TODO 2.9): closes every other window
     * (dialogs, receipt, history), clears the session and returns to the login screen.
     */
    public static void idleLogout(java.time.Duration idle) {
        if (Session.getCurrentUser() == null) return;
        Stage stage = primaryStage;
        Session.clear();
        stopWatching();
        primaryStage = null;
        SceneNavigator.closeOtherWindows(stage);
        if (stage != null) {
            stage.setOnCloseRequest(null);
            SceneNavigator.switchScene(stage, "/fxml/dangnhap.fxml");
        }
        long minutes = Math.max(1, Math.round(idle.toSeconds() / 60.0));
        AlertUtil.info(I18n.t("session.idle.title"), I18n.t("session.idle.message", minutes));
    }

    /** Call right after a successful login. */
    public static void startWatching(Stage stage) {
        stopWatching();
        primaryStage = stage;
        watcher = new Timeline(new KeyFrame(CHECK_INTERVAL, e -> tick()));
        watcher.setCycleCount(Animation.INDEFINITE);
        watcher.play();
    }

    private static void stopWatching() {
        if (watcher != null) {
            watcher.stop();
            watcher = null;
        }
    }

    /** How many 30-second checks a forced logout may wait for a modal dialog to close. */
    static final int MAX_POSTPONED_TICKS = 2;
    private static int postponedTicks;

    /** A definite "locked" answer waits only while a modal dialog is open, and only {@link #MAX_POSTPONED_TICKS} times. */
    static boolean shouldPostpone(boolean modalOpen, int alreadyPostponed) {
        return modalOpen && alreadyPostponed < MAX_POSTPONED_TICKS;
    }

    private static void tick() {
        if (Session.getCurrentUser() == null) {
            stopWatching(); // logged out normally
            return;
        }
        // The check runs even while the history, report or receipt window is open (TODO 3.14): those used to pause it
        // for as long as they stayed open, so a locked account kept working. Only a MODAL dialog (a payment, a form)
        // postpones the forced logout, and only for a couple of ticks, so a person is not thrown out mid-sale for ever.
        boolean modalOpen = false;
        for (Window window : Window.getWindows()) {
            if (window.isShowing() && window != primaryStage
                    && window instanceof Stage stage && stage.getModality() != javafx.stage.Modality.NONE) {
                modalOpen = true;
            }
        }
        final boolean modal = modalOpen;
        Async.run(
                SessionGuard::fetch,
                snapshot -> {
                    if (Session.getCurrentUser() == null) return;
                    boolean invalid = snapshot.check() == Check.INVALID;
                    if (invalid && shouldPostpone(modal, postponedTicks)) {
                        postponedTicks++;
                        return;
                    }
                    postponedTicks = 0;
                    if (!apply(snapshot)) {
                        forceLogout();
                    }
                },
                error -> LOGGER.log(Level.WARNING, "Session watcher check failed", error));
    }
}
