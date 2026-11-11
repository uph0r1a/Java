package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Voucher;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.Assert.*;

/** TODO 4.11, rule D6: the pure rules of vouchers and percentage buttons. */
public class VoucherRulesTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 28);

    private static Voucher voucher(String kind, long value, String min, LocalDate from, LocalDate to, Integer max, int used, boolean active) {
        return new Voucher(1, "HELLO10", kind, value, new BigDecimal(min), from, to, max, used, active);
    }

    private static Voucher percent(long value) {
        return voucher(Voucher.PERCENT, value, "0", null, null, null, 0, true);
    }

    @Test
    public void aCodeIsKeptInCapitalsAndOnlyWithLettersDigitsDashAndUnderscore() {
        assertEquals("HELLO-10_X", VoucherRules.normalise("  hello-10_x "));
        assertNull(VoucherRules.normalise("ab"));
        assertNull(VoucherRules.normalise("HELLO 10"));
        assertNull(VoucherRules.normalise("GIẢM10"));
        assertNull(VoucherRules.normalise("A".repeat(41)));
        assertNull(VoucherRules.normalise(null));
        assertNotNull(VoucherRules.normalise("A".repeat(40)));
    }

    @Test
    public void aPercentageIsRoundedDownToWholeDong() {
        assertEquals(0, new BigDecimal("3333").compareTo(VoucherRules.percentOf(new BigDecimal("33333"), 10)));
        assertEquals(0, new BigDecimal("4000").compareTo(VoucherRules.discountFor(percent(10), new BigDecimal("40000"), null)));
        assertEquals(0, new BigDecimal("16666").compareTo(VoucherRules.discountFor(percent(50), new BigDecimal("33333"), null)));
    }

    @Test
    public void anAmountVoucherIsTheAmountButNeverMoreThanWhatIsLeftToPay() {
        Voucher amount = voucher(Voucher.AMOUNT, 50000, "0", null, null, null, 0, true);
        assertEquals(0, new BigDecimal("50000").compareTo(VoucherRules.discountFor(amount, new BigDecimal("80000"), BigDecimal.ZERO)));
        assertEquals("after a typed discount of 40.000 only 40.000 is left", 0,
                new BigDecimal("40000").compareTo(VoucherRules.discountFor(amount, new BigDecimal("80000"), new BigDecimal("40000"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(VoucherRules.discountFor(amount, new BigDecimal("80000"), new BigDecimal("80000"))));
        assertEquals("a 100% voucher on top of a typed discount takes only the rest", 0,
                new BigDecimal("30000").compareTo(VoucherRules.discountFor(percent(100), new BigDecimal("40000"), new BigDecimal("10000"))));
    }

    @Test
    public void aVoucherIsRefusedForEveryReasonItCanBeOutOfDate() {
        BigDecimal bill = new BigDecimal("100000");
        assertEquals(VoucherRules.Problem.OK, VoucherRules.check(percent(10), bill, DAY));
        assertEquals(VoucherRules.Problem.NOT_FOUND, VoucherRules.check(null, bill, DAY));
        assertEquals(VoucherRules.Problem.INACTIVE, VoucherRules.check(voucher(Voucher.PERCENT, 10, "0", null, null, null, 0, false), bill, DAY));
        assertEquals(VoucherRules.Problem.NOT_YET,
                VoucherRules.check(voucher(Voucher.PERCENT, 10, "0", DAY.plusDays(1), null, null, 0, true), bill, DAY));
        assertEquals(VoucherRules.Problem.EXPIRED,
                VoucherRules.check(voucher(Voucher.PERCENT, 10, "0", null, DAY.minusDays(1), null, 0, true), bill, DAY));
        assertEquals("the last day still counts", VoucherRules.Problem.OK,
                VoucherRules.check(voucher(Voucher.PERCENT, 10, "0", DAY, DAY, null, 0, true), bill, DAY));
        assertEquals(VoucherRules.Problem.USED_UP, VoucherRules.check(voucher(Voucher.PERCENT, 10, "0", null, null, 5, 5, true), bill, DAY));
        assertEquals(VoucherRules.Problem.OK, VoucherRules.check(voucher(Voucher.PERCENT, 10, "0", null, null, 5, 4, true), bill, DAY));
        assertEquals(VoucherRules.Problem.TOO_SMALL,
                VoucherRules.check(voucher(Voucher.PERCENT, 10, "150000", null, null, null, 0, true), bill, DAY));
        assertEquals(VoucherRules.Problem.OK,
                VoucherRules.check(voucher(Voucher.PERCENT, 10, "100000", null, null, null, 0, true), bill, DAY));
    }

    @Test
    public void everyRefusalHasASentence() {
        Voucher v = voucher(Voucher.PERCENT, 10, "150000", DAY, DAY, 1, 1, false);
        for (VoucherRules.Problem problem : VoucherRules.Problem.values()) {
            if (problem == VoucherRules.Problem.OK) continue;
            String text = VoucherRules.message(problem, v);
            assertFalse(problem.name(), text == null || text.isBlank() || text.startsWith("!"));
        }
        try {
            VoucherRules.message(VoucherRules.Problem.OK, v);
            fail("OK has nothing to say");
        } catch (IllegalArgumentException expected) {
            // fine
        }
    }

    @Test
    public void aNewVoucherIsCheckedBeforeItIsSaved() {
        assertNull(VoucherRules.newVoucherProblem("HELLO10", Voucher.PERCENT, 10L, BigDecimal.ZERO, null, null, null));
        assertNotNull(VoucherRules.newVoucherProblem("x", Voucher.PERCENT, 10L, BigDecimal.ZERO, null, null, null));
        assertNotNull(VoucherRules.newVoucherProblem("HELLO10", "OTHER", 10L, BigDecimal.ZERO, null, null, null));
        assertNotNull(VoucherRules.newVoucherProblem("HELLO10", Voucher.PERCENT, 0L, BigDecimal.ZERO, null, null, null));
        assertNotNull(VoucherRules.newVoucherProblem("HELLO10", Voucher.PERCENT, null, BigDecimal.ZERO, null, null, null));
        assertNotNull("over 100 percent", VoucherRules.newVoucherProblem("HELLO10", Voucher.PERCENT, 101L, BigDecimal.ZERO, null, null, null));
        assertNull("an amount may be above 100", VoucherRules.newVoucherProblem("HELLO10", Voucher.AMOUNT, 101L, BigDecimal.ZERO, null, null, null));
        assertNotNull(VoucherRules.newVoucherProblem("HELLO10", Voucher.AMOUNT, 2_000_000_000L, BigDecimal.ZERO, null, null, null));
        assertNotNull(VoucherRules.newVoucherProblem("HELLO10", Voucher.AMOUNT, 5L, new BigDecimal("-1"), null, null, null));
        assertNotNull(VoucherRules.newVoucherProblem("HELLO10", Voucher.AMOUNT, 5L, BigDecimal.ZERO, DAY, DAY.minusDays(1), null));
        assertNotNull(VoucherRules.newVoucherProblem("HELLO10", Voucher.AMOUNT, 5L, BigDecimal.ZERO, null, null, 0));
    }

    @Test
    public void thePercentageButtonsAreTheUsualOnes() {
        assertEquals(java.util.List.of(5, 10, 15, 20), VoucherRules.PERCENT_BUTTONS);
    }

    @Test
    public void theReasonNamesTheVoucherAfterTheCashiersOwnReason() {
        assertEquals("khách quen · " + com.coffeeshop.coffeeshopmanagement.util.I18n.t("order.discount.reason.voucher", "HELLO10"),
                VoucherRules.reasonWithVoucher("khách quen", "HELLO10"));
        assertEquals(com.coffeeshop.coffeeshopmanagement.util.I18n.t("order.discount.reason.voucher", "HELLO10"),
                VoucherRules.reasonWithVoucher(null, "HELLO10"));
        assertEquals("khách quen", VoucherRules.reasonWithVoucher("khách quen", null));
    }
}
