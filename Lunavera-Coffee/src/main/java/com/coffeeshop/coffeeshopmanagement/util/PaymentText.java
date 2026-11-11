package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;

/**
 * How an order's payment reads on screen, in the history, the CSV and the receipt (TODO 4.32): the one method's name, or for an
 * order paid with several methods all the names (and, on the receipt, what each paid).
 */
public final class PaymentText {

    private PaymentText() {
    }

    /** The names only, in the language on screen: "Chuyển khoản + Tiền mặt" or "Tiền mặt"; "-" when unpaid. */
    public static String of(Order order) {
        if (order.isSplitPayment()) {
            return join(order, null);
        }
        return PaymentMethod.labelOf(order.getPaymentMethod());
    }

    /** "Tiền mặt + Chuyển khoản + Thẻ" (names only, language on screen) or, with a language, "Tiền mặt 30.000 ₫ + ..." (cash first, then the list order). */
    private static String join(Order order, String language) {
        StringBuilder text = new StringBuilder();
        for (com.coffeeshop.coffeeshopmanagement.model.PaymentLine line : order.getPaymentLines()) {
            if (text.length() > 0) text.append(" + ");
            text.append(language == null ? line.method().label() : line.method().label(language) + " " + CurrencyUtil.format(line.amount()));
        }
        return text.toString();
    }

    /** With the amounts, in a fixed language: "Tiền mặt 30.000 ₫ + Chuyển khoản 70.000 ₫"; a one-method order is just its name. */
    public static String withAmounts(Order order, String language) {
        if (order.isSplitPayment()) {
            return join(order, language);
        }
        return PaymentMethod.labelOf(order.getPaymentMethod(), language);
    }
}
