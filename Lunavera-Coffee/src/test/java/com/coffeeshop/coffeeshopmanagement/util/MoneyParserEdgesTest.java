package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** TODO 6.78: edges of {@link MoneyParser} that a mutation run (tools/mutation-sweep.py) showed no test noticed. */
public class MoneyParserEdgesTest {

    private static void refused(String text) {
        try {
            MoneyParser.parseDong(text);
            fail("should be refused: " + text);
        } catch (IllegalArgumentException expected) {
            // fine
        }
    }

    @Test
    public void fifteenDigitsAreTheMostInEveryWayOfWritingAnAmount() {
        assertEquals(new BigDecimal("999999999999999"), MoneyParser.parseDong("999999999999999"));
        refused("1000000000000000");
        assertEquals("spoken: 999.999.999 million and 999 thousand", new BigDecimal("999999999999000"), MoneyParser.parseDong("999999999tr999"));
        refused("1000000000tr5");
        assertEquals("with k", new BigDecimal("999999999999000"), MoneyParser.parseDong("999999999999k"));
        refused("1000000000000k");
        assertEquals("zero is never too large", BigDecimal.ZERO, MoneyParser.parseDong("0k"));
    }

    @Test
    public void aLetterIsNeverTakenForASeparator() {
        refused("1a000");
        refused("1x000");
        assertEquals(new BigDecimal("1000"), MoneyParser.parseDong("1.000"));
        assertEquals(new BigDecimal("1000"), MoneyParser.parseDong("1,000"));
    }
}
