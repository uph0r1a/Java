package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.OrderHistoryService;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO.HistoryFilter;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.BusyIndicator;
import com.coffeeshop.coffeeshopmanagement.util.ExportCsv;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

/** The file part of "Xuất CSV" in the order history (moved out of {@code OrderHistoryWindow}, TODO 6.54; unchanged): ask for the file, write the rows. */
final class OrderHistoryExport {

    private OrderHistoryExport() {
    }

    /** Asks where to save, then writes every order matching {@code filter} (not just the page shown), in the given sort order. */
    static void run(Stage stage, Button exportButton, OrderHistoryService history, HistoryFilter filter, OrderDAO.HistorySort order) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("history.export.title"));
        chooser.setInitialFileName("don-hang-" + com.coffeeshop.coffeeshopmanagement.util.AppClock.today() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        Runnable release = BusyIndicator.hold(exportButton, I18n.t("busy.export"));
        Async.run(
                () -> {
                    // Written while the rows are read: no list of every order, no row limit (TODO 3.54).
                    int written = ExportCsv.writeOrders(file.toPath(), sink -> history.exportOrders(filter, order, sink));
                    SecurityLog.event("exported " + written + " orders to CSV");
                    return written;
                },
                count -> {
                    release.run();
                    AlertUtil.info(I18n.t("history.export.title"), I18n.t("history.export.done", count, file.getAbsolutePath()));
                },
                error -> {
                    release.run();
                    AlertUtil.error(I18n.t("common.file.savefail"), error.getMessage());
                });
    }
}
