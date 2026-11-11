package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.ShiftService;
import com.coffeeshop.coffeeshopmanagement.config.ShopSettings;
import com.coffeeshop.coffeeshopmanagement.model.ShiftClose;
import com.coffeeshop.coffeeshopmanagement.service.ShiftSheet;
import com.coffeeshop.coffeeshopmanagement.util.OffScreenThread;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.ExportCsv;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.ShiftSheetPage;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Print or save the sheet of a closed shift (TODO 4.53). Three places use it: the box that opens when a shift has just been closed
 * (print it for the cash bag, or save it), and the report's list of closed shifts (a Reprint and a Save button on every row, and the
 * same two under the list for the selected row; a reprint says "COPY" on the paper). A sheet is saved as a PDF (the page as printed,
 * for the owner's file) or as a CSV (for a spreadsheet), by the name or the filter the person picks. What is on the sheet is decided by {@link ShiftSheet}, how it is drawn and printed by
 * {@link ShiftSheetPage}; this class only reads the figures off the screen thread, asks where to save, and shows what happened.
 */
final class ShiftSheetActions {

    private ShiftSheetActions() {
    }

    /** The box shown right after a shift was closed: the difference, and the offer to print or save the sheet. */
    static void offerAfterClose(ShiftClose close) {
        AlertUtil.infoWithActions(I18n.t("shift.title"),
                I18n.t("shift.closed", CurrencyUtil.format(close.difference())) + "\n" + I18n.t("shiftsheet.closed.hint"),
                List.of(I18n.t("shiftsheet.button.print"), I18n.t("shiftsheet.button.save")),
                List.of(() -> print(close, false), () -> save(close)));
    }

    /**
     * Adds a column with a Reprint and a Save button on every row to the report's list of closed shifts, and returns the two
     * buttons that go under it for the selected row (off while none is selected).
     */
    static HBox listButtons(TableView<ShiftClose> table) {
        table.getColumns().add(rowButtons());
        Button reprint = new Button(I18n.t("shiftsheet.button.reprint"));
        Button save = new Button(I18n.t("shiftsheet.button.save"));
        reprint.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        save.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        reprint.setOnAction(e -> {
            ShiftClose selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) print(selected, true);
        });
        save.setOnAction(e -> {
            ShiftClose selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) save(selected);
        });
        return new HBox(8, reprint, save);
    }

    /** The column of one row's own buttons: Reprint (the paper says COPY) and Save. */
    private static TableColumn<ShiftClose, Void> rowButtons() {
        TableColumn<ShiftClose, Void> column = new TableColumn<>(I18n.t("shiftsheet.col.actions"));
        column.setSortable(false);
        column.setPrefWidth(150);
        column.setCellFactory(c -> new TableCell<>() {
            private final Button reprint = new Button(I18n.t("shiftsheet.row.reprint"));
            private final Button save = new Button(I18n.t("shiftsheet.row.save"));
            private final HBox box = new HBox(4, reprint, save);

            {
                reprint.setOnAction(e -> {
                    ShiftClose row = rowItem();
                    if (row != null) print(row, true);
                });
                save.setOnAction(e -> {
                    ShiftClose row = rowItem();
                    if (row != null) save(row);
                });
            }

            private ShiftClose rowItem() {
                return getTableRow() == null ? null : getTableRow().getItem();
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });
        return column;
    }

    /** Reads the figures off the screen thread, then asks which printer and prints. {@code copy} marks a reprint on the paper. */
    static void print(ShiftClose close, boolean copy) {
        Async.run(() -> sheet(close, copy, AppClock.now()), ShiftSheetPage::print,
                error -> AlertUtil.error(I18n.t("shiftsheet.load.error"), error.getMessage()));
    }

    /**
     * Asks where to save and in which form, then reads the figures off the screen thread and writes the file: a PDF (the page as it is
     * printed) or a CSV (UTF-8 with a BOM so Excel shows the Vietnamese text). The name the person typed decides (.pdf or .csv); a
     * name with neither follows the filter that was selected, and gets its extension.
     */
    static void save(ShiftClose close) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("shiftsheet.save.title"));
        chooser.setInitialFileName(ShiftSheet.fileName(close, "pdf"));
        FileChooser.ExtensionFilter pdfFilter = new FileChooser.ExtensionFilter("PDF", "*.pdf");
        chooser.getExtensionFilters().addAll(pdfFilter, new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File chosen = chooser.showSaveDialog(focusedWindow());
        if (chosen == null) return;
        boolean asPdf = ShiftSheet.savesAsPdf(chosen.getName(), chooser.getSelectedExtensionFilter() == pdfFilter);
        File file = new File(chosen.getParentFile(), ShiftSheet.withExtension(chosen.getName(), asPdf));
        Async.run(() -> sheet(close, false, null), lines -> {
            if (asPdf) writePdf(lines, file); else writeCsv(lines, file);
        }, error -> AlertUtil.error(I18n.t("shiftsheet.load.error"), error.getMessage()));
    }

    private static void writeCsv(List<ShiftSheet.Line> lines, File file) {
        Async.run(() -> {
            ExportCsv.write(file.toPath(), ShiftSheet.csvLines(lines));
            return file;
        }, ShiftSheetActions::saved, error -> AlertUtil.error(I18n.t("common.file.savefail"), error.getMessage()));
    }

    /** The picture is drawn here, on the screen thread (JavaFX needs it); the bytes are written to the file off it. */
    private static void writePdf(List<ShiftSheet.Line> lines, File file) {
        byte[] pdf;
        try {
            pdf = ShiftSheetPage.pdf(lines);
        } catch (RuntimeException e) {
            AlertUtil.error(I18n.t("common.file.savefail"), e.getMessage());
            return;
        }
        Async.run(() -> {
            Files.write(file.toPath(), pdf);
            return file;
        }, ShiftSheetActions::saved, error -> AlertUtil.error(I18n.t("common.file.savefail"), error.getMessage()));
    }

    private static void saved(File file) {
        AlertUtil.info(I18n.t("shiftsheet.save.title"), I18n.t("shiftsheet.save.done", file.getAbsolutePath()));
    }

    /** The lines of the sheet: the stored close and the detail of its window of time, in the language on screen. Reads the database. */
    @OffScreenThread
    private static List<ShiftSheet.Line> sheet(ShiftClose close, boolean copy, LocalDateTime printedAt) {
        ShiftSheet.Extras extras = new ShiftService().sheetExtras(close.periodStart(), close.periodEnd());
        List<String> details = new ArrayList<>(ShopSettings.receiptDetailLines());
        String tax = ShopSettings.cachedTaxCode();
        if (!tax.isEmpty()) details.add(I18n.t("receipt.taxcode", tax)); // in the language on screen, as the rest of the sheet
        return ShiftSheet.build(close, extras, ShopSettings.cachedName(), details, I18n.current(), copy, printedAt);
    }

    private static Window focusedWindow() {
        for (Window window : Window.getWindows()) {
            if (window.isFocused()) return window;
        }
        return null;
    }
}
