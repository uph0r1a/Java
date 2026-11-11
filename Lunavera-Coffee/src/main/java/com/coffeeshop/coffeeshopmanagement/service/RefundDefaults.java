package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.RefundDAO.RefundableLine;

import java.util.List;
import java.util.Map;

/**
 * What the refund boxes suggest, by the kind of product (TODO 2.66 / 6.90 d). The kind is "made to order": the product has a recipe, so
 * the drink or dish was made for the customer and, when it comes back, it is thrown away, not put on the shelf. Everything else keeps
 * the answer it always had. The suggestion only ever moves toward "do not put it back": a shop that has not entered recipes sees no change,
 * and stock can never be raised by cups that no longer exist because of a guess. The cashier can change both answers. Pure: no database.
 */
public final class RefundDefaults {

    /** The i18n key of the reason that is filled in when something made to order is refunded. */
    public static final String MADE_REASON_KEY = "refund.default.made";

    private RefundDefaults() {
    }

    /** Whether any line that is being refunded was made to order; {@code request} is item id to pieces, zero pieces do not count. */
    public static boolean anyMade(List<RefundableLine> lines, Map<Integer, Integer> request) {
        for (RefundableLine line : lines) {
            Integer pieces = request.get(line.itemId());
            if (line.madeFromRecipe() && pieces != null && pieces > 0) return true;
        }
        return false;
    }

    /** Whether any line of the order was made to order (the whole order is being refunded). */
    public static boolean anyMade(List<RefundableLine> lines) {
        return lines.stream().anyMatch(RefundableLine::madeFromRecipe);
    }

    /** The box "put the goods back into stock" for the lines in the request: ticked unless something made to order is among them. */
    public static boolean returnToStock(boolean anyMade) {
        return !anyMade;
    }

    /** The key of the reason to fill in, or null when there is none to suggest. */
    public static String reasonKey(boolean anyMade) {
        return anyMade ? MADE_REASON_KEY : null;
    }
}
