package com.coffeeshop.coffeeshopmanagement.service;

import java.util.OptionalLong;

/**
 * The one reader of a whole number typed in a box (TODO 2.95): the stock receipt and waste box, every ingredient box (stock, minimum,
 * receive, count) and the amount of an ingredient in a recipe. They used to delete every dot and comma wherever it stood, so a recipe
 * of {@code 7.5} grams was stored as 75 and every sale took ten times too much from the stock.
 *
 * <p>The rule is the one the product CSV import already had (TODO 3.46): a dot is a thousands mark only inside a group of exactly
 * three digits ({@code 1.000}, {@code 12.500.000}); spaces (also the no-break ones) group digits the same way; a comma is never
 * accepted. So {@code 7.5}, {@code 0.5}, {@code 2,5}, {@code 2.50}, {@code 12.5.1} and {@code 1 2} are refused, not read as a
 * different number. A leading minus is read (a count of -3 is a number, whether it is allowed is the caller's rule). At most 12 digits.
 */
public final class WholeNumber {

    private static final int MAX_DIGITS = 12;

    private WholeNumber() {
    }

    /** The number typed, or empty when the text is not a whole number (nothing is guessed from a fraction). */
    public static OptionalLong parse(String typed) {
        if (typed == null) return OptionalLong.empty();
        String s = typed.trim().replace('\u00A0', ' ').replace('\u202F', ' ');
        boolean negative = s.startsWith("-");
        String digits = negative ? s.substring(1) : s;
        boolean plain = digits.matches("[0-9]+");
        boolean dotted = digits.matches("[0-9]{1,3}(\\.[0-9]{3})+");
        boolean spaced = digits.matches("[0-9]{1,3}( [0-9]{3})+");
        if (!plain && !dotted && !spaced) return OptionalLong.empty();
        String compact = digits.replace(".", "").replace(" ", "");
        if (compact.length() > MAX_DIGITS) return OptionalLong.empty();
        long value = Long.parseLong(compact);
        return OptionalLong.of(negative ? -value : value);
    }
}
