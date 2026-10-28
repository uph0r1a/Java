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
     * What MariaDB's {@code utf8mb4_unicode_ci} column decides on its own when it compares two names (TODO 3.55): letter case and
     * accent marks do not count, and spaces at the end do not count; everything else does, including spaces inside the name.
     * Checked against MariaDB 10.11 for every Vietnamese letter in both cases, written both ways (one character, or letter plus
     * separate mark): the same pairs come out equal, none more and none fewer. {@code đ} is deliberately <b>not</b> turned into
     * {@code d}: the collation treats it as its own letter ({@code Đỗ} and {@code Do} are two names on MariaDB). {@link #searchKey},
     * which does fold it, is for search boxes only. Used where the question is "would the server refuse this pair?" (the import
     * from SQLite); the app's own duplicate check uses {@link #uniqueKey}, which is a little stricter.
     */
    public static String collationKey(String text) {
        if (text == null) return "";
        String lower = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        String bare = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return Normalizer.normalize(bare, Normalizer.Form.NFC).replaceAll(" +$", "");
    }

    /**
     * For deciding in the app whether a new name is already taken where the database has a unique rule on the name (TODO 3.55):
     * {@link #collationKey} of {@link #key}, so on top of the server's rule (case, accents) extra spaces between words and around the
     * name do not make a different name either (TODO 3.18). The app is therefore never more lenient than the server, on SQLite
     * (which has no such collation) or on MariaDB, and the same two names are refused on both with the same plain message.
     */
    public static String uniqueKey(String text) {
        return collationKey(key(text));
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
