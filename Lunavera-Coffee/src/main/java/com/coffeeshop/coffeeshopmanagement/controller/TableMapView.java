package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.service.PosService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The table map of the till (TODO 6.54): the grid of table buttons with the "Mang đi", booking and move buttons after them, the three
 * labels that name the selected table, and the tables as last read. Moved out of {@code OrderController} word for word. It does not
 * decide anything about the order: what is selected comes in through {@code state}, a tap goes to the callbacks, and when the grid has
 * been drawn {@code afterRender} lets the till keep or choose its selected table.
 */
final class TableMapView {

    private static final Logger LOGGER = Logger.getLogger(TableMapView.class.getName());

    /** What the map needs to know of the till when it draws: the selected table, and the take-away order (if any) that is on screen. */
    record State(Integer selectedTable, boolean newTakeAway, Integer takeAwayOrderId) {
    }

    /** What the table map is drawn from: the tables, the open take-away orders and the waiting reservations (TODO 4.43). */
    private record TableMapData(List<DiningTable> tables, List<Order> takeAway, List<com.coffeeshop.coffeeshopmanagement.model.Reservation> waiting) {}

    private final PosService posService;
    private final GridPane tableGrid;
    private final Label selectedTableLabel;
    private final Label selectedTableStatusLabel;
    private final Label selectedTableCapacityLabel;
    private final ReservationPanel reservations;
    private final Supplier<State> state;
    private final IntConsumer onSelectTable;
    private final Consumer<Integer> onSelectTakeAway;
    private final Runnable onOpenReservations;
    private final Runnable onOpenTableMoves;
    private final Consumer<Boolean> afterRender;
    private final Map<Integer, Button> tableButtonByNumber = new LinkedHashMap<>();
    private final Map<Integer, DiningTable> tableDataByNumber = new LinkedHashMap<>();

    TableMapView(PosService posService, GridPane tableGrid, Label selectedTableLabel, Label selectedTableStatusLabel,
                 Label selectedTableCapacityLabel, ReservationPanel reservations, Supplier<State> state, IntConsumer onSelectTable,
                 Consumer<Integer> onSelectTakeAway, Runnable onOpenReservations, Runnable onOpenTableMoves, Consumer<Boolean> afterRender) {
        this.posService = posService;
        this.tableGrid = tableGrid;
        this.selectedTableLabel = selectedTableLabel;
        this.selectedTableStatusLabel = selectedTableStatusLabel;
        this.selectedTableCapacityLabel = selectedTableCapacityLabel;
        this.reservations = reservations;
        this.state = state;
        this.onSelectTable = onSelectTable;
        this.onSelectTakeAway = onSelectTakeAway;
        this.onOpenReservations = onOpenReservations;
        this.onOpenTableMoves = onOpenTableMoves;
        this.afterRender = afterRender;
    }

    /** The table with this number as last read, or null. */
    DiningTable table(Integer number) {
        return tableDataByNumber.get(number);
    }

    /** True when the table with this number was in the last read. */
    boolean has(int number) {
        return tableDataByNumber.containsKey(number);
    }

    /** The number of the first table as last read (the one the till selects when none is), or null when there is no table. */
    Integer firstTableNumber() {
        return tableDataByNumber.isEmpty() ? null : tableDataByNumber.keySet().iterator().next();
    }

    /** A copy of the tables as last read, in the order they are drawn. */
    List<DiningTable> all() {
        return new ArrayList<>(tableDataByNumber.values());
    }

