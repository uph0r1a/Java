package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Merging the order of one table into another (TODO 4.10, rule D10): the lines of both orders are put together; a line of the same
 * product, with the same note and the same unit price, is counted once with the pieces added (a line without a product, or a different
 * note or price, stays a line of its own). The merged order keeps the customer of the table that stays; if that order has none it takes
 * the other's. Pure: no database, no window.
 */
public final class TableMoveRules {

    private TableMoveRules() {
    }

    /** The lines of {@code kept} followed by those of {@code joined}, same product + note + price added together. */
    public static List<OrderItem> mergeLines(List<OrderItem> kept, List<OrderItem> joined) {
        List<OrderItem> result = new ArrayList<>();
        for (OrderItem line : kept) result.add(SplitBillRules.copy(line, line.getQuantity()));
        for (OrderItem line : joined) {
            OrderItem same = null;
            if (line.getProductId() != null) {
                for (OrderItem have : result) {
                    if (Objects.equals(have.getProductId(), line.getProductId()) && Objects.equals(clean(have.getNote()), clean(line.getNote())) && LineOptions.same(have, line)
                            && have.getUnitPrice().compareTo(line.getUnitPrice()) == 0) {
                        same = have;
                        break;
                    }
                }
            }
            if (same == null) {
                result.add(SplitBillRules.copy(line, line.getQuantity()));
            } else {
                same.setQuantity(same.getQuantity() + line.getQuantity());
                same.setLineTotal(same.getUnitPrice().multiply(java.math.BigDecimal.valueOf(same.getQuantity())));
            }
        }
        return result;
    }

    /** The customer of the merged order: the kept order's, else the joined one's (may be null). */
    public static Integer customerAfterMerge(Integer keptCustomer, Integer joinedCustomer) {
        return keptCustomer != null ? keptCustomer : joinedCustomer;
    }

    private static String clean(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }
}
