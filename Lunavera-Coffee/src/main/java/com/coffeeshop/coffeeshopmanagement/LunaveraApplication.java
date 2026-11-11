package com.coffeeshop.coffeeshopmanagement;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.SqliteSnapshotService;
import com.coffeeshop.coffeeshopmanagement.util.AppLogging;
import com.coffeeshop.coffeeshopmanagement.service.BackupService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppStyle;
import com.coffeeshop.coffeeshopmanagement.util.IdleLogout;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.ScreenFit;
import com.coffeeshop.coffeeshopmanagement.util.StartupProblem;
import javafx.application.Platform;
import com.coffeeshop.coffeeshopmanagement.controller.SidebarExtras;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class LunaveraApplication extends Application {

    private static final Logger LOGGER = Logger.getLogger(LunaveraApplication.class.getName());

    /** Set when the database could not be opened in init(); start() then explains it instead of showing nothing (TODO 2.5). */
    private Throwable startupError;

    /** Outcome of a restore that was prepared in the previous run (TODO 2.8); start() tells the person. */
    private BackupService.RestoreResult restoreResult = new BackupService.RestoreResult(BackupService.RestoreOutcome.NOTHING_PENDING, "");

    /** The folder of a shop this computer used before, when the folder now in use is empty (TODO 3.69); asked about in start(). */
    private java.nio.file.Path shopElsewhere;

    @Override
    public void init() {
        // Before anything is written to the data folder: is the program about to make a second, empty shop? (TODO 3.69)
        shopElsewhere = com.coffeeshop.coffeeshopmanagement.util.DataPaths.existingShopElsewhere();
        // File logging first, so a problem during DB setup itself (schema creation, migration,
        // seeding) still ends up in data/logs/ and not just a console nobody sees
        // during normal desktop use.
        try {
            com.coffeeshop.coffeeshopmanagement.util.ShopZone.install(); // before anything is dated (TODO 3.68)
            AppLogging.install();
            com.coffeeshop.coffeeshopmanagement.util.CrashHandler.install(); // unhandled errors reach the log (TODO 2.24)
            // A second copy on the same data folder could overwrite the database under the first (TODO 3.3, 3.33).
            if (!com.coffeeshop.coffeeshopmanagement.util.SingleInstance.tryAcquire()) {
                throw new com.coffeeshop.coffeeshopmanagement.util.SingleInstance.AlreadyRunningException(
                        I18n.t("app.running.exception"));
            }
            com.coffeeshop.coffeeshopmanagement.util.DataPaths.cleanTmp(); // copies a crashed backup left behind (TODO 2A.21)
            // Customer phones, password hashes and backups live here: close the folder to other users (TODO 2A.4, 2A.14).
            com.coffeeshop.coffeeshopmanagement.util.DataPaths.restrictToOwner(
                    com.coffeeshop.coffeeshopmanagement.util.DataPaths.dataDir());
            // A restore parked more than an hour ago is probably forgotten: ask once before it replaces today's sales.
            if (BackupService.pendingIsOlderThan(java.time.Duration.ofHours(1)) && !askToApplyOldRestore()) {
                BackupService.cancelPendingRestore();
            }
            // A restore chosen last time must be applied before the database is opened.
            restoreResult = BackupService.applyPendingRestore();
            openDatabase();
        } catch (Throwable e) {
            // A locked or corrupt file, a full disk, a failed migration, a missing sqlite library: remember it and
            // tell the person in start(); letting it escape here meant the window just never appeared.
            LOGGER.log(Level.SEVERE, "The application could not start", e);
            startupError = e;
        }
    }

    /**
     * Opens the database and does what must follow at once. Runs on the JavaFX launcher thread before start(), so the login screen
     * never shows until the schema exists; on a first run with no accounts the first-run window then asks the owner to create the
     * administrator. Also runs again from start() when the person chose the local file after "no database server" (TODO 5.40).
     */
    private void openDatabase() {
        DatabaseConfig.initialize();
        // Remember this folder, so a shortcut that starts the program somewhere else finds the same shop (TODO 3.69).
        if (shopElsewhere == null) com.coffeeshop.coffeeshopmanagement.util.DataPaths.rememberCurrent();
        // Daily automatic backup (and pruning), off the start-up path: a failure is only logged.
        BackupService.autoBackupInBackground();
        // MariaDB shops also get an hourly SQLite snapshot (newest 10 kept); a no-op on a SQLite shop.
        SqliteSnapshotService.start();
    }

    /**
     * First start with no database server and no settings file (TODO 5.40): shows the start-up problem with a button "Use the local
     * file on this computer". Returns false when the person closed the box (the program then ends; the box already told them what
     * was wrong). When they pressed the button it writes {@code data/database.properties} with {@code db.type=sqlite}, forgets what
     * was resolved from the old settings and opens the database again: {@code startupError} is then null, or the new error when
     * that failed too (the ordinary start-up box shows it).
     */
    private boolean offerLocalFile() {
        String text = StartupProblem.describe(startupError) + "\n\n" + StartupProblem.localFileOffer();
        if (!AlertUtil.errorWithChoice(StartupProblem.title(), text, I18n.t("startup.local.button"))) {
            return false;
        }
        try {
            java.nio.file.Path written = com.coffeeshop.coffeeshopmanagement.config.LocalFileChoice.write();
            LOGGER.info("No database server was found; the local file was chosen and " + written + " written");
            DatabaseConfig.reset(); // the settings and the address resolved from the missing file are forgotten
            openDatabase();
            startupError = null;
        } catch (java.io.IOException e) {
            LOGGER.log(Level.SEVERE, "The settings file for the local file could not be written", e);
            startupError = new IllegalStateException(I18n.t("startup.local.writefail", e.getMessage()), e);
        } catch (Throwable e) {
            LOGGER.log(Level.SEVERE, "The local file could not be opened", e);
            startupError = e;
        }
        return true;
    }

    /** Asks on the FX thread (init() runs on the launcher thread) and waits for the answer; defaults to "no". */
    private boolean askToApplyOldRestore() {
        java.util.concurrent.atomic.AtomicBoolean answer = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        String since = BackupService.pendingSince().map(t -> t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))).orElse("?");
        Platform.runLater(() -> {
            try {
                answer.set(AlertUtil.confirm(I18n.t("app.restore.pending.title"),
                        I18n.t("app.restore.pending.message", since)));
            } finally {
                done.countDown();
            }
        });
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return answer.get();
    }

    @Override
    public void start(Stage stage) throws Exception {
        // The language the shop picked last time (TODO 1.7): first of all, so even the start-up problem and
        // "already running" dialogs speak it.
        com.coffeeshop.coffeeshopmanagement.util.LanguageSetting.restore();
        hostServices = getHostServices();
        // Bundled font + shared style sheet for every window (TODO 1.6); before the first screen is built.
        AppStyle.install();
        IdleLogout.install();
        if (startupError instanceof com.coffeeshop.coffeeshopmanagement.util.SingleInstance.AlreadyRunningException) {
            AlertUtil.info(I18n.t("app.running.title"),
                    I18n.t("app.running.message", com.coffeeshop.coffeeshopmanagement.util.DataPaths.dataDir()));
            Platform.exit();
            return;
        }
        // No settings file and no server: the box also offers the local file instead of only an error (TODO 5.40).
        if (startupError != null && com.coffeeshop.coffeeshopmanagement.config.LocalFileChoice.applies() && !offerLocalFile()) {
            Platform.exit();
            return;
        }
        if (startupError != null) {
            AlertUtil.errorWithDetails(StartupProblem.title(), StartupProblem.describe(startupError));
            Platform.exit();
            return;
        }
        if (restoreResult.outcome() == BackupService.RestoreOutcome.RESTORED) {
            // TODO 4.2: written into the restored database, so it is the first line after the restore; nobody is signed in yet
            com.coffeeshop.coffeeshopmanagement.dao.AuditDAO.recordQuietly(com.coffeeshop.coffeeshopmanagement.model.AuditAction.RESTORE_APPLIED,
                    com.coffeeshop.coffeeshopmanagement.model.AuditTarget.BACKUP, null, null, null, null, null);
            AlertUtil.info(I18n.t("app.restore.done.title"), restoreResult.message());
        } else if (restoreResult.outcome() == BackupService.RestoreOutcome.FAILED) {
            AlertUtil.error(I18n.t("app.restore.failed.title"), restoreResult.message());
        }
        // The program was pointed at an empty folder while a shop exists elsewhere on this computer: ask before a second shop is made.
        if (shopElsewhere != null && com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.needsFirstRunSetup()) {
            if (!AlertUtil.confirm(I18n.t("app.othershop.title"), I18n.t("app.othershop.message", shopElsewhere,
                    com.coffeeshop.coffeeshopmanagement.util.DataPaths.dataDir()))) {
                Platform.exit();
                return;
            }
            com.coffeeshop.coffeeshopmanagement.util.DataPaths.rememberCurrent(); // the new shop is the one to find next time
        }
        // No built-in administrator: the owner creates the first one (TODO 2A.5). Exiting closes the program.
        if (com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.needsFirstRunSetup()
                && !com.coffeeshop.coffeeshopmanagement.controller.FirstRunDialog.run()) {
            Platform.exit();
            return;
        }
        if (com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.starterMenuFailure() != null) {
            AlertUtil.warning(I18n.t("app.sample.fail.title"),
                    I18n.t("app.sample.fail.message", com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.starterMenuFailure()));
        }
        try {
            String clockProblem = com.coffeeshop.coffeeshopmanagement.service.ClockCheck.problem(
                    com.coffeeshop.coffeeshopmanagement.util.AppClock.now(), new com.coffeeshop.coffeeshopmanagement.dao.OrderDAO().newestActivity());
            if (clockProblem != null) AlertUtil.warning(I18n.t("app.clock.title"), clockProblem);
            String zoneProblem = com.coffeeshop.coffeeshopmanagement.util.ShopZone.mismatchWarning(
                    java.time.ZoneId.systemDefault(), com.coffeeshop.coffeeshopmanagement.util.AppClock.zone(), java.time.Instant.now());
            if (zoneProblem != null) AlertUtil.warning(I18n.t("app.zone.title"), zoneProblem);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Clock check failed", e);
        }
        // A data folder inside OneDrive, Dropbox or Google Drive gets files locked or renamed by the sync program (TODO 3.44).
        String cloudProblem = com.coffeeshop.coffeeshopmanagement.util.CloudFolderCheck.problem(
                com.coffeeshop.coffeeshopmanagement.util.DataPaths.dataDir());
        if (cloudProblem != null) AlertUtil.warning(I18n.t("app.cloud.title"), cloudProblem);
        // The "daily" backup also runs while the app stays open for days (TODO 2.62): check once an hour.
        javafx.animation.Timeline hourly = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.hours(1), e -> BackupService.autoBackupInBackground()));
        hourly.setCycleCount(javafx.animation.Animation.INDEFINITE);
        hourly.play();
        SceneNavigator.setScreenDecorator(SidebarExtras::addButtons);
        com.coffeeshop.coffeeshopmanagement.controller.LockScreen.install(); // TODO 4.36
        FXMLLoader loader = SceneNavigator.newLoader("/fxml/dangnhap.fxml");

        ScreenFit fit = ScreenFit.wrap(loader.load());
        SceneNavigator.rememberFirstScreen(stage, "/fxml/dangnhap.fxml");

        // An administrator closing the program with no hand-made backup for a week is asked once (TODO 5.18). A filter, not
        // setOnCloseRequest: the screens set and clear that handler themselves (unsaved order, session end).
        stage.addEventFilter(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            if (!com.coffeeshop.coffeeshopmanagement.util.Session.isAdmin()
                    || !com.coffeeshop.coffeeshopmanagement.util.BackupReminder.wouldAsk(com.coffeeshop.coffeeshopmanagement.util.BackupReminder.now())) {
                return;
            }
            event.consume();
            com.coffeeshop.coffeeshopmanagement.controller.BackupDialogs.offerBeforeLeaving(stage,
                    () -> stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST)));
        });
        com.coffeeshop.coffeeshopmanagement.config.ShopSettings.load(); // the owner's shop name (TODO 4.15)
        stage.setTitle(com.coffeeshop.coffeeshopmanagement.config.ShopSettings.cachedName());
        com.coffeeshop.coffeeshopmanagement.util.AppIcon.apply(stage);
        stage.setScene(ScreenFit.newScene(fit, 0, 0));
        stage.show();
        // Fit the window to the display (TODO 2.4); the title-bar size is only known once it is showing.
        ScreenFit.fitStage(stage, fit);
    }

    @Override
    public void stop() {
        // The last autosave must not die with its daemon thread (TODO 3.24); then give the data folder back.
        com.coffeeshop.coffeeshopmanagement.util.Async.flushOrdered(3000);
        // A till that was open for days makes its backup now, with that last autosave in it (TODO 2.62); waits for it for 20 seconds at most.
        BackupService.autoBackupBeforeExit(20_000);
        com.coffeeshop.coffeeshopmanagement.util.SingleInstance.release();
    }

    private static volatile HostServices hostServices;

    /** Lets a screen open a web page in the default browser (the "Mở phpMyAdmin" button); null before the app has started. */
    public static HostServices hostServices() {
        return hostServices;
    }

    public static void main(String[] args) {
        launch();
    }
}