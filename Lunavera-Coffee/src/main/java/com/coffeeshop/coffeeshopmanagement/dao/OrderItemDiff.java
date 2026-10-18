package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * What an autosave of an open order has to change (TODO 6.37). Until now every autosave deleted all the lines of the order and
 * inserted them again, which on a shared MariaDB server means a burst of writes every few seconds per till and a gap in
 * which another till could read an empty order. The plan compares the lines already saved with the lines on screen and only
 * touches the difference. A line is "the same line" when product, name and unit price match, taken in order; so a changed
 * quantity is an update in place and a removed line is one delete. No database here, so it is unit-tested.
 */
public final class OrderItemDiff {

    /** A saved line that gets new quantity and total. */
    public static final class Change {
        public final int rowId;
        public final OrderItem item;

        Change(int rowId, OrderItem item) {
            this.rowId = rowId;
            this.item = item;
        }
    }

    public final List<OrderItem> inserts = new ArrayList<>();
    public final List<Change> updates = new ArrayList<>();
    public final List<Integer> deletes = new ArrayList<>();
    public int unchanged;

    private OrderItemDiff() {
    }

    public boolean isEmpty() {
        return inserts.isEmpty() && updates.isEmpty() && deletes.isEmpty();
    }

    /** {@code saved} are the lines now in the database (with their row ids), {@code wanted} the lines to end up with. */
    public static OrderItemDiff plan(List<OrderItem> saved, List<OrderItem> wanted) {
        OrderItemDiff plan = new OrderItemDiff();
        boolean[] used = new boolean[saved.size()];
        for (OrderItem want : wanted) {
            int match = -1;
            for (int i = 0; i < saved.size(); i++) {
                if (!used[i] && sameLine(saved.get(i), want)) {
                    match = i;
                    break;
                }
            }
            if (match < 0) {
                plan.inserts.add(want);
                continue;
            }
            used[match] = true;
            OrderItem have = saved.get(match);
            if (have.getQuantity() == want.getQuantity() && sameMoney(have.getLineTotal(), want.getLineTotal())) {
                plan.unchanged++;
            } else {
                plan.updates.add(new Change(have.getId(), want));
            }
        }
        for (int i = 0; i < saved.size(); i++) {
            if (!used[i]) plan.deletes.add(saved.get(i).getId());
        }
        return plan;
    }

    private static boolean sameLine(OrderItem a, OrderItem b) {
        return Objects.equals(a.getProductId(), b.getProductId())
                && Objects.equals(a.getProductName(), b.getProductName())
                && sameMoney(a.getUnitPrice(), b.getUnitPrice());
    }

    private static boolean sameMoney(java.math.BigDecimal a, java.math.BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }

    /** For readable test failures. */
    @Override
    public String toString() {
        return "insert " + inserts.size() + ", update " + updates.size() + ", delete " + Collections.unmodifiableList(deletes)
                + ", unchanged " + unchanged;
    }
}
