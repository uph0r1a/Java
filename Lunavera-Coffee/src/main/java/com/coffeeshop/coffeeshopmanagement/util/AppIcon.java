package com.coffeeshop.coffeeshopmanagement.util;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

/** Puts the shop logo on a window's title bar and taskbar entry (TODO 4.17). A missing logo is not an error. */
public final class AppIcon {
    private static Image logo;
    private static boolean tried;

    private AppIcon() {}

    public static synchronized void apply(Stage stage) {
        if (stage == null) return;
        if (!tried) {
            tried = true;
            try (InputStream in = AppIcon.class.getResourceAsStream("/images/LunaveraLogo.png")) {
                if (in != null) logo = new Image(in);
            } catch (Exception | LinkageError e) {
                logo = null;
            }
        }
        if (logo != null && !logo.isError() && stage.getIcons().isEmpty()) stage.getIcons().add(logo);
    }
}
