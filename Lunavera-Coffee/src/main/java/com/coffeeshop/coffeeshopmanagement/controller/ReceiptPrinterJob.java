package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.PrintRunner;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptPrinting;
import javafx.print.PrinterJob;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.logging.Logger;

/**
 * The printing side of the receipt window, moved out of {@code HoaDonController} (TODO 5.39; the size ceiling in {@code ControllerSizeTest}
 * asks for a split): which printer a job is opened on, and the two helpers that turn a receipt into something a job can print. What decides
 * where a receipt goes (ask, the default printer, one printer by name) is {@link ReceiptPrinting}, which has no JavaFX and its own tests.
 */
final class ReceiptPrinterJob {

    private static final Logger LOGGER = Logger.getLogger(ReceiptPrinterJob.class.getName());

    /** A job that is open on a printer, and whether the print dialog still has to be shown for it. */
    record Opened(PrinterJob job, boolean showDialog) {
    }

    private ReceiptPrinterJob() {
    }

    /**
     * Opens the job the choice asks for: on the default printer or the named one with no dialog (and the copies of the settings), or a
     * plain job whose dialog the caller shows. A printer that is gone, or no default printer, is said in a box and the dialog is used
     * instead. Returns null when the computer has no printer at all (the box has said so).
     */
    static Opened open(ReceiptPrinting.Choice choice, boolean forceDialog) {
        ReceiptPrinting.Target target = decide(choice, forceDialog);
        PrinterJob job = null;
        boolean showDialog = true;
        switch (target.kind()) {
            case DEFAULT_PRINTER, NAMED_PRINTER -> {
                try {
                    javafx.print.Printer printer = target.kind() == ReceiptPrinting.Kind.DEFAULT_PRINTER
                            ? javafx.print.Printer.getDefaultPrinter() : printerNamed(target.printer());
                    job = printer == null ? null : PrinterJob.createPrinterJob(printer);
                } catch (RuntimeException e) {
                    LOGGER.log(java.util.logging.Level.INFO, "The chosen printer could not be opened", e);
                }
                if (job != null) {
                    showDialog = false;
                    job.getJobSettings().setCopies(choice.copies());
                } else {
                    AlertUtil.warning(I18n.t("receipt.print.fail.title"), I18n.t("receipt.print.printer.missing", target.printer()));
                }
            }
            case DIALOG_PRINTER_MISSING -> AlertUtil.warning(I18n.t("receipt.print.fail.title"), target.printer().isEmpty()
                    ? I18n.t("receipt.print.default.missing") : I18n.t("receipt.print.printer.missing", target.printer()));
            case DIALOG -> { }
        }
        if (job == null) job = PrinterJob.createPrinterJob();
        if (job == null) {
            AlertUtil.error(I18n.t("receipt.print.fail.title"), I18n.t("receipt.print.noprinter"));
            return null;
        }
        return new Opened(job, showDialog);
    }

    /** The printers of this computer by name, and the default one's name; empty when the system cannot say. */
    private static ReceiptPrinting.Target decide(ReceiptPrinting.Choice choice, boolean forceDialog) {
        List<String> names = new java.util.ArrayList<>();
        String defaultName = null;
        try {
            for (javafx.print.Printer printer : javafx.print.Printer.getAllPrinters()) names.add(printer.getName());
            javafx.print.Printer standard = javafx.print.Printer.getDefaultPrinter();
            if (standard != null) defaultName = standard.getName();
        } catch (RuntimeException e) {
            LOGGER.log(java.util.logging.Level.INFO, "The printers of this computer could not be listed", e);
        }
        return ReceiptPrinting.decide(choice, names, defaultName, forceDialog);
    }

    private static javafx.print.Printer printerNamed(String name) {
        for (javafx.print.Printer printer : javafx.print.Printer.getAllPrinters()) {
            if (printer.getName().equals(name)) return printer;
        }
        return null;
    }

    static PrintRunner.Job jobOf(PrinterJob job, javafx.scene.Node page) {
        return new PrintRunner.Job() {
            @Override public boolean printPage() { return job.printPage(page); }
            @Override public void endJob() { job.endJob(); }
            @Override public void cancelJob() { job.cancelJob(); }
        };
    }

    /**
     * A node to print that is NOT in a window, which JavaFX lets any thread print. The narrow roll receipt already is one (it is built
     * for the printer); the receipt on the screen is copied as a picture, sharp enough for the page (about 300 dots an inch, never more
     * than 8000 pixels a side), and shown at the size it would print. Returns null when the copy fails.
     */
    static javafx.scene.Node offScreenPage(javafx.scene.Node toPrint, double scale) {
        if (toPrint.getScene() == null) {
            if (scale < 1.0) toPrint.getTransforms().add(new javafx.scene.transform.Scale(scale, scale));
            return toPrint;
        }
        try {
            double width = toPrint.getLayoutBounds().getWidth();
            double height = toPrint.getLayoutBounds().getHeight();
            double sharp = Math.min(300.0 / 72.0, 8000.0 / Math.max(width, height));
            javafx.scene.SnapshotParameters parameters = new javafx.scene.SnapshotParameters();
            parameters.setFill(Color.WHITE);
            parameters.setTransform(new javafx.scene.transform.Scale(sharp, sharp));
            WritableImage image = toPrint.snapshot(parameters, null);
            if (image == null || image.getWidth() <= 0) return null;
            javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(image);
            view.setPreserveRatio(true);
            view.setFitWidth(width * scale);
            return view;
        } catch (RuntimeException e) {
            LOGGER.log(java.util.logging.Level.WARNING, "Could not copy the receipt for printing", e);
            return null;
        }
    }
}
