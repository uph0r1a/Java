package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/**
 * TODO 6.54: the menu side of the till (chips, tiles, search and scanner Enter, best sellers first) lives in {@code ProductCatalog}, not
 * in {@code OrderController}, and the till reaches it only through the object. Reads the sources, because both build JavaFX nodes.
 */
public class ProductCatalogSourceTest {

    private static final Path DIR = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller");

    private static String read(String file) throws IOException {
        return Files.readString(DIR.resolve(file), StandardCharsets.UTF_8);
    }

    @Test
    public void theMenuCodeLivesInTheCatalog() throws IOException {
        String catalog = read("ProductCatalog.java");
        for (String piece : new String[]{"void reload(boolean quiet)", "private void renderCategoryChips()", "void render()", "private void handleSearchEnter()",
                "ProductCode.looksLikeCode(", "AlertSounds.Cue.CODE_NOT_FOUND", "pos.bestSellersFirst", "void refreshProducts()"}) {
            assertTrue(piece + " is missing from ProductCatalog", catalog.contains(piece));
        }
    }

    @Test
    public void theTillNoLongerHoldsTheMenuLists() throws IOException {
        String till = read("OrderController.java");
        for (String gone : new String[]{"allProducts", "allCategories", "selectedCategoryId", "recentSales", "renderProductCatalog", "handleSearchEnter"}) {
            assertTrue(gone + " came back into OrderController", !till.contains(gone));
        }
        assertTrue(till.contains("new ProductCatalog(") && till.contains("catalog.setUp()"));
    }

    @Test
    public void theCatalogKnowsNothingOfTheCartOrTheTable() throws IOException {
        String catalog = read("ProductCatalog.java");
        for (String state : new String[]{"activeOpenOrder", "selectedTableNumber", "takeAway", "orderInProgress", "cartRules"}) {
            assertTrue(state + " is till state and must come in through a supplier", !catalog.contains(state));
        }
    }
}
