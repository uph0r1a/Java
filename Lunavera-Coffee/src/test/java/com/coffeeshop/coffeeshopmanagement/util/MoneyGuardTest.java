package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

/** TODO 2.40: a huge exponent is refused at once instead of freezing the window for seconds. */
public class MoneyGuardTest {

    @Test(timeout = 1000)
    public void hugeExponentIsRefusedQuickly() {
        try {
            Money.toDong(new BigDecimal("1e9999999"));
            fail("must be refused");
        } catch (ArithmeticException expected) {
            // fine
        }
    }

    @Test(timeout = 1000)
    public void tinyExponentIsRefusedQuickly() {
        try {
            Money.toDong(new BigDecimal("1e-9999999"));
            fail("must be refused");
        } catch (ArithmeticException expected) {
            // fine
        }
    }

    @Test
    public void normalAmountsStillWork() {
        assertEquals(25000L, Money.toDong(new BigDecimal("25000")));
        assertEquals(101L, Money.toDong(new BigDecimal("100.5")));
        assertEquals(1_000_000_000_000L, Money.toDong(new BigDecimal("1000000000000")));
    }

    @Test(timeout = 1000)
    public void parserRefusesExponentText() {
        try {
            MoneyParser.parseDong("1e9999999");
            fail("letters are not an amount");
        } catch (IllegalArgumentException expected) {
            // fine
        }
    }
}
