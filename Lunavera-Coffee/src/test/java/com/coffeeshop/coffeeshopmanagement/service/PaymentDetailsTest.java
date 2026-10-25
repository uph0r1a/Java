package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** TODO 3.17 and 4.31: the change owed to a cash customer and the cleaning of a typed bank reference. */
public class PaymentDetailsTest {

    @Test
    public void theChangeIsWhatWasHandedOverMinusTheTotal() {
        assertEquals(new BigDecimal("20000"), PaymentDetails.changeFor(new BigDecimal("100000"), new BigDecimal("80000")));
    }

    @Test
    public void exactlyTheTotalGivesNoChangeButIsStillAPayment() {
        assertEquals(0, PaymentDetails.changeFor(new BigDecimal("45000"), new BigDecimal("45000")).signum());
    }

    @Test
    public void lessThanTheTotalIsNotAPayment() {
        assertNull(PaymentDetails.changeFor(new BigDecimal("44999"), new BigDecimal("45000")));
    }

    @Test
    public void nothingHandedOverMeansNoChangeFigure() {
        assertNull(PaymentDetails.changeFor(null, new BigDecimal("45000")));
        assertNull(PaymentDetails.changeFor(new BigDecimal("50000"), null));
    }

    @Test
    public void aReferenceIsTrimmedAndItsSpacingMadeEven() {
        assertEquals("FT26298 123456", PaymentDetails.cleanReference("   FT26298 \t  123456 \n"));
    }

    @Test
    public void aBlankReferenceMeansNone() {
        assertNull(PaymentDetails.cleanReference(null));
        assertNull(PaymentDetails.cleanReference(""));
        assertNull(PaymentDetails.cleanReference(" \t\n "));
    }

    @Test
    public void controlCharactersNeverReachTheDatabaseOrThePaper() {
        assertEquals("AB CD", PaymentDetails.cleanReference("AB\u0000\u0007CD"));
    }

    @Test
    public void aPastedParagraphIsCutToTheLimit() {
        String cut = PaymentDetails.cleanReference("X".repeat(500));
        assertEquals(PaymentDetails.REFERENCE_MAX, cut.length());
    }

    @Test
    public void theCaseOfAReferenceIsKeptAsTyped() {
        assertEquals("Ft26aB", PaymentDetails.cleanReference("Ft26aB"));
    }
}
