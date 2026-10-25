package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;

import java.math.BigDecimal;

/**
 * The ceilings on what can be typed (TODO 3.59), in one place. Without them a price typed with three extra zeros was saved without
 * a question, a quantity had no limit, and the loyalty points of a huge order wrapped round to a negative number. The figures are
 * the ones the TODO item suggested; to change a ceiling change it here, every screen and import follows.
 */
public final class Limits {

    /** The largest selling price or cost of one product, in dong. */
    public static final BigDecimal MAX_PRICE = new BigDecimal("100000000");
    /** The largest quantity of one cart line. */
    public static final int MAX_LINE_QUANTITY = 999;
    /** The largest stock a product may be given (the CSV import already refused more). */
    public static final int MAX_STOCK = 1_000_000;

    private Limits() {
    }

    /** True when {@code amount} is above the price ceiling (null is not). */
    public static boolean priceTooBig(BigDecimal amount) {
        return amount != null && amount.compareTo(MAX_PRICE) > 0;
    }

    /** The ceiling written the way amounts are shown, for messages: "100.000.000đ". */
    public static String maxPriceText() {
        return CurrencyUtil.format(MAX_PRICE);
    }
}
