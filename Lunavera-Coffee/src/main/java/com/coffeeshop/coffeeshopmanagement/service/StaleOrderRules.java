package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * When a till is told that its open order was changed on another till (TODO 2.91): which of the lines on its own screen are not in
 * the order as it is saved now, so the cashier can enter them again if they are still wanted. Pure, so it is unit-tested.
 */
public final class StaleOrderRules {

    private StaleOrderRules() {
    }

    /**
     * The pieces this screen shows beyond what the order holds, one line each ("2 x Latte (less ice)"), in the order of the screen. A
     * line is the same line when it has the same product (or the same name when the product is gone) and the same note.
     */
    public static List<String> missingHere(List<OrderItem> onScreen, List<OrderItem> saved) {
        Map<String, Integer> have = new LinkedHashMap<>();
        for (OrderItem line : saved == null ? List.<OrderItem>of() : saved) {
            have.merge(key(line), line.getQuantity(), Integer::sum);
        }
        Map<String, Integer> want = new LinkedHashMap<>();
        Map<String, OrderItem> first = new LinkedHashMap<>();
        for (OrderItem line : onScreen == null ? List.<OrderItem>of() : onScreen) {
            want.merge(key(line), line.getQuantity(), Integer::sum);
            first.putIfAbsent(key(line), line);
        }
        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : want.entrySet()) {
            int extra = entry.getValue() - have.getOrDefault(entry.getKey(), 0);
            if (extra > 0) {
                OrderItem line = first.get(entry.getKey());
                String note = line.getNote() == null ? "" : line.getNote().trim();
                missing.add(extra + " x " + line.getProductName() + (note.isEmpty() ? "" : " (" + note + ")"));
            }
        }
        return missing;
    }

    private static String key(OrderItem line) {
        String note = line.getNote() == null ? "" : line.getNote().trim();
        return (line.getProductId() != null ? "#" + line.getProductId() : "n:" + Objects.toString(line.getProductName(), "")) + "|" + note;
    }
}
