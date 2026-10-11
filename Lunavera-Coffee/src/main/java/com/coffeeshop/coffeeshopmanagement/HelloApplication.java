package com.coffeeshop.coffeeshopmanagement;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.util.AppLogging;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppStyle;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.ScreenFit;
import com.coffeeshop.coffeeshopmanagement.util.StartupProblem;
import javafx.application.Platform;
import org.example.controller.SidebarExtras;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApplication extends Application {

    private static final Logger LOGGER = Logger.getLogger(HelloApplication.class.getName());

    /** Set when the database could not be opened in init(); start() then explains it instead of showing nothing (TODO 2.5). */
    private Throwable startupError;

    @Override
    public void init() {
        // File logging first, so a problem during DB setup itself (schema creation, migration,
        // seeding) still ends up in data/logs/ and not just a console nobody sees
        // during normal desktop use.
        try {
            AppLogging.install();
            // Runs on the JavaFX launcher thread before start(), so the login screen never shows
            // until the schema exists and (on a first run) the default admin account is seeded.
            DatabaseConfig.initialize();
        } catch (Throwable e) {
            // A locked or corrupt file, a full disk, a failed migration, a missing sqlite library: remember it and
            // tell the person in start(); letting it escape here meant the window just never appeared.
            LOGGER.log(Level.SEVERE, "The application could not start", e);
            startupError = e;
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        // Bundled font + shared style sheet for every window (TODO 1.6); before the first screen is built.
        AppStyle.install();
        if (startupError != null) {
            AlertUtil.error(StartupProblem.title(), StartupProblem.describe(startupError));
            Platform.exit();
            return;
        }
        SceneNavigator.setScreenDecorator(SidebarExtras::addChangePasswordButton);
        FXMLLoader loader = new FXMLLoader(
                HelloApplication.class.getResource("/fxml/dangnhap.fxml")
        );

        ScreenFit fit = ScreenFit.wrap(loader.load());

        stage.setTitle("Lunavera Coffee");
        stage.setScene(ScreenFit.newScene(fit, 0, 0));
        stage.show();
        // Fit the window to the display (TODO 2.4); the title-bar size is only known once it is showing.
        ScreenFit.fitStage(stage, fit);
    }

    public static void main(String[] args) {
        launch();
    }
}