package com.coffeeshop.coffeeshopmanagement.util;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Centralizes "load this FXML into the current window" so every controller doesn't
 * duplicate its own copy of this logic (several of the original screens each had their own
 * near-identical switchScene method).
 */
public final class SceneNavigator {

    private static final Logger LOGGER = Logger.getLogger(SceneNavigator.class.getName());
    private static final String LOGIN_FXML = "/fxml/dangnhap.fxml";

    /** Run on every screen right after it is loaded and before it is shown (LunaveraApplication sets it). */
    private static Consumer<Parent> screenDecorator = root -> { };

    private SceneNavigator() {
    }

    /** The screen last put into each window, so a language change can load the same screen again (TODO 1.7). */
    private static final Map<Stage, String> SHOWN = new WeakHashMap<>();

    /**
     * Every screen is loaded here with the current language's text: {@code %key} in an FXML file is looked up in
     * {@link I18n#bundle()}. Loading a screen again after {@link I18n#setLanguage} shows it in the new language.
     */
    public static FXMLLoader newLoader(String fxmlPath) {
        return newLoader(fxmlPath, I18n.bundle());
    }

    /** Same, with the text of a given bundle (the receipt window is always English). */
    public static FXMLLoader newLoader(String fxmlPath, java.util.ResourceBundle bundle) {
        return new FXMLLoader(SceneNavigator.class.getResource(fxmlPath), bundle);
    }

    /**
     * Loads the screen this window is showing once more, in the language now in use. Anything typed into it and not
     * saved is lost, so a screen that holds work in progress must not offer a language switch without asking first.
     */
    public static void reloadCurrent(Stage stage) {
        String path;
        synchronized (SHOWN) {
            path = SHOWN.get(stage);
        }
        if (path != null) {
            switchScene(stage, path);
        }
    }

    /** The very first screen is loaded by the application itself, not by {@code switchScene}; this tells the navigator about it. */
    public static void rememberFirstScreen(Stage stage, String fxmlPath) {
        remember(stage, fxmlPath);
    }

    private static void remember(Stage stage, String fxmlPath) {
        synchronized (SHOWN) {
            SHOWN.put(stage, fxmlPath);
        }
        mainStage = new java.lang.ref.WeakReference<>(stage);
    }

    /** The window the screens are switched in: the owner of the receipt, history and report windows (TODO 5.36). */
    private static java.lang.ref.WeakReference<Stage> mainStage = new java.lang.ref.WeakReference<>(null);

    /**
     * A new, separate window that belongs to the main window (TODO 5.36). An owned window stays in front of the till, is minimised
     * and closed with it and has no taskbar button of its own; without an owner, a click on the till put the receipt that had just
     * opened for a payment behind it with nothing on screen to say it was still there. It is not modal: the till stays usable.
     * Every extra window of the app comes from here, so a new one cannot forget the owner.
     */
    public static Stage newOwnedStage() {
        Stage stage = new Stage();
        Stage main = mainStage.get();
        if (main != null && main.isShowing()) {
            stage.initOwner(main);
        }
        return stage;
    }


    public static void setScreenDecorator(Consumer<Parent> decorator) {
        screenDecorator = decorator == null ? root -> { } : decorator;
    }

    /** Switches the scene of the window that raised the given ActionEvent. */
    public static void switchScene(ActionEvent event, String fxmlPath) {
        Node source = (Node) event.getSource();
        Stage stage = (Stage) source.getScene().getWindow();
        switchScene(stage, fxmlPath);
    }

    /**
     * Closes every window except {@code keep}: the receipt, history and report windows of the person who just left
     * must not stay on screen for the next one (TODO 2.30).
     */
    public static void closeOtherWindows(javafx.stage.Window keep) {
        for (javafx.stage.Window window : new java.util.ArrayList<>(javafx.stage.Window.getWindows())) {
            if (window != keep && window.isShowing()) {
                window.hide();
            }
        }
    }

    /** Shows the screen in the window. Same as {@link #switchSceneAndGetController} when the caller does not need the controller. */
    public static void switchScene(Stage stage, String fxmlPath) {
        switchSceneAndGetController(stage, fxmlPath);
    }

