package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.service.TimeClockService;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.ClockOfferRules;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;

import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The questions "clock in for this shift?" at sign-in and "clock out?" at sign-out and at the idle sign-out (TODO 5.38). Staff clock
 * in and out in their own window ({@code TimeClockDialog}); before this, nothing in the sign-in or sign-out path mentioned it, so
 * stretches were forgotten and only flagged after the forgotten-after limit.
 *
 * <p>Each question is one box with the useful answer as the default button (Enter) and "Not now" as Esc, so the till is never held
 * up: "Not now" simply carries on with what the person was doing, and a failure of the database never stops a sign-out. The
 * decisions (who is asked, when) are in {@code ClockOfferRules}; the time-clock rules and the writing are in {@code TimeClockDAO}.
 * Every database call runs off the screen thread ({@link Async}).
 */
public final class ClockOffer {

    private static final Logger LOGGER = Logger.getLogger(ClockOffer.class.getName());
    private static final DateTimeFormatter SINCE = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    /** True from the first click on "sign out" until the person has answered, so a second click does not stack a second question. Screen thread only. */
    private static boolean asking;

    private ClockOffer() {
    }

    /**
     * Right after a sign-in (and after another cashier took over a locked till with a PIN): a member of the staff who is not
     * clocked in is asked once. Does nothing for the administrator, for somebody already clocked in, or when nobody is signed in.
     */
    public static void afterSignIn() {
        User user = Session.getCurrentUser();
        if (user == null || !ClockOfferRules.offerClockIn(user.getRole(), false)) return; // no database read for the owner's account
        int userId = user.getId();
        Async.run(
                () -> new TimeClockService().openForMe(),
                open -> {
                    User now = Session.getCurrentUser();
                    if (now == null || now.getId() != userId || !ClockOfferRules.offerClockIn(now.getRole(), open.isPresent())) return;
                    if (!ask(I18n.t("clock.offer.in.title"), I18n.t("clock.offer.in"), I18n.t("time.clock.in"))) return;
                    Async.run(() -> new TimeClockService().clockIn(), entry -> { },
                            error -> failed(I18n.t("clock.offer.failed.in"), error));
                },
                error -> LOGGER.log(Level.WARNING, "Could not read the time clock at sign-in; no question asked", error));
    }

    /**
     * Before a sign-out by the person (the "Đăng xuất" button, or "Đăng xuất" on the lock screen): when they are still clocked in,
     * asks "clock out?" and then runs {@code then}, the sign-out itself, whatever the answer. {@code then} also runs at once when
     * nobody is signed in, when the clock cannot be read, and when the open stretch is so old that it looks forgotten.
     */
    public static void beforeSignOut(Runnable then) {
        if (Session.getCurrentUser() == null) {
            then.run();
            return;
        }
        if (asking) return;
        asking = true;
        Runnable carryOn = () -> {
            asking = false;
            then.run();
        };
        Async.run(
                () -> new TimeClockService().openForMe(),
                open -> {
                    if (!ClockOfferRules.offerClockOut(open.orElse(null), AppClock.now())) {
                        carryOn.run();
                        return;
                    }
                    if (!ask(I18n.t("clock.offer.out.title"), I18n.t("clock.offer.out", SINCE.format(open.get().clockIn())), I18n.t("time.clock.out"))) {
                        carryOn.run();
                        return;
                    }
                    Async.run(() -> new TimeClockService().clockOut(), entry -> carryOn.run(), error -> {
                        failed(I18n.t("clock.offer.failed.out"), error);
                        carryOn.run();
                    });
                },
                error -> {
                    LOGGER.log(Level.WARNING, "Could not read the time clock at sign-out; signing out without a question", error);
                    carryOn.run();
                });
    }

    /**
     * After the idle timer signed somebody out: shows the usual "signed out automatically" notice, and when that person is still
     * clocked in, asks in the same box whether to clock them out now. Nobody is signed in at that point, so the question is about
     * {@code userId}, who was signed in a moment ago. Nobody answering leaves the stretch open, exactly as before this existed.
     */
    public static void afterIdleLogout(int userId, long minutes) {
        String title = I18n.t("session.idle.title");
        String notice = I18n.t("session.idle.message", minutes);
        Async.run(
                () -> new TimeClockService().openFor(userId),
                open -> {
                    if (!ClockOfferRules.offerClockOut(open.orElse(null), AppClock.now())) {
                        AlertUtil.info(title, notice);
                        return;
                    }
                    String text = notice + "\n\n" + I18n.t("clock.offer.idle", SINCE.format(open.get().clockIn()));
                    if (ask(title, text, I18n.t("time.clock.out"))) {
                        Async.run(() -> new TimeClockService().clockOutFor(userId), entry -> { },
                                error -> failed(I18n.t("clock.offer.failed.out"), error));
                    }
                },
                error -> {
                    LOGGER.log(Level.WARNING, "Could not read the time clock after the idle sign-out; no question asked", error);
                    AlertUtil.info(title, notice);
                });
    }

    /** One question: {@code yesLabel} is the default button (Enter), "Not now" is Esc and the window's X. True only for the first. */
    private static boolean ask(String title, String message, String yesLabel) {
        ButtonType yes = new ButtonType(yesLabel, ButtonData.OK_DONE);
        ButtonType notNow = new ButtonType(I18n.t("clock.offer.notnow"), ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, yes, notNow);
        alert.setTitle(title);
        alert.setHeaderText(null);
        AlertUtil.configure(alert);
        AlertUtil.setDefaultButton(alert, yes);
        AlertUtil.setCancelButton(alert, notNow);
        return alert.showAndWait().filter(button -> button == yes).isPresent();
    }

    private static void failed(String message, Throwable error) {
        LOGGER.log(Level.WARNING, "The clock offer could not be carried out", error);
        AlertUtil.warning(I18n.t("time.clock.title"), message);
    }
}
