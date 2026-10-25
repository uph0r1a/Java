package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.BackupService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.BackupReminder;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * The "Sao lưu dữ liệu" dialogs (passphrase, file chooser, the saved message with an "open folder" button) in one place,
 * so the dashboard button (TODO 5.17) and the reminder at log-out or close (TODO 5.18) run exactly the same steps.
 */
public final class BackupDialogs {

    private BackupDialogs() {}

    /**
     * Runs the whole backup: passphrase question (SQLite only), file chooser, the copy in the background, the message.
     * Call on the UI thread, for an administrator.
     *
     * @param setBusy    told {@code true} while the copy is being made and {@code false} when it is over (may be null)
     * @param afterSaved runs after a backup was saved (may be null)
     * @param whenOver   runs at the very end whatever happened: cancelled, saved or failed (may be null)
     */
    public static void run(Window owner, Consumer<Boolean> setBusy, Runnable afterSaved, Runnable whenOver) {
        // TODO 2A.4: the backup can be locked with a passphrase; both boxes empty means a plain zip as before. Only for
        // SQLite: the in-app restore is what opens a locked file again, and a MariaDB shop restores by hand (docs/DATABASE.md).
        final boolean sqlite = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.isSqlite();
        final char[] passphrase;
        if (sqlite) {
            java.util.Optional<char[]> asked = BackupPassphraseDialog.askForNew();
            if (asked.isEmpty()) {
                finish(whenOver);
                return;
            }
            passphrase = asked.get();
        } else {
            passphrase = new char[0];
        }
        final boolean locked = passphrase.length > 0;
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("admin.backup.title"));
        chooser.setInitialFileName("lunavera-backup-" +
                AppClock.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                + (locked ? BackupService.ENCRYPTED_EXTENSION : ".zip"));
        chooser.getExtensionFilters().add(locked
                ? new FileChooser.ExtensionFilter(I18n.t("admin.backup.filter.locked"), "*" + BackupService.ENCRYPTED_EXTENSION)
                : new FileChooser.ExtensionFilter(I18n.t("admin.backup.filter"), "*.zip"));
        // Start outside the app's data folder: a backup stored next to the live data is not a real backup.
        // Then the folder used last time (TODO 5.17), if it still exists.
        File startFolder = com.coffeeshop.coffeeshopmanagement.util.RememberedFolder.startFor("backup",
                new File(System.getProperty("user.home")));
        if (startFolder != null) {
            chooser.setInitialDirectory(startFolder);
        }
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            java.util.Arrays.fill(passphrase, '\0');
            finish(whenOver);
            return;
        }
        com.coffeeshop.coffeeshopmanagement.util.RememberedFolder.remember("backup", file);
        if (setBusy != null) setBusy.accept(true);
        Async.run(
                () -> {
                    try {
                        return (locked ? BackupService.createEncryptedBackup(file.toPath(), passphrase)
                                : BackupService.createBackup(file.toPath())).toFile();
                    } finally {
                        java.util.Arrays.fill(passphrase, '\0');
                    }
                },
                saved -> {
                    if (setBusy != null) setBusy.accept(false);
                    boolean inside = com.coffeeshop.coffeeshopmanagement.util.DataPaths.isInsideDataDir(saved.toPath());
                    if (!inside) {
                        BackupReminder.recordManual(BackupReminder.now()); // a copy outside the data folder is what the reminder (5.18) wants
                    }
                    if (afterSaved != null) afterSaved.run();
                    String warning = inside ? "\n\n" + I18n.t("admin.backup.note") : "";
                    AlertUtil.infoWithAction(I18n.t("admin.backup.done.title"),
                            I18n.t("admin.backup.done.message", saved.getAbsolutePath(),
                                    com.coffeeshop.coffeeshopmanagement.util.FileSizeText.format(saved.length()), warning,
                                    locked ? I18n.t("admin.backup.note.locked")
                                            : I18n.t("admin.backup.note.plain")
                                            + (sqlite ? I18n.t("admin.backup.note.hint") : ""),
                                    BackupService.KEEP_AUTOMATIC),
                            I18n.t("admin.backup.openfolder"), () -> openFolder(saved.getAbsoluteFile().getParentFile()));
                    finish(whenOver);
                },
                error -> {
                    if (setBusy != null) setBusy.accept(false);
                    AlertUtil.error(I18n.t("admin.backup.fail"), error.getMessage());
                    finish(whenOver);
                }
        );
    }

    /** Opens {@code folder} in the file manager (TODO 5.17); tells the person where it is if the system cannot do it. */
    static void openFolder(File folder) {
        if (folder == null) return;
        javafx.application.HostServices services = com.coffeeshop.coffeeshopmanagement.LunaveraApplication.hostServices();
        try {
            if (services == null) {
                throw new IllegalStateException("no host services");
            }
            services.showDocument(folder.toURI().toString());
        } catch (RuntimeException e) {
            AlertUtil.warning(I18n.t("admin.backup.openfolder.fail"),
                    I18n.t("admin.backup.openfolder.manual", folder.getAbsolutePath()));
        }
    }

    /**
     * The reminder (TODO 5.18): when an administrator is about to log out or close the program and no backup was saved by
     * hand in the last week, asks once (per start of the program) whether to back up now. "Yes" runs the backup and then
     * {@code then}; "No" or nothing to ask runs {@code then} at once. {@code then} is the log-out or the close.
     */
    public static void offerBeforeLeaving(Window owner, Runnable then) {
        if (!Session.isAdmin() || !BackupReminder.shouldAskNow(BackupReminder.now())) {
            then.run();
            return;
        }
        Instant last = BackupReminder.lastManual();
        String message = last == null
                ? I18n.t("backup.remind.never")
                : I18n.t("backup.remind.old", BackupReminder.daysSince(last, BackupReminder.now()));
        if (!AlertUtil.confirm(I18n.t("backup.remind.title"), message)) {
            then.run();
            return;
        }
        run(owner, null, null, then);
    }

    /** The window the button of {@code event} sits in, or null. */
    static Window windowOf(javafx.event.ActionEvent event) {
        return event != null && event.getSource() instanceof javafx.scene.Node node && node.getScene() != null
                ? node.getScene().getWindow() : null;
    }

    private static void finish(Runnable whenOver) {
        if (whenOver != null) whenOver.run();
    }
}
