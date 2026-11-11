package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.PaymentLine;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** TODO 2.102: a bill paid partly with a gift card is not filed as a gift-card bill. */
public class MainPaymentMethodTest {

    private static BigDecimal d(long v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    public void aCardThatPaysPartOfTheBillLeavesTheChosenMethod() {
        // 150.000 bill: 50.000 on a card and 100.000 typed in cash, so nothing is left for the main method
        assertEquals(PaymentMethod.TRANSFER, MainPaymentMethod.effective(PaymentMethod.TRANSFER, true, d(50_000), d(150_000)));
        assertEquals(PaymentMethod.CASH, MainPaymentMethod.effective(PaymentMethod.CASH, true, d(50_000), d(150_000)));
    }

    @Test
    public void cardsThatPayTheWholeBillMakeItAGiftCardPayment() {
        assertEquals(PaymentMethod.GIFT_CARD, MainPaymentMethod.effective(PaymentMethod.CASH, true, d(150_000), d(150_000)));
    }

    @Test
    public void noCardOrUnusableAmountsKeepTheChosenMethod() {
        assertEquals(PaymentMethod.TRANSFER, MainPaymentMethod.effective(PaymentMethod.TRANSFER, true, d(0), d(150_000)));
        assertEquals(PaymentMethod.TRANSFER, MainPaymentMethod.effective(PaymentMethod.TRANSFER, true, null, d(150_000)));
        assertEquals(PaymentMethod.TRANSFER, MainPaymentMethod.effective(PaymentMethod.TRANSFER, false, d(150_000), d(150_000)));
    }

    @Test
    public void cardPlusTransferIsFiledUnderTheBiggerNonCashLine() {
        List<PaymentLine> lines = List.of(new PaymentLine(PaymentMethod.GIFT_CARD, d(50_000), "GC1"), new PaymentLine(PaymentMethod.TRANSFER, d(100_000)));
        PaymentMethod chosen = MainPaymentMethod.effective(PaymentMethod.TRANSFER, true, d(50_000), d(150_000));
        assertEquals(PaymentMethod.TRANSFER, MainPaymentMethod.stored(lines, chosen));
        assertEquals("even if the chosen method has no line", PaymentMethod.TRANSFER,
                MainPaymentMethod.stored(lines, MainPaymentMethod.effective(PaymentMethod.CARD, true, d(50_000), d(150_000))));
    }

    @Test
    public void cashIsNeverTheStoredMethodOfASplit() {
        List<PaymentLine> lines = List.of(new PaymentLine(PaymentMethod.GIFT_CARD, d(50_000), "GC1"), new PaymentLine(PaymentMethod.CASH, d(100_000)));
        assertEquals("by design: card plus cash is filed under the card; the lines say how much of each", PaymentMethod.GIFT_CARD,
                MainPaymentMethod.stored(lines, PaymentMethod.CASH));
    }
}
