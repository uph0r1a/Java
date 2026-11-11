package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.service.PosShortcutKeys;
import com.coffeeshop.coffeeshopmanagement.service.PosShortcutKeys.Action;
import com.coffeeshop.coffeeshopmanagement.service.PosShortcutKeys.Entry;
import com.coffeeshop.coffeeshopmanagement.service.PosShortcutKeys.Key;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The keyboard shortcuts of the till (TODO 5.45; the table is {@link PosShortcutKeys}, which a test checks for two actions on one
 * key). Ctrl+F and Ctrl+Enter were the only two before (TODO 5.7); now also Esc (clear the search box), Delete / + / - on the
 * selected cart line, F4 (the customer box), Ctrl+L (lock the till, TODO 4.36) and F1 (the list of keys, also behind the small
 * "?" beside the search box).
 *
 * <p>The keys that are also characters (Delete, + and -) do nothing while the cursor is in a text box, so a phone number or a
 * quantity can still be typed. "+" and "-" act on the selected line, or on the last line when none is selected; Delete only on a
 * selected line, and it asks first (the same {@code changeQuantity} path as the "-" button). The keys are taken off the scene again
 * when this screen is replaced, so they never reach the next one. Kept out of {@code OrderController}, which is at its size ceiling.
 */
final class PosShortcuts {

    private PosShortcuts() {
    }

    /**
     * Registers every shortcut on the scene that {@code search} belongs to, now or when it gets one.
     *
     * @param changeBy the till's own quantity change for a line ({@code adjustQuantity}): the stock and the "remove the line?" rules are there
     */
    static void install(TextField search, TextField customerField, TableView<OrderItem> cart, Runnable pay,
                        BiConsumer<OrderItem, Integer> changeBy) {
        Map<Action, Runnable> handlers = new EnumMap<>(Action.class);
        handlers.put(Action.FIND, () -> { search.requestFocus(); search.selectAll(); });
        handlers.put(Action.PAY, pay);
        handlers.put(Action.CLEAR_SEARCH, search::clear);
        handlers.put(Action.REMOVE_LINE, () -> {
            OrderItem line = cart.getSelectionModel().getSelectedItem();
            if (line != null) change(cart, line, -line.getQuantity(), changeBy);
        });
        handlers.put(Action.MORE, () -> changeSelected(cart, 1, changeBy));
        handlers.put(Action.LESS, () -> changeSelected(cart, -1, changeBy));
        handlers.put(Action.FIND_CUSTOMER, () -> { if (customerField != null) { customerField.requestFocus(); customerField.selectAll(); } });
        handlers.put(Action.LOCK, LockScreen::lockNow);
        handlers.put(Action.HELP, PosShortcuts::showHelp);
        for (Action action : Action.values()) {
            if (!handlers.containsKey(action)) throw new IllegalStateException("no handler for the shortcut " + action);
        }

        // Each key with the action that runs it; built once, put on a scene and taken off it again.
        Map<KeyCombination, Runnable> keys = new java.util.LinkedHashMap<>();
        for (Entry entry : PosShortcutKeys.ENTRIES) {
            Runnable run = handlers.get(entry.action());
            for (Key key : entry.keys()) {
                keys.put(combination(key), entry.needsNoTyping() ? () -> { if (!isTyping(search)) run.run(); } : run);
            }
        }
        search.sceneProperty().addListener((o, old, scene) -> {
            if (old != null) keys.keySet().forEach(old.getAccelerators()::remove);
            if (scene != null) keys.forEach(scene.getAccelerators()::put);
        });
        if (search.getScene() != null) keys.forEach(search.getScene().getAccelerators()::put);

        search.setTooltip(new Tooltip(I18n.t("pos.tip.search")));
        addHelpButton(search, handlers.get(Action.HELP));
    }

    /** True while the cursor is in a box where the person types (the search box, a phone, a typed quantity). */
    private static boolean isTyping(Node anyNode) {
        Scene scene = anyNode.getScene();
        Node focus = scene == null ? null : scene.getFocusOwner();
        return focus instanceof TextInputControl || (focus instanceof ComboBoxBase<?> box && box.isEditable());
    }

    private static void changeSelected(TableView<OrderItem> cart, int delta, BiConsumer<OrderItem, Integer> changeBy) {
        OrderItem line = cart.getSelectionModel().getSelectedItem();
        if (line == null && !cart.getItems().isEmpty()) line = cart.getItems().get(cart.getItems().size() - 1);
        if (line != null) change(cart, line, delta, changeBy);
    }

    /** Changes the line, then selects the same place again: refreshing the cart clears the selection, and a second key press needs it. */
    private static void change(TableView<OrderItem> cart, OrderItem line, int delta, BiConsumer<OrderItem, Integer> changeBy) {
        int at = Math.max(0, cart.getItems().indexOf(line));
        changeBy.accept(line, delta);
        int size = cart.getItems().size();
        if (size > 0) cart.getSelectionModel().select(Math.min(at, size - 1));
    }

    static KeyCombination combination(Key key) {
        List<KeyCombination.Modifier> modifiers = new ArrayList<>();
        if (key.ctrl()) modifiers.add(KeyCombination.CONTROL_DOWN);
        if (key.shift()) modifiers.add(KeyCombination.SHIFT_DOWN);
        return new KeyCodeCombination(KeyCode.valueOf(key.code()), modifiers.toArray(new KeyCombination.Modifier[0]));
    }

    /** A small "?" at the end of the search bar; it opens the same list as F1. */
    private static void addHelpButton(TextField search, Runnable showHelp) {
        if (!(search.getParent() instanceof HBox bar)) return;
        for (Node child : bar.getChildren()) {
            if ("posHelpButton".equals(child.getId())) return; // the screen is built once; never two buttons
        }
        Button help = new Button("?");
        help.setId("posHelpButton");
        help.setPrefHeight(32);
        help.setTooltip(new Tooltip(I18n.t("pos.key.help.tip")));
        help.setAccessibleText(I18n.t("pos.key.help.tip"));
        help.setFocusTraversable(false); // Tab goes from the search box to the products, not through a help button
        help.setOnAction(e -> showHelp.run());
        bar.getChildren().add(help);
    }

    /** One line per shortcut: the key, then what it does. */
    static String helpText() {
        StringBuilder text = new StringBuilder();
        for (Entry entry : PosShortcutKeys.ENTRIES) {
            text.append(entry.display()).append("   ").append(I18n.t(entry.labelKey())).append('\n');
        }
        return text.append('\n').append(I18n.t("pos.key.help.note")).toString();
    }

    private static void showHelp() {
        AlertUtil.info(I18n.t("pos.key.help.title"), helpText());
    }
}