    /**
     * Whether a screen may be shown now (TODO 6.64: the one definition both ways of switching screens use). Any screen but the
     * login one needs an account that is still valid: a locked/deactivated account (Account Management -> lock, or the
     * last-admin guard taking effect from another session) is checked on every screen change. {@link SessionGuard} also covers
     * a background tick, the moment of taking a payment and admin-only actions; this is the "every screen change" layer of
     * that, delegated so there is one definition of "still valid". When the account is no longer valid, {@code forceLogout}
     * runs and the screen is not shown.
     */
    static boolean mayShow(String fxmlPath, java.util.function.BooleanSupplier stillValid, Runnable forceLogout) {
        if (LOGIN_FXML.equals(fxmlPath) || Session.getCurrentUser() == null || stillValid.getAsBoolean()) {
            return true;
        }
        forceLogout.run();
        return false;
    }

    /**
     * Puts a loaded screen into the window: decorates it (sidebar extras), wraps it so it fits the display
     * (ScreenFit, TODO 2.4), and sizes/centres the window. The window's title-bar size is measured from the
     * scene it has now, before it is replaced.
     */
    private static void showScreen(Stage stage, Parent root) {
        screenDecorator.accept(root);
        Scene old = stage.getScene();
        ScreenFit fit = ScreenFit.wrap(root);
        if (stage.isShowing() && old != null) {
            // Switching tabs only swaps the content of the scene that is already there. Making a new Scene and
            // re-sizing/re-positioning the window on every click made the whole window jump (and fight a tiling
            // window manager such as Hyprland) - the flicker seen when changing sidebar tabs.
            boolean sameDesign = old.getRoot() instanceof ScreenFit previous
                    && previous.designWidth() == fit.designWidth() && previous.designHeight() == fit.designHeight();
            old.setRoot(fit);
            if (!sameDesign) {
                // Only the login screen (smaller design) <-> the main screens (bigger design) need a different window size.
                ScreenFit.fitStage(stage, fit, Math.max(0, stage.getWidth() - old.getWidth()),
                        Math.max(0, stage.getHeight() - old.getHeight()));
            }
            return;
        }
        boolean measurable = stage.isShowing() && old != null;
        double chromeW = measurable ? Math.max(0, stage.getWidth() - old.getWidth()) : 0;
        double chromeH = measurable ? Math.max(0, stage.getHeight() - old.getHeight()) : 0;
        stage.setScene(ScreenFit.newScene(fit, chromeW, chromeH));
        ScreenFit.fitStage(stage, fit, chromeW, chromeH);
    }

    /**
     * Loads the screen into the window and returns its controller, for callers that need to push data into it. Returns
     * {@code null} when the screen was not shown: the account was locked (the session is ended and the login screen shown) or the
     * file could not be loaded (an error is shown). This is the only place a screen is loaded into a window (TODO 6.64);
     * {@link #switchScene(Stage, String)} calls it and ignores the result.
     */
    public static <T> T switchSceneAndGetController(Stage stage, String fxmlPath) {
        if (LOGIN_FXML.equals(fxmlPath)) {
            closeOtherWindows(stage);
        }
        if (!mayShow(fxmlPath, SessionGuard::validateNow, SessionGuard::forceLogout)) {
            return null;
        }
        try {
            FXMLLoader loader = newLoader(fxmlPath);
            Parent root = loader.load();
            showScreen(stage, root);
            remember(stage, fxmlPath);
            return loader.getController();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load screen: " + fxmlPath, e);
            AlertUtil.error(I18n.t("nav.error.title"), I18n.t("nav.error.screen"));
            return null;
        }
    }

    /**
     * Loads an FXML file and returns its controller as well, for callers that need to push
     * data into the new screen (e.g. opening the invoice window for a specific order).
     */
    public static <T> T loadAndShowNewWindow(String fxmlPath, String title) {
        return loadAndShowNewWindow(fxmlPath, title, I18n.bundle());
    }

    public static <T> T loadAndShowNewWindow(String fxmlPath, String title, java.util.ResourceBundle bundle) {
        try {
            FXMLLoader loader = newLoader(fxmlPath, bundle);
            Parent root = loader.load();
            Stage stage = newOwnedStage();
            stage.setTitle(title);
        com.coffeeshop.coffeeshopmanagement.util.AppIcon.apply(stage);
            stage.setScene(new Scene(root));
            AlertUtil.closeOnEscape(stage.getScene());
            stage.show();
            return loader.getController();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open window: " + fxmlPath, e);
            AlertUtil.error(I18n.t("nav.error.title"), I18n.t("nav.error.window"));
            return null;
        }
    }
}
