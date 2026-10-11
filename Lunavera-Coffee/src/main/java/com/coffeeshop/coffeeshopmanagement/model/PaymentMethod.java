package com.coffeeshop.coffeeshopmanagement.model;

/**
 * How an order was paid. Stored as the enum name in the plain-text {@code orders.payment_method}
 * column, so adding a value needs no schema change; existing rows keep reading as before.
 */
public enum PaymentMethod {
    CASH("Tiền mặt"),
    CARD("Thẻ"),
    /** Bank transfer, paid by the customer scanning the shop's QR (see TransferQr). */
    TRANSFER("Chuyển khoản");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    /** The name shown on screen, the history list and the receipt. */
    public String label() {
        return label;
    }

    /** Label for a possibly missing method ("-" when there is none, e.g. an unpaid order). */
    public static String labelOf(PaymentMethod method) {
        return method != null ? method.label : "-";
    }

    public static PaymentMethod fromDb(String value) {
        if (value == null) return null;
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
