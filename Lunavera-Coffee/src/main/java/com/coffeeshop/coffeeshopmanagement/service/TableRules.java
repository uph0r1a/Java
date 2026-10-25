package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextNormalizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * What a table name and a seat count must look like when the owner adds or edits a table (TODO 4.18). Pure functions, so the
 * rules are tested without a database. Each check returns the i18n key of the problem, or null when the input is fine.
 */
public final class TableRules {

    public static final int MAX_NAME = 40;
    public static final int MAX_CAPACITY = 99;
    /** More than this many tables is almost certainly a typing slip, and the map would be unusable. */
    public static final int MAX_TABLES = 200;

    private TableRules() {
    }

    /**
     * {@code otherNames}: every name the OTHER tables (not the one being edited) can show, see {@link #namesOf}. Compared
     * ignoring case, repeated spaces and how an accent is encoded (one letter or letter plus mark), like the category names.
     */
    public static String nameProblem(String name, Collection<String> otherNames) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) {
            return "tablemgr.val.name.required";
        }
        if (clean.length() > MAX_NAME) {
            return "tablemgr.val.name.long";
        }
        String key = key(clean);
        for (String other : otherNames) {
            if (other != null && key(other).equals(key)) {
                return "tablemgr.val.name.duplicate";
            }
        }
        return null;
    }

    /** The seats typed in the box: a whole number from 1 to {@link #MAX_CAPACITY}. */
    public static String capacityProblem(String text) {
        Integer value = parseCapacity(text);
        return value == null ? "tablemgr.val.capacity" : null;
    }

    /** The number, or null when the text is not a whole number in range. */
    public static Integer parseCapacity(String text) {
        if (text == null) {
            return null;
        }
        String clean = text.trim();
        if (clean.isEmpty() || clean.length() > 3) {
            return null;
        }
        for (int i = 0; i < clean.length(); i++) {
            if (clean.charAt(i) < '0' || clean.charAt(i) > '9') {
                return null;
            }
        }
        int value = Integer.parseInt(clean);
        return value >= 1 && value <= MAX_CAPACITY ? value : null;
    }

    /**
     * The number a new table gets: one more than the highest number in use by a table OR by any order ever written. An order keeps
     * the NUMBER of its table, so reusing the number of a removed table would put old history on the new table.
     */
    public static int nextNumber(int highestTableNumber, int highestOrderTableNumber) {
        return Math.max(Math.max(highestTableNumber, highestOrderTableNumber), 0) + 1;
    }

    /** The text of a problem key in the language on screen. */
    public static String describe(String problemKey) {
        return I18n.t(problemKey, MAX_NAME, MAX_CAPACITY);
    }

    /**
     * Every name a table answers to, for the "is this name taken" check (TODO 4.18). Its stored name, the name shown now, and
     * for a starter table ("Bàn 5") the starter name in both languages: "Bàn 5" and "Table 5" are the same table.
     */
    public static List<String> namesOf(int tableNumber, String storedName) {
        List<String> names = new ArrayList<>();
        if (storedName != null) {
            names.add(storedName);
        }
        if (storedName != null && key(storedName).equals(key(STARTER_PREFIX + tableNumber))) {
            names.add(I18n.in(I18n.VI, "orders.table", tableNumber));
            names.add(I18n.in(I18n.EN, "orders.table", tableNumber));
        }
        return names;
    }

    /**
     * What to save when the owner presses OK in the edit box, which was filled with the name SHOWN ({@code shown}). Typing
     * nothing new keeps the stored name: otherwise changing only the seats of "Bàn 5" while the screen is in English would
     * save "Table 5" and the table would stay English when the language is changed back (TODO 4.18).
     */
    public static String nameToStore(String typed, String storedName, String shown) {
        String clean = typed == null ? "" : TextNormalizer.nfc(typed);
        if (storedName != null && shown != null && key(clean).equals(key(shown))) {
            return storedName;
        }
        return clean;
    }

    private static final String STARTER_PREFIX = "B\u00e0n ";

    private static String key(String name) {
        return TextNormalizer.key(name);
    }
}
