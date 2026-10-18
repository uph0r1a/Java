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
}
