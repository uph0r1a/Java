package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** TODO 3.48: the map position depends on the place in the list, never on the table number. */
public class TableLayoutTest {

    @Test
    public void theFirstTablesFillTheTopRowFromTheLeft() {
        assertEquals(new TableLayout.Cell(0, 0), TableLayout.cellAt(0));
        assertEquals(new TableLayout.Cell(1, 0), TableLayout.cellAt(1));
        assertEquals(new TableLayout.Cell(2, 0), TableLayout.cellAt(2));
        assertEquals(new TableLayout.Cell(0, 1), TableLayout.cellAt(3));
    }

    @Test
    public void twelveTablesStillMakeThreeColumnsAndFourRows() {
        assertEquals(new TableLayout.Cell(2, 3), TableLayout.cellAt(11));
        assertEquals(4, TableLayout.rows(12));
    }

    @Test
    public void aThirteenthTableGetsAFifthRowInsteadOfAWrongCell() {
        assertEquals(new TableLayout.Cell(0, 4), TableLayout.cellAt(12));
        assertEquals(5, TableLayout.rows(13));
    }

    @Test
    public void noTwoTablesShareACellAndThereAreNoHoles() {
        Set<TableLayout.Cell> used = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            assertTrue("cell used twice at " + i, used.add(TableLayout.cellAt(i)));
        }
        for (int row = 0; row < TableLayout.rows(200) - 1; row++) {
            for (int col = 0; col < TableLayout.COLUMNS; col++) {
                assertTrue(used.contains(new TableLayout.Cell(col, row)));
            }
        }
    }

    @Test
    public void rowsOfNoTablesIsZero() {
        assertEquals(0, TableLayout.rows(0));
        assertEquals(1, TableLayout.rows(1));
        assertEquals(1, TableLayout.rows(3));
        assertEquals(2, TableLayout.rows(4));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNegativeIndexIsRefused() {
        TableLayout.cellAt(-1);
    }
}
