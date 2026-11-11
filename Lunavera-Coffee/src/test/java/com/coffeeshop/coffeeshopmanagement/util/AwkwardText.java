package com.coffeeshop.coffeeshopmanagement.util;

import java.util.ArrayList;
import java.util.List;

/**
 * The characters that look like nothing, or like a space, but are not an ordinary space (TODO 3.71): what text pasted from Zalo,
 * Facebook, a phone's contacts or a web page really carries. One list for every parser's test, so a new parser is tried with all of
 * them and nobody has to remember which ones.
 */
public final class AwkwardText {

    private AwkwardText() {
    }

    /** Spaces of other kinds: they mean "a space", and are read as one. */
    public static final List<String> SPACES = List.of(
            "\u00A0",  // no-break space, the one Word, web pages and phone contacts add
            "\u1680",  // ogham space mark
            "\u2002", "\u2003", "\u2004", "\u2005", "\u2006", // en, em and other fixed-width spaces
            "\u2007",  // figure space
            "\u2008", "\u2009", "\u200A", // punctuation, thin and hair space
            "\u202F",  // narrow no-break space (French and some Zalo/phone number formats)
            "\u205F",  // medium mathematical space
            "\u3000",  // ideographic space
            "\u2028", "\u2029"); // line and paragraph separator

    /** Characters nobody can see and that carry no meaning here: they are dropped. */
    public static final List<String> INVISIBLE = List.of(
            "\u200B",  // zero-width space
            "\u200C",  // zero-width non-joiner
            "\u2060",  // word joiner
            "\uFEFF",  // byte-order mark / zero-width no-break space
            "\u200E", "\u200F", // left-to-right and right-to-left marks
            "\u202A", "\u202B", "\u202C", "\u202D", "\u202E", // embedding, pop and override marks
            "\u2066", "\u2067", "\u2068", "\u2069", // isolates
            "\u061C",  // Arabic letter mark
            "\u00AD",  // soft hyphen
            "\u180E"); // Mongolian vowel separator

    /** Every awkward character: spaces first, then the invisible ones. */
    public static List<String> all() {
        List<String> all = new ArrayList<>(SPACES);
        all.addAll(INVISIBLE);
        return all;
    }

    /** True for a character of {@link #SPACES}. */
    public static boolean isSpaceKind(String c) {
        return SPACES.contains(c);
    }
}
