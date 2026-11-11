package com.coffeeshop.coffeeshopmanagement.util;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.EventType;
import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TouchEvent;
import javafx.stage.Window;

import java.time.Duration;
import java.util.List;
import java.util.logging.Logger;

/**
 * Logs the person out after a while with no mouse, keyboard or touch activity (TODO 2.9), so a till left logged in
 * is not left open for the next person. The owner sets the minutes in Settings (15 until changed, 0 = off, TODO 4.49);
 * {@code -Dlunavera.idleMinutes=30} overrides that for one run. Any window counts (dialogs, receipt, history), because they all get the activity listener.
 */
public final class IdleLogout {

    private static final Logger LOGGER = Logger.getLogger(IdleLogout.class.getName());
    public static final String SETTING = "lunavera.idleMinutes";
    private static final String MARK = "lunavera.idleListener";
    private static final List<EventType<?>> ACTIVITY = List.of(MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_PRESSED,
            MouseEvent.MOUSE_DRAGGED, KeyEvent.KEY_PRESSED, ScrollEvent.SCROLL, TouchEvent.TOUCH_PRESSED);

    private static boolean installed;
    private static final IdleTracker TRACKER = new IdleTracker();
    private static Timeline timer;

    private IdleLogout() {
    }

    /** The limit is read again at every look, so a change in Settings (TODO 4.49) counts at once; {@code -Dlunavera.idleMinutes} overrides it. */
    public static void install() {
        install(() -> IdleTracker.limit(System.getProperty(SETTING), com.coffeeshop.coffeeshopmanagement.service.ShopRules.current().idleMinutes()), 10_000);
    }

    /** @param checkEveryMillis how often to look (short values are for tests) */
    public static void install(Duration limit, long checkEveryMillis) {
        if (installed) return;
        if (limit.isZero()) {
            installed = true;
            LOGGER.warning("Idle auto-logout is switched off (" + SETTING + "=0): a till left signed in stays open");
            return;
        }
        install(() -> limit, checkEveryMillis);
    }

    private static void install(java.util.function.Supplier<Duration> limit, long checkEveryMillis) {
        if (installed) return;
        installed = true;
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (change.wasAdded()) change.getAddedSubList().forEach(IdleLogout::watch);
            }
        });
        Window.getWindows().forEach(IdleLogout::watch);

        timer = new Timeline(new KeyFrame(javafx.util.Duration.millis(checkEveryMillis), e -> tick(limit.get())));
        timer.setCycleCount(Animation.INDEFINITE);
        timer.play();
    }

    private static void tick(Duration limit) {
        if (TRACKER.checkIdle(Session.getCurrentUser() != null, limit)) {
            // later, not inside the animation pulse: ending the session shows a dialog
            Platform.runLater(() -> {
                if (!TillLock.lockForIdle()) SessionGuard.idleLogout(limit); // lock first, sign out when it stays idle or cannot lock (TODO 4.36)
            });
        }
    }

    private static void watch(Window window) {
        attach(window.getScene());
        window.sceneProperty().addListener((observable, oldScene, newScene) -> attach(newScene));
    }

    private static void attach(Scene scene) {
        if (scene == null || scene.getProperties().containsKey(MARK)) return;
        scene.getProperties().put(MARK, Boolean.TRUE);
        for (EventType<?> type : ACTIVITY) {
            scene.addEventFilter(type, event -> TRACKER.touch());
        }
    }
}
