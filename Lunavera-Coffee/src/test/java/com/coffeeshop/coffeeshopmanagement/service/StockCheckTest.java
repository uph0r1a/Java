package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.service.StockCheck.Kind;
import com.coffeeshop.coffeeshopmanagement.service.StockCheck.Shortage;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** TODO 3.1 (small version): what the stock re-check says about a cart, with no database. */
public class StockCheckTest {

    private static Product product(int id, String name, int stock, boolean active) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setStock(stock);
        p.setActive(active);
        return p;
    }

    private static OrderItem line(Integer productId, String name, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setProductName(name);
        item.setQuantity(quantity);
        return item;
    }

    private static Map<Integer, Product> fresh(Product... products) {
        Map<Integer, Product> map = new HashMap<>();
        for (Product p : products) map.put(p.getId(), p);
        return map;
    }

    @Test
    public void aCartTheStockCoversHasNoShortage() {
        assertTrue(StockCheck.shortages(List.of(line(1, "Latte", 2), line(2, "Brownie", 1)),
                fresh(product(1, "Latte", 2, true), product(2, "Brownie", 9, true))).isEmpty());
    }

    @Test
    public void theLastItemSoldAtAnotherTableIsNamed() {
        List<Shortage> result = StockCheck.shortages(List.of(line(1, "Latte", 1), line(2, "Brownie", 1)),
                fresh(product(1, "Latte", 0, true), product(2, "Brownie", 5, true)));
        assertEquals(1, result.size());
        assertEquals("Latte", result.get(0).name());
        assertEquals(1, result.get(0).wanted());
        assertEquals(0, result.get(0).available());
        assertEquals(Kind.SHORT, result.get(0).kind());
    }

    @Test
    public void fewerLeftThanTheCartHoldsSaysHowManyAreLeft() {
        Shortage s = StockCheck.shortages(List.of(line(1, "Latte", 4)), fresh(product(1, "Latte", 3, true))).get(0);
        assertEquals(4, s.wanted());
        assertEquals(3, s.available());
    }

    @Test
    public void twoLinesOfOneProductAreAddedUp() {
        // two lines because of different notes: 2 + 2 wanted, 3 in stock: payment would refuse the second line
        List<Shortage> result = StockCheck.shortages(List.of(line(1, "Latte", 2), line(1, "Latte", 2)),
                fresh(product(1, "Latte", 3, true)));
        assertEquals(1, result.size());
        assertEquals(4, result.get(0).wanted());
        assertEquals(3, result.get(0).available());
    }

    @Test
    public void exactlyEnoughIsEnough() {
        assertTrue(StockCheck.shortages(List.of(line(1, "Latte", 3)), fresh(product(1, "Latte", 3, true))).isEmpty());
    }

    @Test
    public void aProductSwitchedOffOrDeletedIsUnavailable() {
        List<Shortage> result = StockCheck.shortages(List.of(line(1, "Latte", 1), line(2, "Old cake", 2)),
                fresh(product(1, "Latte", 50, false)));
        assertEquals(2, result.size());
        assertEquals(Kind.UNAVAILABLE, result.get(0).kind());
        assertEquals(Kind.UNAVAILABLE, result.get(1).kind());
        assertEquals("a deleted product keeps the name from the line", "Old cake", result.get(1).name());
        assertEquals(0, result.get(1).available());
    }

    @Test
    public void severalShortagesComeInCartOrder() {
        List<Shortage> result = StockCheck.shortages(List.of(line(7, "B", 1), line(3, "A", 1)),
                fresh(product(7, "B", 0, true), product(3, "A", 0, true)));
        assertEquals(List.of(7, 3), result.stream().map(Shortage::productId).toList());
    }

    @Test
    public void aNegativeStockCountsAsNothingLeft() {
        assertEquals(0, StockCheck.shortages(List.of(line(1, "Latte", 1)), fresh(product(1, "Latte", -2, true))).get(0).available());
    }

    @Test
    public void aLineWithoutAProductIsIgnoredAndAnEmptyCartIsFine() {
        assertTrue(StockCheck.shortages(List.of(line(null, "Custom item", 3)), Map.of()).isEmpty());
        assertTrue(StockCheck.shortages(List.of(), Map.of()).isEmpty());
    }
}
