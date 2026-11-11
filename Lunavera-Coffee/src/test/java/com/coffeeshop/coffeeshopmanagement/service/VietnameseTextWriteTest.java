package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.TextNormalizer;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

/**
 * TODO 3.18: whatever a person types, the text that is stored has its accents in the composed form, in every place that keeps a
 * note, a reason, a description or a name. "Trà" typed as T, r, a plus a separate accent mark and typed as T, r, à are the same word.
 */
public class VietnameseTextWriteTest {

    private static final String SEPARATE = "Tra\u0300 su\u031b\u0303a"; // "Trà sữa" with every accent as its own mark
    private static final String COMPOSED = "Tr\u00e0 s\u1eefa";

    @Test
    public void theTwoSpellingsAreDifferentTextUntilCleaned() {
        assertEquals(false, SEPARATE.equals(COMPOSED));
    }

    @Test
    public void notesReasonsAndSuppliersOfTheStockLogAndTheExpenses() {
        assertEquals(COMPOSED, StockRules.cleanText(SEPARATE));
        assertEquals(COMPOSED, StockRules.cleanText("  " + SEPARATE + "\n"));
    }

    @Test
    public void theNoteOnAnOrderLine() {
        assertEquals(COMPOSED, OrderNote.clean(SEPARATE));
    }

    @Test
    public void theReasonOfACancelAndOfAnAuditLine() {
        assertEquals(COMPOSED, AuditRules.clip(SEPARATE, 100));
    }

    @Test
    public void theNameAndUnitOfAnIngredient() {
        assertEquals(COMPOSED, IngredientRules.clean(SEPARATE));
        assertEquals("c\u00e1i", IngredientRules.clean(" ca\u0301i "));
    }

    @Test
    public void theReasonForADiscount() {
        assertEquals(COMPOSED, DiscountPolicy.reasonToStore(new BigDecimal("5000"), SEPARATE));
        assertEquals(COMPOSED, DiscountPolicy.reasonToStore(new BigDecimal("5000"), "  " + SEPARATE + "  "));
    }

    @Test
    public void composedTextIsNotChangedAnyFurther() {
        assertEquals(COMPOSED, StockRules.cleanText(COMPOSED));
        assertEquals(COMPOSED, OrderNote.clean(COMPOSED));
        assertEquals(COMPOSED, AuditRules.clip(COMPOSED, 100));
        assertEquals(COMPOSED, TextNormalizer.nfc(COMPOSED));
    }

    @Test
    public void theLengthLimitCountsTheComposedLetters() {
        // 60 decomposed letters are 120 characters; stored composed they are 60, so a limit of 100 must not cut them
        String sixty = "a\u0300".repeat(60);
        assertEquals(60, StockRules.cleanText(sixty).length());
        assertEquals(60, AuditRules.clip(sixty, 100).length());
    }
}
