package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/**
 * TODO 6.54: the table map of the till (the grid of buttons, the "Mang đi", booking and move buttons, the three labels of the selected
 * table and the tables as last read) lives in {@code TableMapView}. The till builds it, gives it what is selected through a supplier and
 * keeps the decision about the selected table in {@code afterTableMapLoaded}. Reads the sources, because both build JavaFX nodes.
 */
public class TableMapViewSourceTest {

    private static final Path DIR = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller");

    private static String read(String file) throws IOException {
        return Files.readString(DIR.resolve(file), StandardCharsets.UTF_8);
    }

    @Test
    public void theMapCodeLivesInTheView() throws IOException {
        String view = read("TableMapView.java");
        for (String piece : new String[]{"void load(boolean quiet)", "void showSelected(int number)", "void highlight(int number)", "TableMapButtons.forTable(",
                "TakeAwayPanel.addButtons(", "TableMovePanel.addButton(", "reservations.remind(", "BookedTables.badges(", "afterRender.accept(quiet)"}) {
            assertTrue(piece + " is missing from TableMapView", view.contains(piece));
        }
    }

    @Test
    public void theTillNoLongerHoldsTheTableLists() throws IOException {
        String till = read("OrderController.java");
        for (String gone : new String[]{"tableDataByNumber", "tableButtonByNumber", "TableMapData", "updateSelectedTableInfo", "highlightSelectedTableButton"}) {
            assertTrue(gone + " came back into OrderController", !till.contains(gone));
        }
        assertTrue(till.contains("new TableMapView(") && till.contains("private void afterTableMapLoaded(boolean quiet)"));
    }

    @Test
    public void theViewIsBuiltBeforeTheFirstLoadAndNothingElseIsCalledBeforeIt() throws IOException {
        String till = read("OrderController.java");
        int built = till.indexOf("tableMap = new TableMapView(");
        int firstLoad = till.indexOf("loadTablesFromDb();", till.indexOf("private void initialize()"));
        assertTrue("the table map must exist before the first load and the refresh timer", built > 0 && built < firstLoad);
    }

    @Test
    public void theViewKnowsNothingOfTheCartOrOfTheSelectedTableField() throws IOException {
        String view = read("TableMapView.java");
        for (String state : new String[]{"selectedTableNumber", "activeOpenOrder", "orderInProgress", "paymentInFlight", "cartRules"}) {
            assertTrue(state + " is till state and must come in through the supplier or a callback", !view.contains(state));
        }
    }
}
