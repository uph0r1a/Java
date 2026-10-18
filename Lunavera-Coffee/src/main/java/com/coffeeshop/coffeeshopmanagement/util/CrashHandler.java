package com.coffeeshop.coffeeshopmanagement.util;

import javafx.application.Platform;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Catches an exception nobody handled (a button handler, a timer, a background task) and writes it to the log file,
 * then tells the person in plain Vietnamese. Before this the stack trace went to {@code System.err}, which a packaged
 * Windows app does not show, so a crash left no trace and the screen just did nothing (TODO 2.24).
 *
 * <p>At most one dialog every {@value #MIN_SECONDS_BETWEEN_DIALOGS} seconds, so an error that repeats on every timer
 * tick cannot bury the screen in dialogs; every occurrence is still logged.
 */
public final class CrashHandler {

    private static final Logger LOGGER = Logger.getLogger(CrashHandler.class.getName());
    static final int MIN_SECONDS_BETWEEN_DIALOGS = 10;
    private static volatile long lastDialogMillis = 0;

    private CrashHandler() {
    }

    public static void install() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> report(thread.getName(), error));
    }

    static void report(String threadName, Throwable error) {
        LOGGER.log(Level.SEVERE, "Unhandled error on thread " + threadName, error);
        if (!shouldShowDialog(System.currentTimeMillis())) return;
        try {
            Platform.runLater(() -> AlertUtil.error("Đã xảy ra lỗi",
                    "Có lỗi không mong muốn. Thao tác vừa rồi có thể chưa được thực hiện. Nếu lỗi lặp lại, hãy khởi động "
                            + "lại ứng dụng và gửi tệp trong thư mục data/logs cho người hỗ trợ."));
        } catch (IllegalStateException toolkitNotRunning) {
            // before the toolkit starts there is nothing to show; the log line above is enough
        }
    }

    /** True at most once per interval; remembers the time of the dialog it allowed. */
    static synchronized boolean shouldShowDialog(long nowMillis) {
        if (nowMillis - lastDialogMillis < MIN_SECONDS_BETWEEN_DIALOGS * 1000L) return false;
        lastDialogMillis = nowMillis;
        return true;
    }

    /** For tests. */
    static synchronized void resetThrottle() {
        lastDialogMillis = 0;
    }
}
