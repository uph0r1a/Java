package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** TODO 3.56: the transfer note and the history search agree on what an order reference looks like. */
public class OrderNumberTest {

    @Test
    public void transferNoteIsDhAndSixDigits() {
        assertEquals("DH000012", OrderNumber.transferNote(12));
        assertEquals("DH123456", OrderNumber.transferNote(123456));
        assertEquals("DH1234567", OrderNumber.transferNote(1234567));
    }

    @Test
    public void everyWayOfWritingAnOrderNumberFindsTheSameOrder() {
        for (String typed : new String[]{"12", "#12", "# 12", " #12 ", "DH000012", "dh000012", "Dh 000012", "000012", "#000012"}) {
            assertEquals(typed, Integer.valueOf(12), OrderNumber.parseReference(typed));
        }
    }

    @Test
    public void theNoteRoundTrips() {
        for (int id : new int[]{1, 12, 999, 123456, 999_999_999}) {
            assertEquals(Integer.valueOf(id), OrderNumber.parseReference(OrderNumber.transferNote(id)));
        }
    }

    @Test
    public void textThatIsNoOrderNumberGivesNull() {
        for (String typed : new String[]{null, "", " ", "#", "DH", "0", "#0", "DH000000", "0901", "0901234567", "1234567890",
                "12a", "1 2", "DH12x", "nguyen", "+84901234567"}) {
            assertNull(typed, OrderNumber.parseReference(typed));
        }
    }
}
