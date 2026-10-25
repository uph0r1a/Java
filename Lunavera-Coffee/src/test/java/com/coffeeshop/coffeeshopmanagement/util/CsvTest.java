package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** TODO 2A.1: a product name must never run as a formula when the report is opened in Excel. */
public class CsvTest {
    @Test
    public void formulaStartersGetAnApostrophe() {
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\")\"", Csv.cell("=HYPERLINK(\"http://x\")"));
        assertEquals("'+1+1", Csv.cell("+1+1"));
        assertEquals("'@SUM(A1)", Csv.cell("@SUM(A1)"));
        assertEquals("'-2+3", Csv.cell("-2+3"));
        assertEquals("'\tcmd", Csv.cell("\tcmd"));
    }

    @Test
    public void ordinaryTextIsUnchangedExceptForQuoting() {
        assertEquals("Cà phê sữa", Csv.cell("Cà phê sữa"));
        assertEquals("\"a,b\"", Csv.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", Csv.cell("say \"hi\""));
        assertEquals("\"a\rb\"", Csv.cell("a\rb"));
        assertEquals("", Csv.cell(null));
        assertEquals("Trà-đào", Csv.cell("Trà-đào"));
    }

    @Test
    public void aFileOpenInExcelIsRecognisedFromTheWindowsReason() {
        java.nio.file.FileSystemException locked = new java.nio.file.FileSystemException("C:\\x\\orders.csv", null,
                "The process cannot access the file because it is being used by another process");
        org.junit.Assert.assertTrue(ExportCsv.fileInUse(locked));
        org.junit.Assert.assertFalse(ExportCsv.fileInUse(new java.nio.file.NoSuchFileException("orders.csv")));
        org.junit.Assert.assertFalse(ExportCsv.fileInUse(new java.io.IOException("disk full")));
        org.junit.Assert.assertFalse(ExportCsv.fileInUse(new java.nio.file.FileSystemException("a", null, "No space left on device")));
    }

    @Test
    public void writingToAFolderThatDoesNotExistKeepsTheOriginalError() throws Exception {
        java.nio.file.Path missing = java.nio.file.Files.createTempDirectory("csv-test").resolve("no-such-folder").resolve("a.csv");
        try {
            ExportCsv.write(missing, java.util.List.of("a"));
            org.junit.Assert.fail("expected an IOException");
        } catch (java.io.IOException expected) {
            org.junit.Assert.assertFalse(expected.getMessage().contains(com.coffeeshop.coffeeshopmanagement.util.I18n.t("export.file.inuse")));
        } finally {
            java.nio.file.Files.deleteIfExists(missing.getParent().getParent());
        }
    }

    /** TODO 3.47: Excel shows the apostrophe of a CSV cell, so only what can really run gets one. */
    @Test
    public void textThatOnlyLooksLikeANumberOrANoteKeepsNoApostrophe() {
        assertEquals("-5% combo", Csv.cell("-5% combo"));
        assertEquals("+84901234567", Csv.cell("+84901234567"));
        assertEquals("+84 901 234 567", Csv.cell("+84 901 234 567"));
        assertEquals("- giảm giá", Csv.cell("- giảm giá"));
        assertEquals("-.5", Csv.cell("-.5"));
        assertEquals("-", Csv.cell("-"));
    }

    @Test
    public void whatCanRunAsAFormulaStillGetsTheApostrophe() {
        assertEquals("'=SUM(A1)", Csv.cell("=SUM(A1)"));
        assertEquals("'@SUM(A1)", Csv.cell("@SUM(A1)"));
        assertEquals("'+cmd|' /C calc'!A0", Csv.cell("+cmd|' /C calc'!A0"));
        assertEquals("'-cmd", Csv.cell("-cmd"));
        // starts like a number but carries a formula behind it (the DDE trick)
        assertEquals("'-2+3+cmd|' /C calc'!A0", Csv.cell("-2+3+cmd|' /C calc'!A0"));
        assertEquals("'+1+1", Csv.cell("+1+1"));
        assertEquals("'-5(x)", Csv.cell("-5(x)"));
    }

    /** TODO 3.49: the first line tells Excel the separator, so a Vietnamese-format Windows does not use one column. */
    @Test
    public void anExportedFileStartsWithTheBomAndTheSepLine() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("csv-sep");
        java.nio.file.Path file = dir.resolve("a.csv");
        try {
            ExportCsv.write(file, java.util.List.of("Tên,Số lượng", "Trà,2"));
            String text = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
            assertEquals("\uFEFFsep=,\r\nTên,Số lượng\r\nTrà,2\r\n", text);
        } finally {
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.deleteIfExists(dir);
        }
    }
}
