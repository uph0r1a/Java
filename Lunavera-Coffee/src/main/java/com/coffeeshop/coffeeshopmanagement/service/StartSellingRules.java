package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.ShopSettings;

import java.util.List;
import java.util.Locale;

/**
 * "Start selling for real" (TODO 4.57): which tables are emptied, which are kept, and when the typed shop name is accepted.
 * Pure (no database, no screen), so a test can compare the two lists with the schema: a table that is in neither list is a
 * table somebody forgot to decide about.
 */
public final class StartSellingRules {

    /**
     * The tables that are emptied, in the order the rows can be deleted (a table that points at another comes first):
     * everything the till, the drawer, the stock book and the diary wrote while the owner was trying the program.
     */
    public static final List<String> EMPTIED = List.of(
            "stock_takes", "refunds", "order_payments", "gift_card_moves", "order_items", "orders", "gift_cards",
            "expenses", "stock_log", "shift_closes", "time_clock", "reservations", "audit_log");

    /** Emptied only when the owner ticks the second box. */
    public static final String CUSTOMERS = "customers";

    /**
     * The tables that are kept on purpose. Some are touched without losing a row: {@code dining_tables} (every table is freed),
     * {@code vouchers} (the use count goes back to 0) and {@code customers} when they are kept (points and the "birthday given"
     * day go back to nothing, because the orders that earned them are gone).
     */
    public static final List<String> KEPT = List.of(
            "employees", "users", "categories", "products", "dining_tables", "ingredients", "recipes", "product_options",
            "promotions", "promotion_targets", "vouchers", "login_attempts", "app_flags", "settings", "schema_version");

    private StartSellingRules() {
    }

    /** What the owner has to type: the shop's name as set in Settings (the program's own name until one is set). */
    public static String expectedName() {
        String name = clean(ShopSettings.cachedName());
        return name.isEmpty() ? ShopSettings.DEFAULT_NAME : name;
    }

    /** True when {@code typed} is the shop's name, ignoring capitals and extra spaces at the ends or in the middle. */
    public static boolean nameMatches(String typed, String shopName) {
        String expected = clean(shopName);
        if (expected.isEmpty()) expected = clean(ShopSettings.DEFAULT_NAME);
        String given = clean(typed);
        return !given.isEmpty() && given.equals(expected);
    }

    private static String clean(String text) {
        if (text == null) return "";
        return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
