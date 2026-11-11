package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 4.53 (with 2.14 and 2.83): the shift-close sheet is printed the way the receipt is. The page goes to the printer through
 * {@code PrintRunner} (a failing driver or a cancelled job never leaves an entry in the printer's queue) and, as a picture that is not
 * in any window, through {@code Async.run} (a network printer that is slow or off does not freeze the till); the figures are read
 * from the database, and the file is written, off the screen thread too. Reads the sources as text, like
 * {@code PrintingOffTheScreenThreadTest}: no JavaFX, no printer, no database.
 */
public class ShiftSheetPrintingSourceTest {

    private static final Path ROOT = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement");

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
    }

    /** The text of one method, from its signature to the next method at the same indent. */
    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("method not found: " + signature, start >= 0);
        int end = source.indexOf("\n    }\n", start);
        assertTrue("end of method not found: " + signature, end > start);
        return source.substring(start, end);
    }

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    // ---- the page: PrintRunner and Async

    @Test
    public void thePageIsHandedToPrintRunnerAndNothingElseCallsPrintPage() throws IOException {
        String page = read("util/ShiftSheetPage.java");
        assertEquals("printPage is called in one place only: the job wrapper that PrintRunner runs", 1, count(page, ".printPage("));
        assertTrue(method(page, "private static PrintRunner.Job jobOf(").contains("job.printPage(page)"));
        assertTrue("the copy that failed is printed through PrintRunner too", page.contains("PrintRunner.run(jobOf(job, sheet))"));
        assertTrue(page.contains("PrintRunner.run(jobOf(job, page))"));
    }

    @Test
    public void thePictureOfTheSheetIsPrintedOnABackgroundThread() throws IOException {
        String page = read("util/ShiftSheetPage.java");
        int async = page.indexOf("Async.run(() -> PrintRunner.run(jobOf(job, page))");
        assertTrue("the print of the picture goes to Async.run", async > 0);
        assertTrue("the print dialog stays on the screen thread, before it", page.indexOf("showPrintDialog") < async);
        assertTrue("the page is copied as a picture before it leaves the screen thread", page.indexOf("copyForPrinting(sheet, scale)") < async);
        assertTrue("a failing job is shown, not lost", method(page, "public static void print(").contains("showOutcome("));
    }

    @Test
    public void aClosedPrintDialogCancelsTheJob() throws IOException {
        String print = method(read("util/ShiftSheetPage.java"), "public static void print(");
        int dialog = print.indexOf("if (!job.showPrintDialog(");
        assertTrue(dialog >= 0);
        int cancel = print.indexOf("job.cancelJob()", dialog);
        int leave = print.indexOf("return;", dialog);
        assertTrue("the job is cancelled before print() returns when the dialog was closed", cancel > dialog && cancel < leave);
    }

    // ---- the actions: the figures and the file are read and written off the screen thread

    @Test
    public void theFiguresAreReadOffTheScreenThreadAndThePageIsPrintedWhenTheyAreReady() throws IOException {
        String print = method(read("controller/ShiftSheetActions.java"), "static void print(ShiftClose close, boolean copy)");
        int async = print.indexOf("Async.run(() -> sheet(close, copy,");
        assertTrue("the sheet is built inside Async.run", async >= 0);
        assertTrue("the result goes to ShiftSheetPage.print, which runs on the screen thread", print.contains("ShiftSheetPage::print"));
        assertTrue("a failure to read is shown", print.contains("AlertUtil.error("));
    }

    @Test
    public void theFileIsChosenOnTheScreenThreadAndTheFiguresAreReadOffIt() throws IOException {
        String save = method(read("controller/ShiftSheetActions.java"), "static void save(ShiftClose close)");
        int dialog = save.indexOf("showSaveDialog(");
        int async = save.indexOf("Async.run(() -> sheet(close, false, null)");
        assertTrue(dialog >= 0 && async > dialog);
        assertTrue("the choice between PDF and CSV is made from the name and the filter, before anything is read",
                save.indexOf("ShiftSheet.savesAsPdf(") > dialog && save.indexOf("ShiftSheet.savesAsPdf(") < async);
        assertTrue("both formats are offered", save.contains("\"*.pdf\"") && save.contains("\"*.csv\""));
        assertTrue(save.contains("writePdf(lines, file)") && save.contains("writeCsv(lines, file)"));
    }

    @Test
    public void aCsvIsWrittenInsideAsyncRun() throws IOException {
        String csv = method(read("controller/ShiftSheetActions.java"), "private static void writeCsv(");
        assertTrue(csv.indexOf("Async.run(") >= 0 && csv.indexOf("ExportCsv.write(") > csv.indexOf("Async.run("));
    }

    @Test
    public void aPdfIsDrawnOnTheScreenThreadAndWrittenOffIt() throws IOException {
        String pdf = method(read("controller/ShiftSheetActions.java"), "private static void writePdf(");
        int draw = pdf.indexOf("ShiftSheetPage.pdf(lines)");
        int async = pdf.indexOf("Async.run(");
        int write = pdf.indexOf("Files.write(");
        assertTrue("the picture is drawn first, here, because JavaFX needs the screen thread", draw >= 0 && draw < async);
        assertTrue("the file is written inside Async.run", write > async);
        assertTrue("a sheet that cannot be drawn is said, not lost", pdf.indexOf("AlertUtil.error(") > 0 && pdf.indexOf("AlertUtil.error(") < async);
    }

    @Test
    public void thePdfIsThePageAsPrintedSetOnA4BySimplePdf() throws IOException {
        String page = method(read("util/ShiftSheetPage.java"), "public static byte[] pdf(");
        assertTrue(page.contains("build(lines, WIDE_WIDTH)"));
        assertTrue(page.contains("sheet.snapshot("));
        assertTrue(page.contains("SimplePdf.imagePage("));
        assertEquals("the file path never reaches the printer", 0, count(page, "printPage("));
    }

    @Test
    public void theDatabaseIsReadInOnePlaceAndOnlyFromInsideAsync() throws IOException {
        String actions = read("controller/ShiftSheetActions.java");
        assertEquals("sheetExtras is read in one place", 1, count(actions, ".sheetExtras("));
        assertTrue(method(actions, "private static List<ShiftSheet.Line> sheet(").contains(".sheetExtras("));
        // every use of sheet(...) is the first thing inside an Async.run lambda, never a call on the screen thread
        int uses = 0;
        int from = 0;
        while ((from = actions.indexOf("sheet(close", from)) >= 0) {
            uses++;
            String before = actions.substring(Math.max(0, from - 160), from);
            assertTrue("sheet(close...) is called outside Async.run: " + before, before.contains("Async.run(") || before.contains("ExportCsv.write("));
            from++;
        }
        assertEquals("print and save use it", 2, uses);
        assertTrue("the actions never print by themselves", !actions.contains("PrinterJob") && !actions.contains(".printPage("));
    }

    // ---- the places that use it

    @Test
    public void theSheetIsOfferedRightAfterAClose() throws IOException {
        assertTrue(read("controller/ShiftCloseDialog.java").contains("ShiftSheetActions.offerAfterClose("));
    }

    @Test
    public void theReportListHasReprintAndSaveAndTheReportCsvHasTheShifts() throws IOException {
        String report = read("controller/ReportWindow.java");
        assertTrue(report.contains("ShiftSheetActions.listButtons(shiftTable)"));
        // the CSV lines are built in util/ReportCsv since TODO 2.86; the window hands over the closed shifts it shows
        assertTrue(read("util/ReportCsv.java").contains("ShiftSheet.csvBlock(data.shifts())"));
        assertTrue(report.contains("shiftTable.getItems()"));
    }

    @Test
    public void everyRowOfTheListHasItsOwnReprintAndSaveButtons() throws IOException {
        String actions = read("controller/ShiftSheetActions.java");
        assertTrue("listButtons adds the column", method(actions, "static HBox listButtons(").contains("table.getColumns().add(rowButtons())"));
        String column = method(actions, "private static TableColumn<ShiftClose, Void> rowButtons()");
        assertTrue(column.contains("setCellFactory(") && column.contains("TableCell"));
        assertTrue("the row's own sheet is reprinted as a copy", column.contains("print(row, true)"));
        assertTrue("and saved", column.contains("save(row)"));
        assertTrue("a button of an empty row is not shown", column.contains("setGraphic(empty ? null : box)"));
    }

    @Test
    public void aReprintSaysCopyAndTheFirstPrintDoesNot() throws IOException {
        String actions = read("controller/ShiftSheetActions.java");
        assertTrue("the box after a close prints the first copy", actions.contains("() -> print(close, false)"));
        assertTrue("the button of the list prints a reprint", actions.contains("print(selected, true)"));
    }
}
