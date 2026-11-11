package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.service.TableAge;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.scene.control.Button;

import java.time.Duration;

/** The button of one table on the sales screen's map (moved out of {@code OrderController}, TODO 6.54; unchanged). */
final class TableMapButtons {

    private TableMapButtons() {
    }

    /** A table's button: its name, whether it is taken and for how long (TODO 5.11; a long one is highlighted), and whether it is the chosen one. */
    static Button forTable(DiningTable table, boolean selected) {
        return forTable(table, selected, null);
    }

    /** The same with the booking line of the table (TODO 4.43): "Đặt 18:30 · Nam · 4", or null when there is none to show. */
    static Button forTable(DiningTable table, boolean selected, String reservation) {
        String statusText = I18n.t(table.isOccupied() ? "tables.status.occupied" : "tables.status.free");
        Duration age = table.isOccupied() ? TableAge.since(table.getOpenedAt(), AppClock.now()) : null;
        if (age != null) {
            statusText += "  ·  " + TableAge.text(age);
        }
        Button button = new Button("☕  " + table.displayName() + "\n" + statusText + (reservation == null ? "" : "\n📅 " + reservation));
        TakeAwayPanel.style(button, table.isOccupied() ? "table-button-occupied" : "table-button-empty", selected);
        if (TableAge.isLong(age)) {
            button.getStyleClass().add("table-button-long");
        }
        if (reservation != null) {
            button.getStyleClass().add("table-button-reserved");
        }
        return button;
    }
}
