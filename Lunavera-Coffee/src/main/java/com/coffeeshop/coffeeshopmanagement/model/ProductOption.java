package com.coffeeshop.coffeeshopmanagement.model;

import java.math.BigDecimal;

/**
 * One option of a product (TODO 4.47): a size or an extra, as the owner defined it. Never copied onto an order as it is: the till
 * writes the NAME and the PRICE at the time of the sale onto the order line (see {@link LineChoice}), so changing or deleting an
 * option later never moves an old order.
 *
 * @param id            the row id, 0 for an option that is not saved yet
 * @param kind          size or extra
 * @param name          what the cashier taps and the receipt prints: "L", "Extra shot"
 * @param priceDelta    added to the product's price (a size may be negative: "S" = -5.000); an extra is never negative
 * @param costDelta     added to the cost of one piece, so the profit of a big or loaded drink is not overstated; 0 when not known
 * @param recipePercent a size only: how much of the product's recipe one piece uses, 100 = the normal amount (an extra has 100)
 * @param isDefault     a size only: the one that is already chosen when the box opens
 * @param active        false = switched off at the till (kept so the owner can switch it on again)
 */
public record ProductOption(int id, int productId, OptionKind kind, String name, BigDecimal priceDelta, BigDecimal costDelta,
                            int recipePercent, boolean isDefault, boolean active) {

    /** The recipe amount that means "the normal serving". */
    public static final int NORMAL_PERCENT = 100;

    public boolean isSize() {
        return kind == OptionKind.SIZE;
    }
}
