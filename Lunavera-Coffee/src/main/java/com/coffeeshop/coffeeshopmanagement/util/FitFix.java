package com.coffeeshop.coffeeshopmanagement.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Accordion;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Control;
import javafx.scene.control.DialogPane;
import javafx.scene.control.IndexedCell;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * Keeps text from being cut to "..." when it does not need to be (TODO 1.7, the English fit check). English and Vietnamese
 * texts differ in length, and two things in a JavaFX layout cut text instead of making room: an {@code HBox} that is wider than
 * its window squeezes a label or a button down to "...", and a label that wraps in a {@code VBox} gets a height computed before it
 * wrapped, so its last line is cut (also a text set later). Both are cured the same way: the label or button keeps its natural width, the wrapping label its
 * natural height; the text boxes and combo boxes beside them are what gives way. Applied once to every window as it opens
 * ({@link AppStyle}); only what the code or the FXML left at the default size is touched.
 */
public final class FitFix {

    private FitFix() {
    }

    /** Applies the two rules to everything under {@code node} (not into the inside of a control such as a table or a combo box). */
    public static void protect(Node node) {
        if (node instanceof Labeled labeled && !(labeled instanceof IndexedCell)) {
            if (labeled instanceof Label label && label.isWrapText()) {
                keepWrappedHeight(label);
            } else if (labeled.getParent() instanceof HBox && labeled.getMinWidth() == Region.USE_COMPUTED_SIZE
                    && (labeled instanceof Label || labeled instanceof ButtonBase)) {
                labeled.setMinWidth(Region.USE_PREF_SIZE);
            }
        }
        if (node instanceof Parent parent && (!(node instanceof Control) || holdsContent(node))) {
            for (Node child : parent.getChildrenUnmodifiable().toArray(new Node[0])) protect(child);
        }
    }

    /**
     * A wrapping label asks for the height of ONE line when its parent first measures it, and is then given just that. It is given
     * the height its text needs at the width it really has, each time the width changes.
     */
    public static void keepWrappedHeight(Label label) {
        if (label.getProperties().putIfAbsent("fitFix", Boolean.TRUE) != null) return;
        if (label.getMinHeight() == Region.USE_COMPUTED_SIZE) label.setMinHeight(Region.USE_PREF_SIZE);
        Runnable fit = () -> {
            if (label.getWidth() <= 0) return;
            double height = label.prefHeight(label.getWidth());
            if (Math.abs(height - label.getPrefHeight()) > 1) label.setPrefHeight(height);
        };
        label.widthProperty().addListener((o, was, width) -> fit.run());
        // The text that arrives later (a report summary filled in when its numbers are loaded) needs its own height as well.
        // and a moment after the text or the font changed, once the style sheet has laid the new text out.
        javafx.animation.PauseTransition later = new javafx.animation.PauseTransition(javafx.util.Duration.millis(120));
        later.setOnFinished(e -> fit.run());
        label.textProperty().addListener((o, was, text) -> later.playFromStart());
        label.fontProperty().addListener((o, was, font) -> later.playFromStart());
        Platform.runLater(fit);
    }

    /** Lets every plain label under {@code parent} wrap, for a form whose column is narrower than its longest sentence (English runs longer). */
    public static void wrapLabels(Parent parent) {
        for (Node child : parent.getChildrenUnmodifiable().toArray(new Node[0])) {
            if (child instanceof Label label && !label.isWrapText()) {
                label.setWrapText(true);
                keepWrappedHeight(label);
            } else if (child instanceof Parent inner && !(child instanceof Control)) {
                wrapLabels(inner);
            }
        }
    }

    /** Controls that are only containers for other nodes: their children are the screen, not the inside of a widget. */
    private static boolean holdsContent(Node node) {
        return node instanceof TabPane || node instanceof TitledPane || node instanceof SplitPane || node instanceof ScrollPane
                || node instanceof Accordion || node instanceof DialogPane || node instanceof ToolBar;
    }

    /** Applies {@link #protect} to the scene now and once more after the first layout (content built after the window opened). */
    public static void protectScene(Scene scene) {
        if (scene == null || scene.getRoot() == null || Boolean.getBoolean("lunavera.noFitFix")) return; // the property is for measuring "before"
        protect(scene.getRoot());
        Platform.runLater(() -> {
            if (scene.getRoot() != null) protect(scene.getRoot());
        });
    }
}
