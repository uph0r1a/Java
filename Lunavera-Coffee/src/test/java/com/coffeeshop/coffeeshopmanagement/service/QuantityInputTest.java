package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.OptionalInt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** TODO 5.27: what the typed quantity box accepts. */
public class QuantityInputTest {

    @Test
    public void onlyDigitsStayAndOnlyThree() {
        assertEquals("12", QuantityInput.sanitize("1a2"));
        assertEquals("123", QuantityInput.sanitize("12345"));
        assertEquals("55", QuantityInput.sanitize("-5.5"));
        assertEquals("", QuantityInput.sanitize(null));
        assertEquals("a letter-digit of another script is not a digit here", "", QuantityInput.sanitize("٣"));
    }

    @Test
    public void aTypedNumberIsParsed() {
        assertEquals(OptionalInt.of(12), QuantityInput.parse("12"));
        assertEquals(OptionalInt.of(12), QuantityInput.parse("  12 "));
        assertEquals(OptionalInt.of(7), QuantityInput.parse("007"));
        assertEquals("0 means take the line off", OptionalInt.of(0), QuantityInput.parse("0"));
        assertEquals(OptionalInt.of(999), QuantityInput.parse("999"));
    }

    @Test
    public void nothingUsableGivesNoNumber() {
        assertFalse(QuantityInput.parse("").isPresent());
        assertFalse(QuantityInput.parse("   ").isPresent());
        assertFalse(QuantityInput.parse(null).isPresent());
        assertFalse(QuantityInput.parse("1000").isPresent());
        assertFalse(QuantityInput.parse("1.5").isPresent());
        assertFalse(QuantityInput.parse("-3").isPresent());
        assertFalse(QuantityInput.parse("x").isPresent());
        assertTrue(QuantityInput.parse("5").isPresent());
    }
}
