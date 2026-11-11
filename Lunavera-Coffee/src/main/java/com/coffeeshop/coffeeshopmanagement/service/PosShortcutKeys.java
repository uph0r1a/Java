package com.coffeeshop.coffeeshopmanagement.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The keyboard shortcuts of the till screen (TODO 5.45), as plain data with no JavaFX: which key does what, and the text that
 * the help list shows. The screen side is {@code PosShortcuts} (it turns each {@link Key} into a JavaFX key combination and
 * puts it on the scene); keeping the table here lets a test check that no two actions share a key.
 *
 * <p>{@code code} is the name of a {@code javafx.scene.input.KeyCode} constant. A new shortcut is a new {@link Action} and a
 * new line in {@link #ENTRIES}; {@code PosShortcutKeysTest} fails when two lines use the same key or when a label is missing
 * from a language file.
 */
public final class PosShortcutKeys {

    /** What a key does. The till gives each one a handler; the screen refuses to start when one has none. */
    public enum Action { FIND, PAY, CLEAR_SEARCH, REMOVE_LINE, MORE, LESS, FIND_CUSTOMER, LOCK, HELP }

    /** One key, with the modifiers that must be held (and, by JavaFX's rule, no others). */
    public record Key(String code, boolean ctrl, boolean shift) {
        static Key of(String code) {
            return new Key(code, false, false);
        }

        static Key ctrl(String code) {
            return new Key(code, true, false);
        }

        static Key shift(String code) {
            return new Key(code, false, true);
        }

        /** A text that tells two keys apart, e.g. {@code CTRL+F}. */
        public String id() {
            return (ctrl ? "CTRL+" : "") + (shift ? "SHIFT+" : "") + code;
        }
    }

    /**
     * One row of the help list: the action, the key(s) that run it, the text of the key as a cashier reads it, and the language key
     * of what it does. {@code needsNoTyping}: the key is also a character you type (a digit sign, Delete in a box), so it does
     * nothing while the cursor is in a text box.
     */
    public record Entry(Action action, List<Key> keys, String display, String labelKey, boolean needsNoTyping) {
    }

    public static final List<Entry> ENTRIES = List.of(
            new Entry(Action.FIND, List.of(Key.ctrl("F")), "Ctrl+F", "pos.key.find", false),
            new Entry(Action.PAY, List.of(Key.ctrl("ENTER")), "Ctrl+Enter", "pos.key.pay", false),
            new Entry(Action.CLEAR_SEARCH, List.of(Key.of("ESCAPE")), "Esc", "pos.key.clear", false),
            new Entry(Action.REMOVE_LINE, List.of(Key.of("DELETE")), "Delete", "pos.key.remove", true),
            // "+" is Shift+= on most keyboards; plain = is accepted too, so no Shift is needed; the number pad has its own keys.
            new Entry(Action.MORE, List.of(Key.of("ADD"), Key.shift("EQUALS"), Key.of("EQUALS")), "+", "pos.key.more", true),
            new Entry(Action.LESS, List.of(Key.of("SUBTRACT"), Key.of("MINUS")), "-", "pos.key.less", true),
            new Entry(Action.FIND_CUSTOMER, List.of(Key.of("F4")), "F4", "pos.key.customer", false),
            new Entry(Action.LOCK, List.of(Key.ctrl("L")), "Ctrl+L", "pos.key.lock", false),
            new Entry(Action.HELP, List.of(Key.of("F1")), "F1", "pos.key.help", false));

    private PosShortcutKeys() {
    }

    /** Every key that two different entries claim, as {@code key id -> the actions}; empty when the table is sound. */
    public static Map<String, List<Action>> clashes() {
        Map<String, List<Action>> byKey = new LinkedHashMap<>();
        for (Entry entry : ENTRIES) {
            for (Key key : entry.keys()) {
                byKey.computeIfAbsent(key.id(), k -> new ArrayList<>()).add(entry.action());
            }
        }
        byKey.values().removeIf(actions -> actions.size() < 2);
        return byKey;
    }
}
