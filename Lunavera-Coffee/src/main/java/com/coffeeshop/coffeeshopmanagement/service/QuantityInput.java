package com.coffeeshop.coffeeshopmanagement.service;

import java.util.OptionalInt;

/**
 * A quantity typed into the POS cart line (TODO 5.27): digits only, at most {@value #MAX_DIGITS} of them. Kept out of the
 * screen so the rule can be tested without JavaFX. The stock limit is not here: it stays in the one place that "+" uses too
 * ({@code OrderController.changeQuantity}, through {@link Cart#exceedsStock}), so a typed number cannot go past it.
 */
public final class QuantityInput {

    public static final int MAX_DIGITS = 3;

    private QuantityInput() {
    }

    /** What may stay in the text box: the digits of {@code text} (any script's digits are not accepted), cut to {@link #MAX_DIGITS}. */
    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < text.length() && digits.length() < MAX_DIGITS; i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        return digits.toString();
    }

    /** The typed number, or empty when nothing usable was typed. 0 is a number: it means "take the line off". */
    public static OptionalInt parse(String text) {
        if (text == null) {
            return OptionalInt.empty();
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_DIGITS || !sanitize(trimmed).equals(trimmed)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Integer.parseInt(trimmed));
    }
}