    /** {@code quiet}: a background refresh (TODO 5.37) logs a failure instead of opening an error box every few seconds. */
    void load(boolean quiet) {
        if (tableGrid == null) return;
        Async.run(
                () -> {
                    PosService.TableMap map = posService.tableMap();
                    return new TableMapData(map.tables(), map.takeAway(), ReservationPanel.waitingOrNone());
                },
                loaded -> {
                    final State st = state.get(); // read now, on the screen thread, as the till's fields were
                    List<DiningTable> tables = loaded.tables();
                    Map<Integer, String> booked = BookedTables.badges(loaded.waiting()); // also remembers the bookings for the question on a booked table (TODO 3.74)
                    tableGrid.getChildren().clear();
                    tableButtonByNumber.clear();
                    tableDataByNumber.clear();
                    if (tables.isEmpty()) { // no table at all: say what to do instead of showing an empty square
                        tableGrid.add(com.coffeeshop.coffeeshopmanagement.util.DashboardWidgets.emptyTablesNote(),
                                0, 0, com.coffeeshop.coffeeshopmanagement.service.TableLayout.COLUMNS, 1);
                    }
                    int index = 0;
                    for (DiningTable table : tables) {
                        int num = table.getTableNumber();
                        tableDataByNumber.put(num, table);
                        // the place comes from the position in the list, not from the number (TODO 3.48)
                        com.coffeeshop.coffeeshopmanagement.service.TableLayout.Cell cell =
                                com.coffeeshop.coffeeshopmanagement.service.TableLayout.cellAt(index++);
                        Button btn = TableMapButtons.forTable(table, st.selectedTable() != null && st.selectedTable() == num, booked.get(num));
                        btn.setOnAction(e -> onSelectTable.accept(num));
                        tableGrid.add(btn, cell.column(), cell.row());
                        tableButtonByNumber.put(num, btn);
                    }
                    int afterTables = tables.isEmpty() ? com.coffeeshop.coffeeshopmanagement.service.TableLayout.COLUMNS : index;
                    TakeAwayPanel.addButtons(tableGrid, afterTables, loaded.takeAway(), st.newTakeAway(), st.takeAwayOrderId(),
                            onSelectTakeAway::accept, () -> onSelectTakeAway.accept(null)); // "Mang đi" and the open take-away orders (TODO 4.25)
                    reservations.addButton(tableGrid, afterTables + TakeAwayPanel.newButtons() + loaded.takeAway().size(), loaded.waiting().size(), onOpenReservations);
                    TableMovePanel.addButton(tableGrid, afterTables + TakeAwayPanel.newButtons() + 1 + loaded.takeAway().size(), onOpenTableMoves); // TODO 4.10
                    reservations.remind(loaded.waiting(), tableDataByNumber); // once per booking that is due (TODO 4.43)
                    afterRender.accept(quiet); // the till keeps or picks the selected table, and stamps a background refresh
                },
                error -> {
                    if (quiet) {
                        LOGGER.log(Level.FINE, "Table refresh failed", error);
                    } else {
                        AlertUtil.error(I18n.t("common.error.title"), I18n.t("pos.load.tables", error.getMessage()));
                    }
                }
        );
    }

    void showSelected(int number) {
        if (selectedTableLabel != null) {
            DiningTable named = tableDataByNumber.get(number);
            selectedTableLabel.setText(named != null ? named.displayName() : I18n.t("orders.table", number)); // the shop's own name (TODO 4.18)
        }
        DiningTable table = tableDataByNumber.get(number);
        boolean occupied = table != null && table.isOccupied();
        if (selectedTableStatusLabel != null) {
            selectedTableStatusLabel.setText(I18n.t(occupied ? "tables.status.occupied" : "tables.status.free"));
            selectedTableStatusLabel.getStyleClass().setAll(occupied ? "occupied-badge" : "empty-badge");
        }
        if (selectedTableCapacityLabel != null && table != null) {
            selectedTableCapacityLabel.setText(I18n.t("pos.capacity", table.getCapacity()));
        }
    }

    void highlight(int number) {
        for (Map.Entry<Integer, Button> entry : tableButtonByNumber.entrySet()) {
            entry.getValue().getStyleClass().remove("table-button-selected");
            if (entry.getKey() == number) {
                entry.getValue().getStyleClass().add("table-button-selected");
            }
        }
    }
}
