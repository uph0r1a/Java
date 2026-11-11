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
 * touches the difference. A line is "the same line" when product, name, note, options and unit price match, taken in order; so a changed
 * quantity is an update in place and a removed line is one delete. A line that matches nothing exactly but has a saved line of the same
 * product, name, options and price that was not matched either is the SAME LINE WITH A NEW NOTE (TODO 2.100): it is updated in place, so it
 * keeps its row id and its place in the order instead of going to the end. No database here, so it is unit-tested.
 */
public final class OrderItemDiff {

    /** A saved line that gets new quantity, total and note (the note is the same as before unless {@link #noteChanged}). */
    public static final class Change {
        public final int rowId;
        public final OrderItem item;
        /** True when the line was paired with a saved line that differed from it only in the note (TODO 2.100). */
        public final boolean noteChanged;

        Change(int rowId, OrderItem item, boolean noteChanged) {
            this.rowId = rowId;
            this.item = item;
            this.noteChanged = noteChanged;
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
        // First every line that matches exactly, so a loose pairing below can never take the row an exact match would have had.
        int[] paired = new int[wanted.size()];
        for (int w = 0; w < wanted.size(); w++) {
            paired[w] = -1;
            for (int i = 0; i < saved.size(); i++) {
                if (!used[i] && sameLine(saved.get(i), wanted.get(w))) {
                    paired[w] = i;
                    used[i] = true;
                    break;
                }
            }
        }
        // Then, for the lines left over, the first saved line left over that differs only in the note (TODO 2.100).
        boolean[] noteOnly = new boolean[wanted.size()];
        for (int w = 0; w < wanted.size(); w++) {
            if (paired[w] >= 0) continue;
            for (int i = 0; i < saved.size(); i++) {
                if (!used[i] && sameLineExceptNote(saved.get(i), wanted.get(w))) {
                    paired[w] = i;
                    used[i] = true;
                    noteOnly[w] = true;
                    break;
                }
            }
        }
        for (int w = 0; w < wanted.size(); w++) {
            OrderItem want = wanted.get(w);
            if (paired[w] < 0) {
                plan.inserts.add(want);
                continue;
            }
            OrderItem have = saved.get(paired[w]);
            if (!noteOnly[w] && have.getQuantity() == want.getQuantity() && sameMoney(have.getLineTotal(), want.getLineTotal())) {
                plan.unchanged++;
            } else {
                plan.updates.add(new Change(have.getId(), want, noteOnly[w]));
            }
        }
        for (int i = 0; i < saved.size(); i++) {
            if (!used[i]) plan.deletes.add(saved.get(i).getId());
        }
        return plan;
    }

    private static boolean sameLine(OrderItem a, OrderItem b) {
        return sameLineExceptNote(a, b)
                && Objects.equals(com.coffeeshop.coffeeshopmanagement.service.OrderNote.clean(a.getNote()),
                        com.coffeeshop.coffeeshopmanagement.service.OrderNote.clean(b.getNote()));
    }

    /**
     * Same product, name, options and price: the lines can differ in the note (and quantity) only, so one can be turned into the other
     * with an UPDATE of quantity, total and note. Another size, extra or price is another line (TODO 4.47): those columns are not updated.
     */
    private static boolean sameLineExceptNote(OrderItem a, OrderItem b) {
        return Objects.equals(a.getProductId(), b.getProductId())
                && Objects.equals(a.getProductName(), b.getProductName())
                && com.coffeeshop.coffeeshopmanagement.service.LineOptions.same(a, b) // TODO 4.47: another size or extra is another line
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
