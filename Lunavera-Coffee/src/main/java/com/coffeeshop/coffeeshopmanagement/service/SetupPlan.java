package com.coffeeshop.coffeeshopmanagement.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What the first-time setup window (TODO 4.15) asks for, checked and turned into actions without a window or a database, so it
 * can be tested. Today it is the number of tables: the starter data has twelve, and the owner says how many the shop has.
 */
public final class SetupPlan {

    /** The seats given to a table the setup adds; the owner changes them later in "Quản lý bàn". */
    public static final int DEFAULT_SEATS = 4;

    private SetupPlan() {}

    /** What to do to reach the wanted number of tables: add {@code toAdd} new ones and remove the tables numbered {@code toRemove}. */
    public record TablePlan(int toAdd, List<Integer> toRemove) {}

    /** The number typed in the box, or null when it is not a whole number from 1 to {@link TableRules#MAX_TABLES}. */
    public static Integer parseTableCount(String typed) {
        if (typed == null) return null;
        String text = typed.trim();
        if (text.isEmpty() || text.length() > 4) return null;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') return null;
        }
        int value = Integer.parseInt(text);
        return value >= 1 && value <= TableRules.MAX_TABLES ? value : null;
    }

    /**
     * To get from the tables that exist to {@code wanted} tables: more are added at the end, and fewer are reached by removing the
     * highest-numbered ones first (the ones the owner most likely never meant to have).
     */
    public static TablePlan planTables(List<Integer> existingNumbers, int wanted) {
        int have = existingNumbers.size();
        if (wanted >= have) {
            return new TablePlan(wanted - have, List.of());
        }
        List<Integer> highestFirst = new ArrayList<>(existingNumbers);
        highestFirst.sort(Comparator.reverseOrder());
        return new TablePlan(0, new ArrayList<>(highestFirst.subList(0, have - wanted)));
    }
}
