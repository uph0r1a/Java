package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.junit.Assert.*;

/** TODO 4.40: the words of the comparison, in both languages. */
public class SalesPatternsTextTest {

    private String language;

    @Before
    public void english() {
        language = I18n.current();
        I18n.setLanguage(I18n.EN);
    }

    @After
    public void restore() {
        I18n.setLanguage(language);
    }

    @Test
    public void hoursAreWrittenByHandWithTwoDigits() {
        assertEquals("09:00 - 10:00", SalesPatternsText.hourLabel(9));
        assertEquals("23:00 - 00:00", SalesPatternsText.hourLabel(23));
        assertEquals("00:00 - 01:00", SalesPatternsText.hourLabel(0));
    }

    @Test
    public void everyWeekdayHasAName() {
        for (DayOfWeek day : DayOfWeek.values()) {
            assertFalse(SalesPatternsText.weekdayName(day).startsWith("weekday."));
        }
        assertEquals("Monday", SalesPatternsText.weekdayName(DayOfWeek.MONDAY));
        I18n.setLanguage(I18n.VI);
        assertEquals("Chủ nhật", SalesPatternsText.weekdayName(DayOfWeek.SUNDAY));
    }

    @Test
    public void theChangeIsWrittenAsUpDownOrNewOrNone() {
        assertEquals("↑ up 15%", SalesPatternsText.changeText(SalesPatterns.change(BigDecimal.valueOf(115), BigDecimal.valueOf(100))));
        assertEquals("↓ down 50%", SalesPatternsText.changeText(SalesPatterns.change(BigDecimal.valueOf(50), BigDecimal.valueOf(100))));
        assertEquals("no change", SalesPatternsText.changeText(SalesPatterns.change(BigDecimal.TEN, BigDecimal.TEN)));
        assertEquals("new, nothing before", SalesPatternsText.changeText(SalesPatterns.change(BigDecimal.TEN, BigDecimal.ZERO)));
        assertEquals("no sales in either period", SalesPatternsText.changeText(SalesPatterns.change(BigDecimal.ZERO, BigDecimal.ZERO)));
        assertEquals("a change under one percent is not shown as 0", "↑ up <1%",
                SalesPatternsText.changeText(SalesPatterns.change(BigDecimal.valueOf(100_001), BigDecimal.valueOf(100_000))));
    }

    @Test
    public void theComparisonNamesThePreviousPeriodAndAllThreeFigures() {
        ReportDAO.Summary now = new ReportDAO.Summary(12, BigDecimal.valueOf(1_200_000), BigDecimal.ZERO, 0);
        ReportDAO.Summary before = new ReportDAO.Summary(10, BigDecimal.valueOf(1_000_000), BigDecimal.ZERO, 0);
        String text = SalesPatternsText.comparison(
                SalesPatterns.previousRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), now, before);
        assertTrue(text, text.contains("31/08/2026 - 30/09/2026"));
        assertTrue(text, text.contains("↑ up 20%"));          // revenue and orders both +20%
        assertTrue(text, text.contains("Orders 12"));
        assertTrue(text, text.contains("before: 10"));
        assertEquals("the average order is the same, 100.000 in both", 2, text.split("↑ up 20%", -1).length - 1);
        assertTrue(text, text.contains("no change"));
    }

    @Test
    public void aBarIsMadeOfBlocks() {
        assertEquals("", SalesPatternsText.bar(BigDecimal.ZERO, BigDecimal.TEN));
        assertEquals(20, SalesPatternsText.bar(BigDecimal.TEN, BigDecimal.TEN).length());
    }
}
