package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Voucher;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.Assert.*;

/** TODO 6.78: edges of {@link VoucherRules} that a mutation run (tools/mutation-sweep.py) showed no test noticed. */
public class VoucherRulesEdgesTest {

    private static final LocalDate D1 = LocalDate.of(2026, 1, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 2, 1);

    private static String problem(String kind, Long value, BigDecimal min, LocalDate from, LocalDate to, Integer uses) {
        return VoucherRules.newVoucherProblem("HELLO10", kind, value, min, from, to, uses);
    }

    @Test
    public void aUseIsNoneWhenItHasNoCodeOrNothingTakenOff() {
        assertTrue(VoucherRules.Use.NONE.isNone());
        assertTrue(new VoucherRules.Use(null, BigDecimal.TEN).isNone());
        assertTrue(new VoucherRules.Use("X", null).isNone());
        assertTrue(new VoucherRules.Use("X", BigDecimal.ZERO).isNone());
        assertTrue(new VoucherRules.Use("X", new BigDecimal("-1")).isNone());
        assertFalse(new VoucherRules.Use("X", BigDecimal.ONE).isNone());
    }

    @Test
    public void aCodeIsThreeToFortyAllowedCharacters() {
        assertNull("two is too short", VoucherRules.normalise("AB"));
        assertEquals("ABC", VoucherRules.normalise("abc"));
        assertEquals("A".repeat(40), VoucherRules.normalise("a".repeat(40)));
        assertNull("forty-one is too long", VoucherRules.normalise("A".repeat(41)));
        assertEquals("the ends of each range", "AZ09-_", VoucherRules.normalise("az09-_"));
        assertEquals("ZZZ", VoucherRules.normalise("ZZZ"));
        assertEquals("999", VoucherRules.normalise("999"));
        assertEquals("000", VoucherRules.normalise("000"));
        for (String bad : new String[]{"@AB", "AB[", "A`C", "A{C", "A/C", "A:C", "A.C", "A C", "AB!"}) {
            assertNull(bad, VoucherRules.normalise(bad));
        }
    }

    @Test
    public void oncePerCustomerNeedsACustomerWhoHasNotUsedIt() {
        Voucher once = new Voucher(1, "ONCE", Voucher.AMOUNT, 1000, BigDecimal.ZERO, null, null, null, 0, true, true);
        Voucher many = new Voucher(2, "MANY", Voucher.AMOUNT, 1000, BigDecimal.ZERO, null, null, null, 0, true, false);
        assertEquals(VoucherRules.Problem.OK, VoucherRules.checkCustomer(null, null, false));
        assertEquals(VoucherRules.Problem.OK, VoucherRules.checkCustomer(many, null, true));
        assertEquals(VoucherRules.Problem.NEEDS_CUSTOMER, VoucherRules.checkCustomer(once, null, false));
        assertEquals(VoucherRules.Problem.ONCE_ONLY, VoucherRules.checkCustomer(once, 5, true));
        assertEquals(VoucherRules.Problem.OK, VoucherRules.checkCustomer(once, 5, false));
    }

    @Test
    public void nothingIsTakenOffWhenNothingIsLeftToPay() {
        Voucher v = new Voucher(1, "A", Voucher.AMOUNT, 1000, null, null, null, null, 0, true);
        assertEquals(0, BigDecimal.ZERO.compareTo(VoucherRules.discountFor(v, new BigDecimal("500"), new BigDecimal("500"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(VoucherRules.discountFor(v, new BigDecimal("500"), new BigDecimal("900"))));
        assertEquals(0, new BigDecimal("500").compareTo(VoucherRules.discountFor(v, new BigDecimal("500"), null)));
    }

    @Test
    public void theLimitsOfANewVoucherAreInclusive() {
        assertNull(problem(Voucher.AMOUNT, 1L, null, null, null, null));
        assertNotNull(problem(Voucher.AMOUNT, 0L, null, null, null, null));
        assertNotNull(problem(Voucher.AMOUNT, null, null, null, null, null));
        assertNull(problem(Voucher.PERCENT, 100L, null, null, null, null));
        assertNotNull(problem(Voucher.PERCENT, 101L, null, null, null, null));
        assertNull(problem(Voucher.AMOUNT, VoucherRules.VALUE_MAX, null, null, null, null));
        assertNotNull(problem(Voucher.AMOUNT, VoucherRules.VALUE_MAX + 1, null, null, null, null));
        assertNull(problem(Voucher.AMOUNT, 1000L, BigDecimal.valueOf(VoucherRules.VALUE_MAX), null, null, null));
        assertNotNull(problem(Voucher.AMOUNT, 1000L, BigDecimal.valueOf(VoucherRules.VALUE_MAX + 1), null, null, null));
        assertNotNull(problem(Voucher.AMOUNT, 1000L, new BigDecimal("-1"), null, null, null));
        assertNull(problem(Voucher.AMOUNT, 1000L, BigDecimal.ZERO, null, null, 1));
        assertNotNull(problem(Voucher.AMOUNT, 1000L, null, null, null, 0));
        assertNull(problem(Voucher.AMOUNT, 1000L, null, null, null, VoucherRules.USES_MAX));
        assertNotNull(problem(Voucher.AMOUNT, 1000L, null, null, null, VoucherRules.USES_MAX + 1));
    }

    @Test
    public void aDateRangeMayBeOpenOnOneSideAndMayStartAndEndOnTheSameDay() {
        assertNull(problem(Voucher.AMOUNT, 1000L, null, D1, null, null));
        assertNull(problem(Voucher.AMOUNT, 1000L, null, null, D1, null));
        assertNull(problem(Voucher.AMOUNT, 1000L, null, D1, D1, null));
        assertNull(problem(Voucher.AMOUNT, 1000L, null, D1, D2, null));
        assertNotNull(problem(Voucher.AMOUNT, 1000L, null, D2, D1, null));
    }
}
