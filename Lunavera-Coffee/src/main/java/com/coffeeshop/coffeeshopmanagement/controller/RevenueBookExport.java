package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.ReportService;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.BusyIndicator;
import com.coffeeshop.coffeeshopmanagement.util.ExportCsv;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.RevenueBookText;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/** "Xuất sổ bán hàng" (TODO 4.58): the revenue book of the report's days as a CSV file for the accountant. The text is {@link RevenueBookText}; the lines come from {@code ReportDAO.revenueBook}. */
final class RevenueBookExport {

    private RevenueBookExport() {
    }

    /** The button for the report window; it asks for a file, then reads and writes off the screen thread. {@code from} and {@code to} give the days on screen (null before the first load). */
    static Button button(Supplier<LocalDate> from, Supplier<LocalDate> to) {
        Button button = new Button(I18n.t("book.button"));
        button.setOnAction(e -> {
            LocalDate first = from.get();
            LocalDate last = to.get();
            if (first == null || last == null) return;
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.t("book.button"));
            chooser.setInitialFileName("so-ban-hang-" + first + "_" + last + ".csv");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            File file = chooser.showSaveDialog(button.getScene().getWindow());
            if (file == null) return;
            Runnable release = BusyIndicator.hold(button, I18n.t("busy.export"));
            Async.run(() -> {
                List<String> lines = RevenueBookText.lines(new ReportService().revenueBook(first, last), first, last,
                        com.coffeeshop.coffeeshopmanagement.config.ShopSettings.cachedName());
                ExportCsv.write(file.toPath(), lines); // UTF-8 with a BOM, so Excel shows the Vietnamese text
                return file;
            }, saved -> {
                release.run();
                AlertUtil.info(I18n.t("book.button"), I18n.t("report.export.done", saved.getAbsolutePath()));
            }, error -> {
                release.run();
                AlertUtil.error(I18n.t("common.file.savefail"), error.getMessage());
            });
        });
        return button;
    }
}
