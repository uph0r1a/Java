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
        String statusText = I18n.t(table.isOccupied() ? "tables.status.occupied" : "tables.status.free");
        Duration age = table.isOccupied() ? TableAge.since(table.getOpenedAt(), AppClock.now()) : null;
        if (age != null) {
            statusText += "  ·  " + TableAge.text(age);
        }
        Button button = new Button("☕  " + table.displayName() + "\n" + statusText);
        TakeAwayPanel.style(button, table.isOccupied() ? "table-button-occupied" : "table-button-empty", selected);
        if (TableAge.isLong(age)) {
            button.getStyleClass().add("table-button-long");
        }
        return button;
    }
}
