package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Order;

import java.util.Objects;

/**
 * What the till's order screen decides about the order it shows (TODO 6.5), apart from the screen so it can be tested without JavaFX:
 * whether a loaded order is still the one wanted, what the order box says about a table or take-away order, whether a booking may be
 * seated, and the two small questions the autosave asks. The controller reads its fields, asks, and shows the answer.
 */
public final class OrderScreenRules {

    private OrderScreenRules() {
    }

    /**
     * An answer for a table that is no longer the selected one must not overwrite the screen (TODO 2.55). For a take-away order
     * ({@code number} null) the order that was asked for must also still be the wanted one.
     *
     * @param selectedTable   the table selected now, or {@code null} (take-away screen or none)
     * @param number          the table the answer is for, or {@code null} for take-away
     * @param takeAwayOrderId the take-away order selected now, or {@code null}
     * @param wantedTakeAway  the take-away order that was asked for when the load started
     */
    public static boolean isStale(Integer selectedTable, Integer number, Integer takeAwayOrderId, Integer wantedTakeAway) {
        return !Objects.equals(selectedTable, number) || (number == null && !Objects.equals(takeAwayOrderId, wantedTakeAway));
    }

    /** What the order number label shows. */
    public enum IdText {
        /** The number of the saved order. */
        NUMBER,
        /** "Being served": the table is taken but nothing is saved yet. */
        SERVING,
        /** "No order". */
        NONE
    }

    /**
     * What the order box says once an order (or nothing) was loaded.
     *
     * @param serving          an order, or a table marked taken with no order saved yet: the status badge says "being served"
     * @param idText           which text the order number label shows
     * @param customerName     the text for the customer name field (the customer's name, the walk-in text while serving, else empty)
     * @param customerPhone    the text for the phone field (empty when unknown)
     * @param showCustomerBox  the customer box shows the customer (only for a saved order)
     * @param totalFromCart    the total label shows the cart's subtotal (a saved order); otherwise zero
     */
    public record Shown(boolean serving, IdText idText, String customerName, String customerPhone, boolean showCustomerBox, boolean totalFromCart) {
    }

    /**
     * @param order    the order loaded, or {@code null} when there is none
     * @param known    the customer on the order, or {@code null}
     * @param occupied the table is marked taken
     * @param walkIn   the text for a customer nobody named
     */
    public static Shown shown(Order order, Customer known, boolean occupied, String walkIn) {
        boolean serving = order != null || occupied;
        IdText id = order != null ? IdText.NUMBER : occupied ? IdText.SERVING : IdText.NONE;
        String name = known != null ? known.getFullName() : serving ? walkIn : "";
        String phone = known != null && known.getPhone() != null ? known.getPhone() : "";
        return new Shown(serving, id, name, phone, order != null, order != null);
    }

    /** Whether a booking can be seated now. */
    public enum Seat {
        /** A payment is being saved or an order is being started: nothing happens, no message. */
        IGNORE,
        /** The table is missing from the map or already taken: say so. */
        TABLE_BUSY,
        OK
    }

    public static Seat canSeat(boolean paymentInFlight, boolean startingOrder, DiningTable table) {
        if (paymentInFlight || startingOrder) return Seat.IGNORE;
        return table == null || table.isOccupied() ? Seat.TABLE_BUSY : Seat.OK;
    }

    /** Whether the screen shows a table or a take-away order (so a reload has something to load). */
    public static boolean somethingSelected(Integer selectedTable, boolean takeAway) {
        return selectedTable != null || takeAway;
    }

    /** Whether the order on screen is one that was saved, so the cart is worth autosaving (a new order has no number yet). */
    public static boolean isSaved(Order order) {
        return order != null && order.getId() > 0;
    }
}
