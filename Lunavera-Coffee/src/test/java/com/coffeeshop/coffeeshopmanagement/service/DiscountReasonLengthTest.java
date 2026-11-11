package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** TODO 3.86: the saved discount reason must fit orders.discount_reason (VARCHAR(255) on MariaDB, strict mode refuses longer text). */
public class DiscountReasonLengthTest {

    private static String of(int n) { return "a".repeat(n); }

    @Test
    public void theLongestCashierReasonWithVoucherPointsAndPromotionsFitsTheColumn() {
        String manual = of(DiscountPolicy.REASON_MAX_LENGTH);
        String saved = CheckoutService.joinedReason(manual, of(40), 999_999_999, List.of("Gio vang", "Mua 2 tang 1"));
        assertNotNull(saved);
        assertTrue("length " + saved.length(), saved.length() <= DiscountPolicy.COLUMN_MAX_LENGTH);
    }

    @Test
    public void theNotesSurviveAndTheCashiersReasonIsTheOneThatIsShortened() {
        String saved = CheckoutService.joinedReason(of(200), of(40), 999_999_999, null);
        assertTrue(saved.length() <= DiscountPolicy.COLUMN_MAX_LENGTH);
        assertTrue(saved.contains(of(40)));            // the voucher code is kept whole
        assertTrue(saved.contains("999999999"));       // so are the points
        assertTrue(saved.startsWith("aaaa"));          // and some of the reason is still there
    }

    @Test
    public void aShortTextIsNotChanged() {
        String saved = CheckoutService.joinedReason("regular customer", "HELLO10", 0, null);
        assertTrue(saved.startsWith("regular customer"));
        assertTrue(saved.contains("HELLO10"));
        assertEquals(saved, CheckoutService.joinedReason("regular customer", "HELLO10", 0, null));
    }

    @Test
    public void noReasonAndNoNotesIsNothing() {
        assertNull(CheckoutService.joinedReason(null, null, 0, null));
    }

    @Test
    public void notesAloneThatAreTooLongAreCutAtTheColumn() {
        String saved = CheckoutService.joinedReason(null, of(40), 5, List.of(of(300)));
        assertEquals(DiscountPolicy.COLUMN_MAX_LENGTH, saved.length());
    }
}
