package com.coffeeshop.coffeeshopmanagement.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Vietnamese text can be typed two ways that look the same: "à" as one character, or "a" followed by a separate accent mark.
 * They are different strings to the computer, so duplicates slip past a uniqueness check and searches miss (TODO 3.18).
 * Everything the shop types as a name is stored in the composed form (NFC), and names are compared with {@link #key}.
 *
 * <p>Text pasted from Zalo, Facebook, a phone's contacts or a web page often carries characters nobody can see (TODO 3.71): a
 * non-breaking space (U+00A0), a narrow one (U+202F), a zero-width space (U+200B), a left-to-right mark (U+200E) or the
 * U+202A to U+202C embedding marks. They are not blanks to Java ({@code trim()}, {@code \\s} and {@code isBlank()} all miss them), so
 * a phone number with one was refused with no hint why, two names that look the same were two names, and a search found nothing.
 * {@link #clean} is the one place that deals with them; every name, key, phone, e-mail, amount, code and number typed or pasted goes
 * through it first.
 */
public final class TextNormalizer {

    private TextNormalizer() {}

    /**
     * Text copied from Zalo, a web page or the phone's contacts carries characters nobody can see (TODO 3.71): a non-breaking space
     * (U+00A0), U+202F and the other Unicode space separators, which Java's {@code trim()} and {@code \s} do not know; and format
     * characters that take no room: the zero-width space U+200B, the direction marks U+200E, U+200F and U+202A to U+202E, U+2066 to
     * U+2069, the byte-order mark U+FEFF and the soft hyphen. Every space separator (and a line or paragraph separator) becomes a
     * normal space; the format characters are dropped. The zero-width joiner and non-joiner (U+200D, U+200C) are kept, because an
     * emoji sequence in a product name needs them; {@link #strip} drops those too. Null stays null.
     */
    public static String clean(String text) {
        return text == null ? null : clean(text, true);
    }

    /** The same as {@link #strip} (the name the 3.71 branch used): invisible characters and the joiners go too. */
    public static String plain(String text) {
        return strip(text);
    }

    /** {@link #clean} for text that is a number, a code, an address: the joiners go too. */
    public static String strip(String text) {
        return text == null ? null : clean(text, false);
    }

    private static String clean(String text, boolean keepJoiners) {
        StringBuilder out = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int type = Character.getType(c);
            boolean space = type == Character.SPACE_SEPARATOR || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
            boolean invisible = type == Character.FORMAT && !(keepJoiners && (c == '\u200d' || c == '\u200c'));
            if (out == null && (space && c != ' ' || invisible)) {
                out = new StringBuilder(text.length()).append(text, 0, i);
            }
            if (out != null) {
                if (space) out.append(' ');
                else if (!invisible) out.append(c);
            }
        }
        return out == null ? text : out.toString();
    }

    /** The composed form, cleaned of invisible characters (see {@link #clean}) and trimmed; null stays null. */
    public static String nfc(String text) {
        return text == null ? null : Normalizer.normalize(clean(text), Normalizer.Form.NFC).trim();
    }

    /** For comparing names: composed form, one space between words, lower case (Java lower-cases Vietnamese letters correctly). */
    public static String key(String text) {
        return text == null ? "" : nfc(text).replace("\u200D", "").replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
        String lower = Normalizer.normalize(clean(text), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
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
