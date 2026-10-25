package com.coffeeshop.coffeeshopmanagement.util;

/** One CSV cell, safe to open in Excel (TODO 2A.1, 3.47). */
public final class Csv {

    /** Characters that make Excel read more than a number or a word: operators, brackets, a call, a sheet reference. */
    private static final String FORMULA_PARTS = "=+-*/^&|!()<>@:;\\";

    private Csv() {}

    /**
     * Excel treats a cell that can run as a formula as one, so a product named {@code =HYPERLINK(...)} would run when the
     * file is opened. Such a cell gets a leading apostrophe. In a CSV file that Excel opens the apostrophe is shown, so it is
     * kept to what can really run (TODO 3.47): a value that starts with {@code =}, {@code @}, a tab or a carriage return, and
     * one that starts with {@code +} or {@code -} unless it only looks like a number or a note such as {@code -5% combo} or
     * {@code +84901234567} (next character a digit, a space or a point, and no operator, bracket or sheet sign after it).
     * Then the usual quoting for commas, quotes and line breaks.
     */
    public static String cell(String value) {
        String v = value == null ? "" : value;
        if (canRunAsFormula(v)) {
            v = "'" + v;
        }
        return (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r"))
                ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    /** True when Excel could run this text as a formula (or a DDE call) if it were the first thing in a cell. */
    static boolean canRunAsFormula(String v) {
        if (v == null || v.isEmpty()) return false;
        char first = v.charAt(0);
        if ("=@\t\r".indexOf(first) >= 0) return true;
        if (first != '+' && first != '-') return false;
        if (v.length() == 1) return false;
        char next = v.charAt(1);
        if (!(Character.isDigit(next) || next == ' ' || next == '.')) return true;
        for (int i = 1; i < v.length(); i++) {
            if (FORMULA_PARTS.indexOf(v.charAt(i)) >= 0) return true;
        }
        return false;
    }
}
