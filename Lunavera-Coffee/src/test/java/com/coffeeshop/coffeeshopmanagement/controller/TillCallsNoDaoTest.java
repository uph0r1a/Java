package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.21: the till screen asks {@code PosService} (and the other services) for what it reads and writes; it holds no database call of
 * its own. Reads the source, because the class builds JavaFX nodes and cannot be created in a test. The DAOs that are still made in the
 * file are only handed to the services' constructors (and to {@code PosCustomerBox}); a call on one of them would show up here as
 * {@code orderDAO.something(}.
 */
public class TillCallsNoDaoTest {

    private static final Path TILL = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller", "OrderController.java");
    private static final Path CATALOG = TILL.resolveSibling("ProductCatalog.java");
    private static final Path VIEW = TILL.resolveSibling("TableMapView.java");
    private static final Pattern CALL_ON_A_DAO_FIELD = Pattern.compile("\\b\\w+DAO\\.\\w+\\(");
    private static final Pattern DAO_MADE_IN_A_BODY = Pattern.compile("new (?:com\\.coffeeshop\\.coffeeshopmanagement\\.dao\\.)?(?!DataAccess)\\w+DAO\\(\\)\\s*\\.");

    private static String till() throws IOException {
        return Files.readString(TILL, StandardCharsets.UTF_8);
    }

    @Test
    public void noCallIsMadeOnADaoFromTheTill() throws IOException {
        Matcher calls = CALL_ON_A_DAO_FIELD.matcher(till());
        StringBuilder found = new StringBuilder();
        while (calls.find()) found.append(calls.group()).append(' ');
        assertEquals("a database call crept back into the till: " + found, "", found.toString());
        assertTrue("a DAO made and used in one expression", !DAO_MADE_IN_A_BODY.matcher(till()).find());
        String catalog = Files.readString(CATALOG, StandardCharsets.UTF_8);
        assertTrue("a database call in ProductCatalog", !CALL_ON_A_DAO_FIELD.matcher(catalog).find() && !DAO_MADE_IN_A_BODY.matcher(catalog).find());
        String view = Files.readString(VIEW, StandardCharsets.UTF_8);
        assertTrue("a database call in TableMapView", !CALL_ON_A_DAO_FIELD.matcher(view).find() && !DAO_MADE_IN_A_BODY.matcher(view).find());
    }

    @Test
    public void theTillAsksThePosService() throws IOException {
        String till = till();
        for (String call : new String[]{"posService.stockShortages(", "posService.markSeated("}) {
            assertTrue(call + " is missing from the till", till.contains(call));
        }
        // the table map lives in TableMapView (TODO 6.54)
        assertTrue("posService.tableMap() is missing from TableMapView", Files.readString(VIEW, StandardCharsets.UTF_8).contains("posService.tableMap()"));
        // the menu side of the till lives in ProductCatalog (TODO 6.54)
        String catalog = Files.readString(CATALOG, StandardCharsets.UTF_8);
        for (String call : new String[]{"posService.menu(", "posService.productsForSale(", "posService::recentUnitsSold"}) {
            assertTrue(call + " is missing from ProductCatalog", catalog.contains(call));
        }
    }

    @Test
    public void theFieldsOfTheRemovedCallsAreGone() throws IOException {
        String till = till();
        assertTrue(!till.contains("ProductDAO productDAO") && !till.contains("CategoryDAO categoryDAO"));
    }
}
