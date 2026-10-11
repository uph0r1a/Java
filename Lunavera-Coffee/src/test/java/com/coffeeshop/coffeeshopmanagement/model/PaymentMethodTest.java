package com.coffeeshop.coffeeshopmanagement.model;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PaymentMethodTest {

    @Test
    public void bankTransferIsAMethodWithItsOwnLabel() {
        assertEquals("Chuyển khoản", PaymentMethod.TRANSFER.label());
        assertEquals("Tiền mặt", PaymentMethod.CASH.label());
        assertEquals("Thẻ", PaymentMethod.CARD.label());
    }

    @Test
    public void everyMethodHasADifferentNonEmptyLabel() {
        Set<String> labels = new HashSet<>();
        for (PaymentMethod method : PaymentMethod.values()) {
            assertTrue(!method.label().isBlank());
            labels.add(method.label());
        }
        assertEquals(PaymentMethod.values().length, labels.size());
    }

    @Test
    public void transferIsReadBackFromTheDatabaseTextAndOldValuesStillWork() {
        assertEquals(PaymentMethod.TRANSFER, PaymentMethod.fromDb("TRANSFER"));
        assertEquals(PaymentMethod.TRANSFER, PaymentMethod.fromDb(" transfer "));
        assertEquals(PaymentMethod.CASH, PaymentMethod.fromDb("CASH"));
        assertEquals(PaymentMethod.CARD, PaymentMethod.fromDb("CARD"));
        assertEquals("what is written to the column is the enum name", "TRANSFER", PaymentMethod.TRANSFER.name());
    }

    @Test
    public void unknownOrMissingValuesReadAsNone() {
        assertNull(PaymentMethod.fromDb(null));
        assertNull(PaymentMethod.fromDb(""));
        assertNull(PaymentMethod.fromDb("BITCOIN"));
    }

    @Test
    public void labelOfShowsADashWhenThereIsNoMethod() {
        assertEquals("-", PaymentMethod.labelOf(null));
        assertEquals("Chuyển khoản", PaymentMethod.labelOf(PaymentMethod.TRANSFER));
    }
}
