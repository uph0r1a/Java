package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;

import java.util.List;
import java.util.Objects;

/**
 * The decisions around a payment that {@code OrderController} used to take in the middle of its screen code (TODO 6.5), apart from the
 * screen so they can be tested without JavaFX: may a payment start, which lines the receipt shows, and what the till does with its own
 * screen once the sale is saved. The controller only reads its fields, asks, and does what the answer says.
 */
public final class PaymentFlowRules {

    private PaymentFlowRules() {
    }

    /** Whether a payment (or a free drink) may start, and if not, why; checked in this order. */
    public enum Start {
        /** Go on: the stock check and the payment box follow. */
        OK,
        /** A payment is being saved (TODO 2.79): nothing happens, no message. */
        ALREADY_PAYING,
        /** No order is open on the screen. */
        NO_ORDER,
        /** The cart has no lines. */
        EMPTY_CART
    }

    public static Start canStart(boolean paymentInFlight, boolean orderInProgress, boolean cartEmpty) {
        if (paymentInFlight) return Start.ALREADY_PAYING;
        if (!orderInProgress) return Start.NO_ORDER;
        if (cartEmpty) return Start.EMPTY_CART;
        return Start.OK;
    }

    /**
     * The lines the receipt shows: the pieces paid in a split (TODO 4.10), the lines at no charge for a free drink (TODO 4.61, the receipt of
     * a free bill shows 0), or else the lines of the cart.
     */
    public static List<OrderItem> receiptLines(List<OrderItem> splitPart, boolean complimentary, List<OrderItem> items) {
        if (splitPart != null) return splitPart;
        return complimentary ? ComplimentaryRules.atNoCharge(items) : items;
    }

    /** What the till does with its own screen after a sale was saved. The product tiles are refreshed in every case. */
    public enum ScreenAfter {
        /** The sale was a split of the table now on screen: show the rest of the order again (TODO 4.10). */
        RELOAD_REST,
        /** The sale closed the order now on screen: empty the cart and make the till ready for the next one. */
        CLEAR_ORDER,
        /** The screen shows something else now (another table, another order): leave it alone. */
        KEEP
    }

    /**
     * @param split          the sale paid only some pieces of the order
     * @param paidTable      the table that was paid, or {@code null} for a take-away order
     * @param selectedTable  the table on screen now, or {@code null} when none (the take-away screen)
     * @param takeAwayShown  the take-away screen is showing
     * @param sameOpenOrder  the order open on the screen is the one that was paid (matters for take-away only)
     */
    public static ScreenAfter screenAfter(boolean split, Integer paidTable, Integer selectedTable, boolean takeAwayShown, boolean sameOpenOrder) {
        if (split) return Objects.equals(selectedTable, paidTable) ? ScreenAfter.RELOAD_REST : ScreenAfter.KEEP;
        boolean stillShown = paidTable != null ? Objects.equals(selectedTable, paidTable) : takeAwayShown && sameOpenOrder;
        return stillShown ? ScreenAfter.CLEAR_ORDER : ScreenAfter.KEEP;
    }
}
