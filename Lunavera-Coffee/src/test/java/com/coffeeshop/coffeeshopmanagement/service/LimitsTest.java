package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

/** TODO 3.59: the ceilings on a price, a stock and a cart line. */
public class LimitsTest {

    @Test
    public void aPriceIsFineUpToTheCeilingAndNotAbove() {
        assertFalse(Limits.priceTooBig(null));
        assertFalse(Limits.priceTooBig(new BigDecimal("25000")));
        assertFalse(Limits.priceTooBig(Limits.MAX_PRICE));
        assertTrue(Limits.priceTooBig(Limits.MAX_PRICE.add(BigDecimal.ONE)));
        assertTrue("25.000 typed as 25.000.000.000 (three extra zeros twice)", Limits.priceTooBig(new BigDecimal("25000000000")));
    }

    @Test
    public void theCeilingIsShownTheWayAmountsAre() {
        assertEquals("100.000.000đ", Limits.maxPriceText());
    }

    @Test
    public void ceilingsAreSensibleNumbers() {
        assertEquals(999, Limits.MAX_LINE_QUANTITY);
        assertEquals(1_000_000, Limits.MAX_STOCK);
    }
}
