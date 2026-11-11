package com.coffeeshop.coffeeshopmanagement.model;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

/**
 * Where an order came from (TODO 4.48): at a table, over the counter to take away, or through a delivery app. Stored as the enum name
 * in the plain-text {@code orders.channel} column (20 characters), so a new value needs no schema change.
 *
 * <p>An order at a table is always {@link #DINE_IN}; an order with no table is {@link #TAKE_AWAY} unless the cashier started it as a
 * delivery-app order. Orders made before the column existed have it empty and are read by the same rule from their table
 * ({@link #of}, and {@link #SQL} in a query), so nothing needs to be rewritten in the database.
 */
public enum OrderChannel {
    DINE_IN("channel.dine_in", "🍽"),
    TAKE_AWAY("channel.take_away", "🥡"),
    GRAB_FOOD("channel.grab_food", "🛵"),
    SHOPEE_FOOD("channel.shopee_food", "🛵");

    /**
     * The channel of an order as SQL, for a query on the {@code orders} table or a derived table called {@code o}: the stored value,
     * else worked out from the table, exactly as {@link #of}. Put it in a {@code SELECT}/{@code GROUP BY}, not in a {@code WHERE}
     * that should use an index.
     */
    public static final String SQL = "COALESCE(o.channel, CASE WHEN o.table_number IS NULL THEN '" + "TAKE_AWAY" + "' ELSE '" + "DINE_IN" + "' END)";

    private final String labelKey;
    private final String icon;

    OrderChannel(String labelKey, String icon) {
        this.labelKey = labelKey;
        this.icon = icon;
    }

    /** The name in the language in use. */
    public String label() {
        return I18n.t(labelKey);
    }

    /** The key of the name in the language files, for a text printed in the receipt's own language. */
    public String labelKey() {
        return labelKey;
    }

    public String icon() {
        return icon;
    }

    /** The delivery apps: the order is handed to a driver, and the app pays the shop later. */
    public boolean isDeliveryApp() {
        return this == GRAB_FOOD || this == SHOPEE_FOOD;
    }

    /** The channels the cashier can start a table-less order as, in the order the buttons show them. */
    public static java.util.List<OrderChannel> withoutTable() {
        return java.util.List.of(TAKE_AWAY, GRAB_FOOD, SHOPEE_FOOD);
    }

    /** The stored text to a channel; null for an empty or unknown text (an old row, or a value from a newer version). */
    public static OrderChannel fromDb(String text) {
        if (text == null) return null;
        for (OrderChannel channel : values()) {
            if (channel.name().equals(text.trim())) return channel;
        }
        return null;
    }

    /**
     * The channel an order really has: at a table it is always {@link #DINE_IN} (whatever was asked for); with no table it is the
     * one asked for, but never DINE_IN, and {@link #TAKE_AWAY} when none was.
     */
    public static OrderChannel of(OrderChannel asked, Integer tableNumber) {
        if (tableNumber != null) return DINE_IN;
        return asked == null || asked == DINE_IN ? TAKE_AWAY : asked;
    }
}
