package com.coffeeshop.coffeeshopmanagement.util;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
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

    public static void switchScene(Stage stage, String fxmlPath) {
        if (LOGIN_FXML.equals(fxmlPath)) {
            closeOtherWindows(stage);
        }
        // A locked/deactivated account (Account Management -> lock, or the last-admin guard
        // taking effect from another session) used to be checked only here, on screen changes.
        // SessionGuard (progress.md, latest session) now also covers a background 30s tick, the
        // moment of taking a payment, and admin-only actions - this call is the "every screen
        // change" layer of that, delegated so there's one definition of "still valid".
        if (!LOGIN_FXML.equals(fxmlPath)
                && Session.getCurrentUser() != null && !SessionGuard.validateNow()) {
            SessionGuard.forceLogout();
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(SceneNavigator.class.getResource(fxmlPath));
            Parent root = loader.load();
            showScreen(stage, root);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load screen: " + fxmlPath, e);
            AlertUtil.error("Lỗi điều hướng",
                    "Không thể mở màn hình được yêu cầu. Vui lòng thử lại hoặc liên hệ quản trị viên.");
        }
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

    public static <T> T switchSceneAndGetController(Stage stage, String fxmlPath) {
        if (LOGIN_FXML.equals(fxmlPath)) {
            closeOtherWindows(stage);
        }
        if (!LOGIN_FXML.equals(fxmlPath)
                && Session.getCurrentUser() != null && !SessionGuard.validateNow()) {
            SessionGuard.forceLogout();
            return null;
        }
        try {
            FXMLLoader loader = new FXMLLoader(SceneNavigator.class.getResource(fxmlPath));
            Parent root = loader.load();
            showScreen(stage, root);
            return loader.getController();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load screen: " + fxmlPath, e);
            AlertUtil.error("Lỗi điều hướng",
                    "Không thể mở màn hình được yêu cầu. Vui lòng thử lại hoặc liên hệ quản trị viên.");
            return null;
        }
    }

    /**
     * Loads an FXML file and returns its controller as well, for callers that need to push
     * data into the new screen (e.g. opening the invoice window for a specific order).
     */
    public static <T> T loadAndShowNewWindow(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(SceneNavigator.class.getResource(fxmlPath));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle(title);
        com.coffeeshop.coffeeshopmanagement.util.AppIcon.apply(stage);
            stage.setScene(new Scene(root));
            AlertUtil.closeOnEscape(stage.getScene());
            stage.show();
            return loader.getController();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to open window: " + fxmlPath, e);
            AlertUtil.error("Lỗi điều hướng", "Không thể mở cửa sổ được yêu cầu.");
            return null;
        }
    }
}
