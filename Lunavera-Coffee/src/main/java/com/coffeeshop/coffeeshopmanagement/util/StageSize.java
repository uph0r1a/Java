package com.coffeeshop.coffeeshopmanagement.util;

/** The window-size memory of the report, history and audit windows. Kept out of {@link UiSettings}, which has no JavaFX in it (TODO 6.83). */
public final class StageSize {

    private StageSize() {
    }

    /** A window opens at the size it had when last closed, when that size is sensible for this screen. */
    public static void remember(javafx.stage.Stage stage, String key, double defaultWidth, double defaultHeight) {
        double w = UiSettings.getDouble(key + ".width", defaultWidth);
        double h = UiSettings.getDouble(key + ".height", defaultHeight);
        javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        if (w < 600 || h < 400 || w > screen.getWidth() || h > screen.getHeight()) {
            w = defaultWidth;
            h = defaultHeight;
        }
        stage.setWidth(w);
        stage.setHeight(h);
        stage.setOnHidden(e -> {
            if (!stage.isMaximized() && !stage.isIconified() && stage.getWidth() > 0) {
                UiSettings.put(key + ".width", String.valueOf(Math.round(stage.getWidth())));
                UiSettings.put(key + ".height", String.valueOf(Math.round(stage.getHeight())));
            }
        });
    }
}
