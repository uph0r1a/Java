package com.coffeeshop.coffeeshopmanagement.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An ESTIMATE of what a person earned from the hours they clocked (TODO 4.60): the hourly rate times the hours. It is not a payroll: it
 * knows nothing of overtime, breaks, bonuses, tax or insurance, and the hours are the clocked ones (a forgotten clock-out counts only up
 * to its limit). The salary in the staff table can be read two ways, so the owner says which in Settings ({@link ShopRules.Values#salaryHourly()}):
 * a monthly salary is divided by the hours of a month's work ({@link ShopRules.Values#monthlyHours()}) to get the hourly rate, an hourly
 * figure is used as it is. No JavaFX and no database, so it is unit-tested.
 */
public final class EarningsRules {

    private EarningsRules() {
    }

    /**
     * The rate for one hour in whole dong, or null when there is no salary to work from (missing or not above zero).
     *
     * @param salary        the figure in the staff table
     * @param salaryHourly  true when that figure is already per hour, false when it is per month
     * @param monthlyHours  the hours in a month's work, used for a monthly salary
     */
    public static BigDecimal hourlyRate(BigDecimal salary, boolean salaryHourly, int monthlyHours) {
        if (salary == null || salary.signum() <= 0) return null;
        if (salaryHourly) return salary.setScale(0, RoundingMode.HALF_UP);
        if (monthlyHours <= 0) return null;
        return salary.divide(BigDecimal.valueOf(monthlyHours), 0, RoundingMode.HALF_UP);
    }

    /** The same, reading the choice from the rules in force. */
    public static BigDecimal hourlyRate(BigDecimal salary, ShopRules.Values rules) {
        return hourlyRate(salary, rules.salaryHourly() == 1, rules.monthlyHours());
    }

    /** What {@code minutes} of work come to at {@code hourlyRate} (whole dong, rounded half up); null when there is no rate. */
    public static BigDecimal estimate(long minutes, BigDecimal hourlyRate) {
        if (hourlyRate == null || minutes < 0) return null;
        return hourlyRate.multiply(BigDecimal.valueOf(minutes)).divide(BigDecimal.valueOf(60), 0, RoundingMode.HALF_UP);
    }
}
