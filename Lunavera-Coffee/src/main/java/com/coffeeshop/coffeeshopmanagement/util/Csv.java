package com.coffeeshop.coffeeshopmanagement.util;

/** One CSV cell, safe to open in Excel (TODO 2A.1). */
public final class Csv {

    private Csv() {}

    /**
     * Excel treats a cell that starts with {@code = + - @} (or a tab / carriage return) as a formula, so a product named
     * {@code =HYPERLINK(...)} would run when the report is opened. Such a cell gets a leading apostrophe, which Excel hides
     * and does not evaluate. Then the usual quoting for commas, quotes and line breaks.
     */
    public static String cell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r"))
                ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
