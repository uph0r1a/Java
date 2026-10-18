package com.coffeeshop.coffeeshopmanagement.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Small things the app remembers between runs (window sizes, the page size of the history, "best sellers first"),
 * in {@code ui-settings.properties} inside the data folder (TODO 5.9). It is only a convenience: a missing, unreadable
 * or read-only file just means the defaults are used, and nothing here may ever stop the app.
 */
public final class UiSettings {

    private static final Logger LOGGER = Logger.getLogger(UiSettings.class.getName());
    static final String FILE_NAME = "ui-settings.properties";

    private static Properties values;
    private static Path loadedFrom;

    private UiSettings() {}

    private static synchronized Properties props() {
        Path file = file();
        if (values == null || !file.equals(loadedFrom)) {
            values = new Properties();
            loadedFrom = file;
            if (Files.isRegularFile(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    values.load(in);
                } catch (IOException | IllegalArgumentException e) {
                    LOGGER.log(Level.WARNING, "Could not read " + file, e);
                }
            }
        }
        return values;
    }

    private static Path file() {
        return DataPaths.dataDir().resolve(FILE_NAME);
    }

    public static synchronized String get(String key, String fallback) {
        String v = props().getProperty(key);
        return v == null ? fallback : v;
    }

    public static boolean getBoolean(String key, boolean fallback) {
        String v = get(key, null);
        return v == null ? fallback : Boolean.parseBoolean(v);
    }

    public static double getDouble(String key, double fallback) {
        try {
            String v = get(key, null);
            return v == null ? fallback : Double.parseDouble(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static synchronized void put(String key, String value) {
        Properties p = props();
        if (value.equals(p.getProperty(key))) return;
        p.setProperty(key, value);
        try {
            Path file = file();
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "Lunavera Coffee: remembered screen choices (safe to delete)");
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.FINE, "Could not save " + key, e);
        }
    }

    public static void putBoolean(String key, boolean value) {
        put(key, String.valueOf(value));
    }

    /** A window opens at the size it had when last closed, when that size is sensible for this screen. */
    public static void rememberSize(javafx.stage.Stage stage, String key, double defaultWidth, double defaultHeight) {
        double w = getDouble(key + ".width", defaultWidth);
        double h = getDouble(key + ".height", defaultHeight);
        javafx.geometry.Rectangle2D screen = javafx.stage.Screen.getPrimary().getVisualBounds();
        if (w < 600 || h < 400 || w > screen.getWidth() || h > screen.getHeight()) {
            w = defaultWidth;
            h = defaultHeight;
        }
        stage.setWidth(w);
        stage.setHeight(h);
        stage.setOnHidden(e -> {
            if (!stage.isMaximized() && !stage.isIconified() && stage.getWidth() > 0) {
                put(key + ".width", String.valueOf(Math.round(stage.getWidth())));
                put(key + ".height", String.valueOf(Math.round(stage.getHeight())));
            }
        });
    }
}
