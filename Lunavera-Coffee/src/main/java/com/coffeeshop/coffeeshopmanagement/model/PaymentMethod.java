package com.coffeeshop.coffeeshopmanagement.model;

import java.util.Locale;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

/**
 * How an order was paid. Stored as the enum name in the plain-text {@code orders.payment_method}
 * column, so adding a value needs no schema change; existing rows keep reading as before.
 */
public enum PaymentMethod {
    CASH("payment.cash"),
    CARD("payment.card"),
    /** Bank transfer, paid by the customer scanning the shop's QR (see TransferQr). */
    TRANSFER("payment.transfer");

    /** The key of the name in the language files ({@code /i18n/messages_*.properties}); the name itself follows the language in use. */
    private final String labelKey;

    PaymentMethod(String labelKey) {
        this.labelKey = labelKey;
    }

    /** The name shown on screen, the history list and the receipt. */
    public String label() {
        return I18n.t(labelKey);
    }

    /** The name in a fixed language, whatever language is in use (the receipt is always English). */
    public String label(String language) {
        return I18n.in(language, labelKey);
    }

    /** Label for a possibly missing method ("-" when there is none, e.g. an unpaid order). */
    public static String labelOf(PaymentMethod method) {
        return method != null ? method.label() : "-";
    }

    public static String labelOf(PaymentMethod method, String language) {
        return method != null ? method.label(language) : "-";
    }

    public static PaymentMethod fromDb(String value) {
        if (value == null) return null;
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
