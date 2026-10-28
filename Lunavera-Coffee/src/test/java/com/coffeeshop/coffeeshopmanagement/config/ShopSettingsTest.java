package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 4.15: the shop name's rules (no database needed). */
public class ShopSettingsTest {

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
    public void untilTheOwnerSetsOneTheNameIsTheDefaultAndNothingTouchesTheDatabase() {
        assertEquals("Lunavera Coffee", ShopSettings.cachedName());
    }

    @Test
    public void theNameIsCleanedLikeOtherNames() {
        assertEquals("Quán Cà Phê Mai", ShopSettings.clean("   Quán   Cà  Phê Mai \t"));
        assertEquals("", ShopSettings.clean(null));
        // an accent typed as a letter plus a combining mark is stored as the one-character form
        assertEquals(java.text.Normalizer.normalize("Cà", java.text.Normalizer.Form.NFC),
                ShopSettings.clean(java.text.Normalizer.normalize("Cà", java.text.Normalizer.Form.NFD)));
    }

    @Test
    public void aBlankOrTooLongNameIsRefused() {
        assertNotNull(ShopSettings.problem(""));
        assertNotNull(ShopSettings.problem(null));
        assertNull(ShopSettings.problem("Mai"));
        assertNull(ShopSettings.problem("x".repeat(ShopSettings.NAME_MAX)));
        assertNotNull(ShopSettings.problem("x".repeat(ShopSettings.NAME_MAX + 1)));
    }

    @Test
    public void aRefusedNameIsNeverSavedOrRemembered() {
        try {
            ShopSettings.setName("   ");
            fail("a blank name must be refused");
        } catch (IllegalArgumentException expected) {
            assertEquals("Lunavera Coffee", ShopSettings.cachedName());
        }
    }

    @Test
    public void theNameFlagFitsTheFlagColumnTogetherWithItsPrefix() {
        assertTrue(ShopSettings.PREFIX.length() + ShopSettings.NAME_MAX <= 100);
        assertFalse("the prefix lookup treats % and _ as wildcards", ShopSettings.PREFIX.contains("_") || ShopSettings.PREFIX.contains("%"));
    }
}
