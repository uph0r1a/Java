package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.78, mutation sweep 2026-11-16 (first batch): the sentences of {@link ExpenseRules} and {@link ClockCheck} were only tested as
 * "there is one", and the rule that other methods' typed amounts must be numbers but the main method's box is ignored had no test of
 * its own.
 */
public class ExpenseClockSplitEdgesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 16);

    @Test
    public void aCategoryIsShownInWordsAndAnUnknownOneAsItIs() {
        for (String code : ExpenseRules.CATEGORIES) {
            String name = ExpenseRules.categoryName(code);
            assertEquals(I18n.t("expense.cat." + code), name);
            assertNotEquals("a category has a name in words", code, name);
            assertFalse(name.isBlank());
        }
        assertEquals("XYZ", ExpenseRules.categoryName("XYZ"));
        assertEquals("null", ExpenseRules.categoryName(null));
    }

    @Test
    public void everyKindOfExpenseProblemHasItsOwnSentence() {
        String amount = I18n.t("expense.val.amount");
        String toobig = I18n.t("expense.val.toobig");
        String category = I18n.t("expense.val.category");
        String date = I18n.t("expense.val.date");
        assertEquals(amount, ExpenseRules.problem(TODAY, null, ExpenseRules.RENT, TODAY));
        assertEquals(amount, ExpenseRules.problem(TODAY, BigDecimal.ZERO, ExpenseRules.RENT, TODAY));
        assertEquals(amount, ExpenseRules.problem(TODAY, new BigDecimal("-5"), ExpenseRules.RENT, TODAY));
        assertEquals("a part of a dong", amount, ExpenseRules.problem(TODAY, new BigDecimal("10.5"), ExpenseRules.RENT, TODAY));
        assertEquals(toobig, ExpenseRules.problem(TODAY, ExpenseRules.MAX_AMOUNT.add(BigDecimal.ONE), ExpenseRules.RENT, TODAY));
        assertEquals(category, ExpenseRules.problem(TODAY, BigDecimal.TEN, "COFFEE", TODAY));
        assertEquals(category, ExpenseRules.problem(TODAY, BigDecimal.TEN, null, TODAY));
        assertEquals(date, ExpenseRules.problem(null, BigDecimal.TEN, ExpenseRules.RENT, TODAY));
        assertEquals(date, ExpenseRules.problem(ExpenseRules.EARLIEST.minusDays(1), BigDecimal.TEN, ExpenseRules.RENT, TODAY));
        assertEquals(date, ExpenseRules.problem(TODAY.plusDays(1), BigDecimal.TEN, ExpenseRules.RENT, TODAY));
        for (String sentence : List.of(amount, toobig, category, date)) assertFalse(sentence.isBlank());
        assertEquals(4, java.util.Set.of(amount, toobig, category, date).size());
    }

    @Test
    public void theEdgesOfAnExpenseAreAllowed() {
        assertNull("the biggest amount", ExpenseRules.problem(TODAY, ExpenseRules.MAX_AMOUNT, ExpenseRules.RENT, TODAY));
        assertNull("one dong", ExpenseRules.problem(TODAY, BigDecimal.ONE, ExpenseRules.RENT, TODAY));
        assertNull("trailing zeros are not a part of a dong", ExpenseRules.problem(TODAY, new BigDecimal("100.00"), ExpenseRules.RENT, TODAY));
        assertNull("the earliest day", ExpenseRules.problem(ExpenseRules.EARLIEST, BigDecimal.TEN, ExpenseRules.RENT, TODAY));
        assertNull("today", ExpenseRules.problem(TODAY, BigDecimal.TEN, ExpenseRules.RENT, TODAY));
    }

    @Test
    public void aClockBehindTheNewestOrderIsWarnedAboutAfterTenMinutes() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 16, 8, 0);
        assertNull(ClockCheck.problem(null, now));
        assertNull(ClockCheck.problem(now, null));
        assertNull("ten minutes behind is still normal", ClockCheck.problem(now, now.plusMinutes(10)));
        assertNull("ahead is fine", ClockCheck.problem(now, now.minusHours(3)));
        String warning = ClockCheck.problem(now, now.plusMinutes(10).plusSeconds(1));
        assertEquals(I18n.t("clock.wrong", "08:00 16/11/2026", "08:10 16/11/2026"), warning);
        assertFalse(warning.isBlank());
    }

    private static SplitPaymentRules.Input split(Map<PaymentMethod, String> texts) {
        return new SplitPaymentRules.Input(new BigDecimal("100000"), PaymentMethod.CARD, true, texts, List.of());
    }

    @Test
    public void anAmountTypedForAnotherMethodMustBeANumber() {
        Map<PaymentMethod, String> texts = new HashMap<>();
        texts.put(PaymentMethod.TRANSFER, "30.000");
        assertTrue(SplitPaymentRules.valid(split(texts)));
        texts.put(PaymentMethod.CASH, "abc");
        assertFalse("cash is not the main method, so its text counts", SplitPaymentRules.valid(split(texts)));
        assertEquals(SplitPaymentRules.Problem.SPLIT, SplitPaymentRules.problem(split(texts)));
    }

    @Test
    public void whatIsTypedInTheMainMethodsOwnBoxIsIgnored() {
        Map<PaymentMethod, String> texts = new HashMap<>();
        texts.put(PaymentMethod.TRANSFER, "30.000");
        texts.put(PaymentMethod.CARD, "abc");
        assertTrue("the main method receives what is left, whatever its box says", SplitPaymentRules.valid(split(texts)));
        assertEquals(new BigDecimal("70000"), SplitPaymentRules.due(split(texts)));
    }

    @Test
    public void boxesLeftOutOrBlankAreNotAnAmountButAtLeastOneIsNeeded() {
        Map<PaymentMethod, String> blank = new HashMap<>();
        blank.put(PaymentMethod.TRANSFER, "  ");
        assertFalse("a ticked split with nothing typed", SplitPaymentRules.valid(split(blank)));
        assertFalse("and with no map at all", SplitPaymentRules.valid(split(null)));
        blank.put(PaymentMethod.MOMO, "100.000");
        assertFalse("an amount as big as the bill leaves the main method nothing", SplitPaymentRules.valid(split(blank)));
        blank.put(PaymentMethod.MOMO, "99.999");
        assertTrue(SplitPaymentRules.valid(split(blank)));
    }
}
