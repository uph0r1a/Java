package com.coffeeshop.coffeeshopmanagement.util;

/**
 * Cuts a text to a length without cutting through a character that Java stores as two {@code char}s (an emoji, a rare Han letter): a plain
 * {@code substring(0, max)} can end on the first half, which the database then stores as a stray "?" (TODO 3.83). The limit is still counted in
 * Java {@code char}s, as the database columns and {@link TextLimits} count it, so an emoji uses two of them; when the cut would fall in the
 * middle of one, the whole character is dropped and the result is one char shorter.
 */
public final class TextClip {

    private TextClip() {
    }

    /** {@code text} cut to at most {@code max} chars, never in the middle of a surrogate pair; null stays null. */
    public static String clip(String text, int max) {
        if (text == null || text.length() <= max) return text;
        if (max <= 0) return "";
        int end = max;
        if (Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) end--;
        return text.substring(0, end);
    }
}
