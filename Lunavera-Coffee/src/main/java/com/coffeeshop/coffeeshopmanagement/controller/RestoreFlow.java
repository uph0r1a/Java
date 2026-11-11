package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.BackupService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.scene.control.Button;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;

/**
 * What happens after the administrator has picked a backup file to restore (SQLite shop): a file locked with a passphrase asks for it
 * first (TODO 2A.4), the file is checked and what it holds is shown before anything is parked (TODO 5.22), and then the restore is
 * scheduled for the next start. Every step that touches the file runs in the background (TODO 6.14); the questions are asked on the
 * screen thread between the steps. Moved out of {@link AdminDashboardController}, which is at its size ceiling.
 */
final class RestoreFlow {

    private RestoreFlow() {
    }

    /** @param refreshBanner redraws the "a restore is waiting" banner once the restore is parked */
    static void start(File file, Button restoreButton, Runnable refreshBanner) {
        Async.run(() -> BackupService.isEncryptedBackup(file.toPath()), encrypted -> {
            final char[] passphrase; // a plain backup passes null
            if (encrypted) {
                Optional<char[]> asked = BackupPassphraseDialog.askToOpen(file.getName());
                if (asked.isEmpty()) return;
                passphrase = asked.get();
            } else {
                passphrase = null;
            }
            inspect(file, passphrase, restoreButton, refreshBanner);
        }, error -> AlertUtil.error(I18n.t("admin.restore.fail"), error.getMessage()),
                restoreButton);
    }

    private static void inspect(File file, char[] passphrase, Button restoreButton, Runnable refreshBanner) {
        Async.run(
                () -> BackupService.inspect(file.toPath(), passphrase),
                summary -> {
                    if (!AlertUtil.confirm(I18n.t("admin.restore.title"),
                            I18n.t("admin.restore.confirm", file.getName(), summary.describe()))) {
                        return;
                    }
                    Async.run(
                            () -> {
                                try {
                                    BackupService.scheduleRestore(file.toPath(), passphrase);
                                    return file;
                                } finally {
                                    if (passphrase != null) Arrays.fill(passphrase, '\0');
                                }
                            },
                            picked -> {
                                refreshBanner.run();
                                if (AlertUtil.confirm(I18n.t("admin.restore.ready.title"),
                                        I18n.t("admin.restore.ready.message"))) {
                                    javafx.application.Platform.exit();
                                } else {
                                    AlertUtil.info(I18n.t("admin.restore.waiting.title"),
                                            I18n.t("admin.restore.waiting.message"));
                                }
                            },
                            error -> AlertUtil.error(I18n.t("admin.restore.fail"), error.getMessage()),
                            restoreButton);
                },
                error -> AlertUtil.error(I18n.t("admin.restore.fail"), error.getMessage()),
                restoreButton // a second click must not start a second look at the file (TODO 2.75)
        );
    }
}
