package com.coffeeshop.coffeeshopmanagement.model;

import java.util.Locale;

public enum OrderStatus {
    OPEN,
    PAID,
    CANCELLED;

    public static OrderStatus fromDb(String value) {
        if (value == null) return OPEN;
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OPEN;
        }
    }
}
