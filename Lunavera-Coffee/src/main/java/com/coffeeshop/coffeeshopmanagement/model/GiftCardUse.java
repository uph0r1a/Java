package com.coffeeshop.coffeeshopmanagement.model;

import java.math.BigDecimal;

/**
 * What one order took from one gift card, for the receipt (TODO 5.51): the last four characters of the code (the code itself is a
 * secret that anybody can spend, so it is never printed), what the order paid with the card, and what was left on the card right
 * after that payment (not today's balance, so a reprint does not change).
 */
public record GiftCardUse(String codeTail, BigDecimal paid, BigDecimal left) {

    /** The last four characters of {@code code}, or all of it when it is shorter; "" for null. */
    public static String tailOf(String code) {
        if (code == null) return "";
        String text = code.strip();
        return text.length() <= 4 ? text : text.substring(text.length() - 4);
    }
}
