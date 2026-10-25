package com.coffeeshop.coffeeshopmanagement.util;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ProgressIndicator;

/**
 * Makes it visible that a slow action (a backup, a CSV export, a login) is working, so a click does not look ignored
 * (TODO 5.16): the button shows a small spinner and a "working" text and is switched off, and the window's mouse pointer
 * becomes the waiting one. Every {@code hold} returns what puts things back; call it on the FX thread when the work ends,
 * success or failure. Calling it twice does nothing the second time.
 */
public final class BusyIndicator {

    /** Kept in the scene's properties: how many actions are running in this window and the pointer it had before. */
    private static final class SceneBusy {
        final BusyCount count = new BusyCount();
        Cursor saved;
    }

    private static final String KEY = "lunavera.busy";

    private BusyIndicator() {
    }

    /** The button shows {@code busyText} with a spinner, is disabled, and the window's pointer waits, until the result is run. */
    public static Runnable hold(Button button, String busyText) {
        if (button == null) {
            return () -> { };
        }
        String oldText = button.getText();
        Node oldGraphic = button.getGraphic();
        ContentDisplay oldDisplay = button.getContentDisplay();
        boolean wasDisabled = button.isDisable();

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(14, 14);
        spinner.setMinSize(14, 14);
        spinner.setMaxSize(14, 14);
        button.setText(busyText);
        button.setGraphic(spinner);
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setDisable(true);
        Runnable pointer = waitCursor(button);

        boolean[] released = {false};
        return () -> {
            if (released[0]) {
                return;
            }
            released[0] = true;
            button.setText(oldText);
            button.setGraphic(oldGraphic);
            button.setContentDisplay(oldDisplay);
            if (!wasDisabled) {
                button.setDisable(false);
            }
            pointer.run();
        };
    }

    /** Only the waiting pointer over the window that contains {@code node}; nothing happens for a node that is not on a screen. */
    public static Runnable waitCursor(Node node) {
        Scene scene = node == null ? null : node.getScene();
        if (scene == null) {
            return () -> { };
        }
        SceneBusy busy = (SceneBusy) scene.getProperties().computeIfAbsent(KEY, k -> new SceneBusy());
        if (busy.count.enter()) {
            busy.saved = scene.getCursor();
            scene.setCursor(Cursor.WAIT);
        }
        boolean[] released = {false};
        return () -> {
            if (released[0]) {
                return;
            }
            released[0] = true;
            if (busy.count.exit()) {
                scene.setCursor(busy.saved);
                busy.saved = null;
            }
        };
    }
}
