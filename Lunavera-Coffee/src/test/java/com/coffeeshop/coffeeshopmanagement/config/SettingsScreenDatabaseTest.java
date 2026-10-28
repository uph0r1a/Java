package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.service.StockLevel;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/** TODO 4.7 on the real (SQLite test) database: the settings are kept in {@code app_flags} and read back by a fresh start. */
public class SettingsScreenDatabaseTest {

    private String language;

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void useEnglish() {
        language = I18n.current();
        I18n.setLanguage(I18n.EN);
        ShopSettings.resetForTests();
    }

    @After
    public void putBack() {
        AppFlags.replaceValues(Map.of(
                ShopSettings.PREFIX, "",
                ShopSettings.ADDRESS_PREFIX, "",
                ShopSettings.PHONE_PREFIX, "",
                ShopSettings.TAX_PREFIX, ""));
        AppFlags.replaceValues(Map.of(ShopSettings.LOW_STOCK_PREFIX, Integer.toString(StockLevel.DEFAULT)));
        ShopSettings.resetForTests();
        I18n.setLanguage(language);
    }

    @Test
    public void everythingIsSavedTogetherAndReadBackByAFreshStart() {
        ShopSettings.saveAll("  Quán   Mai ", " 12 Hàng Bài ", "024 3825 1234", "25");
        ShopSettings.resetForTests(); // as if the program had just started
        assertEquals("Lunavera Coffee", ShopSettings.cachedName());

        ShopSettings.load();
        assertEquals("Quán Mai", ShopSettings.cachedName());
        assertEquals("12 Hàng Bài", ShopSettings.cachedAddress());
        assertEquals("024 3825 1234", ShopSettings.cachedPhone());
        assertEquals(25, StockLevel.defaultLevel());
        assertEquals(java.util.List.of("12 Hàng Bài", "024 3825 1234"), ShopSettings.receiptDetailLines());
    }

    @Test
    public void savingAgainReplacesTheOldValuesAndLeavesOnlyOneOfEach() {
        ShopSettings.saveAll("First", "Old street", "111", "5");
        ShopSettings.saveAll("Second", "", "", "7");
        ShopSettings.resetForTests();
        ShopSettings.load();
        assertEquals("Second", ShopSettings.cachedName());
        assertEquals("a cleared address is really cleared", "", ShopSettings.cachedAddress());
        assertEquals("", ShopSettings.cachedPhone());
        assertEquals(7, StockLevel.defaultLevel());
        assertTrue(ShopSettings.receiptDetailLines().isEmpty());
    }

    @Test
    public void aRefusedFormWritesNothing() {
        ShopSettings.saveAll("Kept", "Kept street", "123", "9");
        try {
            ShopSettings.saveAll("Changed", "x", "not a phone", "4");
            fail("a bad phone number must be refused");
        } catch (IllegalArgumentException expected) {
            // nothing of the second form may have reached the database
        }
        ShopSettings.resetForTests();
        ShopSettings.load();
        assertEquals("Kept", ShopSettings.cachedName());
        assertEquals("Kept street", ShopSettings.cachedAddress());
        assertEquals(9, StockLevel.defaultLevel());
    }

    @Test
    public void theOldShopNameFlagIsUnaffectedByTheNewOnes() {
        ShopSettings.setName("Just a name");   // the setup window's way (TODO 4.15)
        ShopSettings.resetForTests();
        ShopSettings.load();
        assertEquals("Just a name", ShopSettings.cachedName());
        assertEquals("", ShopSettings.cachedAddress());
        assertEquals(StockLevel.DEFAULT, StockLevel.defaultLevel());
    }

    @Test
    public void theDashboardCountFollowsTheShopLevel() {
        ProductDAO products = new ProductDAO();
        int atTen = products.countLowStock(10);
        int atThousand = products.countLowStock(1000);
        assertTrue("a higher level can only add products to the list", atThousand >= atTen);
    }

    // ------------------------------------------------------------------ the tax code (TODO 4.22)

    @Test
    public void theTaxCodeIsSavedReadBackAndPrintedOnTheReceiptLines() {
        ShopSettings.saveAll("Quán Mai", "12 Hàng Bài", "024 3825 1234", " 0312 345 678 ", "10");
        ShopSettings.resetForTests();
        assertEquals("", ShopSettings.cachedTaxCode());

        ShopSettings.load();
        assertEquals("0312345678", ShopSettings.cachedTaxCode());
        assertEquals("the receipt's own language, after the address and the phone",
                java.util.List.of("12 Hàng Bài", "024 3825 1234", "Tax code: 0312345678"),
                com.coffeeshop.coffeeshopmanagement.util.ReceiptText.shopDetailLines());
    }

    @Test
    public void aBlankTaxCodeClearsTheOldOneAndTheLineGoesAway() {
        ShopSettings.saveAll("Mai", "", "", "0312345678", "10");
        ShopSettings.saveAll("Mai", "", "", "", "10");
        ShopSettings.resetForTests();
        ShopSettings.load();
        assertEquals("", ShopSettings.cachedTaxCode());
        assertTrue(com.coffeeshop.coffeeshopmanagement.util.ReceiptText.shopDetailLines().isEmpty());
    }

    @Test
    public void theFourTextFormLeavesTheTaxCodeAlone() {
        ShopSettings.saveAll("Mai", "", "", "0312345678-001", "10");
        ShopSettings.saveAll("Mai Two", "x", "", "12");
        ShopSettings.resetForTests();
        ShopSettings.load();
        assertEquals("Mai Two", ShopSettings.cachedName());
        assertEquals("0312345678-001", ShopSettings.cachedTaxCode());
    }

    @Test
    public void aRefusedTaxCodeWritesNothing() {
        ShopSettings.saveAll("Kept", "Kept street", "123", "0312345678", "9");
        try {
            ShopSettings.saveAll("Changed", "x", "", "abc", "4");
            fail("a bad tax code must be refused");
        } catch (IllegalArgumentException expected) {
            // nothing of the second form may have reached the database
        }
        ShopSettings.resetForTests();
        ShopSettings.load();
        assertEquals("Kept", ShopSettings.cachedName());
        assertEquals("0312345678", ShopSettings.cachedTaxCode());
    }
}
