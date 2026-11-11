package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.PaymentLine;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;

/**
 * The one method an order is filed under ({@code orders.payment_method}) when it is paid with several (TODO 2.102, 4.32): the pure rules,
 * with no screen and no database, so they are tested on their own.
 */
public final class MainPaymentMethod {

    private MainPaymentMethod() {
    }

    /**
     * What the split box passes on as the method of the payment: {@link PaymentMethod#GIFT_CARD} only when the cards alone pay the
     * whole bill; a card that pays a part leaves the method the cashier chose (a bill of 50.000 on a card and 100.000 in cash is not a
     * "gift card" bill just because nothing is left for the main method).
     */
    public static PaymentMethod effective(PaymentMethod chosen, boolean amountsUsable, BigDecimal cardTotal, BigDecimal billTotal) {
        boolean cardsPayAll = amountsUsable && cardTotal != null && billTotal != null && cardTotal.signum() > 0 && cardTotal.compareTo(billTotal) == 0;
        return cardsPayAll ? PaymentMethod.GIFT_CARD : chosen;
    }

    /**
     * What {@code orders.payment_method} keeps for an order with lines: the method the cashier chose as the main one (its transfer code
     * and "money arrived" belong to it) when it is one of the lines and is not cash, else the non-cash method with the biggest amount.
     * Cash is never the stored method of a split, so a split always has one that is not cash (card plus cash is filed under the card).
     */
    public static PaymentMethod stored(List<PaymentLine> lines, PaymentMethod preferred) {
        if (preferred != null && preferred != PaymentMethod.CASH && lines.stream().anyMatch(l -> l.method() == preferred)) return preferred;
        PaymentLine best = null;
        for (PaymentLine line : lines) {
            if (line.method() == PaymentMethod.CASH) continue;
            if (best == null || line.amount().compareTo(best.amount()) > 0) best = line;
        }
        return best != null ? best.method() : lines.get(0).method();
    }
}
