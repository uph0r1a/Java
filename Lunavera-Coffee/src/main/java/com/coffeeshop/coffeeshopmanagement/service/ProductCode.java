package com.coffeeshop.coffeeshopmanagement.service;

import java.util.Locale;

/**
 * The product code / barcode (TODO 4.42): what a USB barcode scanner types ("8934563138165" and Enter), or a short code the shop
 * makes up ("CF01"). The rules live here, with no screen and no database, so the product form, the CSV import and the sales
 * screen all read a code the same way.
 *
 * <p>A code is stored in capitals, with no spaces, so two spellings of one code are one code on SQLite (which compares exact text)
 * and on MariaDB (which ignores capitals) alike, and the database's unique rule ({@code ux_products_code}) means the same
 * thing on both. Letters, digits and {@code - . _ /} are allowed; most barcodes are digits only. Empty means "no code".
 */
public final class ProductCode {

    public static final int MAX_LENGTH = 64;

    private ProductCode() {
    }

    /**
     * The code as it is stored: trimmed, capitals, or null for nothing. A scanner may add a line break or tab after the digits,
     * and a code copied from a spreadsheet may carry spaces; none of that is part of the code. Spaces inside the text are kept
     * here (and refused by {@link #problem}) so a typo is shown to the person instead of being quietly glued together.
     */
    public static String clean(String typed) {
        if (typed == null) return null;
        String trimmed = typed.strip();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    /**
     * Why this text cannot be a code, as the language key of a sentence (never raw text from the person), or null when it is fine
     * or empty. Pass what was typed; it is cleaned first.
     */
    public static String problem(String typed) {
        String code = clean(typed);
        if (code == null) return null;
        if (code.length() > MAX_LENGTH) return "products.val.code.long";
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || c == '-' || c == '.' || c == '_' || c == '/';
            if (!ok) return "products.val.code.chars";
        }
        return null;
    }

    /** True for text that could be a code a scanner just typed: not empty, at most {@link #MAX_LENGTH} characters, all allowed. */
    public static boolean looksLikeCode(String typed) {
        return clean(typed) != null && problem(typed) == null;
    }
}
