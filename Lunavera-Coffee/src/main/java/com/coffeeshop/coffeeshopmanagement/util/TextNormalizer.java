package com.coffeeshop.coffeeshopmanagement.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Vietnamese text can be typed two ways that look the same: "à" as one character, or "a" followed by a separate accent mark.
 * They are different strings to the computer, so duplicates slip past a uniqueness check and searches miss (TODO 3.18).
 * Everything the shop types as a name is stored in the composed form (NFC), and names are compared with {@link #key}.
 */
public final class TextNormalizer {

    private TextNormalizer() {}

    /** The composed form, trimmed; null stays null. */
    public static String nfc(String text) {
        return text == null ? null : Normalizer.normalize(text, Normalizer.Form.NFC).trim();
    }

    /** For comparing names: composed form, one space between words, lower case (Java lower-cases Vietnamese letters correctly). */
    public static String key(String text) {
        return text == null ? "" : nfc(text).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * For search boxes: the same as {@link #key} but also without accent marks and with "đ" as "d", so typing "ca phe"
     * finds "Cà phê" and "do" finds "Đỗ" (TODO 5.14). Accents typed by the user still match: both sides are reduced the same way.
     */
    public static String searchKey(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(key(text), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").replace('đ', 'd');
    }
}
