package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.service.TableAge;
import com.coffeeshop.coffeeshopmanagement.service.TableLayout;
import com.coffeeshop.coffeeshopmanagement.service.TablePolicy;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.OrderNumber;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.time.Duration;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The take-away side of the sales screen (TODO 4.25): an order with no table. Beside the table buttons the map shows one
 * "Mang đi" button that starts a new take-away order, and one button per take-away order still open (a cup being made while the next
 * customer is served), each with its order number and how long it has been open. The order itself is the same OPEN order as a table's,
 * with {@code table_number} empty; paying and the receipt already handled that. Kept out of {@code OrderController}, which is at its
 * size ceiling (TODO 6.54).
 *
 * <p>There is no queue number of its own: the order number ({@code #12}) already tells two orders apart and is on the receipt, and a
 * per-day counter would need a new column in both databases.
 */
final class TakeAwayPanel {

    private TakeAwayPanel() {
    }

    /** "Bàn 3", or "Mang đi" for no table. */
    static String tableLabel(Integer tableNumber) {
        return tableNumber == null ? I18n.t("orders.takeaway") : I18n.t("orders.table", tableNumber);
    }

    /**
     * Adds the buttons after the tables: the "new take-away" button, then the open orders. {@code firstIndex} is the number of
     * tables already placed, so the buttons follow the same grid the tables use ({@link TableLayout}).
     *
     * @param newSelected  the till is on a new take-away order that has no order saved yet
     * @param selectedId   the open take-away order on screen, or null
     */
    static void addButtons(GridPane grid, int firstIndex, List<Order> open, boolean newSelected, Integer selectedId,
                           IntConsumer onOpen, Runnable onNew) {
        int index = firstIndex;
        Button fresh = new Button("🥡  " + I18n.t("orders.takeaway") + "\n" + I18n.t("pos.takeaway.new"));
        style(fresh, "table-button-empty", newSelected);
        fresh.setOnAction(e -> onNew.run());
        place(grid, fresh, index++);
        for (Order order : open) {
            Duration age = TableAge.since(order.getOrderDate(), AppClock.now());
            String time = age == null ? "" : "  ·  " + TableAge.text(age);
            Button button = new Button("🥡  " + I18n.t("orders.takeaway") + " " + OrderNumber.format(order.getId()) + "\n"
                    + I18n.t("tables.status.occupied") + time);
            style(button, "table-button-occupied", selectedId != null && selectedId == order.getId());
            if (TableAge.isLong(age)) button.getStyleClass().add("table-button-long");
            int id = order.getId();
            button.setOnAction(e -> onOpen.accept(id));
            place(grid, button, index++);
        }
    }

    static void style(Button button, String look, boolean selected) {
        button.setMaxWidth(Double.MAX_VALUE);
        button.setMaxHeight(Double.MAX_VALUE);
        button.setMinHeight(78.0);
        button.getStyleClass().add(look);
        if (selected) button.getStyleClass().add("table-button-selected");
        GridPane.setHgrow(button, Priority.ALWAYS);
        GridPane.setVgrow(button, Priority.ALWAYS);
    }

    private static void place(GridPane grid, Button button, int index) {
        TableLayout.Cell cell = TableLayout.cellAt(index);
        grid.add(button, cell.column(), cell.row());
    }

    /** The name, status and seats labels above the order while a take-away order is chosen (no seats; any label may be missing). */
    static void showOn(javafx.scene.control.Label name, javafx.scene.control.Label status, javafx.scene.control.Label seats, boolean hasOrder) {
        if (name != null) name.setText(tableLabel(null));
        if (seats != null) seats.setText("");
        if (status != null) {
            status.setText(I18n.t(hasOrder ? "tables.status.occupied" : "tables.status.free"));
            status.getStyleClass().setAll(hasOrder ? "occupied-badge" : "empty-badge");
        }
    }

    /**
     * Cancels the open take-away order on screen, after a question. Like emptying a table (TODO 3.8): only the cashier who opened it,
     * or an administrator. {@code after} runs on the screen thread when it is done (or was already gone).
     */
    static void cancel(Order order, Runnable after) {
        if (order == null) return;
        Integer me = Session.getCurrentEmployee() != null ? Session.getCurrentEmployee().getId() : null;
        if (!TablePolicy.canEmptyTableWithOpenOrder(Session.isAdmin(), me, order.getEmployeeId())) {
            AlertUtil.warning(I18n.t("admin.only.title"), TablePolicy.refusedMessage());
            return;
        }
        if (!AlertUtil.confirm(I18n.t("pos.cancel.title"), I18n.t("pos.takeaway.cancel.message", OrderNumber.format(order.getId())))) {
            return;
        }
        Async.run(() -> new OrderDAO().cancelOpenTakeAway(order.getId()), done -> after.run(),
                error -> AlertUtil.error(I18n.t("common.error.title"), I18n.t("pos.toggle.fail", error.getMessage())));
    }
}
