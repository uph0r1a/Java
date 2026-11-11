package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

/** TODO 6.78: tests that came from a mutation run on {@link MoneyParser}: every shorthand word, the size limit on each path, the separators. */
public class MoneyParserBoundaryTest {

    private static void refused(String text) {
        try {
            MoneyParser.parseDong(text);
            fail("should be refused: " + text);
        } catch (IllegalArgumentException expected) {
            // refused, as it should be
        }
    }

    private static BigDecimal dong(String text) {
        return MoneyParser.parseDong(text);
    }

    @Test
    public void everyWordForAThousandMeansAThousandAndEveryWordForAMillionAMillion() {
        for (String word : new String[]{"k", "K", "ngh\u00ecn", "nghin", "ng\u00e0n", "ngan", "NGH\u00ccN"}) {
            assertEquals(word, 0, new BigDecimal("5000").compareTo(dong("5" + word)));
            assertEquals(word + " with a space", 0, new BigDecimal("5000").compareTo(dong("5 " + word)));
        }
        for (String word : new String[]{"tri\u1ec7u", "trieu", "tr", "TR"}) {
            assertEquals(word, 0, new BigDecimal("2000000").compareTo(dong("2" + word)));
            assertEquals(word + " with a space", 0, new BigDecimal("2000000").compareTo(dong("2 " + word)));
        }
    }

    @Test
    public void millionsThenDigitsReadTheRestAsThousandsAtTheFinest() {
        assertEquals(0, new BigDecimal("1500000").compareTo(dong("1tr5")));
        assertEquals(0, new BigDecimal("2350000").compareTo(dong("2tr350")));
        assertEquals(0, new BigDecimal("1050000").compareTo(dong("1tr05")));
        refused("1tr5000"); // four digits after the word: finer than a thousand
    }

    @Test
    public void theSizeLimitHoldsOnEveryPathAndTheLastAllowedSizeIsStillAccepted() {
        int max = MoneyParser.MAX_DIGITS;
        // plain digits
        assertEquals(max, dong("9".repeat(max)).precision());
        refused("9".repeat(max + 1));
        // a number and the word for a thousand: the result has three digits more
        assertEquals(max, dong("9".repeat(max - 3) + "k").precision());
        refused("9".repeat(max - 2) + "k");
        // a number and the word for a million
        assertEquals(max, dong("9".repeat(max - 6) + "tr").precision());
        refused("9".repeat(max - 5) + "tr");
        // millions then digits: the millions part is max - 6 digits when the rest is thousands
        assertEquals(max, dong("9".repeat(max - 6) + "tr5").precision());
        refused("9".repeat(max - 5) + "tr5");
    }

    @Test
    public void onlyADotACommaASpaceOrANonBreakingSpaceSeparatesThousands() {
        assertEquals(0, new BigDecimal("25000").compareTo(dong("25.000")));
        assertEquals(0, new BigDecimal("1250000").compareTo(dong("1,250,000")));
        assertEquals(0, new BigDecimal("8000000").compareTo(dong("8 000 000")));
        assertEquals("a non-breaking space counts as a space", 0, new BigDecimal("8000000").compareTo(dong("8\u00a0000\u00a0000")));
        refused("12a345");
        refused("25-000");
        refused("25_000");
        refused("25/000");
        refused("1.000,000"); // two kinds of separator
        refused("1 000.000");
    }

    @Test
    public void aNumberWithADecimalBeforeAWordIsKeptWhenItMakesWholeDong() {
        assertEquals(0, new BigDecimal("1500").compareTo(dong("1,5k")));
        assertEquals(0, new BigDecimal("1250").compareTo(dong("1.25k")));
        assertEquals(0, new BigDecimal("1500000").compareTo(dong("1,5tr")));
        refused("1.500k"); // thousands separator before a word is unclear
        refused("1,234k"); // three decimals
    }
}
