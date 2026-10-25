package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** TODO 1.7: the English names of the starter menu stay in step with the starter menu itself. */
public class SeedNamesTest {

    private final String languageAtStart = I18n.current();

    @After
    public void putTheLanguageBack() {
        I18n.setLanguage(languageAtStart);
    }

    @Test
    public void everyStarterProductAndCategoryHasAnEnglishName() {
        for (MenuSeedData.MenuItem item : MenuSeedData.ITEMS) {
            assertTrue("no English name for the starter product: " + item.name(), SeedNames.products().containsKey(item.name()));
            assertTrue("no English name for the starter category: " + item.category(), SeedNames.categories().containsKey(item.category()));
        }
    }

    @Test
    public void thereIsNoEnglishNameForAStarterItemThatNoLongerExists() {
        Set<String> products = new HashSet<>();
        Set<String> categories = new HashSet<>();
        for (MenuSeedData.MenuItem item : MenuSeedData.ITEMS) {
            products.add(item.name());
            categories.add(item.category());
        }
        assertEquals(products, SeedNames.products().keySet());
        assertEquals(categories, SeedNames.categories().keySet());
    }

    @Test
    public void theEnglishNamesAreRealNames() {
        SeedNames.products().forEach((vi, en) -> {
            assertFalse("empty English name for " + vi, en.isBlank());
            assertEquals("stray space around the English name of " + vi, en.trim(), en);
        });
        SeedNames.categories().forEach((vi, en) -> assertFalse("empty English name for " + vi, en.isBlank()));
        assertEquals("Black coffee", SeedNames.product(I18n.EN, "Cafe đen"));
        assertEquals("Tea", SeedNames.category(I18n.EN, "Trà"));
    }

    @Test
    public void vietnameseShowsTheStoredName() {
        assertEquals("Cafe đen", SeedNames.product(I18n.VI, "Cafe đen"));
        assertEquals("Trà", SeedNames.category(I18n.VI, "Trà"));
    }

    @Test
    public void aNameTheShopTypedItselfIsNeverTranslated() {
        assertEquals("Cà phê muối", SeedNames.product(I18n.EN, "Cà phê muối"));
        assertEquals("Cafe đen 2", SeedNames.product(I18n.EN, "Cafe đen 2"));
        assertEquals("Trà (new)", SeedNames.category(I18n.EN, "Trà (new)"));
        assertNull(SeedNames.product(I18n.EN, null));
    }

    @Test
    public void theLanguageInUseDecidesWhenNoLanguageIsGiven() {
        I18n.setLanguage(I18n.EN);
        assertEquals("Lemon tea", SeedNames.product("Trà chanh"));
        I18n.setLanguage(I18n.VI);
        assertEquals("Trà chanh", SeedNames.product("Trà chanh"));
    }

    @Test
    public void aNameWithSpacesAroundItIsStillRecognised() {
        assertEquals("Lemon tea", SeedNames.product(I18n.EN, " Trà chanh "));
    }
}
