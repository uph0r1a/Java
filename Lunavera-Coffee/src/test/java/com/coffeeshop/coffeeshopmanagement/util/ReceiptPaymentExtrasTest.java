package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** TODO 3.17 and 4.31: the receipt lines under the payment method. The receipt is always in English. */
public class ReceiptPaymentExtrasTest {

    private static Order order(PaymentMethod method) {
        Order o = new Order();
        o.setPaymentMethod(method);
        o.setTotal(new BigDecimal("80000"));
        return o;
    }

    @Test
    public void aCashOrderShowsWhatWasHandedOverAndTheChange() {
        Order o = order(PaymentMethod.CASH);
        o.setCashReceived(new BigDecimal("100000"));
        o.setChangeGiven(new BigDecimal("20000"));
        List<String> lines = ReceiptText.paymentExtras(o);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0), lines.get(0).startsWith("Cash received:"));
        assertTrue(lines.get(0), lines.get(0).contains("100"));
        assertTrue(lines.get(1), lines.get(1).startsWith("Change:"));
        assertTrue(lines.get(1), lines.get(1).contains("20"));
    }

    @Test
    public void anOldCashOrderWithNothingRecordedShowsNothing() {
        assertTrue(ReceiptText.paymentExtras(order(PaymentMethod.CASH)).isEmpty());
    }

    @Test
    public void aTransferShowsItsReferenceAsTyped() {
        Order o = order(PaymentMethod.TRANSFER);
        o.setTransferRef("Ft26 298");
        assertEquals(List.of("Reference: Ft26 298"), ReceiptText.paymentExtras(o));
    }

    @Test
    public void aTransferWithoutAReferenceShowsNothing() {
        assertTrue(ReceiptText.paymentExtras(order(PaymentMethod.TRANSFER)).isEmpty());
        Order blank = order(PaymentMethod.TRANSFER);
        blank.setTransferRef("  ");
        assertTrue(ReceiptText.paymentExtras(blank).isEmpty());
    }

    @Test
    public void aCardOrderNeverShowsCashOrReferenceLines() {
        Order o = order(PaymentMethod.CARD);
        o.setCashReceived(new BigDecimal("100000"));
        o.setTransferRef("X1");
        assertTrue(ReceiptText.paymentExtras(o).isEmpty());
    }

    @Test
    public void aWalletPaymentShowsItsTransactionCodeLikeATransfer() { // TODO 4.34
        for (PaymentMethod wallet : PaymentMethod.eWallets()) {
            Order o = order(wallet);
            assertTrue(wallet.name(), ReceiptText.paymentExtras(o).isEmpty());
            o.setTransferRef("MM 123456");
            assertEquals(wallet.name(), List.of("Reference: MM 123456"), ReceiptText.paymentExtras(o));
        }
    }
}
