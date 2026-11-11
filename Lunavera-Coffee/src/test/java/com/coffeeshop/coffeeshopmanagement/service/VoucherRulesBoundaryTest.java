package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Voucher;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.Assert.*;

/** TODO 6.78: tests that came from a mutation run on {@link VoucherRules}: every limit is tried on both sides. No database. */
public class VoucherRulesBoundaryTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 6);

    private static String problem(String code, String kind, Long value, BigDecimal min, LocalDate from, LocalDate to, Integer uses) {
        return VoucherRules.newVoucherProblem(code, kind, value, min, from, to, uses);
    }

    private static Voucher once(boolean oncePerCustomer) {
        return new Voucher(1, "ONCE", Voucher.AMOUNT, 5000, null, null, null, null, 0, true, oncePerCustomer);
    }

    // ---- the code

    @Test
    public void aCodeOfExactlyTheLeastAndTheMostLengthIsKeptAndOneMoreOrLessIsNot() {
        assertEquals("ABC", VoucherRules.normalise("abc"));
        assertNull(VoucherRules.normalise("AB"));
        assertEquals(40, VoucherRules.normalise("A".repeat(40)).length());
        assertNull(VoucherRules.normalise("A".repeat(41)));
        assertEquals(3, VoucherRules.CODE_MIN);
        assertEquals(40, VoucherRules.CODE_MAX);
    }

    @Test
    public void everyCharacterOfTheCodeIsCheckedTheFirstAndTheEdgesOfEachRangeIncluded() {
        assertNull("the first character counts", VoucherRules.normalise("#ABC"));
        assertNull("the last character counts", VoucherRules.normalise("ABC#"));
        assertEquals("ZZZ", VoucherRules.normalise("zzz"));
        assertEquals("999", VoucherRules.normalise("999"));
        assertEquals("A-0_Z9", VoucherRules.normalise("a-0_z9"));
        assertNull(VoucherRules.normalise("AB[")); // the character after Z
        assertNull(VoucherRules.normalise("AB:")); // the character after 9
        assertNull(VoucherRules.normalise("AB/")); // the character before 0
        assertNull(VoucherRules.normalise("AB@")); // the character before A
    }

    // ---- what was used

    @Test
    public void aUseIsNoneWithoutACodeWithoutAValueOrWithAValueOfZero() {
        assertTrue(new VoucherRules.Use(null, new BigDecimal("5000")).isNone());
        assertTrue(new VoucherRules.Use("CODE", null).isNone());
        assertTrue(new VoucherRules.Use("CODE", BigDecimal.ZERO).isNone());
        assertTrue(new VoucherRules.Use("CODE", new BigDecimal("-1")).isNone());
        assertFalse(new VoucherRules.Use("CODE", BigDecimal.ONE).isNone());
        assertFalse(new VoucherRules.Use("CODE", new BigDecimal("5000")).isNone());
        assertTrue(VoucherRules.Use.NONE.isNone());
    }

    // ---- once per customer

    @Test
    public void aOncePerCustomerVoucherNeedsACustomerAndRefusesOneWhoUsedIt() {
        assertEquals(VoucherRules.Problem.NEEDS_CUSTOMER, VoucherRules.checkCustomer(once(true), null, false));
        assertEquals(VoucherRules.Problem.ONCE_ONLY, VoucherRules.checkCustomer(once(true), 7, true));
        assertEquals(VoucherRules.Problem.OK, VoucherRules.checkCustomer(once(true), 7, false));
    }

    @Test
    public void anOrdinaryVoucherOrNoVoucherNeedsNoCustomer() {
        assertEquals(VoucherRules.Problem.OK, VoucherRules.checkCustomer(once(false), null, false));
        assertEquals(VoucherRules.Problem.OK, VoucherRules.checkCustomer(once(false), 7, true));
        assertEquals(VoucherRules.Problem.OK, VoucherRules.checkCustomer(null, null, false));
        assertEquals(VoucherRules.Problem.OK, VoucherRules.checkCustomer(null, 7, true));
    }

    // ---- what an administrator types

    @Test
    public void aVoucherWithEveryLimitExactlyAtItsEdgeIsAccepted() {
        assertNull(problem("EDGE", Voucher.AMOUNT, VoucherRules.VALUE_MAX, new BigDecimal(VoucherRules.VALUE_MAX), DAY, DAY, VoucherRules.USES_MAX));
        assertNull(problem("EDGE", Voucher.PERCENT, 100L, BigDecimal.ZERO, null, null, 1));
        assertNull(problem("EDGE", Voucher.PERCENT, 1L, null, null, null, null));
        assertNull("a single day is a valid range", problem("EDGE", Voucher.AMOUNT, 1L, null, DAY, DAY, null));
    }

    @Test
    public void oneStepPastEachLimitIsRefusedWithItsOwnSentence() {
        assertEquals(I18n.t("voucher.val.value"), problem("EDGE", Voucher.AMOUNT, 0L, null, null, null, null));
        assertEquals(I18n.t("voucher.val.value"), problem("EDGE", Voucher.AMOUNT, null, null, null, null, null));
        assertEquals(I18n.t("voucher.val.percent"), problem("EDGE", Voucher.PERCENT, 101L, null, null, null, null));
        assertEquals(I18n.t("voucher.val.toobig"), problem("EDGE", Voucher.AMOUNT, VoucherRules.VALUE_MAX + 1, null, null, null, null));
        assertEquals(I18n.t("voucher.val.min"), problem("EDGE", Voucher.AMOUNT, 1L, new BigDecimal(VoucherRules.VALUE_MAX + 1), null, null, null));
        assertEquals(I18n.t("voucher.val.min"), problem("EDGE", Voucher.AMOUNT, 1L, new BigDecimal("-1"), null, null, null));
        assertEquals(I18n.t("voucher.val.uses"), problem("EDGE", Voucher.AMOUNT, 1L, null, null, null, 0));
        assertEquals(I18n.t("voucher.val.uses"), problem("EDGE", Voucher.AMOUNT, 1L, null, null, null, VoucherRules.USES_MAX + 1));
    }

    @Test
    public void theLimitsAreTheOnesTheSentencesPromise() {
        assertEquals(1_000_000_000L, VoucherRules.VALUE_MAX);
        assertEquals(1_000_000, VoucherRules.USES_MAX);
    }

    @Test
    public void aRangeWithOnlyOneEndIsFineAndAReversedRangeIsNot() {
        assertNull(problem("EDGE", Voucher.AMOUNT, 1L, null, DAY, null, null));
        assertNull(problem("EDGE", Voucher.AMOUNT, 1L, null, null, DAY, null));
        assertEquals(I18n.t("voucher.val.range"), problem("EDGE", Voucher.AMOUNT, 1L, null, DAY, DAY.minusDays(1), null));
    }
}
