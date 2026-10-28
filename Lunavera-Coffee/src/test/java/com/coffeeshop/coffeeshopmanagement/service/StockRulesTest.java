package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 4.5: what may be typed when goods come in. */
public class StockRulesTest {

    @Test
    public void aWholeNumberFromOneIsAccepted() {
        assertEquals(24, StockRules.parseQuantity("24"));
        assertEquals(1500, StockRules.parseQuantity(" 1.500 "));
        assertEquals(Limits.MAX_STOCK, StockRules.parseQuantity(String.valueOf(Limits.MAX_STOCK)));
    }

    @Test
    public void everythingElseIsRefused() {
        assertEquals(-1, StockRules.parseQuantity(null));
        assertEquals(-1, StockRules.parseQuantity(""));
        assertEquals(-1, StockRules.parseQuantity("0"));
        assertEquals(-1, StockRules.parseQuantity("-3"));
        assertEquals(-1, StockRules.parseQuantity("2,5x"));
        assertEquals(-1, StockRules.parseQuantity("abc"));
        assertEquals(-1, StockRules.parseQuantity(String.valueOf(Limits.MAX_STOCK + 1)));
        assertEquals(-1, StockRules.parseQuantity("99999999999999"));
    }

    @Test
    public void theCeilingIsAboutTheStockAfterwards() {
        assertFalse(StockRules.passesCeiling(Limits.MAX_STOCK - 5, 5));
        assertTrue(StockRules.passesCeiling(Limits.MAX_STOCK - 5, 6));
    }

    @Test
    public void textIsOneShortLine() {
        assertNull(StockRules.cleanText(" \n "));
        assertEquals("Nhà cung cấp A", StockRules.cleanText("  Nhà   cung\ncấp A "));
        assertEquals(StockRules.TEXT_MAX, StockRules.cleanText("y".repeat(StockRules.TEXT_MAX + 30)).length());
    }
}
