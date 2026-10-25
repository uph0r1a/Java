package com.coffeeshop.coffeeshopmanagement.model;

import java.util.Locale;

/**
 * The words for a table, in one place (TODO 2.68): a free table is "Trống" (empty); it used to say "Không hoạt động"
 * (not active), which a new cashier reads as "out of service". The screens read the words from the language files
 * (<code>tables.status.free</code>, <code>tables.status.occupied</code>); the names below are only a plain fallback.
 */
public enum TableStatus {
    EMPTY("Trống"),
    OCCUPIED("Đang sử dụng");

    private final String displayName;

    TableStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static TableStatus fromDb(String value) {
        if (value == null) return EMPTY;
        try {
            return TableStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return EMPTY;
        }
    }
}
