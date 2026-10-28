package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What an expense may be (TODO 4.38, decision D8), kept apart from the screen and the database so it can be tested on its own.
 *
 * <p><b>D8 as built</b> (the project files did not contain the decision; change it if yours differs):
 * <ul>
 *   <li>An expense is a day, a whole-dong amount above zero (at most {@link #MAX_AMOUNT}), one of six fixed categories, an optional
 *       short note, and whether it was paid from the cash drawer.</li>
 *   <li>Only an administrator records an expense or deletes one. Any signed-in person may write down cash they took out of the
 *       drawer (always dated today), because that is what closing a shift has to count; they cannot change or delete it.</li>
 *   <li>Net profit = gross profit (4.20) minus the cost of waste (4.33) minus the expenses of the range. The cost of what was sold
 *       is already in the gross profit through each product's cost price, so the category {@code SUPPLIES} is for things that are
 *       <em>not</em> in a product's cost (cups, straws, cleaning): entering the milk of a product whose cost price already
 *       includes milk would take it off twice.</li>
 *   <li>A report counts an expense on the day it was spent ({@code spent_on}); a shift close counts a drawer cash-out in the shift
 *       during which it was written down ({@code created_at}), whatever day was typed.</li>
 * </ul>
 */
public final class ExpenseRules {

    public static final String RENT = "RENT";
    public static final String WAGES = "WAGES";
    public static final String UTILITIES = "UTILITIES";
    public static final String SUPPLIES = "SUPPLIES";
    public static final String EQUIPMENT = "EQUIPMENT";
    public static final String OTHER = "OTHER";

    /** The categories, in the order they are offered. The code is what is stored; the words come from the language file. */
    public static final List<String> CATEGORIES = List.of(RENT, WAGES, UTILITIES, SUPPLIES, EQUIPMENT, OTHER);

    /** One expense is at most this many dong (one billion): a bigger number is a typing mistake. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000");

    /** Nothing is older than this: a typo such as year 0026 is refused. */
    public static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);

    private ExpenseRules() {
    }

    public static boolean isKnownCategory(String code) {
        return code != null && CATEGORIES.contains(code);
    }

    /** The category in words, in the language in use; an unknown code is shown as it is. */
    public static String categoryName(String code) {
        return isKnownCategory(code) ? I18n.t("expense.cat." + code) : String.valueOf(code);
    }

    /**
     * What is wrong with an expense, as a sentence to show, or null when it is fine.
     *
     * @param today the day it is now (an expense cannot be dated after it)
     */
    public static String problem(LocalDate spentOn, BigDecimal amount, String category, LocalDate today) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
            return I18n.t("expense.val.amount");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            return I18n.t("expense.val.toobig");
        }
        if (!isKnownCategory(category)) {
            return I18n.t("expense.val.category");
        }
        if (spentOn == null || spentOn.isBefore(EARLIEST) || spentOn.isAfter(today)) {
            return I18n.t("expense.val.date");
        }
        return null;
    }

    /** Gross profit minus the cost of waste minus the expenses: what is left of the sales after everything written down. */
    public static BigDecimal netProfit(BigDecimal grossProfit, BigDecimal wasteCost, BigDecimal expenses) {
        return nz(grossProfit).subtract(nz(wasteCost)).subtract(nz(expenses));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
