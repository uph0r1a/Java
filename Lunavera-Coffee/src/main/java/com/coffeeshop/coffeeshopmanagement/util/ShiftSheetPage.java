package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.service.ShiftSheet;

import javafx.geometry.Insets;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The shift-close sheet (TODO 4.53) drawn for paper and sent to a printer. {@link ShiftSheet} decides WHAT is on the sheet; this
 * class only draws those lines black on white, one column, with the same fonts and helpers as the narrow receipt
 * ({@link ThermalReceipt}), and prints them the way the receipt is printed: the print dialog first, the page copied as a picture
 * so it can be printed off the screen thread, the job handed to {@link PrintRunner} so a failing driver or a cancelled job never
 * leaves a stuck entry in the printer's queue (TODO 2.14, 2.83).
 *
 * <p>On a paper roll (58 or 80 mm) the sheet fills the printable width; on a sheet of ordinary paper it is drawn in a column
 * {@link #WIDE_WIDTH} points wide, about the width of a receipt, so it reads the same and fits the cash bag when cut out.
 */
public final class ShiftSheetPage {

    private static final Logger LOGGER = Logger.getLogger(ShiftSheetPage.class.getName());

    /** The width of the sheet on ordinary paper, in points (1/72 inch): about 11 cm. */
    static final double WIDE_WIDTH = 300;

    private ShiftSheetPage() {
    }

    /** The sheet as a node {@code width} points wide, ready to be laid out and printed (it is never shown in a window). */
    public static VBox build(List<ShiftSheet.Line> lines, double width) {
        double size = ThermalReceipt.fontSizeFor(width);
        VBox root = new VBox(2);
        root.setPrefWidth(width);
        root.setMinWidth(width);
        root.setMaxWidth(width);
        root.setPadding(new Insets(2, 0, 8, 0));
        root.setStyle("-fx-background-color: white;");
        for (ShiftSheet.Line line : lines) {
            switch (line.kind()) {
                case CENTER -> root.getChildren().add(ThermalReceipt.centered(line.left(), line.bold() ? size + 1 : size, line.bold(), width));
                case LEFT -> root.getChildren().add(ThermalReceipt.line(line.left(), size, line.bold(), width));
                case PAIR -> root.getChildren().add(ThermalReceipt.pair(line.left(), line.right(), size, line.bold(), width));
                case RULE -> root.getChildren().add(ThermalReceipt.rule(width));
                case GAP -> root.getChildren().add(spacer(size * 1.2));
                case SIGNATURE -> {
                    root.getChildren().add(spacer(size * 2.4)); // room to sign above the line
                    root.getChildren().add(ThermalReceipt.rule(width));
                    root.getChildren().add(ThermalReceipt.line(line.left(), size - 1, false, width));
                }
            }
        }
        return root;
    }

    private static Region spacer(double height) {
        Region gap = new Region();
        gap.setMinHeight(height);
        gap.setPrefHeight(height);
        gap.setMaxHeight(height);
        return gap;
    }

    /**
     * Asks which printer, then prints the sheet. Must be called on the screen thread. Nothing is printed when there is no printer
     * (said on screen) or when the person closes the print dialog; the outcome of a real print is shown when it is known.
     */
    public static void print(List<ShiftSheet.Line> lines) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            AlertUtil.error(I18n.t("receipt.print.fail.title"), I18n.t("receipt.print.noprinter"));
            return;
        }
        if (!job.showPrintDialog(AlertUtil.activeWindow())) {
            job.cancelJob(); // the person closed the print dialog: do not leave the job open (TODO 2.14)
            return;
        }
        double printable = job.getJobSettings().getPageLayout().getPrintableWidth();
        double width;
        if (ThermalReceipt.isNarrow(printable)) {
            try { // a roll: the smallest margins, so the sheet has all the width the printer can print
                PageLayout wide = job.getPrinter().createPageLayout(job.getJobSettings().getPageLayout().getPaper(),
                        PageOrientation.PORTRAIT, Printer.MarginType.HARDWARE_MINIMUM);
                job.getJobSettings().setPageLayout(wide);
                printable = wide.getPrintableWidth();
            } catch (RuntimeException e) {
                LOGGER.log(Level.INFO, "Could not use the smallest margins for the roll", e);
            }
            width = printable;
        } else {
            width = Math.min(printable, WIDE_WIDTH);
        }
        VBox sheet = build(lines, width);
        ThermalReceipt.layOut(sheet);
        PageLayout layout = job.getJobSettings().getPageLayout();
        double scale = Math.min(1.0, Math.min(layout.getPrintableWidth() / sheet.getLayoutBounds().getWidth(),
                layout.getPrintableHeight() / sheet.getLayoutBounds().getHeight()));
        Node page = copyForPrinting(sheet, scale);
        if (page == null) {
            // The copy failed: print the node itself, here on the screen thread, as the receipt does in that case.
            Scale transform = new Scale(scale, scale);
            if (scale < 1.0) sheet.getTransforms().add(transform);
            try {
                showOutcome(PrintRunner.run(jobOf(job, sheet)));
            } finally {
                sheet.getTransforms().remove(transform);
            }
            return;
        }
        // Off the screen thread, so a slow or switched-off network printer does not freeze the till (TODO 2.83).
        Async.run(() -> PrintRunner.run(jobOf(job, page)), ShiftSheetPage::showOutcome, error -> {
            LOGGER.log(Level.WARNING, "Printing the shift sheet failed", error);
            showOutcome(new PrintRunner.Outcome(false, ErrorText.forScreen(error.getMessage())));
        });
    }

    /**
     * The sheet as the bytes of a PDF file (TODO 4.53): drawn the way it is on ordinary paper, copied as a sharp picture (about 300
     * dots an inch) and set in the middle of an A4 page by {@link SimplePdf}. Must be called on the screen thread, which the
     * snapshot needs; writing the bytes to a file is then done off it.
     */
    public static byte[] pdf(List<ShiftSheet.Line> lines) {
        VBox sheet = build(lines, WIDE_WIDTH);
        ThermalReceipt.layOut(sheet);
        double width = sheet.getLayoutBounds().getWidth();
        double height = sheet.getLayoutBounds().getHeight();
        double sharp = Math.min(300.0 / 72.0, 8000.0 / Math.max(width, height));
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.WHITE);
        parameters.setTransform(new Scale(sharp, sharp));
        WritableImage image = sheet.snapshot(parameters, null);
        if (image == null || image.getWidth() <= 0) throw new IllegalStateException("The sheet could not be drawn");
        int pixelWidth = (int) image.getWidth();
        int pixelHeight = (int) image.getHeight();
        byte[] bgra = new byte[pixelWidth * pixelHeight * 4];
        image.getPixelReader().getPixels(0, 0, pixelWidth, pixelHeight, PixelFormat.getByteBgraInstance(), bgra, 0, pixelWidth * 4);
        return SimplePdf.imagePage(pixelWidth, pixelHeight, SimplePdf.rgbFromBgra(bgra, pixelWidth, pixelHeight), width, height);
    }

    private static PrintRunner.Job jobOf(PrinterJob job, Node page) {
        return new PrintRunner.Job() {
            @Override public boolean printPage() { return job.printPage(page); }
            @Override public void endJob() { job.endJob(); }
            @Override public void cancelJob() { job.cancelJob(); }
        };
    }

    private static void showOutcome(PrintRunner.Outcome outcome) {
        if (outcome.printed()) {
            AlertUtil.info(I18n.t("shiftsheet.print.title"), I18n.t("shiftsheet.print.sent"));
        } else {
            AlertUtil.error(I18n.t("receipt.print.fail.title"), outcome.problem() == null
                    ? I18n.t("shiftsheet.print.fail") : I18n.t("shiftsheet.print.fail.driver", outcome.problem()));
        }
    }

    /**
     * The sheet copied as a picture that is not in any window, which JavaFX lets any thread print; sharp enough for the page (about
     * 300 dots an inch, never more than 8000 pixels a side) and sized as it will print. Null when the copy fails.
     */
    private static Node copyForPrinting(VBox sheet, double scale) {
        try {
            double width = sheet.getLayoutBounds().getWidth();
            double height = sheet.getLayoutBounds().getHeight();
            double sharp = Math.min(300.0 / 72.0, 8000.0 / Math.max(width, height));
            SnapshotParameters parameters = new SnapshotParameters();
            parameters.setFill(Color.WHITE);
            parameters.setTransform(new Scale(sharp, sharp));
            WritableImage image = sheet.snapshot(parameters, null);
            if (image == null || image.getWidth() <= 0) return null;
            ImageView view = new ImageView(image);
            view.setPreserveRatio(true);
            view.setFitWidth(width * scale);
            return view;
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not copy the shift sheet for printing", e);
            return null;
        }
    }
}
