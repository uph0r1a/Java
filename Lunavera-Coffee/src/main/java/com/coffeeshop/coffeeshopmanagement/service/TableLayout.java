package com.coffeeshop.coffeeshopmanagement.service;

/**
 * Where the n-th table of the (sorted) list goes on the table map (TODO 3.48). The map used to put table {@code num} at column
 * {@code (num - 1) % 3}: a table 13, or a gap after table 5 was removed, landed in the wrong cell or left a hole. The position now
 * depends on the place in the list, never on the table's number, so any number of tables, with any numbers, fills the grid from
 * the top left without holes.
 */
public final class TableLayout {

    /** Tables per row on the map. */
    public static final int COLUMNS = 3;

    public record Cell(int column, int row) {
    }

    private TableLayout() {
    }

    /** The cell of the table at {@code index} (0 = first in the list). */
    public static Cell cellAt(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("index < 0: " + index);
        }
        return new Cell(index % COLUMNS, index / COLUMNS);
    }

    /** How many rows {@code count} tables need. */
    public static int rows(int count) {
        return count <= 0 ? 0 : (count + COLUMNS - 1) / COLUMNS;
    }
}
