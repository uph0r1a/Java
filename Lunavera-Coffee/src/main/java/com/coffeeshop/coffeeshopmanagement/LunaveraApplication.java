package com.coffeeshop.coffeeshopmanagement;

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

    @Override
    public void init() {
        // File logging first, so a problem during DB setup itself (schema creation, migration,
        // seeding) still ends up in data/logs/ and not just a console nobody sees
        // during normal desktop use.
        try {
            AppLogging.install();
            com.coffeeshop.coffeeshopmanagement.util.CrashHandler.install(); // unhandled errors reach the log (TODO 2.24)
            // A second copy on the same data folder could overwrite the database under the first (TODO 3.3, 3.33).
            if (!com.coffeeshop.coffeeshopmanagement.util.SingleInstance.tryAcquire()) {
                throw new com.coffeeshop.coffeeshopmanagement.util.SingleInstance.AlreadyRunningException(
                        "Lunavera Coffee đang chạy rồi (trên máy này hoặc cùng thư mục dữ liệu).");
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
            // Runs on the JavaFX launcher thread before start(), so the login screen never shows
            // until the schema exists and (on a first run) the default admin account is seeded.
            DatabaseConfig.initialize();
            // Daily automatic backup (and pruning), off the start-up path: a failure is only logged.
            Thread backup = new Thread(BackupService::autoBackupIfDue, "auto-backup");
            backup.setDaemon(true);
            backup.start();
            // MariaDB shops also get an hourly SQLite snapshot (newest 10 kept); a no-op on a SQLite shop.
            SqliteSnapshotService.start();
        } catch (Throwable e) {
            // A locked or corrupt file, a full disk, a failed migration, a missing sqlite library: remember it and
            // tell the person in start(); letting it escape here meant the window just never appeared.
            LOGGER.log(Level.SEVERE, "The application could not start", e);
            startupError = e;
        }
    }

    /** Asks on the FX thread (init() runs on the launcher thread) and waits for the answer; defaults to "no". */
    private boolean askToApplyOldRestore() {
        java.util.concurrent.atomic.AtomicBoolean answer = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        String since = BackupService.pendingSince().map(t -> t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))).orElse("?");
        Platform.runLater(() -> {
            try {
                answer.set(AlertUtil.confirm("Khôi phục dữ liệu đang chờ",
                        "Có một yêu cầu khôi phục được chuẩn bị lúc " + since + " nhưng chưa thực hiện.\n"
                                + "Nếu tiếp tục, dữ liệu hiện tại (kể cả đơn hàng từ lúc đó) sẽ được thay bằng bản sao lưu.\n\n"
                                + "Thực hiện khôi phục bây giờ? (Chọn Không để hủy yêu cầu và giữ dữ liệu hiện tại.)"));
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
        hostServices = getHostServices();
        // Bundled font + shared style sheet for every window (TODO 1.6); before the first screen is built.
        AppStyle.install();
        IdleLogout.install();
        if (startupError instanceof com.coffeeshop.coffeeshopmanagement.util.SingleInstance.AlreadyRunningException) {
            AlertUtil.info("Lunavera Coffee đang chạy",
                    "Chương trình đã được mở rồi. Hãy dùng cửa sổ đang mở (xem trên thanh tác vụ). "
                            + "Mở hai bản cùng lúc trên một dữ liệu có thể làm hỏng dữ liệu.\n\nThư mục dữ liệu: "
                            + com.coffeeshop.coffeeshopmanagement.util.DataPaths.dataDir());
            Platform.exit();
            return;
        }
        if (startupError != null) {
            AlertUtil.errorWithDetails(StartupProblem.title(), StartupProblem.describe(startupError));
            Platform.exit();
            return;
        }
        if (restoreResult.outcome() == BackupService.RestoreOutcome.RESTORED) {
            AlertUtil.info("Đã khôi phục dữ liệu", restoreResult.message());
        } else if (restoreResult.outcome() == BackupService.RestoreOutcome.FAILED) {
            AlertUtil.error("Không khôi phục được", restoreResult.message());
        }
        // No built-in administrator: the owner creates the first one (TODO 2A.5). Exiting closes the program.
        if (com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.needsFirstRunSetup()
                && !com.coffeeshop.coffeeshopmanagement.controller.FirstRunDialog.run()) {
            Platform.exit();
            return;
        }
        if (com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.starterMenuFailure() != null) {
            AlertUtil.warning("Chưa tạo được thực đơn mẫu",
                    "Chương trình không tạo được thực đơn mẫu nên menu đang trống. Bạn vẫn có thể tự thêm sản phẩm. "
                            + "Lần mở sau chương trình sẽ thử lại.\n\nLý do: "
                            + com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.starterMenuFailure());
        }
        try {
            String clockProblem = com.coffeeshop.coffeeshopmanagement.service.ClockCheck.problem(
                    java.time.LocalDateTime.now(), new com.coffeeshop.coffeeshopmanagement.dao.OrderDAO().newestActivity());
            if (clockProblem != null) AlertUtil.warning("Ngày giờ của máy có thể bị sai", clockProblem);
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Clock check failed", e);
        }
        // The "daily" backup also runs while the app stays open for days (TODO 2.62): check once an hour.
        javafx.animation.Timeline hourly = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.hours(1), e -> {
                    Thread t = new Thread(BackupService::autoBackupIfDue, "auto-backup");
                    t.setDaemon(true);
                    t.start();
                }));
        hourly.setCycleCount(javafx.animation.Animation.INDEFINITE);
        hourly.play();
        SceneNavigator.setScreenDecorator(SidebarExtras::addChangePasswordButton);
        FXMLLoader loader = new FXMLLoader(
                LunaveraApplication.class.getResource("/fxml/dangnhap.fxml")
        );

        ScreenFit fit = ScreenFit.wrap(loader.load());

        stage.setTitle("Lunavera Coffee");
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