package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 4.42: what a product code / barcode may be, with no screen and no database. */
public class ProductCodeTest {

    @Test
    public void aCodeIsTrimmedAndStoredInCapitals() {
        assertEquals("CF01", ProductCode.clean("  cf01 \n"));
        assertEquals("8934563138165", ProductCode.clean("8934563138165\r\n")); // a scanner's line break
    }

    @Test
    public void nothingMeansNoCode() {
        assertNull(ProductCode.clean(null));
        assertNull(ProductCode.clean("   "));
        assertNull(ProductCode.problem(""));
        assertFalse(ProductCode.looksLikeCode("  "));
    }

    @Test
    public void lettersDigitsAndFewSignsAreFine() {
        assertNull(ProductCode.problem("CF-01.a/b_2"));
        assertTrue(ProductCode.looksLikeCode("8934563138165"));
    }

    @Test
    public void spacesInsideAndOtherSignsAreRefused() {
        assertEquals("products.val.code.chars", ProductCode.problem("CF 01"));
        assertEquals("products.val.code.chars", ProductCode.problem("CF#01"));
        assertEquals("products.val.code.chars", ProductCode.problem("cà-phê")); // accents are not scanner text
    }

    @Test
    public void aTooLongCodeIsRefused() {
        assertNull(ProductCode.problem("1".repeat(ProductCode.MAX_LENGTH)));
        assertEquals("products.val.code.long", ProductCode.problem("1".repeat(ProductCode.MAX_LENGTH + 1)));
    }
}
