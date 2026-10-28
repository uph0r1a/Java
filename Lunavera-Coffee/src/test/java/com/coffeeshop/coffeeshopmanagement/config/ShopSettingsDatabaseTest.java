package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/** TODO 4.15 on the real (SQLite test) database: the shop name and the "setup pending" mark are kept in {@code app_flags}. */
public class ShopSettingsDatabaseTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @After
    public void putBack() {
        AppFlags.replaceValues(Map.of(ShopSettings.PREFIX, "")); // a blank stored name means "none"
        AppFlags.clear(AppFlags.SETUP_PENDING);
        ShopSettings.resetForTests();
    }

    @Test
    public void theNameIsSavedForTheWholeShopAndReadBackByAFreshStart() {
        ShopSettings.setName("  Quán   Mai  ");
        assertEquals("Quán Mai", ShopSettings.cachedName());
        ShopSettings.resetForTests();                  // as if the program had just started
        assertEquals("Lunavera Coffee", ShopSettings.cachedName());
        assertEquals("Quán Mai", ShopSettings.load());
        assertEquals("Quán Mai", ShopSettings.cachedName());
    }

    @Test
    public void aNewNameReplacesTheOldOneAndThereIsOnlyOne() {
        ShopSettings.setName("First");
        ShopSettings.setName("Second");
        ShopSettings.resetForTests();
        assertEquals("Second", ShopSettings.load());
    }

    @Test
    public void aBlankStoredNameMeansTheDefault() {
        AppFlags.replaceValues(Map.of(ShopSettings.PREFIX, ""));
        ShopSettings.resetForTests();
        assertEquals("Lunavera Coffee", ShopSettings.load());
    }

    @Test
    public void theSetupPendingMarkCanBeSetAndClearedAndSettingItTwiceIsHarmless() {
        assertFalse(AppFlags.has(AppFlags.SETUP_PENDING));
        AppFlags.mark(AppFlags.SETUP_PENDING);
        AppFlags.mark(AppFlags.SETUP_PENDING);
        assertTrue(AppFlags.has(AppFlags.SETUP_PENDING));
        AppFlags.clear(AppFlags.SETUP_PENDING);
        assertFalse(AppFlags.has(AppFlags.SETUP_PENDING));
    }
}
