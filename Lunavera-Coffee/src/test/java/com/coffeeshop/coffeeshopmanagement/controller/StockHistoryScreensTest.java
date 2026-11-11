package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/** TODO 4.59: both stock windows open the history, the history window is read-only and asks the DAO (which checks the administrator). */
public class StockHistoryScreensTest {

    private static final Path CONTROLLERS = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller");

    private static String source(String name) throws IOException {
        return Files.readString(CONTROLLERS.resolve(name), StandardCharsets.UTF_8);
    }

    @Test
    public void theStockWindowOpensTheHistoryOfTheChosenProduct() throws IOException {
        String s = source("StockLogDialog.java");
        assertTrue(s.contains("StockHistoryDialog.showProduct("));
        assertTrue("a product must be chosen first", s.contains("productBox.getValue()") && s.contains("stock.val.product"));
    }

    @Test
    public void theIngredientWindowOpensTheHistoryOfTheSelectedIngredient() throws IOException {
        assertTrue(source("IngredientDialog.java").contains("StockHistoryDialog.showIngredient("));
    }

    @Test
    public void theHistoryWindowOnlyReadsThroughTheService() throws IOException {
        String s = source("StockHistoryDialog.java");
        assertTrue(s.contains("new StockService()"));
        assertTrue("it keeps its limit of lines in one place, in the service", s.contains("StockService.HISTORY_LINES"));
        assertTrue("a refusal or a missing item is shown, not thrown at the screen", s.contains("failed(name, error)") && s.contains("AlertUtil.error("));
    }
}
