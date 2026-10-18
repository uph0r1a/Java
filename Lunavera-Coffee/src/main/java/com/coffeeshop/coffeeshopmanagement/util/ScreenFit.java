package com.coffeeshop.coffeeshopmanagement.util;

import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Makes the fixed-size screens (laid out for 1600x1000, login 1280x720) fit whatever screen the shop has (TODO 2.4).
 *
 * <p>Before: the window was non-resizable and exactly as big as the layout, so on a 1920x1080 screen at 125%
 * Windows scaling (about 1536x864 usable) the right and bottom edges, with their buttons, were off screen and could not be reached.
 * Now every screen is wrapped in a {@code ScreenFit}: the layout keeps its design size but is drawn scaled
 * (never below {@link ScreenFitMath#MIN_SCALE}, never above {@link ScreenFitMath#MAX_SCALE}) to fit the window, and the window is resizable,
 * so a smaller or larger window just zooms the whole screen. Nothing needs to be rearranged and nothing can be cut off.
 */
public final class ScreenFit extends Pane {

    /** Room left for the taskbar/dock when the window cannot be measured yet. */
    private static final double MARGIN = 8;

    private final Parent content;
    private final double designWidth;
    private final double designHeight;

    private ScreenFit(Parent content, double designWidth, double designHeight) {
        this.content = content;
        this.designWidth = designWidth;
        this.designHeight = designHeight;
        content.setManaged(false);
        getChildren().add(content);
    }

    /** Wraps a freshly loaded screen. Its own style sheet's {@code .root} rules keep applying (the wrapper is the scene root now). */
    public static ScreenFit wrap(Parent content) {
        if (!content.getStyleClass().contains("root")) {
            content.getStyleClass().add("root");
        }
        double w = content.prefWidth(-1);
        double h = content.prefHeight(-1);
        if (content instanceof javafx.scene.layout.Region region) {
            // The screens were drawn with their size locked to 1600x1000 (min = max = pref). Unlock it so a window that
            // is wider or taller than that shape gives the extra room to the layout instead of leaving blank margins.
            region.setMinSize(0, 0);
            region.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        }
        ScreenFit fit = new ScreenFit(content, w > 0 ? w : 1600, h > 0 ? h : 1000);
        // The screen's own style sheet also reaches the wrapper, so its ".root" background fills the margins around a
        // scaled screen instead of the default grey (TODO 2.39).
        fit.getStylesheets().addAll(content.getStylesheets());
        return fit;
    }

    /** True when {@code other} was drawn for the same design size (login to login, dashboard to products). */
    public boolean sameDesignAs(ScreenFit other) {
        return other != null && Math.abs(designWidth - other.designWidth) < 0.5 && Math.abs(designHeight - other.designHeight) < 0.5;
    }

    public double designWidth() {
        return designWidth;
    }

    public double designHeight() {
        return designHeight;
    }

    @Override
    protected double computePrefWidth(double height) {
        return designWidth;
    }

    @Override
    protected double computePrefHeight(double width) {
        return designHeight;
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        double scale = ScreenFitMath.scaleFor(w, h, designWidth, designHeight);
        // The layout is given the window's own shape (divided by the zoom), never less than the design size,
        // so there is no empty strip at the side or the bottom (the blank left margin seen on a tiled window).
        double cw = Math.max(designWidth, w / scale);
        double ch = Math.max(designHeight, h / scale);
        content.resize(cw, ch);
        content.setScaleX(scale);
        content.setScaleY(scale);
        // Scaling is about the node's centre, so centring the unscaled box centres the scaled picture.
        content.relocate((w - cw) / 2, (h - ch) / 2);
    }

    /**
     * Sizes and centres the window so the screen fits the display, and makes it resizable.
     *
     * @param chromeWidth  stage width minus scene width measured BEFORE the scene was replaced (title bar and borders);
     *                     pass 0 for a window that is not showing yet and call {@link #fitStage(Stage, ScreenFit)} after show()
     */
    public static void fitStage(Stage stage, ScreenFit fit, double chromeWidth, double chromeHeight) {
        if (stage.isMaximized() || stage.isFullScreen()) return;
        Rectangle2D free = Screen.getPrimary().getVisualBounds();
        double sceneW = ScreenFitMath.fitLength(fit.designWidth, free.getWidth(), chromeWidth + MARGIN);
        double sceneH = ScreenFitMath.fitLength(fit.designHeight, free.getHeight(), chromeHeight + MARGIN);
        if (!stage.isResizable()) stage.setResizable(true);
        // The smallest window is the smallest the zoom can still draw completely (TODO 2.54).
        stage.setMinWidth(Math.min(free.getWidth(), fit.designWidth * ScreenFitMath.MIN_SCALE + chromeWidth));
        stage.setMinHeight(Math.min(free.getHeight(), fit.designHeight * ScreenFitMath.MIN_SCALE + chromeHeight));
        stage.setWidth(sceneW + chromeWidth);
        stage.setHeight(sceneH + chromeHeight);
        stage.setX(free.getMinX() + (free.getWidth() - stage.getWidth()) / 2);
        stage.setY(free.getMinY() + (free.getHeight() - stage.getHeight()) / 2);
    }

    /**
     * A scene with an explicit size that fits the display. A scene made with {@code new Scene(root)} takes the
     * root's preferred size (1600x1000) and does not shrink to a smaller window that was sized afterwards.
     */
    public static javafx.scene.Scene newScene(ScreenFit fit, double chromeWidth, double chromeHeight) {
        Rectangle2D free = Screen.getPrimary().getVisualBounds();
        return new javafx.scene.Scene(fit,
                ScreenFitMath.fitLength(fit.designWidth, free.getWidth(), chromeWidth + MARGIN),
                ScreenFitMath.fitLength(fit.designHeight, free.getHeight(), chromeHeight + MARGIN));
    }

    /** For a stage that is already showing (measures its own chrome from the scene it has right now). */
    public static void fitStage(Stage stage, ScreenFit fit) {
        double chromeW = stage.getScene() == null ? 0 : Math.max(0, stage.getWidth() - stage.getScene().getWidth());
        double chromeH = stage.getScene() == null ? 0 : Math.max(0, stage.getHeight() - stage.getScene().getHeight());
        fitStage(stage, fit, chromeW, chromeH);
    }
}
