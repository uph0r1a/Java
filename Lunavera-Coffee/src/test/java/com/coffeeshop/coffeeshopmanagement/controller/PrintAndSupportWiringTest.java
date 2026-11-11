package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * TODO 5.39 and 5.42 read the sources that cannot be run without a screen or a printer: the Print button follows what the computer
 * chose, "Print with options" always opens the dialog, only a payment prints at once, the paper setting reaches the roll layout, the
 * Settings window saves the choice per computer and offers the support zip, and every text they use exists in both languages.
 */
public class PrintAndSupportWiringTest {

    private static final String MAIN = "src/main/java/com/coffeeshop/coffeeshopmanagement/";

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    private static String method(String source, String signature) {
        int at = source.indexOf(signature);
        assertTrue(signature + " must exist", at >= 0);
        int brace = source.indexOf('{', at);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            if (source.charAt(i) == '}' && --depth == 0) return source.substring(at, i + 1);
        }
        fail("no end of " + signature);
        return "";
    }

    @Test
    public void thePrintButtonFollowsTheChoiceAndTheOptionsButtonAlwaysAsksForTheDialog() throws IOException {
        String receipt = read(MAIN + "controller/HoaDonController.java");
        assertTrue(method(receipt, "public void handlePrint()").contains("print(false)"));
        assertTrue(method(receipt, "public void handlePrintWithOptions()").contains("print(true)"));
        String print = method(receipt, "private void print(boolean forceDialog)");
        assertTrue(print.contains("ReceiptPrinting.load()") && print.contains("ReceiptPrinterJob.open(choice, forceDialog)"));
        assertTrue("the dialog is only shown when the choice says so", print.contains("if (opened.showDialog())"));
        assertTrue("the paper setting reaches the roll layout", print.contains("ReceiptPrinting.useRoll(choice.paper()") && print.contains("ReceiptPrinting.rollWidth(choice.paper()"));
        assertTrue("the cancelled dialog still closes the job (TODO 2.14)", print.contains("job.cancelJob()"));
        String opener = method(read(MAIN + "controller/ReceiptPrinterJob.java"), "static Opened open(");
        assertTrue("a missing printer says so and falls back to the dialog", opener.contains("receipt.print.printer.missing") && opener.contains("receipt.print.default.missing"));
        assertTrue("copies come from the settings", opener.contains("setCopies(choice.copies())"));
        assertTrue("no dialog once a printer was opened", opener.contains("showDialog = false"));
    }

    @Test
    public void theOptionsButtonIsInTheReceiptWindowAndHiddenUntilAPrinterIsChosen() throws IOException {
        String fxml = read("src/main/resources/fxml/donthanhtoan.fxml");
        assertTrue(fxml.contains("fx:id=\"printOptionsButton\"") && fxml.contains("onAction=\"#handlePrintWithOptions\""));
        assertTrue(fxml.contains("%receipt.print.options"));
        String receipt = read(MAIN + "controller/HoaDonController.java");
        assertTrue(method(receipt, "private void showPrintOptionsButton()").contains("ReceiptPrinting.load().direct()"));
        assertTrue("shown or hidden every time a receipt is loaded", method(receipt, "public void setInvoiceData(").contains("showPrintOptionsButton()"));
    }

    @Test
    public void onlyAPaymentPrintsAtOnceAndOnlyWhenTheComputerIsSetToIt() throws IOException {
        String receipt = read(MAIN + "controller/HoaDonController.java");
        String after = method(receipt, "public void printAfterPayment()");
        assertTrue(after.contains("choice.direct()") && after.contains("choice.automatic()"));
        String till = read(MAIN + "controller/OrderController.java");
        Matcher paid = Pattern.compile("openInvoiceWindow\\([^;]*, true\\);").matcher(till);
        assertTrue("the payment path asks for it", paid.find());
        assertFalse("and only that one call does", paid.find());
        assertTrue("a receipt opened again never prints by itself", method(till, "public void handlePrintInvoice()")
                .contains("openInvoiceWindow(lastCompletedOrder, lastCompletedItems, lastCompletedCustomerName, false)"));
    }

    @Test
    public void theSettingsWindowSavesThePrintChoicePerComputerAndOffersTheSupportZip() throws IOException {
        String settings = read(MAIN + "controller/SettingsDialog.java");
        assertTrue(settings.contains("ReceiptPrinting.save(new ReceiptPrinting.Choice("));
        assertTrue("a named printer needs a printer", settings.contains("settings.print.choose"));
        assertTrue("printing at once is not offered while the dialog is asked for each time", settings.contains("printAfterBox.setDisable("));
        assertTrue(method(settings, "private static void saveSupportDetails(").contains("SupportBundle.create(target)"));
        assertTrue("the zip is made off the screen thread", method(settings, "private static void saveSupportDetails(").contains("Async.run("));
        assertFalse("the window never reaches into the database itself", settings.contains("DatabaseConfig"));
    }

    @Test
    public void everyTextTheNewCodeUsesExistsInBothLanguages() throws IOException {
        String en = read("src/main/resources/i18n/messages_en.properties");
        String vi = read("src/main/resources/i18n/messages_vi.properties");
        String code = read(MAIN + "controller/SettingsDialog.java") + read(MAIN + "controller/HoaDonController.java") + read(MAIN + "controller/ReceiptPrinterJob.java")
                + read("src/main/resources/fxml/donthanhtoan.fxml");
        Matcher m = Pattern.compile("(?:I18n\\.t\\(|%)\"?((?:settings\\.(?:print|support|section\\.print|section\\.support)|receipt\\.print\\.(?:options|printer|default))[\\w.]*)").matcher(code);
        int found = 0;
        while (m.find()) {
            found++;
            assertTrue(m.group(1) + " in English", en.contains("\n" + m.group(1) + "="));
            assertTrue(m.group(1) + " in Vietnamese", vi.contains("\n" + m.group(1) + "="));
        }
        assertTrue("the pattern found the texts (" + found + ")", found >= 20);
    }
}
