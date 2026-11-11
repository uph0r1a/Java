package com.coffeeshop.coffeeshopmanagement.util;

/**
 * The length limits of the text fields and the rule that applies them, with no JavaFX in it (TODO 6.83) so the services that check the
 * same limits before saving ({@code StaffRules}, {@code ProductCsv}) compile and are tested without it. {@link TextLengthLimiter}
 * keeps the same numbers under its old names and adds the text-field part.
 */
public final class TextLimits {

    public static final int NAME_MAX = 100;

    public static final int DESCRIPTION_MAX = 500;

    /** Longest phone we accept: +84 and 10 digits is 13; 15 is the E.164 maximum. */
    public static final int PHONE_MAX = 15;

    public static final int ADDRESS_MAX = 200;

    public static final int POSITION_MAX = 60;

    public static final int EMAIL_MAX = 100;

    public static final int USERNAME_MAX = 32;

    public static final int PASSWORD_MAX = 64;

    public static final int SEARCH_MAX = 100;

    /** Money / quantity inputs: up to 12 characters (digits, plus separators typed by hand). */
    public static final int NUMBER_MAX = 12;

    /** A money box that may hold the biggest amount with its dots and a trailing "đ" ("1.000.000.000đ"): 16 characters (TODO 5.50). */
    public static final int MONEY_MAX = 16;

    private TextLimits() {
    }

    /** The rule applied to every change of a limited field: the new text may be at most {@code maxLength} characters (TODO 6.35). */
    public static boolean accepts(String newText, int maxLength) {
        return newText == null || newText.length() <= maxLength;
    }
}
