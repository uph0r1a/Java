package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * TODO 4.46 reads the sources that cannot be run without a screen: the hours dialog has the same "Xuất CSV" button as the other lists (in the
 * administrator part only), the file is written through the shared {@code ExportCsv.write} off the screen thread, an audit-style line goes to
 * the security log, and the text of the file comes from the tested {@code StaffHoursCsv}.
 */
public class TimeClockExportSourceTest {

    private static final String MAIN = "src/main/java/com/coffeeshop/coffeeshopmanagement/";

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    public void theHoursDialogHasTheSameExportButtonAsTheOtherLists() throws IOException {
        String dialog = read(MAIN + "controller/TimeClockDialog.java");
        assertTrue("the same label as the history and the customer list", dialog.contains("I18n.t(\"common.exportcsv\")"));
        assertTrue(dialog.contains("TimeClockExport.run(export, dao, from.getValue(), to.getValue(), message)"));
        int admin = dialog.indexOf("if (Session.isAdmin())");
        assertTrue("only for an administrator, beside the range it exports", admin > 0 && dialog.indexOf("common.exportcsv") > admin
                && dialog.contains("new HBox(8, from, to, show, export)"));
    }

    @Test
    public void theFileIsWrittenTheSameWayAsEveryOtherExportAndOffTheScreenThread() throws IOException {
        String export = read(MAIN + "controller/TimeClockExport.java");
        assertTrue("Excel-safe UTF-8, the sep line, the in-use message", export.contains("ExportCsv.write(file.toPath(), lines)"));
        assertTrue(export.contains("Async.run("));
        assertTrue(export.contains("BusyIndicator.hold(button"));
        assertTrue("the text of the file comes from the tested class", export.contains("StaffHoursCsv.perPerson(") && export.contains("StaffHoursCsv.perDay("));
        assertTrue("an export of staff data leaves a trace", export.contains("SecurityLog.event(\"exported the staff hours"));
        assertTrue("the person can pick one line per person or per person per day, or cancel", export.contains("time.clock.export.person")
                && export.contains("time.clock.export.day") && export.contains("ButtonType.CANCEL"));
        assertTrue("an empty range writes no file", export.contains("if (people == 0) return 0;"));
    }

    @Test
    public void thePersonalCsvTextNeverBuildsACellByHand() throws IOException {
        String csv = read(MAIN + "util/StaffHoursCsv.java");
        assertTrue("names go through Csv.cell (formulas neutralised, TODO 2A.1)", csv.contains("Csv.cell(h.userName())") && csv.contains("Csv.cell(names.get(id))"));
    }
}
