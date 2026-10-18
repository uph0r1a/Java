package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

public class MoneyParserTest {

    private static void assertParses(long expected, String text) {
        assertEquals(text, BigDecimal.valueOf(expected), MoneyParser.parseDong(text));
    }

    @Test
    public void readsPlainAndGroupedAmountsTheWayTheyAreShown() {
        assertParses(25000, "25000");
        assertParses(25000, "25.000");
        assertParses(25000, "25,000");
        assertParses(25000, "25 000");
        assertParses(8_000_000, "8.000.000");
        assertParses(25000, "25.000đ");
        assertParses(25000, "25.000 đ");
        assertParses(25000, "25000 VND");
        assertParses(25000, "  25000d ");
        assertParses(0, "0");
    }

    @Test
    public void blankMeansNoValue() {
        assertNull(MoneyParser.parseDong(null));
        assertNull(MoneyParser.parseDong(""));
        assertNull(MoneyParser.parseDong("   "));
    }

    @Test
    public void rejectsAnythingThatIsNotAPlainAmount() {
        for (String bad : new String[]{"1e5", "-5", "12abc", "abc", "đ", "25.5.x", "$100", "+100", "1/2"}) {
            try {
                MoneyParser.parseDong(bad);
                fail("should reject: " + bad);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().length() > 5);
            }
        }
    }

    @Test
    public void rejectsAbsurdlyLargeAmounts() {
        try {
            MoneyParser.parseDong("1".repeat(MoneyParser.MAX_DIGITS + 1));
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        assertEquals(new BigDecimal("999999999999999"), MoneyParser.parseDong("9".repeat(MoneyParser.MAX_DIGITS)));
    }

    // ---- TODO 6.5: parseOptionalDong, moved out of the till screen ----

    @Test
    public void anOptionalAmountThatIsBlankOrMissingIsZero() {
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(MoneyParser.parseOptionalDong(null)));
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(MoneyParser.parseOptionalDong("")));
        assertEquals(0, java.math.BigDecimal.ZERO.compareTo(MoneyParser.parseOptionalDong("   ")));
    }

    @Test
    public void anOptionalAmountThatIsNotANumberIsNullAndAValidOneIsParsed() {
        assertNull(MoneyParser.parseOptionalDong("abc"));
        assertNull(MoneyParser.parseOptionalDong("-5"));
        assertEquals(0, new java.math.BigDecimal("150000").compareTo(MoneyParser.parseOptionalDong("150.000")));
    }
}
