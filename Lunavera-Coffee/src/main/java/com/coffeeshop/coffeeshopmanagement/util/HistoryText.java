package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO.OrderSummary;

import java.math.BigDecimal;

/**
 * The texts of the order history table that are worked out from a row (TODO 3.17, 6.90 b): kept out of the window, which is at its size ceiling,
 * and testable without JavaFX. The cash figures are stored only for a cash sale ({@code OrderDAO.paymentDetails}); for any other
 * payment, or an order saved before they were kept, the cell is empty, never a made-up zero.
 */
public final class HistoryText {

    private HistoryText() {
    }

    /** The discount and, after a dash, the reason; empty when there is none. */
    public static String discount(OrderSummary row) {
        BigDecimal d = row.order().getDiscount();
        if (d == null || d.signum() == 0) return "";
        String why = row.order().getDiscountReason();
        return CurrencyUtil.format(d) + (why == null || why.isBlank() ? "" : " - " + why);
    }

    /** The cash the customer handed over, or empty when it was not a cash sale or nothing was kept. */
    public static String cashGiven(OrderSummary row) {
        BigDecimal cash = row.order().getCashReceived();
        return cash == null ? "" : CurrencyUtil.format(cash);
    }

    /** The change handed back, or empty when it was not a cash sale or nothing was kept. */
    public static String change(OrderSummary row) {
        BigDecimal change = row.order().getChangeGiven();
        return change == null ? "" : CurrencyUtil.format(change);
    }

    /** The same two figures as plain dong for the CSV (no thousands marks, so a spreadsheet reads them as numbers): empty when not kept. */
    public static String plainDong(BigDecimal money) {
        return money == null ? "" : money.toPlainString();
    }
}
