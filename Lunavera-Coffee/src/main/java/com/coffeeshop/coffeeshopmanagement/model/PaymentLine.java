package com.coffeeshop.coffeeshopmanagement.model;

import java.math.BigDecimal;

/**
 * One line of an order paid with several methods (TODO 4.32, owner's rule D4): how much was paid with which method. An order paid
 * with one method has no lines; an order paid with two or more has one line per method, and the lines add up to the total. An order paid in full with a gift card has the one line.
 */
public record PaymentLine(PaymentMethod method, BigDecimal amount, String reference) {

    /** A line with no reference; only a gift-card line carries one (the card's code, TODO 7). */
    public PaymentLine(PaymentMethod method, BigDecimal amount) {
        this(method, amount, null);
    }
}
