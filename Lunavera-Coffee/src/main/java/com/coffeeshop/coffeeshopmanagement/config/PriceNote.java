package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.PriceNoteText;

import java.util.Map;

/**
 * The small line under the total on the sales screen (TODO 2.103), such as "Đã bao gồm VAT & phí dịch vụ". The program calculates no
 * VAT and no service charge, so it must not claim anything about them by itself: the line is a shop setting, <b>off (empty) until the owner
 * writes it</b>, and the owner is the one who knows whether the prices really include them. Kept like the other shop texts (one value
 * under a prefix, see {@link AppFlags}).
 */
public final class PriceNote {

    public static final String PREFIX = "pricenote:";
    public static final int MAX = PriceNoteText.MAX;

    private static volatile String cached = "";

    private PriceNote() {
    }

    /** The text to show under the total; empty means nothing is shown. */
    public static String cached() {
        return cached;
    }

    /** One line, trimmed (see {@link PriceNoteText#clean}). */
    public static String clean(String text) {
        return PriceNoteText.clean(text);
    }

    /** What is wrong with a typed note (too long), or null. */
    public static String problem(String text) {
        return PriceNoteText.problem(text);
    }

    /** Reads the note from the database; a failure keeps what is known (the line is only a courtesy). */
    public static void load() {
        try {
            cached = AppFlags.valueWithPrefix(PREFIX).map(PriceNote::clean).orElse("");
        } catch (RuntimeException e) {
            // keep what we have
        }
    }

    /** Saves the note (blank switches the line off); refuses a note that is too long. The known value changes only after the database accepted it. */
    public static void save(String text) {
        String problem = problem(text);
        if (problem != null) throw new IllegalArgumentException(problem);
        String clean = clean(text);
        if (clean.equals(cached)) return;
        AppFlags.replaceValues(Map.of(PREFIX, clean));
        cached = clean;
    }
}
