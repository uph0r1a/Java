package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Splitting a bill (TODO 4.10, rule D10, my choice until the owner decides): some pieces of the lines of an open order are paid now,
 * by their own payment, and the rest stays open on the table. The pieces are chosen per line (a line of 3 can be split 1 + 2); a split
 * must take at least one piece and leave at least one (taking everything is an ordinary payment); each part is priced at the lines' unit
 * prices, so the two parts add up to the order; a discount, a voucher or points belong to the part they are used on. Pure: no window.
 */
public final class SplitBillRules {

    /** The lines to pay now and the lines that stay on the table; both are new objects, the cart's own lines are not touched. */
    public record Split(List<OrderItem> part, List<OrderItem> remainder) {
    }

    private SplitBillRules() {
    }

    /** What is wrong with {@code take} (line index to pieces) for these lines, as a sentence; null when it is a good split. */
    public static String problem(List<OrderItem> lines, Map<Integer, Integer> take) {
        if (lines == null || lines.isEmpty()) return I18n.t("split.val.nolines");
        int taken = 0;
        int all = 0;
        for (OrderItem line : lines) all += Math.max(line.getQuantity(), 0);
        for (Map.Entry<Integer, Integer> e : take.entrySet()) {
            int index = e.getKey();
            int pieces = e.getValue() == null ? 0 : e.getValue();
            if (index < 0 || index >= lines.size() || pieces < 0 || pieces > lines.get(index).getQuantity()) return I18n.t("split.val.pieces");
            taken += pieces;
        }
        if (taken == 0) return I18n.t("split.val.none");
        if (taken >= all) return I18n.t("split.val.all");
        return null;
    }

    /** The two parts for a good split (see {@link #problem}); an {@link IllegalArgumentException} for a bad one. */
    public static Split split(List<OrderItem> lines, Map<Integer, Integer> take) {
        String problem = problem(lines, take);
        if (problem != null) throw new IllegalArgumentException(problem);
        List<OrderItem> part = new ArrayList<>();
        List<OrderItem> rest = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            OrderItem line = lines.get(i);
            int pieces = take.getOrDefault(i, 0);
            if (pieces > 0) part.add(copy(line, pieces));
            if (line.getQuantity() - pieces > 0) rest.add(copy(line, line.getQuantity() - pieces));
        }
        return new Split(part, rest);
    }

    /** One line with {@code quantity} pieces at the same unit price, product and note. */
    public static OrderItem copy(OrderItem line, int quantity) {
        OrderItem copy = new OrderItem();
        copy.setProductId(line.getProductId());
        copy.setProductName(line.getProductName());
        copy.setQuantity(quantity);
        copy.setUnitPrice(line.getUnitPrice());
        copy.setLineTotal(line.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
        copy.setNote(line.getNote());
        copy.setSizeName(line.getSizeName()); // TODO 4.47: the size and extras go with the pieces that are split off
        copy.setSizePercent(line.getSizePercent());
        copy.setExtras(line.getExtras());
        copy.setOptionCost(line.getOptionCost());
        return copy;
    }

    /** What the lines add up to (unit price x quantity). */
    public static BigDecimal sum(List<OrderItem> lines) {
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderItem line : lines) sum = sum.add(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
        return sum;
    }
}
