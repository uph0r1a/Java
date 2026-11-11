package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.service.TablePolicy;
import com.coffeeshop.coffeeshopmanagement.service.TableService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.util.function.IntConsumer;

/**
 * The two small buttons under the table map of the sales screen (TODO 6.54): "mark the table in use / empty" and "table details".
 * Moved out of {@code OrderController} so that it can get under the 900-line rule; what they do is unchanged. The screen passes in
 * what it knows (the selected table, the order on screen, whether the cart is empty) and what to do afterwards, so this class keeps
 * no state and holds no reference to the screen. The checks themselves are in {@link TableService} and {@link TablePolicy}.
 */
final class TableStatusActions {

    private TableStatusActions() {
    }

    /**
     * Switches the selected table between free and in use. On the take-away screen (no table) it cancels the unpaid take-away order
     * instead (TODO 4.25); with nothing selected it says so. Emptying a table cancels its unpaid order, so only the cashier who
     * opened it or an administrator may (TODO 3.8), and a cart with lines is confirmed first. {@code table} is the table of
     * {@code selectedNumber} on the map, or null when it is not there.
     *
     * @param afterTakeAwayCancelled runs after the take-away order was cancelled
     * @param afterChange            runs on the screen thread with the table number once the table was switched
     */
    static void toggle(Integer selectedNumber, boolean takeAway, Order activeOpenOrder, DiningTable table, boolean cartEmpty,
                       TableService tableService, Runnable afterTakeAwayCancelled, IntConsumer afterChange) {
        if (selectedNumber == null) {
            if (takeAway && activeOpenOrder != null) { // the take-away way of emptying a table: cancel the unpaid order (TODO 4.25)
                TakeAwayPanel.cancel(activeOpenOrder, afterTakeAwayCancelled);
                return;
            }
            AlertUtil.info(I18n.t("pos.notable.title"), I18n.t("pos.notable.toggle"));
            return;
        }
        if (table == null) return;

        boolean newOccupied = !table.isOccupied();
        final int tNum = selectedNumber;
        // Emptying a table cancels its unpaid order: only the cashier who opened it, or an admin, may (TODO 3.8). Checked here only to
        // refuse at once; TableService checks again against the database (TODO 6.21).
        if (!newOccupied && !TableService.mayEmpty(activeOpenOrder)) {
            AlertUtil.warning(I18n.t("admin.only.title"), TablePolicy.refusedMessage());
            return;
        }
        // setEmpty() also cancels the table's OPEN order, so never do that silently.
        if (!newOccupied && activeOpenOrder != null && !cartEmpty
                && !AlertUtil.confirm(I18n.t("pos.cancel.title"), I18n.t("pos.cancel.message", tNum))) {
            return;
        }
        Async.run(
                () -> {
                    if (newOccupied) {
                        tableService.markOccupied(tNum);
                    } else {
                        tableService.markEmpty(tNum);
                    }
                    return null;
                },
                result -> {
                    afterChange.accept(tNum);
                    AlertUtil.info(I18n.t("common.success"),
                            I18n.t("pos.toggle.done", tNum, I18n.t(newOccupied ? "tables.status.occupied" : "tables.status.free")));
                },
                error -> AlertUtil.error(I18n.t("common.error.title"), I18n.t("pos.toggle.fail", error.getMessage()))
        );
    }

    /** The "table details" box: the capacity and whether the table is in use. {@code label} is the table's name as the screen shows it. */
    static void detail(Integer selectedNumber, DiningTable table, String label) {
        if (selectedNumber == null) {
            AlertUtil.info(I18n.t("pos.notable.title"), I18n.t("pos.notable.detail"));
            return;
        }
        String status = I18n.t((table != null && table.isOccupied()) ? "tables.status.occupied" : "tables.status.free");
        AlertUtil.info(I18n.t("pos.detail.title", label),
                (table != null ? I18n.t("pos.capacity", table.getCapacity()) + "\n" : "")
                        + I18n.t("pos.detail.status", status));
    }
}
