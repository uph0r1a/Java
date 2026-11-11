package com.coffeeshop.coffeeshopmanagement.util;

/** The pure part of the note under the total (TODO 2.103): how a typed note is cleaned and when it is too long. No database here, so it is tested on its own; {@code PriceNote} keeps it. */
public final class PriceNoteText {

    public static final int MAX = 60;

    private PriceNoteText() {
    }

    /** One line, trimmed, with invisible characters and runs of spaces removed. */
    public static String clean(String text) {
        return text == null ? "" : TextNormalizer.clean(text).replaceAll("\\s+", " ").trim();
    }

    /** What is wrong with a typed note (too long), or null. */
    public static String problem(String text) {
        return clean(text).length() > MAX ? I18n.t("settings.pricenote.long", MAX) : null;
    }
}
