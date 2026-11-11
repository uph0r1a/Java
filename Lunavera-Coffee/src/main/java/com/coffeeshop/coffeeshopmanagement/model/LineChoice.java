package com.coffeeshop.coffeeshopmanagement.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the cashier picked for one cart line (TODO 4.47): at most one size and any extras, with what they add to the price and cost
 * of ONE piece. It is a snapshot: names and amounts are copied from the options at the moment of the tap, so it stays what was sold
 * even if the owner edits the options afterwards. {@code NONE} is a plain drink.
 *
 * @param sizeName    the chosen size, null when the product has no sizes (or none was chosen)
 * @param sizePercent how much of the recipe one piece uses, null when there is no size (= 100)
 * @param extras      the chosen extras in the order of the list (empty when none)
 * @param priceDelta  size difference plus the extras' prices, added to the product's price
 * @param costDelta   size cost difference plus the extras' costs, added to the product's cost when the line is paid
 */
public record LineChoice(String sizeName, Integer sizePercent, List<Extra> extras, BigDecimal priceDelta, BigDecimal costDelta) {

    /** One chosen extra with its price at the time. */
    public record Extra(String name, BigDecimal price) {
    }

    /** A plain drink: no size, no extras. */
    public static final LineChoice NONE = new LineChoice(null, null, List.of(), BigDecimal.ZERO, BigDecimal.ZERO);

    public LineChoice {
        extras = extras == null ? List.of() : List.copyOf(extras);
        priceDelta = priceDelta == null ? BigDecimal.ZERO : priceDelta;
        costDelta = costDelta == null ? BigDecimal.ZERO : costDelta;
    }

    public boolean isNone() {
        return sizeName == null && extras.isEmpty();
    }
}
