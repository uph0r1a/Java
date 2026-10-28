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
    TRANSFER("payment.transfer"),
    /**
     * The three e-wallets most shops take (TODO 4.34). Each has its own value so the report and the bank statement can be
     * reconciled separately: a wallet's money arrives in the wallet's own account, not in the bank account of a transfer. The
     * stored text is the enum name ({@code MOMO}, {@code ZALOPAY}, {@code VNPAY}), which fits the 20-character column.
     */
    MOMO("payment.momo"),
    ZALOPAY("payment.zalopay"),
    VNPAY("payment.vnpay");

    /** The key of the name in the language files ({@code /i18n/messages_*.properties}); the name itself follows the language in use. */
    private final String labelKey;

    PaymentMethod(String labelKey) {
        this.labelKey = labelKey;
    }

    /** The e-wallet methods, in the order the payment dialog shows them (TODO 4.34). */
    public static java.util.List<PaymentMethod> eWallets() {
        return java.util.List.of(MOMO, ZALOPAY, VNPAY);
    }

    public boolean isEWallet() {
        return eWallets().contains(this);
    }

    /**
     * True when the money arrives outside the till, so the cashier must tick that it arrived before the sale can be finished,
     * and may type the bank's or wallet's transaction code: a bank transfer and the e-wallets. Cash and card are not like that.
     */
    public boolean needsReceivedConfirmation() {
        return this == TRANSFER || isEWallet();
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
