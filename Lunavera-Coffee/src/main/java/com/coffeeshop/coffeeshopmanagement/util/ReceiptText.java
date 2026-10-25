package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.config.SeedNames;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;

import java.util.ResourceBundle;

/**
 * The text of a receipt, on the receipt window and on paper, is always English (the owner's decision, TODO 1.7), whatever
 * language the rest of the app is in. Everything a receipt shows that is a word goes through here: its own labels, the
 * table name, the payment method and the starter-menu product names. What the shop typed (customer and staff names, a
 * discount reason, a product it added itself) is shown as typed. The app's own pop-ups about printing are not part of the
 * receipt and follow the language in use.
 */
public final class ReceiptText {

    public static final String LANGUAGE = I18n.EN;

    private ReceiptText() {
    }

    public static String t(String key, Object... args) {
        return I18n.in(LANGUAGE, key, args);
    }

    /** For loading the receipt window's FXML. */
    public static ResourceBundle bundle() {
        return I18n.bundleFor(LANGUAGE);
    }

    public static String paymentMethod(PaymentMethod method) {
        return PaymentMethod.labelOf(method, LANGUAGE);
    }

    /**
     * The extra lines under the payment method (TODO 3.17, 4.31): for a cash payment what the customer handed over and the change
     * that went back (only when they were recorded, so an older order shows nothing), for a transfer the bank's reference. Empty
     * when there is nothing to add.
     */
    public static java.util.List<String> paymentExtras(com.coffeeshop.coffeeshopmanagement.model.Order order) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (order.getPaymentMethod() == PaymentMethod.CASH && order.getCashReceived() != null) {
            lines.add(t("receipt.line.cash", CurrencyUtil.format(order.getCashReceived())));
            if (order.getChangeGiven() != null) {
                lines.add(t("receipt.line.change", CurrencyUtil.format(order.getChangeGiven())));
            }
        }
        if (order.getPaymentMethod() == PaymentMethod.TRANSFER && order.getTransferRef() != null && !order.getTransferRef().isBlank()) {
            lines.add(t("receipt.line.ref", order.getTransferRef())); // as the cashier typed it
        }
        return lines;
    }

    public static String productName(String storedName) {
        return SeedNames.product(LANGUAGE, storedName);
    }

    /** "Points earned: +5, total: 120"; without the balance (not read yet, or its lookup failed) only the first part (TODO 5.28). */
    public static String points(int earned, Integer balance) {
        // text, not numbers: a number would be printed with a thousands separator of the language
        return balance == null ? t("receipt.points.earned", String.valueOf(earned)) : t("receipt.points", String.valueOf(earned), String.valueOf(balance));
    }

    public static String table(Integer tableNumber) {
        return tableNumber != null ? t("orders.table", tableNumber) : t("orders.takeaway");
    }
}
