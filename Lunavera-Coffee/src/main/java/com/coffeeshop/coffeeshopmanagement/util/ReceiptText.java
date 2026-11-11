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

    /**
     * The lines under the shop name on every receipt, window and paper (TODO 4.7, 4.22): the address and the phone as the owner
     * typed them, then "Tax code: ..." in the receipt's language. Only what is filled in; empty when nothing is.
     */
    public static java.util.List<String> shopDetailLines() {
        java.util.List<String> lines = new java.util.ArrayList<>(
                com.coffeeshop.coffeeshopmanagement.config.ShopSettings.receiptDetailLines());
        String tax = com.coffeeshop.coffeeshopmanagement.config.ShopSettings.cachedTaxCode();
        if (!tax.isEmpty()) lines.add(t("receipt.taxcode", tax));
        return lines;
    }

    /** For loading the receipt window's FXML. */
    public static ResourceBundle bundle() {
        return I18n.bundleFor(LANGUAGE);
    }

    public static String paymentMethod(PaymentMethod method) {
        return PaymentMethod.labelOf(method, LANGUAGE);
    }

    /** The payment of the order, with what each method paid when there were two (TODO 4.32). */
    public static String paymentOf(com.coffeeshop.coffeeshopmanagement.model.Order order) {
        return PaymentText.withAmounts(order, LANGUAGE);
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
        if (order.getPaymentMethod() != null && order.getPaymentMethod().needsReceivedConfirmation() && order.getTransferRef() != null && !order.getTransferRef().isBlank()) {
            lines.add(t("receipt.line.ref", order.getTransferRef())); // as the cashier typed it
        }
        for (com.coffeeshop.coffeeshopmanagement.model.GiftCardUse use : order.getGiftCardUses()) { // TODO 5.51
            lines.add(t("receipt.line.giftcard", use.codeTail(), CurrencyUtil.format(use.paid()), CurrencyUtil.format(use.left())));
        }
        return lines;
    }

    /** "Points spent: 5" for a sale that was part-paid with loyalty points (TODO 4.16); null when it was not. */
    public static String pointsSpent(com.coffeeshop.coffeeshopmanagement.model.Order order) {
        Integer spent = order.getPointsRedeemed();
        return spent == null || spent <= 0 ? null : t("receipt.points.spent", String.valueOf(spent));
    }

    /** "Promotion: Giờ vàng (-12.000đ)" for a line that got an automatic promotion (TODO 4.55), else null. */
    public static String promoLine(com.coffeeshop.coffeeshopmanagement.model.OrderItem item) {
        if (item == null || item.getPromoName() == null || item.getPromoName().isBlank()
                || item.getPromoValue() == null || item.getPromoValue().signum() <= 0) {
            return null;
        }
        return t("receipt.line.promo", item.getPromoName(), CurrencyUtil.format(item.getPromoValue()));
    }

    /** {@code text} with the line's promotion under it when it has one. */
    public static String withPromo(String text, com.coffeeshop.coffeeshopmanagement.model.OrderItem item) {
        String promo = promoLine(item);
        return promo == null ? text : text + "\n" + promo;
    }

    public static String productName(String storedName) {
        return SeedNames.product(LANGUAGE, storedName);
    }

    /** "Points earned: +5, total: 120"; without the balance (not read yet, or its lookup failed) only the first part (TODO 5.28). */
    public static String points(int earned, Integer balance) {
        // text, not numbers: a number would be printed with a thousands separator of the language
        return balance == null ? t("receipt.points.earned", String.valueOf(earned)) : t("receipt.points", String.valueOf(earned), String.valueOf(balance));
    }

    /** "Bàn 3", or "Mang đi số 5" for a take-away order with its number of the day (TODO 4.25, D3). */
    public static String tableOf(com.coffeeshop.coffeeshopmanagement.model.Order order) {
        if (order.getTableNumber() == null && order.getQueueNo() != null) {
            if (order.channel().isDeliveryApp()) { // "GrabFood no. 5" (TODO 4.48)
                return t(order.channel().labelKey()) + " " + t("pos.takeaway.number", String.valueOf(order.getQueueNo()));
            }
            return t("orders.takeaway.number", String.valueOf(order.getQueueNo())); // text, so no thousands separator
        }
        if (order.getTableNumber() == null && order.channel().isDeliveryApp()) return t(order.channel().labelKey());
        return table(order.getTableNumber());
    }

    /** "*** COPY - print no. 2 ***" for the second and later print of a receipt, null for the first (TODO 3.72). */
    public static String copyMark(com.coffeeshop.coffeeshopmanagement.model.Order order) {
        int number = order.nextCopyNumber();
        return number == 0 ? null : t("receipt.copy", String.valueOf(number)); // text, so no thousands separator
    }

    public static String table(Integer tableNumber) {
        return tableNumber != null ? t("orders.table", tableNumber) : t("orders.takeaway");
    }

    /**
     * The time the receipt shows: when it was PAID, not when the order was opened (TODO 2.44); an old order with no payment time falls
     * back to the opening time, and "-" when neither is known.
     */
    public static String timeOf(com.coffeeshop.coffeeshopmanagement.model.Order order, java.time.format.DateTimeFormatter format) {
        java.time.LocalDateTime shown = order.getPaidAt() != null ? order.getPaidAt() : order.getOrderDate();
        return shown != null ? shown.format(format) : "-";
    }

    /**
     * The language key of the banner at the top of the receipt: "PAID" for a paid order and "CANCELLED - NOT A VALID PAYMENT" for a
     * cancelled one (TODO 2.45: a refunded order must not pass for proof of a sale); null for an order that is still open, which
     * shows no banner.
     */
    public static String bannerKey(com.coffeeshop.coffeeshopmanagement.model.Order order) {
        if (order.getStatus() == com.coffeeshop.coffeeshopmanagement.model.OrderStatus.PAID) return "receipt.paid";
        if (order.getStatus() == com.coffeeshop.coffeeshopmanagement.model.OrderStatus.CANCELLED) return "receipt.cancelled";
        return null;
    }
}
