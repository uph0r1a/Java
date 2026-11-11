package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.ClipboardText;
import com.coffeeshop.coffeeshopmanagement.util.CopyText;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.animation.PauseTransition;
import javafx.beans.value.ObservableValue;
import javafx.event.EventTarget;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Copying with one click (TODO 5.46): a small "Sao chép" button next to a value, and a right-click menu on the big tables that has a
 * copy of the row, the copy of the values people ask for (an order number, a transfer note, a phone, a gift card code) and the SAME
 * actions as the buttons of the window. What is copied is built by {@link CopyText}: what the screen shows, nothing more.
 */
final class CopyMenus {

    private CopyMenus() {
    }

    // ---- a button next to a value

    /**
     * A "Sao chép" button that copies what {@code text} gives at the moment of the click, and says "Đã chép" for a moment. A blank text
     * (nothing to copy yet) copies nothing and changes nothing.
     */
    static Button button(Supplier<String> text) {
        Button button = new Button(I18n.t("copy.button"));
        button.setFocusTraversable(false);
        button.setOnAction(e -> {
            if (!ClipboardText.copy(text.get())) return;
            button.setText(I18n.t("copy.done"));
            PauseTransition back = new PauseTransition(Duration.seconds(1.5));
            back.setOnFinished(done -> button.setText(I18n.t("copy.button")));
            back.play();
        });
        return button;
    }

    // ---- a right-click menu on a table

    /** One entry of a table's menu; it is built for its table and may ask to be refreshed each time the menu opens. */
    @FunctionalInterface
    interface Item<T> {
        MenuItem build(TableView<T> table, List<Runnable> refresh);
    }

    /** "Copy ...": puts {@code text} of the selected row on the clipboard; off while no row is selected or the row has nothing to copy. */
    static <T> Item<T> copy(String labelKey, Function<T, String> text) {
        return (table, refresh) -> {
            MenuItem item = new MenuItem(I18n.t(labelKey));
            item.setOnAction(e -> {
                T row = table.getSelectionModel().getSelectedItem();
                if (row != null) ClipboardText.copy(text.apply(row));
            });
            refresh.add(() -> {
                T row = table.getSelectionModel().getSelectedItem();
                item.setDisable(row == null || text.apply(row) == null || text.apply(row).isBlank());
            });
            return item;
        };
    }

    /** An action on the selected row (the one an edit or delete button of the row does); off while no row is selected. */
    static <T> Item<T> action(String labelKey, Consumer<T> action) {
        return (table, refresh) -> {
            MenuItem item = new MenuItem(I18n.t(labelKey));
            item.setOnAction(e -> {
                T row = table.getSelectionModel().getSelectedItem();
                if (row != null) action.accept(row);
            });
            refresh.add(() -> item.setDisable(table.getSelectionModel().getSelectedItem() == null));
            return item;
        };
    }

    /** The same action as a button of the window: the menu entry has its text, is off when it is off, and presses it. */
    static <T> Item<T> button(Button button) {
        return (table, refresh) -> {
            MenuItem item = new MenuItem();
            item.textProperty().bind(button.textProperty());
            item.disableProperty().bind(button.disableProperty());
            item.visibleProperty().bind(button.visibleProperty());
            item.setOnAction(e -> button.fire());
            return item;
        };
    }

    @SafeVarargs
    static <T> void install(TableView<T> table, Item<T>... items) {
        List<Item<T>> all = new ArrayList<>(items.length);
        for (Item<T> item : items) all.add(item);
        install(table, all);
    }

    /**
     * Puts a right-click menu on {@code table}: "Copy row" first, then {@code items}. A right click on a row selects it first, so the
     * menu acts on the row that was clicked.
     */
    static <T> void install(TableView<T> table, List<Item<T>> items) {
        ContextMenu menu = new ContextMenu();
        List<Runnable> refresh = new ArrayList<>();
        menu.getItems().add(CopyMenus.<T>copy("copy.row", row -> rowText(table, row)).build(table, refresh));
        if (!items.isEmpty()) menu.getItems().add(new SeparatorMenuItem());
        for (Item<T> item : items) menu.getItems().add(item.build(table, refresh));
        menu.setOnShowing(e -> refresh.forEach(Runnable::run));
        table.setContextMenu(menu);
        table.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.SECONDARY) selectRowUnder(table, e.getTarget());
        });
    }

    private static <T> void selectRowUnder(TableView<T> table, EventTarget target) {
        Node node = target instanceof Node n ? n : null;
        while (node != null && !(node instanceof TableRow)) node = node.getParent();
        if (node instanceof TableRow<?> row && !row.isEmpty()) table.getSelectionModel().select(row.getIndex());
    }

    /** A row as its columns show it (the columns that have a value; the buttons and the pictures are left out). */
    private static <T> String rowText(TableView<T> table, T row) {
        List<String> cells = new ArrayList<>();
        for (TableColumn<T, ?> column : table.getVisibleLeafColumns()) {
            if (column.getCellValueFactory() == null) continue;
            ObservableValue<?> value = column.getCellObservableValue(row);
            Object shown = value == null ? null : value.getValue();
            cells.add(shown == null ? "" : String.valueOf(shown));
        }
        return CopyText.row(cells);
    }

    // ---- the menus of the four big tables

    /** Order history: the number as the list shows it, the transfer note of the order, and the window's own buttons. */
    static void history(TableView<OrderDAO.OrderSummary> table, Button... buttons) {
        List<Item<OrderDAO.OrderSummary>> items = new ArrayList<>();
        items.add(copy("copy.order", s -> CopyText.orderNumber(s.order().getId())));
        items.add(copy("copy.note", s -> CopyText.transferNote(s.order().getId())));
        for (Button button : buttons) items.add(button(button));
        install(table, items);
    }

    /** Customers: the phone (nothing when the list shows a dash), the name, edit, and the purchase-history button. */
    static void customers(TableView<Customer> table, Consumer<Customer> edit, Button history) {
        install(table, copy("copy.phone", (Customer c) -> CopyText.phone(c.getPhone())), copy("copy.name", Customer::getFullName),
                action("common.edit", edit), button(history));
    }

    /** Products: the name, edit and delete, which are what the buttons of the row do. */
    static void products(TableView<Product> table, Consumer<Product> edit, Consumer<Product> delete) {
        install(table, copy("copy.name", Product::getName), action("common.edit", edit), action("common.delete", delete));
    }
}
