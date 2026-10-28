package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.Assert.*;

/** TODO 4.38 (rule D8): what an expense may be, and how net profit is worked out. No database. */
public class ExpenseRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    public void aNormalExpenseHasNoProblem() {
        assertNull(ExpenseRules.problem(TODAY, d("150000"), ExpenseRules.RENT, TODAY));
        assertNull(ExpenseRules.problem(TODAY.minusDays(40), d("1"), ExpenseRules.OTHER, TODAY));
        assertNull(ExpenseRules.problem(TODAY, ExpenseRules.MAX_AMOUNT, ExpenseRules.WAGES, TODAY));
    }

    @Test
    public void theAmountMustBeWholeDongAboveZeroAndNotAbsurd() {
        assertNotNull(ExpenseRules.problem(TODAY, null, ExpenseRules.RENT, TODAY));
        assertNotNull(ExpenseRules.problem(TODAY, BigDecimal.ZERO, ExpenseRules.RENT, TODAY));
        assertNotNull(ExpenseRules.problem(TODAY, d("-5000"), ExpenseRules.RENT, TODAY));
        assertNotNull("half a dong", ExpenseRules.problem(TODAY, d("1000.5"), ExpenseRules.RENT, TODAY));
        assertNull("1000.00 is whole dong", ExpenseRules.problem(TODAY, d("1000.00"), ExpenseRules.RENT, TODAY));
        assertNotNull(ExpenseRules.problem(TODAY, ExpenseRules.MAX_AMOUNT.add(BigDecimal.ONE), ExpenseRules.RENT, TODAY));
    }

    @Test
    public void theCategoryMustBeOneOfTheSix() {
        assertEquals(6, ExpenseRules.CATEGORIES.size());
        for (String code : ExpenseRules.CATEGORIES) {
            assertTrue(ExpenseRules.isKnownCategory(code));
            assertNull(ExpenseRules.problem(TODAY, d("1000"), code, TODAY));
        }
        assertFalse(ExpenseRules.isKnownCategory("rent"));
        assertFalse(ExpenseRules.isKnownCategory(null));
        assertNotNull(ExpenseRules.problem(TODAY, d("1000"), "BEER", TODAY));
        assertNotNull(ExpenseRules.problem(TODAY, d("1000"), null, TODAY));
    }

    @Test
    public void theDayCannotBeInTheFutureOrAbsurdlyOld() {
        assertNotNull(ExpenseRules.problem(TODAY.plusDays(1), d("1000"), ExpenseRules.RENT, TODAY));
        assertNotNull(ExpenseRules.problem(null, d("1000"), ExpenseRules.RENT, TODAY));
        assertNotNull("year 0026 is a typo", ExpenseRules.problem(LocalDate.of(26, 10, 1), d("1000"), ExpenseRules.RENT, TODAY));
        assertNull(ExpenseRules.problem(ExpenseRules.EARLIEST, d("1000"), ExpenseRules.RENT, TODAY));
    }

    @Test
    public void netProfitIsGrossMinusWasteMinusExpenses() {
        assertEquals(0, d("700000").compareTo(ExpenseRules.netProfit(d("1000000"), d("100000"), d("200000"))));
        assertEquals("a shop can be below zero", 0, d("-150000").compareTo(ExpenseRules.netProfit(d("50000"), d("0"), d("200000"))));
        assertEquals("nothing known counts as zero", 0, d("500").compareTo(ExpenseRules.netProfit(d("500"), null, null)));
        assertEquals(0, d("-300").compareTo(ExpenseRules.netProfit(null, d("100"), d("200"))));
    }
}
