package com.coffeeshop.coffeeshopmanagement.util;

import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Window;

import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One font for the whole app (TODO 1.6). Georgia and Segoe UI differ per machine, and the Georgia that ships
 * with Windows has no glyphs for many Vietnamese letters (they were drawn as empty squares), so the app bundles
 * "Be Vietnam Pro" and every window uses it.
 *
 * <p>{@link #install()} (called once from {@code HelloApplication.start}) loads the font files and then adds
 * {@code /css/app.css} to the scene of <em>every</em> window that ever opens: screens, code-built windows,
 * dialogs, alerts, context menus and tooltips. That way no screen can forget it and no style sheet needs its own
 * {@code -fx-font-family}.
 */
public final class AppStyle {

    private static final Logger LOGGER = Logger.getLogger(AppStyle.class.getName());

    /** Regular, italic and bold only: bold and italic are looked up by family name, so they must share it. */
    public static final List<String> FONT_FILES = List.of(
            "/fonts/BeVietnamPro-Regular.ttf",
            "/fonts/BeVietnamPro-Italic.ttf",
            "/fonts/BeVietnamPro-Bold.ttf");

    public static final String FONT_FAMILY = "Be Vietnam Pro";
    public static final String STYLESHEET = "/css/app.css";

    private static boolean installed;

    private AppStyle() {
    }

    /** Loads the bundled font and starts adding the shared style sheet to every window. FX thread; safe to call twice. */
    public static void install() {
        if (installed) return;
        installed = true;
        loadFonts();
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (Window window : change.getAddedSubList()) watch(window);
                }
            }
        });
        for (Window window : Window.getWindows()) watch(window);
    }

    /** Registers the bundled font files with JavaFX. A missing file is logged, not fatal: the default font is used. */
    static void loadFonts() {
        for (String file : FONT_FILES) {
            URL url = AppStyle.class.getResource(file);
            Font font = url == null ? null : Font.loadFont(url.toExternalForm(), 12);
            if (font == null) {
                LOGGER.log(Level.WARNING, "Could not load the bundled font " + file + "; the default font will be used");
            }
        }
    }

    private static void watch(Window window) {
        apply(window.getScene());
        window.sceneProperty().addListener((observable, oldScene, newScene) -> apply(newScene));
    }

    static void apply(Scene scene) {
        if (scene == null) return;
        URL sheet = AppStyle.class.getResource(STYLESHEET);
        if (sheet == null) {
            LOGGER.warning("Missing style sheet " + STYLESHEET);
            return;
        }
        String external = sheet.toExternalForm();
        if (!scene.getStylesheets().contains(external)) {
            scene.getStylesheets().add(external);
        }
    }
}
