package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * What an ingredient costs and what a drink's recipe therefore costs (TODO 4.51). No screen and no database, so the dialogs and the
 * DAO say the same thing and the arithmetic is tested alone.
 *
 * <p>Money is whole dong everywhere in the program, so an ingredient's price is <b>whole dong per unit</b> (per gram, per millilitre,
 * per piece) and the cost of a recipe is the exact sum of {@code amount * price}: only the price of a unit is ever rounded, to the
 * nearest dong. A unit worth less than one dong (a gram of ice) cannot be priced; the owner then leaves it without a price and the
 * recipe says that its cost is not complete.
 *
 * <p>Which price a delivery leaves behind is decided in one place, {@link #afterDelivery}: the average of what is on the shelf and
 * what just came in, weighted by quantity; when nothing is on the shelf (or nothing was priced before) it is simply the new price.
 */
public final class IngredientCostRules {

    /** The dearest price of one unit accepted; generous (10.000.000 đ for a gram), so only a typing slip is caught. */
    public static final long UNIT_COST_MAX = 10_000_000L;

    /** The largest total paid for one delivery that is accepted, in dong (a typing slip, not a real lot, is above it). */
    public static final long PAID_MAX = 1_000_000_000_000L;

    private IngredientCostRules() {
    }

    /**
     * What is wrong with a delivery, as a sentence; null when it is fine: a quantity of at least 1 within the stock ceiling, and,
     * when the total paid is given, an amount that comes to a price of one unit between 1 đ and {@link #UNIT_COST_MAX}.
     *
     * @param paid what was paid for the whole lot in whole dong, or null when it is not known
     */
    public static String deliveryProblem(long quantity, Long paid) {
        if (quantity <= 0) return I18n.t("ingredient.val.receive");
        String amount = IngredientRules.amountProblem(quantity);
        if (amount != null) return amount;
        if (paid == null) return null;
        if (paid <= 0 || paid > PAID_MAX) return I18n.t("ingredient.val.paid");
        long unit = unitCostFromPaid(paid, quantity);
        if (unit < 1) return I18n.t("ingredient.val.pricelow");
        return unitCostProblem(unit);
    }

    /** What is wrong with the price of one unit, as a sentence; null when it is fine (whole dong, from 1 up to {@link #UNIT_COST_MAX}). */
    public static String unitCostProblem(long unitCost) {
        if (unitCost < 1) return I18n.t("ingredient.val.price");
        if (unitCost > UNIT_COST_MAX) return I18n.t("ingredient.val.pricebig", UNIT_COST_MAX);
        return null;
    }

    /** What the price box held: {@code ok} false when it is not a whole amount of dong; {@code cost} null when it was left empty. */
    public record Typed(boolean ok, Long cost) {
    }

    /** Reads the optional price box of an ingredient: empty is "no price", anything that is not a whole amount of dong is not ok. */
    public static Typed parseOptionalUnitCost(String text) {
        if (text == null || text.isBlank()) return new Typed(true, null);
        BigDecimal value = MoneyParser.parseOptionalDong(text); // null for anything that is not a plain amount
        return value == null || value.signum() < 0 ? new Typed(false, null) : new Typed(true, value.longValueExact());
    }

    /** The total typed for a delivery ("what was paid for the whole lot"): empty is fine (null); not a whole amount of dong is not ok. */
    public static Typed parseOptionalPaid(String text) {
        return parseOptionalUnitCost(text);
    }

    /**
     * The price of one unit that a delivery implies: what was paid for the lot divided by the quantity, to the nearest dong
     * (halves up).
     *
     * @return the price, or 0 when it comes to less than half a dong a unit (too small to keep: {@link #unitCostProblem} says so)
     */
    public static long unitCostFromPaid(long paid, long quantity) {
        if (quantity <= 0 || paid <= 0) return 0;
        return (2 * paid + quantity) / (2 * quantity);
    }

    /**
     * The price per unit an ingredient has after a delivery: the stock on the shelf at its old price and the new quantity at the
     * new price, averaged by quantity and rounded to the nearest dong. With nothing on the shelf, or no earlier price, it is the new
     * price: the cost follows the shelf, not the history of what has ever been bought.
     */
    public static long afterDelivery(long stockBefore, Long unitCostBefore, long quantity, long newUnitCost) {
        if (unitCostBefore == null || stockBefore <= 0) return newUnitCost;
        long onShelf = stockBefore * unitCostBefore;
        long arrived = quantity * newUnitCost;
        long units = stockBefore + quantity;
        return (2 * (onShelf + arrived) + units) / (2 * units);
    }

    /** One line of a recipe for the sum: how much one serving takes and the ingredient's price per unit (null = not priced). */
    public record Line(String name, long amount, Long unitCost) {

        /** What this line costs in one serving; null when the ingredient has no price. */
        public Long cost() {
            return unitCost == null ? null : amount * unitCost;
        }
    }

    /**
     * The cost of one serving of a recipe.
     *
     * @param total   the sum of the lines that have a price
     * @param missing the names of the ingredients without a price, in recipe order
     * @param lines   how many lines the recipe has
     */
    public record RecipeCost(long total, List<String> missing, int lines) {

        /** True when the recipe has lines and every one of them is priced: only then is {@code total} the cost of a serving. */
        public boolean complete() {
            return lines > 0 && missing.isEmpty();
        }
    }

    public static RecipeCost recipeCost(List<Line> recipe) {
        long total = 0;
        List<String> missing = new ArrayList<>();
        for (Line line : recipe) {
            Long cost = line.cost();
            if (cost == null) missing.add(line.name());
            else total += cost;
        }
        return new RecipeCost(total, List.copyOf(missing), recipe.size());
    }
}
