package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;

/**
 * How an order's payment reads on screen, in the history, the CSV and the receipt (TODO 4.32): the one method's name, or for an
 * order paid with two methods both names (and, on the receipt, what each paid).
 */
public final class PaymentText {

    private PaymentText() {
    }

    /** The names only, in the language on screen: "Chuyển khoản + Tiền mặt" or "Tiền mặt"; "-" when unpaid. */
    public static String of(Order order) {
        if (order.isSplitPayment() && order.getPaymentMethod() != null) {
            return order.getPaymentMethod().label() + " + " + PaymentMethod.CASH.label();
        }
        return PaymentMethod.labelOf(order.getPaymentMethod());
    }

    /** With the amounts, in a fixed language: "Tiền mặt 30.000 ₫ + Chuyển khoản 70.000 ₫"; a one-method order is just its name. */
    public static String withAmounts(Order order, String language) {
        if (order.isSplitPayment() && order.getPaymentMethod() != null && order.getTotal() != null) {
            return PaymentMethod.CASH.label(language) + " " + CurrencyUtil.format(order.getCashPart()) + " + "
                    + order.getPaymentMethod().label(language) + " " + CurrencyUtil.format(order.getTotal().subtract(order.getCashPart()));
        }
        return PaymentMethod.labelOf(order.getPaymentMethod(), language);
    }
}
