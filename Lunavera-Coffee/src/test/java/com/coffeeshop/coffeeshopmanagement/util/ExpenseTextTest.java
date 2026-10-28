package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.service.ExpenseRules;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** TODO 4.38: the net-profit line and the CSV rows of the report. */
public class ExpenseTextTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static ReportDAO.Expenses expenses(String rent, String supplies) {
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        byCategory.put(ExpenseRules.RENT, d(rent));
        byCategory.put(ExpenseRules.SUPPLIES, d(supplies));
        return new ReportDAO.Expenses(d(rent).add(d(supplies)), 2, byCategory);
    }

    private static final ReportDAO.Waste WASTE = new ReportDAO.Waste(1, 2, d("30000"), 0);

    @Test
    public void netProfitTakesOffWasteAndExpenses() {
        assertEquals(0, d("670000").compareTo(ExpenseText.net(d("1000000"), WASTE, expenses("200000", "100000"))));
        assertEquals("no expenses, no waste: the gross profit", 0, d("1000000").compareTo(ExpenseText.net(d("1000000"), null, null)));
    }

    @Test
    public void theSummaryAlwaysSaysNetProfitAndListsCategoriesOnlyWhenThereAreExpenses() {
        String none = ExpenseText.summaryLines(d("1000000"), new ReportDAO.Waste(0, 0, BigDecimal.ZERO, 0),
                new ReportDAO.Expenses(BigDecimal.ZERO, 0, new LinkedHashMap<>()));
        assertFalse("one line only", none.contains("\n"));
        assertFalse(none.isBlank());

        String some = ExpenseText.summaryLines(d("1000000"), WASTE, expenses("200000", "100000"));
        String[] lines = some.split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[1].contains(CurrencyUtil.format(d("200000"))));
    }

    @Test
    public void theCsvAlwaysHasTheSameRows() {
        List<String> none = ExpenseText.csvLines(d("1000"), null, null);
        List<String> some = ExpenseText.csvLines(d("1000000"), WASTE, expenses("200000", "100000"));
        assertEquals("total + six categories + net profit", 8, none.size());
        assertEquals(none.size(), some.size());
        assertTrue(some.get(0).endsWith(",300000"));
        assertTrue("net profit is the last row", some.get(7).endsWith(",670000"));
        assertTrue(none.get(7).endsWith(",1000"));
    }
}
