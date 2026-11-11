package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.MariaDbRestoreService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

/**
 * The "Khôi phục dữ liệu" steps when the shop runs on MariaDB/MySQL (TODO 1.8): pick the .zip, check it on the scratch
 * database and show what it holds, ask once, restore now, then sign out so nobody keeps working on screens that show the old data.
 * A SQLite shop keeps its own flow in {@code AdminDashboardController}. Call on the UI thread, for an administrator.
 */
final class MariaDbRestoreDialogs {

    private MariaDbRestoreDialogs() {
    }

    /** @param busy the button that started this; it stays disabled while the file is checked and while the restore runs */
    static void start(Node busy) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("admin.restore.choose"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(I18n.t("admin.backup.filter"), "*.zip"));
        File start = DataPaths.backupsDir().toFile();
        if (start.isDirectory()) {
            chooser.setInitialDirectory(start);
        }
        File file = chooser.showOpenDialog(busy.getScene().getWindow());
        if (file == null) {
            return;
        }
        // The scratch check first: it shows what the backup holds and refuses a bad file before anything is asked (nothing is changed).
        Async.run(
                () -> MariaDbRestoreService.inspect(file.toPath()),
                summary -> {
                    if (!AlertUtil.confirm(I18n.t("admin.restore.title"),
                            I18n.t("admin.restore.maria.confirm", file.getName(), summary.describe()))) {
                        return;
                    }
                    restore(file, busy);
                },
                error -> AlertUtil.error(I18n.t("admin.restore.fail"), error.getMessage()),
                busy);
    }

    private static void restore(File file, Node busy) {
        Stage stage = busy.getScene() != null && busy.getScene().getWindow() instanceof Stage s ? s : null;
        Async.run(
                () -> MariaDbRestoreService.restoreNow(file.toPath()),
                message -> {
                    AlertUtil.info(I18n.t("admin.restore.maria.done.title"), message);
                    signOut(stage);
                },
                error -> AlertUtil.error(I18n.t("admin.restore.fail"), error.getMessage()),
                busy);
    }

    /** The accounts, roles and the product list this till holds in memory may be older than the restored data: start again from the login screen. */
    private static void signOut(Stage stage) {
        Session.clear();
        if (stage != null) {
            stage.setOnCloseRequest(null);
            SceneNavigator.closeOtherWindows(stage);
            SceneNavigator.switchScene(stage, "/fxml/dangnhap.fxml");
        }
    }
}
