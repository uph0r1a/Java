package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * What the shift-close box says about orders that are still unpaid (TODO 5.48). Closing the shift only counts what was PAID before
 * now; a table that is still open will be paid later and its money falls into the next shift, which the cashier counting the drawer
 * should know before confirming. The box only warns: the cashier can still close.
 *
 * <p>Numbers of tables and orders are passed as text so a big order number is not written with a thousands separator.
 *
 * <p>No JavaFX and no database ({@code ShiftDAO.openOrders} reads the orders); the text is in the language of the screen.
 */
public final class ShiftOpenOrders {

    /** Most entries named in one line; the rest are counted ("and 3 more") so a forgotten pile of orders cannot fill the box. */
    static final int MAX_SHOWN = 8;

    /**
     * One unpaid order.
     *
     * @param tableNumber the table, null for a take-away order
     * @param queueNo     the take-away number of the day, null when it has none (an old order) or the order is on a table
     */
    public record Open(int orderId, Integer tableNumber, Integer queueNo, BigDecimal total) {
    }

    private ShiftOpenOrders() {
    }

    /** The warning for the box: empty when every order is paid. Tables first, then take-away orders, each in the order given. */
    public static String text(List<Open> open) {
        if (open == null || open.isEmpty()) return "";
        List<String> tables = new ArrayList<>();
        List<String> takeAway = new ArrayList<>();
        for (Open order : open) {
            String money = CurrencyUtil.format(order.total() == null ? BigDecimal.ZERO : order.total());
            if (order.tableNumber() != null) {
                tables.add(I18n.t("shift.unpaid.table", String.valueOf(order.tableNumber()), money));
            } else if (order.queueNo() != null) {
                takeAway.add(I18n.t("shift.unpaid.queue", String.valueOf(order.queueNo()), money));
            } else {
                takeAway.add(I18n.t("shift.unpaid.order", String.valueOf(order.orderId()), money));
            }
        }
        List<String> lines = new ArrayList<>();
        if (!tables.isEmpty()) lines.add(I18n.t("shift.unpaid.tables", tables.size(), list(tables)));
        if (!takeAway.isEmpty()) lines.add(I18n.t("shift.unpaid.takeaway", takeAway.size(), list(takeAway)));
        lines.add(I18n.t("shift.unpaid.note"));
        return String.join("\n", lines);
    }

    private static String list(List<String> entries) {
        if (entries.size() <= MAX_SHOWN) return String.join(", ", entries);
        return String.join(", ", entries.subList(0, MAX_SHOWN)) + " " + I18n.t("shift.unpaid.more", entries.size() - MAX_SHOWN);
    }
}
