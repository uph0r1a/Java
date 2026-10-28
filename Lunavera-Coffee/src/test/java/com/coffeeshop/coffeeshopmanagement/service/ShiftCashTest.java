package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

/** TODO 4.6: the cash arithmetic of closing a shift. */
public class ShiftCashTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    public void expectedIsTheFloatPlusCashSales() {
        assertEquals(0, d("1300000").compareTo(ShiftCash.expected(d("500000"), d("800000"))));
    }

    @Test
    public void differenceIsCountedMinusExpected() {
        assertEquals(0, d("-20000").compareTo(ShiftCash.difference(d("1280000"), d("1300000"))));
        assertEquals(0, d("5000").compareTo(ShiftCash.difference(d("1305000"), d("1300000"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(ShiftCash.difference(d("1300000"), d("1300000"))));
    }

    @Test
    public void missingNumbersCountAsZero() {
        assertEquals(0, d("100").compareTo(ShiftCash.expected(null, d("100"))));
        assertEquals(0, d("-100").compareTo(ShiftCash.difference(null, d("100"))));
    }

    @Test
    public void expectedTakesOffTheCashTakenOutOfTheDrawer() { // TODO 4.38
        assertEquals(0, d("1150000").compareTo(ShiftCash.expected(d("500000"), d("800000"), d("150000"))));
        assertEquals("no cash out is the old arithmetic", 0, d("1300000").compareTo(ShiftCash.expected(d("500000"), d("800000"), null)));
        assertEquals(0, d("-20000").compareTo(ShiftCash.difference(d("1130000"), ShiftCash.expected(d("500000"), d("800000"), d("150000")))));
    }
}
