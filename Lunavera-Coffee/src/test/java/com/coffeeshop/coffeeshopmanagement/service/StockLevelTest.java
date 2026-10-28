package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Product;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 3.21 + 4.19: a reorder level per product, the shop default for a product with none. */
public class StockLevelTest {

    @After
    public void backToTheFactoryLevel() {
        StockLevel.resetForTests();
    }

    @Test
    public void aProductWithNoLevelUsesTheShopDefaultOfTen() {
        assertEquals(10, StockLevel.effective(null));
        assertTrue(StockLevel.isLow(10, null));
        assertFalse(StockLevel.isLow(11, null));
    }

    @Test
    public void aProductWithItsOwnLevelIgnoresTheDefault() {
        assertFalse("a slow seller with level 3 is fine at 8", StockLevel.isLow(8, 3));
        assertTrue(StockLevel.isLow(3, 3));
        assertTrue("a fast seller with level 40 is low at 30", StockLevel.isLow(30, 40));
        assertFalse(StockLevel.isLow(41, 40));
    }

    @Test
    public void levelZeroWarnsOnlyWhenOutOfStock() {
        assertTrue(StockLevel.isLow(0, 0));
        assertFalse(StockLevel.isLow(1, 0));
    }

    @Test
    public void criticalIsHalfTheLevelOrLessAndAlwaysWhenOut() {
        assertTrue(StockLevel.isCritical(5, null));      // the old "5 or fewer" with the default level
        assertFalse(StockLevel.isCritical(6, null));
        assertTrue(StockLevel.isCritical(20, 40));
        assertFalse(StockLevel.isCritical(21, 40));
        assertTrue(StockLevel.isCritical(0, 0));
        assertTrue(StockLevel.isCritical(0, 100));
        assertFalse(StockLevel.isCritical(1, 0));
    }

    @Test
    public void theBoxAcceptsEmptyOrAWholeNumberInRange() {
        assertNull(StockLevel.problem(""));
        assertNull(StockLevel.problem("  "));
        assertNull(StockLevel.problem(null));
        assertNull(StockLevel.problem("0"));
        assertNull(StockLevel.problem(" 25 "));
        assertNull(StockLevel.problem(String.valueOf(Limits.MAX_STOCK)));
        assertEquals("products.val.reorder", StockLevel.problem(String.valueOf(Limits.MAX_STOCK + 1)));
        assertEquals("products.val.reorder", StockLevel.problem("-1"));
        assertEquals("products.val.reorder", StockLevel.problem("2.5"));
        assertEquals("products.val.reorder", StockLevel.problem("abc"));
        assertEquals("products.val.reorder", StockLevel.problem("99999999999"));
    }

    @Test
    public void parseGivesNullForEmptyAndTheNumberOtherwise() {
        assertNull(StockLevel.parse(""));
        assertNull(StockLevel.parse(null));
        assertEquals(Integer.valueOf(0), StockLevel.parse("0"));
        assertEquals(Integer.valueOf(40), StockLevel.parse("40"));
    }

    @Test
    public void aProductKnowsIfItIsLow() {
        Product p = new Product();
        p.setStock(8);
        assertTrue(p.isLowStock());       // default 10
        p.setReorderLevel(3);
        assertFalse(p.isLowStock());
        p.setReorderLevel(null);
        assertTrue(p.isLowStock());
    }
}
