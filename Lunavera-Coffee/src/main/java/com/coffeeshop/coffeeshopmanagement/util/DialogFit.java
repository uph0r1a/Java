package com.coffeeshop.coffeeshopmanagement.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogEvent;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.stage.Screen;
import javafx.stage.Window;

/**
 * Makes a dialog fit a short screen (TODO 2.99). A dialog is as tall as its content, so the payment box (the tallest: methods,
 * split, points, voucher, transfer QR) and every form with a list ran off the bottom of a 1366x768 till, with the buttons out of
 * reach. Now, when the dialog is about to show, its content is put in a {@link ScrollPane} and the dialog's own size is limited to
 * what the screen's free area allows ({@link DialogFitMath}); the buttons stay at the bottom, always visible, and the content
 * scrolls when it does not fit. On a screen where everything fits nothing changes: the scroll bars do not appear.
 *
 * <p>{@link AlertUtil#configure} installs it on every dialog of the program, so a new dialog gets it for free;
 * {@code DialogFitUsageTest} fails for a dialog that never goes through {@code configure} or {@link #install}.
 */
public final class DialogFit {

    private static final String MARK = "lunavera.dialogFit";

    private DialogFit() {
    }

    /** Fits {@code dialog} to the screen when it shows. Safe to call twice. */
    public static void install(Dialog<?> dialog) {
        if (dialog.getDialogPane().getProperties().putIfAbsent(MARK, Boolean.TRUE) != null) return;
        dialog.setOnShowing(e -> apply(dialog));
    }

    /** {@link #install} for a dialog made in one expression: {@code Dialog<ButtonType> d = DialogFit.fitted(new Dialog<>());}. */
    public static <D extends Dialog<?>> D fitted(D dialog) {
        install(dialog);
        return dialog;
    }

    private static void apply(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        Rectangle2D area = freeAreaOf(dialog.getOwner());
        double maxHeight = DialogFitMath.maxHeight(area.getHeight());
        double maxWidth = DialogFitMath.maxWidth(area.getWidth());
        Node content = pane.getContent();
        Label longText = null;
        double longTextWidth = 0;
        if (content == null && DialogFitMath.isLongText(pane.getContentText())) {
            // an alert with a long message (a list of skipped rows, a help text): scroll it instead of cutting it off
            Label text = new Label(pane.getContentText());
            text.setWrapText(true);
            longTextWidth = Math.min(560, maxWidth - 80);
            text.setMaxWidth(longTextWidth);
            pane.setContentText(null);
            content = text;
            longText = text;
        }
        if (content != null && !(content instanceof ScrollPane)) {
            pane.setContent(scrolling(content));
        }
        pane.setMaxHeight(maxHeight);
        // AlertUtil keeps a dialog at the height its text needs (TODO 1.7); on a screen too short for that height the scroll box wins (TODO 2.99).
        if (pane.prefHeight(-1) > maxHeight) pane.setMinHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
        pane.setMaxWidth(maxWidth);
        if (longText != null && pane.getContent() instanceof ScrollPane scroll) {
            final Label shownText = longText;
            final double width = longTextWidth;
            // The window is sized before the text wraps, so the scroll box can come out shorter than its text even on a tall screen
            // (seen on the restore confirmation: the last paragraph and the question were out of sight). Once it shows, measure the
            // wrapped text and give the box the height it needs, as far as the screen allows.
            dialog.addEventHandler(DialogEvent.DIALOG_SHOWN, e -> Platform.runLater(() -> growToText(pane, scroll, shownText, width, area)));
        }
    }

    private static void growToText(DialogPane pane, ScrollPane scroll, Label text, double width, Rectangle2D area) {
        Scene scene = pane.getScene();
        if (scene == null || scene.getWindow() == null) return;
        Window window = scene.getWindow();
        double needed = Math.ceil(text.prefHeight(width)) + 4;
        double viewport = scroll.getViewportBounds().getHeight();
        if (needed <= viewport + 1) return;
        double room = pane.getMaxHeight() - (pane.getHeight() - viewport); // the buttons and the padding stay
        double wanted = Math.min(needed, room);
        if (wanted <= viewport + 1) return;
        scroll.setPrefViewportHeight(wanted);
        window.sizeToScene();
        double y = Math.min(window.getY(), area.getMaxY() - window.getHeight());
        window.setY(Math.max(area.getMinY(), y));
    }

    /** The content in a scroll box that looks like no box at all while nothing needs scrolling. */
    static ScrollPane scrolling(Node content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPadding(Insets.EMPTY);
        scroll.setFocusTraversable(false);
        // Not "-fx-background: transparent": the theme works the text colour of everything inside out of that colour, and a transparent one
        // gives white text on the light dialog (seen on a real packaged build, TODO 6.15). Only the box and its viewport are made see-through.
        scroll.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        scroll.skinProperty().addListener((o, was, skin) -> {
            Node viewport = scroll.lookup(".viewport");
            if (viewport != null) viewport.setStyle("-fx-background-color: transparent;");
        });
        return scroll;
    }

    /** The free area (without the taskbar) of the screen the dialog's owner is on, else of the main screen. */
    static Rectangle2D freeAreaOf(Window owner) {
        Screen screen = null;
        if (owner != null) {
            for (Screen candidate : Screen.getScreensForRectangle(owner.getX(), owner.getY(), Math.max(1, owner.getWidth()), Math.max(1, owner.getHeight()))) {
                screen = candidate;
                break;
            }
        }
        return (screen != null ? screen : Screen.getPrimary()).getVisualBounds();
    }
}
