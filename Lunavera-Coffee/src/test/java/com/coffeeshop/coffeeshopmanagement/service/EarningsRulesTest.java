package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

/** TODO 4.60: the pay estimate is the hourly rate times the clocked hours; a monthly salary becomes an hourly rate first. */
public class EarningsRulesTest {

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    @Test
    public void aMonthlySalaryIsDividedByTheHoursOfAMonthsWork() {
        assertEquals(d("25000"), EarningsRules.hourlyRate(d("5200000"), false, 208));
        assertEquals("rounded half up to the dong", d("26923"), EarningsRules.hourlyRate(d("7000000"), false, 260));
    }

    @Test
    public void anHourlyFigureIsUsedAsItIsWhateverTheHoursOfAMonth() {
        assertEquals(d("30000"), EarningsRules.hourlyRate(d("30000"), true, 208));
        assertEquals(d("30000"), EarningsRules.hourlyRate(d("30000"), true, 40));
    }

    @Test
    public void noSalaryOrANonPositiveOneGivesNoRate() {
        assertNull(EarningsRules.hourlyRate(null, false, 208));
        assertNull(EarningsRules.hourlyRate(BigDecimal.ZERO, false, 208));
        assertNull(EarningsRules.hourlyRate(d("-5"), true, 208));
        assertNull("no hours in a month: no rate, not a division by zero", EarningsRules.hourlyRate(d("5200000"), false, 0));
    }

    @Test
    public void theEstimateIsTheRateTimesTheMinutesOverSixty() {
        assertEquals(d("25000"), EarningsRules.estimate(60, d("25000")));
        assertEquals(d("12500"), EarningsRules.estimate(30, d("25000")));
        assertEquals(d("0"), EarningsRules.estimate(0, d("25000")));
        assertEquals("8 hours 20 minutes", d("208333"), EarningsRules.estimate(500, d("25000")));
        assertEquals("rounded half up: 1 minute at 25.001", d("417"), EarningsRules.estimate(1, d("25001")));
    }

    @Test
    public void noRateOrNegativeMinutesGiveNoEstimate() {
        assertNull(EarningsRules.estimate(60, null));
        assertNull(EarningsRules.estimate(-1, d("25000")));
    }

    @Test
    public void aFullMonthAtTheDefaultHoursGivesBackTheSalary() {
        BigDecimal rate = EarningsRules.hourlyRate(d("5200000"), ShopRules.DEFAULTS);
        assertEquals("208 hours at the monthly rate come back to the monthly salary", d("5200000"), EarningsRules.estimate(208 * 60L, rate));
    }

    @Test
    public void theRulesInForceChooseHowTheSalaryIsRead() {
        ShopRules.Values hourly = new ShopRules.Values(10, 10_000, 1_000, 50, 20, 100, 500, 10, 90, 15, 16, 1, 208);
        assertEquals(d("30000"), EarningsRules.hourlyRate(d("30000"), hourly));
        assertEquals("a new shop reads it as monthly", d("144"), EarningsRules.hourlyRate(d("30000"), ShopRules.DEFAULTS));
    }
}
