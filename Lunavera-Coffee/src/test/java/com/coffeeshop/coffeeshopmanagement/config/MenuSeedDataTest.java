package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The starter menu data is checked as data (TODO 6.3): a typo here would otherwise only show up as a failed first start
 * (a missing picture, a name the database refuses as a duplicate). No database needed.
 */
public class MenuSeedDataTest {

    @Test
    public void thereIsAMenuWithSeveralCategories() {
        assertFalse(MenuSeedData.ITEMS.isEmpty());
        Set<String> categories = new HashSet<>();
        MenuSeedData.ITEMS.forEach(item -> categories.add(item.category()));
        assertEquals("Bánh ngọt, Cà phê, Nước ép & Sinh tố, Trà", 4, categories.size());
    }

    @Test
    public void productNamesAreUniqueBecauseTheDatabaseDemandsIt() {
        Set<String> seen = new HashSet<>();
        for (MenuSeedData.MenuItem item : MenuSeedData.ITEMS) {
            assertTrue("duplicate product name: " + item.name(), seen.add(item.name().trim().toLowerCase(Locale.ROOT)));
        }
    }

    @Test
    public void everyItemHasSaneTextAndPrice() {
        for (MenuSeedData.MenuItem item : MenuSeedData.ITEMS) {
            assertFalse("blank category", item.category().isBlank());
            assertFalse("blank name", item.name().isBlank());
            assertTrue("name too long for the name box: " + item.name(), item.name().length() <= TextLengthLimiter.NAME_MAX);
            assertTrue("price must be positive: " + item.name(), item.price() > 0);
            assertEquals("a placeholder price in whole thousands: " + item.name(), 0, item.price() % 1000);
        }
    }

    @Test
    public void everyPictureIsBundledAndHasASafeFileName() throws IOException {
        for (MenuSeedData.MenuItem item : MenuSeedData.ITEMS) {
            String file = item.imageResource();
            assertTrue("unsafe picture name: " + file, file.matches("[a-z0-9-]+\\.(png|jpg|jpeg|jfif)"));
            try (InputStream in = MenuSeedData.class.getResourceAsStream("/seed-menu/" + file)) {
                assertNotNull("picture missing from resources/seed-menu: " + file, in);
                assertTrue("picture is empty: " + file, in.read() != -1);
            }
        }
    }

    @Test
    public void noTwoItemsShareAPicture() {
        Set<String> pictures = new HashSet<>();
        for (MenuSeedData.MenuItem item : MenuSeedData.ITEMS) {
            assertTrue("picture used twice: " + item.imageResource(), pictures.add(item.imageResource()));
        }
    }
}
