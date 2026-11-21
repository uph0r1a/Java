package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/** TODO 6.78, mutation sweep 2026-11-16 (first batch): the edges of the day-start parser and of the money-size guard. */
public class ParseAndMoneyEdgesTest {

    @Test
    public void aDayStartTakesMinutesUpToFiftyNine() {
        assertEquals(Integer.valueOf(4 * 60 + 59), BusinessDay.parse("4:59"));
        assertNull(BusinessDay.parse("4:60"));
    }

    @Test
    public void aDayStartTakesTimesFromMidnightUpToElevenFiftyNine() {
        assertEquals(Integer.valueOf(0), BusinessDay.parse("0:00"));
        assertEquals(Integer.valueOf(BusinessDay.MAX_START_MINUTES), BusinessDay.parse("11:59"));
        assertNull("noon is too late", BusinessDay.parse("12:00"));
        assertNull(BusinessDay.parse("24:00"));
    }

    @Test
    public void aDayStartIsReadWithOrWithoutALeadingZeroAndSpaces() {
        assertEquals(Integer.valueOf(240), BusinessDay.parse("04:00"));
        assertEquals(Integer.valueOf(270), BusinessDay.parse("  4:30 "));
        assertNull(BusinessDay.parse("4:5"));
        assertNull(BusinessDay.parse("4h30"));
        assertNull(BusinessDay.parse(null));
    }

    @Test
    public void aMoneyAmountOfEighteenDigitsIsAcceptedAndOneOfNineteenIsRefused() {
        assertEquals(999_999_999_999_999_999L, Money.toDong(new BigDecimal("999999999999999999")));
        assertEquals("nineteen digits, of which one is a decimal", 100_000_000_000_000_000L, Money.toDong(new BigDecimal("100000000000000000.0")));
        try {
            Money.toDong(new BigDecimal("1000000000000000000"));
            fail("nineteen digits before the point must be refused");
        } catch (ArithmeticException expected) {
            // refused
        }
        try {
            Money.toDong(new BigDecimal("1000000000000000000.0"));
            fail("twenty digits, one behind the point");
        } catch (ArithmeticException expected) {
            // refused
        }
    }

    @Test
    public void aMoneyAmountWithAHugeScaleIsRefusedBeforeAnyRoundingStarts() {
        assertEquals("a scale of exactly 1000 is rounded as usual", 0L, Money.toDong(new BigDecimal(BigInteger.ONE, 1000)));
        try {
            Money.toDong(new BigDecimal(BigInteger.ONE, 1001));
            fail("a scale of 1001 must be refused");
        } catch (ArithmeticException expected) {
            // refused
        }
    }
}
