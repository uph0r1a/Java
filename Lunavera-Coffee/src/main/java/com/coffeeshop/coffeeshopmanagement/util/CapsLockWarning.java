package com.coffeeshop.coffeeshopmanagement.util;

import javafx.scene.control.Label;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Shows a small "Caps Lock is on" line under the login and password fields (TODO 5.26): a cashier who types the password with
 * Caps Lock on otherwise only sees "wrong password", and every wrong try also feeds the login delay. The state comes from
 * {@link CapsLockState}.
 */
public final class CapsLockWarning {

    private CapsLockWarning() {
    }

    /** The warning line: orange, hidden and taking no room until it is needed. */
    public static Label newLabel() {
        Label label = new Label(I18n.t("capslock.warning"));
        label.setStyle("-fx-text-fill: #b45309; -fx-font-weight: bold; -fx-font-size: 12px;");
        label.setVisible(false);
        label.setManaged(false);
        return label;
    }

    /** Keeps {@code label} in step with the keys typed in any of {@code fields}; they share one state. */
    public static void install(Label label, TextInputControl... fields) {
        if (label == null) return;
        CapsLockState state = new CapsLockState();
        for (TextInputControl field : fields) {
            if (field == null) continue;
            field.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                if (event.getCode() == KeyCode.CAPS) {
                    state.capsKeyPressed();
                } else {
                    state.keyTyped(event.getText(), event.isShiftDown(),
                            event.isControlDown() || event.isAltDown() || event.isMetaDown());
                }
                show(label, state.isOn());
            });
            field.sceneProperty().addListener((observable, oldScene, scene) -> {
                if (scene == null) return;
                scene.windowProperty().addListener((o, oldWindow, window) -> watchFocus(label, state, window));
                watchFocus(label, state, scene.getWindow());
            });
        }
    }

    private static void watchFocus(Label label, CapsLockState state, javafx.stage.Window window) {
        if (window == null) return;
        window.focusedProperty().addListener((observable, was, focused) -> {
            if (!focused) {
                state.forget();
                show(label, false);
            }
        });
    }

    private static void show(Label label, boolean visible) {
        label.setVisible(visible);
        label.setManaged(visible);
    }
}
