package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.service.StockLevel;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 4.7: what the settings window accepts (no database needed). */
public class SettingsScreenRulesTest {

    private String language;

    @Before
    public void useEnglish() {
        language = I18n.current();
        I18n.setLanguage(I18n.EN);
        ShopSettings.resetForTests();
    }

    @After
    public void restore() {
        I18n.setLanguage(language);
        ShopSettings.resetForTests();
    }

    @Test
    public void aFullyValidFormHasNoProblem() {
        assertNull(ShopSettings.problemWith("Mai Coffee", "12 Hàng Bài, Hà Nội", "+84 24 3825 1234", "15"));
        assertNull("address and phone are optional", ShopSettings.problemWith("Mai", "", "", "0"));
        assertNull(ShopSettings.problemWith("Mai", null, null, "10"));
    }

    @Test
    public void theNameRuleOfTheSetupWindowStillApplies() {
        assertNotNull(ShopSettings.problemWith("  ", "", "", "10"));
        assertNotNull(ShopSettings.problemWith("x".repeat(ShopSettings.NAME_MAX + 1), "", "", "10"));
    }

    @Test
    public void anAddressOverTheLimitIsRefused() {
        assertNull(ShopSettings.problemWith("Mai", "a".repeat(ShopSettings.ADDRESS_MAX), "", "10"));
        assertNotNull(ShopSettings.problemWith("Mai", "a".repeat(ShopSettings.ADDRESS_MAX + 1), "", "10"));
    }

    @Test
    public void aPhoneNumberHasOnlyPhoneCharactersAndALimit() {
        assertNull(ShopSettings.problemWith("Mai", "", "(024) 3825-1234", "10"));
        assertNotNull(ShopSettings.problemWith("Mai", "", "0912 abc 345", "10"));
        assertNotNull(ShopSettings.problemWith("Mai", "", "0".repeat(ShopSettings.PHONE_MAX + 1), "10"));
        assertNotNull("a link or a script is not a phone number", ShopSettings.problemWith("Mai", "", "<b>1</b>", "10"));
    }

    @Test
    public void theLowStockLevelIsAWholeNumberWithinTheStockCeiling() {
        assertNull(ShopSettings.problemWith("Mai", "", "", "0"));
        assertNull(ShopSettings.problemWith("Mai", "", "", " 25 "));
        assertNotNull(ShopSettings.problemWith("Mai", "", "", ""));
        assertNotNull(ShopSettings.problemWith("Mai", "", "", "-1"));
        assertNotNull(ShopSettings.problemWith("Mai", "", "", "2.5"));
        assertNotNull(ShopSettings.problemWith("Mai", "", "", "ten"));
        assertNotNull(ShopSettings.problemWith("Mai", "", "", "99999999"));
    }

    @Test
    public void aRefusedFormChangesNothingInMemory() {
        try {
            ShopSettings.saveAll("Mai", "street", "abc", "5");
            fail("a bad phone number must be refused");
        } catch (IllegalArgumentException expected) {
            assertEquals("Lunavera Coffee", ShopSettings.cachedName());
            assertEquals("", ShopSettings.cachedAddress());
            assertEquals(StockLevel.DEFAULT, StockLevel.defaultLevel());
        }
    }

    @Test
    public void theReceiptLinesAreOnlyTheOnesTheOwnerFilledIn() {
        assertTrue(ShopSettings.receiptDetailLines().isEmpty());
    }

    @Test
    public void theShopLevelChangesWhatCountsAsLowForProductsWithoutTheirOwn() {
        StockLevel.setDefaultLevel(25);
        assertEquals(25, StockLevel.effective(null));
        assertTrue(StockLevel.isLow(25, null));
        assertFalse(StockLevel.isLow(26, null));
        assertTrue(StockLevel.isCritical(12, null));
        assertFalse(StockLevel.isCritical(13, null));
        assertEquals("a product's own level wins", 3, StockLevel.effective(3));
        assertFalse(StockLevel.isLow(8, 3));
    }

    @Test
    public void aLevelOutsideTheAllowedRangeIsIgnored() {
        StockLevel.setDefaultLevel(-1);
        StockLevel.setDefaultLevel(com.coffeeshop.coffeeshopmanagement.service.Limits.MAX_STOCK + 1);
        assertEquals(StockLevel.DEFAULT, StockLevel.defaultLevel());
    }

    // ------------------------------------------------------------------ the tax code (TODO 4.22)

    @Test
    public void theTaxCodeIsOptional() {
        assertNull(ShopSettings.problemWith("Mai", "", "", "", "10"));
        assertNull(ShopSettings.problemWith("Mai", "", "", null, "10"));
        assertNull(ShopSettings.problemWith("Mai", "", "", "   ", "10"));
        assertNull("the four-text form has no tax code to refuse", ShopSettings.problemWith("Mai", "", "", "10"));
    }

    @Test
    public void aTaxCodeIsDigitsWithAtMostOneHyphen() {
        assertNull("a 10-digit code", ShopSettings.problemWith("Mai", "", "", "0312345678", "10"));
        assertNull("a branch code with the hyphen", ShopSettings.problemWith("Mai", "", "", "0312345678-001", "10"));
        assertNull("the same without it", ShopSettings.problemWith("Mai", "", "", "0312345678001", "10"));
        assertNull("a 12-digit personal number", ShopSettings.problemWith("Mai", "", "", "079123456789", "10"));
        assertNull("spaces typed between the digits", ShopSettings.problemWith("Mai", "", "", " 0312 345 678 ", "10"));
        assertNotNull("too short", ShopSettings.problemWith("Mai", "", "", "12345", "10"));
        assertNotNull("too long", ShopSettings.problemWith("Mai", "", "", "12345678901234", "10"));
        assertNotNull("letters", ShopSettings.problemWith("Mai", "", "", "03123456AB", "10"));
        assertNotNull("two hyphens", ShopSettings.problemWith("Mai", "", "", "0312-345-678", "10"));
        assertNotNull("a hyphen with nothing after it", ShopSettings.problemWith("Mai", "", "", "0312345678-", "10"));
        assertNotNull("markup", ShopSettings.problemWith("Mai", "", "", "<b>0312345678</b>", "10"));
    }

    @Test
    public void theTaxCodeIsStoredWithoutSpaces() {
        assertEquals("0312345678", ShopSettings.normalizeTaxCode(" 0312 345\t678 "));
        assertEquals("", ShopSettings.normalizeTaxCode("   "));
        assertNull(ShopSettings.normalizeTaxCode(null));
    }

    @Test
    public void aRefusedTaxCodeChangesNothingInMemory() {
        try {
            ShopSettings.saveAll("Mai", "street", "024", "not-a-code", "5");
            fail("a bad tax code must be refused");
        } catch (IllegalArgumentException expected) {
            assertEquals("", ShopSettings.cachedTaxCode());
            assertEquals("", ShopSettings.cachedAddress());
        }
    }

    @Test
    public void aReceiptHasNoTaxLineUntilTheOwnerTypedACode() {
        assertTrue(com.coffeeshop.coffeeshopmanagement.util.ReceiptText.shopDetailLines().isEmpty());
    }
}
