package com.coffeeshop.coffeeshopmanagement.util;

import java.math.BigDecimal;

/**
 * Reads an amount of money typed by a person, the way the app shows it ("25.000đ"): whole dong, with "." or "," or
 * spaces as thousands separators (and only as that, TODO 2.77) and an optional trailing "đ"/"d"/"VND". Before this, fields used
 * {@code new BigDecimal(text)}, so "25.000" was read as 25 and "1e5" as 100000 (TODO 2.16). Used for the employee salary
 * (TODO 2.10); the other money fields move to it when item 2.16 is done.
 */
public final class MoneyParser {

    /** 15 digits is far beyond any real amount and keeps the value inside a long. */
    public static final int MAX_DIGITS = 15;

    private MoneyParser() {
    }

    /** @return the amount in whole dong, or {@code null} for blank text
     *  @throws IllegalArgumentException with a message fit to show the person, for anything that is not a plain amount */
    public static BigDecimal parseDong(String text) {
        if (text == null || text.isBlank()) return null;
        String s = text.trim();
        // The dong sign U+20AB, as Windows, phone keyboards, bank apps and supplier lists write it, may come first or last (TODO 2.80).
        if (s.startsWith("₫")) s = s.substring(1).trim();
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        for (String suffix : new String[]{"vnđ", "vnd", "₫", "đ", "d"}) {
            if (lower.endsWith(suffix)) {
                s = s.substring(0, s.length() - suffix.length()).trim();
                break;
            }
        }
        BigDecimal shorthand = parseShorthand(s);
        if (shorthand != null) {
            return shorthand;
        }
        String digits = digitsOf(s);
        if (digits.length() > MAX_DIGITS) {
            throw new IllegalArgumentException(I18n.t("money.toolarge"));
        }
        return new BigDecimal(digits);
    }

    /** Words a shop writes after an amount, longest first, with what they multiply by (TODO 5.33). */
    private static final Object[][] SHORTHAND = {
            {"tri\u1ec7u", 1_000_000}, {"trieu", 1_000_000}, {"tr", 1_000_000},
            {"ngh\u00ecn", 1_000}, {"nghin", 1_000}, {"ng\u00e0n", 1_000}, {"ngan", 1_000}, {"k", 1_000},
    };

    /** A plain number, with at most two decimals after a comma or a dot (it cannot be a thousands group then). */
    private static final java.util.regex.Pattern SHORTHAND_NUMBER = java.util.regex.Pattern.compile("[0-9]+(?:[.,][0-9]{1,2})?");

    /**
     * {@code 50k}, {@code 50 nghìn}, {@code 1tr}, {@code 1,5tr} and {@code 1 triệu} (TODO 5.33), any capitals. The number before
     * the word is plain: no thousands separators ({@code 1.500k} stays refused, it is unclear what it means) and at most two
     * decimals. Returns null when the text does not end in such a word, so the ordinary reading applies.
     */
    private static BigDecimal parseShorthand(String s) {
        String text = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC);
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (Object[] entry : SHORTHAND) {
            String word = (String) entry[0];
            if (!lower.endsWith(word)) continue;
            String number = text.substring(0, text.length() - word.length()).trim();
            if (!SHORTHAND_NUMBER.matcher(number).matches()) {
                throw new IllegalArgumentException(notAnAmount());
            }
            BigDecimal value = new BigDecimal(number.replace(',', '.')).multiply(BigDecimal.valueOf((Integer) entry[1]));
            BigDecimal whole = value.stripTrailingZeros().scale() <= 0 ? value.setScale(0) : null;
            if (whole == null) throw new IllegalArgumentException(notAnAmount());
            if (whole.precision() > MAX_DIGITS && whole.signum() != 0) {
                throw new IllegalArgumentException(I18n.t("money.toolarge"));
            }
            return whole;
        }
        return null;
    }

    /** The refusal text, in the language in use (a method, not a constant, so it is read when needed). */
    private static String notAnAmount() {
        return I18n.t("money.notanamount");
    }

    /**
     * The digits of an amount with its thousands separators taken out (TODO 2.77). A {@code .}, {@code ,} or space is accepted
     * <em>only</em> as a thousands separator: one kind of separator throughout, a first group of one to three digits (not
     * starting with 0) and every following group exactly three digits. So {@code 25.000}, {@code 1,250,000} and
     * {@code 8 000 000} are read, and {@code 25.5}, {@code 10.50}, {@code 1,234.5}, {@code 1.000,5} and {@code 1,2,3} are
     * refused, because a decimal point cannot be told from a thousands separator and guessing was saving prices 10 to 100
     * times too big or too small without a word.
     */
    private static String digitsOf(String s) {
        java.util.List<StringBuilder> groups = new java.util.ArrayList<>();
        groups.add(new StringBuilder());
        Character separator = null;
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') {
                groups.get(groups.size() - 1).append(c);
            } else if (c == '.' || c == ',' || c == ' ' || c == '\u00a0') {
                char kind = c == '\u00a0' ? ' ' : c; // a non-breaking space counts as a space
                if (separator != null && separator != kind) {
                    throw new IllegalArgumentException(notAnAmount());
                }
                separator = kind;
                groups.add(new StringBuilder());
            } else {
                throw new IllegalArgumentException(notAnAmount());
            }
        }
        if (separator != null) {
            for (int i = 0; i < groups.size(); i++) {
                int length = groups.get(i).length();
                boolean ok = i == 0 ? length >= 1 && length <= 3 && groups.get(i).charAt(0) != '0' : length == 3;
                if (!ok) {
                    throw new IllegalArgumentException(notAnAmount());
                }
            }
        }
        StringBuilder digits = new StringBuilder();
        groups.forEach(digits::append);
        if (digits.length() == 0) {
            throw new IllegalArgumentException(notAnAmount());
        }
        return digits.toString();
    }

    /**
     * Like {@link #parseDong}, for a box that may be left empty (a discount, the cash received): blank is 0 and text that is
     * not a valid amount is {@code null}, so the caller can say "enter a valid number" (TODO 6.5, moved out of the till screen).
     */
    public static BigDecimal parseOptionalDong(String text) {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal value = parseDong(text);
            return value == null ? BigDecimal.ZERO : value;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
