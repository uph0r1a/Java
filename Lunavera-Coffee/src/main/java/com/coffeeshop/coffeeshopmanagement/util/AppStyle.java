package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.service.LargeTextSheet;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Window;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One font for the whole app (TODO 1.6). Georgia and Segoe UI differ per machine, and the Georgia that ships
 * with Windows has no glyphs for many Vietnamese letters (they were drawn as empty squares), so the app bundles
 * "Be Vietnam Pro" and every window uses it.
 *
 * <p>{@link #install()} (called once from {@code LunaveraApplication.start}) loads the font files and then adds
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
    /** The folder of the generated sheets of the large text (TODO 5.44): one beside each sheet that sets a font size, plus base.css. */
    public static final String LARGE_DIR = "/css/" + LargeTextSheet.DIR_NAME + "/";
    private static final Pattern SHEET_NAME = Pattern.compile("/css/([\\w.-]+\\.css)$");

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
            // A stream, not a URL string: Font.loadFont(String) fails when the path holds a space ("/storage/Lunavera Coffee/...", or a jar
            // under "C:\\Program Files"), because the space is written %20 in the URL.
            Font font = null;
            try (java.io.InputStream in = AppStyle.class.getResourceAsStream(file)) {
                if (in != null) font = Font.loadFont(in, 12);
            } catch (java.io.IOException e) {
                LOGGER.log(Level.FINE, "Could not read the bundled font " + file, e);
            }
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
            FitFix.protectScene(scene); // text is not cut to "..." where a label or button can keep its size (TODO 1.7)
            scene.rootProperty().addListener((o, oldRoot, newRoot) -> {
                FitFix.protectScene(scene); // a screen swapped into the same window
                syncLargeText(scene); // and it brings its own sheets
            });
        }
        syncLargeText(scene);
    }

    /**
     * While the choice is "Lớn" (TODO 5.44): adds {@code large/base.css} and, beside every sheet the window loads (its scene's and its
     * root's, which is where an FXML file's sheets are), that sheet's generated companion. While it is not, takes them all off.
     * Each screen gets the companions of its own sheets only, so a class that two screens size differently stays right on both.
     */
    static void syncLargeText(Scene scene) {
        if (scene == null) return;
        List<String> sceneSheets = scene.getStylesheets();
        List<String> rootSheets = scene.getRoot() == null ? null : scene.getRoot().getStylesheets();
        sceneSheets.removeIf(sheet -> sheet.contains(LARGE_DIR));
        if (rootSheets != null) rootSheets.removeIf(sheet -> sheet.contains(LARGE_DIR));
        if (!TextSize.isLarge()) return;
        URL base = AppStyle.class.getResource(LARGE_DIR + LargeTextSheet.BASE_NAME);
        if (base != null) sceneSheets.add(base.toExternalForm());
        addCompanions(sceneSheets);
        if (rootSheets != null) addCompanions(rootSheets);
    }

    private static void addCompanions(List<String> sheets) {
        for (String sheet : new ArrayList<>(sheets)) {
            Matcher name = SHEET_NAME.matcher(sheet);
            if (!name.find()) continue;
            URL companion = AppStyle.class.getResource(LARGE_DIR + name.group(1));
            if (companion != null && !sheets.contains(companion.toExternalForm())) sheets.add(companion.toExternalForm());
        }
    }

    /** Chooses the text size ({@link TextSize}) and, when it changed, applies it to every open window at once (screen thread). */
    public static void chooseTextSize(boolean large) {
        if (TextSize.choose(large)) refreshAll();
    }

    /** After the text size was chosen: every open window follows at once (screen thread). */
    static void refreshAll() {
        for (Window window : Window.getWindows()) syncLargeText(window.getScene());
    }
}
