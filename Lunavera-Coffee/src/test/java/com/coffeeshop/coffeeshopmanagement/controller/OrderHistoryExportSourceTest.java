package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * TODO 6.84: {@link OrderHistoryExport} opens a file chooser, so it cannot run without a screen. This reads its source and pins what the
 * order history's "Xuất CSV" must keep doing: ask first, write every matching order while reading (no list, no limit, TODO 3.54), log
 * the export, and always give the button back whatever happens. The rows themselves are tested in {@code ReportCsvTest} and the order
 * list's own tests.
 */
public class OrderHistoryExportSourceTest {

    private static final String MAIN = "src/main/java/com/coffeeshop/coffeeshopmanagement/";

    private static String source() throws IOException {
        return Files.readString(Path.of(MAIN + "controller/OrderHistoryExport.java"), StandardCharsets.UTF_8);
    }

    @Test
    public void nothingIsWrittenWhenTheCashierClosesTheFileChooser() throws IOException {
        String s = source();
        int chooser = s.indexOf("showSaveDialog(stage)");
        int cancelled = s.indexOf("if (file == null) return;");
        int busy = s.indexOf("BusyIndicator.hold(");
        int write = s.indexOf("ExportCsv.writeOrders(");
        assertTrue("the chooser is shown", chooser > 0);
        assertTrue("a cancelled chooser ends the export", cancelled > chooser);
        assertTrue("before the button is held and before anything is written", cancelled < busy && busy < write);
    }

    @Test
    public void theFileIsOfferedAsADatedCsvAndTheFilterShowsCsvOnly() throws IOException {
        String s = source();
        assertTrue(s.contains("\"don-hang-\" + com.coffeeshop.coffeeshopmanagement.util.AppClock.today() + \".csv\""));
        assertTrue(s.contains("new FileChooser.ExtensionFilter(\"CSV\", \"*.csv\")"));
        assertTrue("the title is a text of the bundle", s.contains("I18n.t(\"history.export.title\")"));
    }

    @Test
    public void everyMatchingOrderIsWrittenWhileItIsReadOffTheScreenThread() throws IOException {
        String s = source();
        assertTrue("work is off the screen thread", s.contains("Async.run("));
        assertTrue("the rows come from the filter and the sort order, not from the page on screen",
                s.contains("history.exportOrders(filter, order, sink)"));
        assertFalse("no list of every order is built first (TODO 3.54)", s.contains("findAll") || s.contains("history(") || s.contains("List<Order>"));
    }

    @Test
    public void theExportIsWrittenToTheSecurityLogWithItsCount() throws IOException {
        String s = source();
        assertTrue(s.contains("SecurityLog.event(\"exported \" + written + \" orders to CSV\")"));
        assertFalse("the log line carries a count, not the file path or any customer text", s.contains("SecurityLog.event(\"exported \" + file"));
    }

    @Test
    public void theButtonIsReleasedAndTheCashierToldOnSuccessAndOnFailure() throws IOException {
        String s = source();
        int done = s.indexOf("count ->");
        int failed = s.indexOf("error ->");
        assertTrue(done > 0 && failed > done);
        assertTrue("success releases the button and says how many and where", s.substring(done, failed).contains("release.run()")
                && s.substring(done, failed).contains("history.export.done"));
        assertTrue("failure releases the button and shows the error", s.substring(failed).contains("release.run()")
                && s.substring(failed).contains("AlertUtil.error("));
    }

    @Test
    public void theHistoryWindowStillCallsIt() throws IOException {
        String window = Files.readString(Path.of(MAIN + "controller/OrderHistoryWindow.java"), StandardCharsets.UTF_8);
        assertTrue(window.contains("OrderHistoryExport.run("));
    }

    @Test
    public void everyTextItUsesExistsInBothLanguages() throws IOException {
        for (String lang : new String[]{"vi", "en"}) {
            String bundle = Files.readString(Path.of("src/main/resources/i18n/messages_" + lang + ".properties"), StandardCharsets.UTF_8);
            for (String key : new String[]{"history.export.title", "history.export.done", "busy.export", "common.file.savefail"}) {
                assertTrue(lang + " has " + key, bundle.lines().anyMatch(l -> l.startsWith(key + "=")));
            }
        }
    }
}
