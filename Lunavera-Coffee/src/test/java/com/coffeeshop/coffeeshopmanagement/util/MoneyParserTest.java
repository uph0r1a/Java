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

    // ---- TODO 2.77: a decimal amount is refused, not read as a number 10 or 100 times too big ----

    private static void refused(String text) {
        try {
            MoneyParser.parseDong(text);
            fail("should be refused: " + text);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("số thập phân"));
        }
        assertNull("the optional form says 'not valid' with null: " + text, MoneyParser.parseOptionalDong(text));
    }

    @Test
    public void everyDecimalWrittenAmountOfTheReportIsRefused() {
        for (String text : new String[]{"25.5", "25,5", "10.50", "1,234.5", "25,000.50", "1.000,5", "1,2,3"}) {
            refused(text);
        }
    }

    @Test
    public void aSeparatorWithoutGroupsOfThreeIsRefused() {
        for (String text : new String[]{"1250.000", "12.34.567", "1..000", ".500", "500.", "1.00", "1.0000", "0.500", "0,500", "1 00"}) {
            refused(text);
        }
    }

    @Test
    public void mixedSeparatorKindsAreRefused() {
        refused("1.000,000");
        refused("1,000.000");
        refused("1 000.000");
    }

    @Test
    public void properThousandsSeparatorsStillWork() {
        assertEquals(0, new BigDecimal("25000").compareTo(MoneyParser.parseDong("25.000")));
        assertEquals(0, new BigDecimal("1250000").compareTo(MoneyParser.parseDong("1,250,000")));
        assertEquals(0, new BigDecimal("8000000").compareTo(MoneyParser.parseDong("8 000 000")));
        assertEquals(0, new BigDecimal("8000000").compareTo(MoneyParser.parseDong("8\u00a0000\u00a0000")));
        assertEquals(0, new BigDecimal("50000").compareTo(MoneyParser.parseDong("50.000đ")));
        assertEquals(0, new BigDecimal("50000").compareTo(MoneyParser.parseDong("50.000 VND")));
        assertEquals(0, new BigDecimal("999").compareTo(MoneyParser.parseDong("999")));
        assertEquals(0, new BigDecimal("1000").compareTo(MoneyParser.parseDong("1.000")));
        assertEquals(0, new BigDecimal("0").compareTo(MoneyParser.parseDong("0")));
        assertEquals(0, new BigDecimal("150000").compareTo(MoneyParser.parseDong("150000")));
    }

    @Test
    public void theRealDongSignIsAccepted() {
        // TODO 2.80
        assertEquals(new java.math.BigDecimal(10000), MoneyParser.parseDong("10000₫"));
        assertEquals(new java.math.BigDecimal(10000), MoneyParser.parseDong("₫10000"));
        assertEquals(new java.math.BigDecimal(10000), MoneyParser.parseDong("10 000 ₫"));
        assertEquals(new java.math.BigDecimal(10000), MoneyParser.parseDong("₫ 10.000"));
        assertEquals(new java.math.BigDecimal(10000), MoneyParser.parseDong("10.000đ"));
        assertEquals(new java.math.BigDecimal(10000), MoneyParser.parseDong("10.000 VNĐ"));
        assertEquals(new java.math.BigDecimal(10000), MoneyParser.parseDong("10.000vnd"));
        for (String bad : new String[] {"₫₫", "₫", "10₫5", "abc₫"}) {
            try {
                MoneyParser.parseDong(bad);
                fail("must be refused: " + bad);
            } catch (IllegalArgumentException expected) {
                // fine
            }
        }
    }

    /** TODO 5.33: the way amounts are written in a Vietnamese shop. */
    @Test
    public void readsShopShorthand() {
        assertParses(50_000, "50k");
        assertParses(50_000, "50K");
        assertParses(50_000, "50 k");
        assertParses(50_000, "50 nghìn");
        assertParses(50_000, "50 NGHÌN");
        assertParses(50_000, "50 ngàn");
        assertParses(50_000, "50 nghin");
        assertParses(1_000_000, "1tr");
        assertParses(1_000_000, "1 triệu");
        assertParses(1_000_000, "1 trieu");
        assertParses(1_500_000, "1,5tr");
        assertParses(1_500_000, "1.5tr");
        assertParses(1_250_000, "1,25 triệu");
        assertParses(2_500, "2,5k");
        assertParses(50_000, "50kđ");
        assertParses(50_000, "50k đ");
        assertParses(50_000, "50k VND");
        assertParses(50_000, "50 nghi\u0300n"); // typed with a combining accent
        assertParses(0, "0k");
    }

    @Test
    public void shorthandStaysStrict() {
        for (String bad : new String[]{"1.500k", "1,500k", "1.000.000k", "1 000k", "k", "tr", "triệu", "1,234tr", "5,k", "-5k", "5kk",
                "1e3k", "5 x k", "abc k", "1/2k"}) {
            try {
                MoneyParser.parseDong(bad);
                fail("should be refused: " + bad);
            } catch (IllegalArgumentException expected) {
                // fine
            }
        }
    }

    @Test
    public void shorthandKeepsTheDigitLimit() {
        try {
            MoneyParser.parseDong("999999999999999tr");
            fail("a result of more than 15 digits is refused");
        } catch (IllegalArgumentException expected) {
            // fine
        }
        assertNull(MoneyParser.parseOptionalDong("1.500k"));
        assertEquals(BigDecimal.valueOf(100_000), MoneyParser.parseOptionalDong("100k"));
    }
}
