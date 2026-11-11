package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** TODO 2.91: which lines of a stale screen are missing from the order as it is saved now. */
public class StaleOrderRulesTest {

    private static OrderItem line(Integer productId, String name, int quantity, String note) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setProductName(name);
        item.setQuantity(quantity);
        item.setNote(note);
        return item;
    }

    @Test
    public void nothingIsMissingWhenTheScreenHoldsNoMoreThanTheOrder() {
        assertTrue(StaleOrderRules.missingHere(List.of(line(1, "Latte", 1, null)), List.of(line(1, "Latte", 2, null), line(2, "Cake", 1, null))).isEmpty());
        assertTrue(StaleOrderRules.missingHere(null, null).isEmpty());
    }

    @Test
    public void aLineOnlyTheScreenHasIsListedWithItsQuantity() {
        List<String> missing = StaleOrderRules.missingHere(List.of(line(1, "Latte", 3, null), line(3, "Tea", 1, null)), List.of(line(1, "Latte", 1, null)));
        assertEquals(List.of("2 x Latte", "1 x Tea"), missing);
    }

    @Test
    public void theNoteMakesItAnotherLine() {
        List<String> missing = StaleOrderRules.missingHere(List.of(line(1, "Latte", 1, "less ice")), List.of(line(1, "Latte", 1, null)));
        assertEquals(List.of("1 x Latte (less ice)"), missing);
    }

    @Test
    public void aProductThatWasDeletedIsMatchedByName() {
        assertTrue(StaleOrderRules.missingHere(List.of(line(null, "Old drink", 1, null)), List.of(line(null, "Old drink", 1, null))).isEmpty());
        assertEquals(1, StaleOrderRules.missingHere(List.of(line(null, "Old drink", 2, null)), List.of(line(null, "Old drink", 1, null))).size());
    }
}
