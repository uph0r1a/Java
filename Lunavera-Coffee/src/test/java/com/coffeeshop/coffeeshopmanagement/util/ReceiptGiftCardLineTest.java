package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.GiftCardUse;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.PaymentLine;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 5.51: the receipt says which gift card paid and what is left on it, with only the last four characters of the code. */
public class ReceiptGiftCardLineTest {

    private static Order giftOrder() {
        Order o = new Order();
        o.setPaymentMethod(PaymentMethod.GIFT_CARD);
        o.setTotal(new BigDecimal("50000"));
        return o;
    }

    @Test
    public void theLineNamesTheCardTailWhatItPaidAndWhatIsLeft() {
        Order o = giftOrder();
        o.setGiftCardUses(List.of(new GiftCardUse("EFGH", new BigDecimal("50000"), new BigDecimal("120000"))));
        List<String> lines = ReceiptText.paymentExtras(o);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0), lines.get(0).startsWith("Gift card ...EFGH: paid "));
        assertTrue(lines.get(0), lines.get(0).contains("50") && lines.get(0).contains("120"));
    }

    @Test
    public void twoCardsGiveTwoLinesInOrder() {
        Order o = giftOrder();
        o.setGiftCardUses(List.of(new GiftCardUse("AAAA", new BigDecimal("30000"), BigDecimal.ZERO),
                new GiftCardUse("BBBB", new BigDecimal("20000"), new BigDecimal("5000"))));
        List<String> lines = ReceiptText.paymentExtras(o);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("AAAA"));
        assertTrue(lines.get(1).contains("BBBB"));
    }

    @Test
    public void theWholeCodeIsNeverOnTheReceipt() {
        assertEquals("EFGH", GiftCardUse.tailOf("GC-ABCD-EFGH"));
        assertEquals("AB", GiftCardUse.tailOf("AB"));
        assertEquals("", GiftCardUse.tailOf(null));
        Order o = giftOrder();
        o.setGiftCardUses(List.of(new GiftCardUse(GiftCardUse.tailOf("GC-ABCD-EFGH"), BigDecimal.TEN, BigDecimal.ONE)));
        assertFalse(String.join("|", ReceiptText.paymentExtras(o)).contains("ABCD"));
    }

    @Test
    public void anOrderWithNoCardsAddsNothing() {
        assertTrue(ReceiptText.paymentExtras(giftOrder()).isEmpty());
    }

    @Test
    public void paidWithGiftCardSeesASplitPaymentToo() {
        Order o = new Order();
        o.setPaymentMethod(PaymentMethod.CASH);
        o.setPaymentLines(List.of(new PaymentLine(PaymentMethod.CASH, new BigDecimal("10000")),
                new PaymentLine(PaymentMethod.GIFT_CARD, new BigDecimal("20000"), "GC-ABCD-EFGH")));
        assertTrue(o.paidWithGiftCard());
        assertTrue(giftOrder().paidWithGiftCard());
        Order cash = new Order();
        cash.setPaymentMethod(PaymentMethod.CASH);
        assertFalse(cash.paidWithGiftCard());
    }
}
