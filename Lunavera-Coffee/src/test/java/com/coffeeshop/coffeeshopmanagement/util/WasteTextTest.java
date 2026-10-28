package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.33: what the report window and its CSV say about waste and breakage. */
public class WasteTextTest {

    private static ReportDAO.Waste waste(int lines, int pieces, String cost, int withoutCost) {
        return new ReportDAO.Waste(lines, pieces, new BigDecimal(cost), withoutCost);
    }

    @Test
    public void aQuietPeriodSaysNothing() {
        assertEquals("", WasteText.summaryLine(waste(0, 0, "0", 0)));
        assertEquals("", WasteText.summaryLine(null));
    }

    @Test
    public void theSummaryLineHasLinesPiecesAndCostInBothLanguages() {
        String before = I18n.current();
        try {
            I18n.setLanguage(I18n.EN);
            String en = WasteText.summaryLine(waste(3, 15, "29000", 0));
            assertTrue(en, en.contains("3") && en.contains("15") && en.contains(CurrencyUtil.format(new BigDecimal("29000"))));
            assertFalse("no note when every line has a cost", en.contains("not counted"));
            I18n.setLanguage(I18n.VI);
            String vi = WasteText.summaryLine(waste(3, 15, "29000", 0));
            assertTrue(vi, vi.contains("Hao hụt") && vi.contains(CurrencyUtil.format(new BigDecimal("29000"))));
        } finally {
            I18n.setLanguage(before);
        }
    }

    @Test
    public void linesWithoutACostPriceAreSaidToBeLeftOut() {
        String line = WasteText.summaryLine(waste(2, 4, "1000", 1));
        assertTrue(line, line.contains("1"));
        assertNotEquals(WasteText.summaryLine(waste(2, 4, "1000", 0)), line);
    }

    @Test
    public void theCsvAlwaysHasTheSameThreeRows() {
        List<String> some = WasteText.csvLines(waste(3, 15, "29000", 1));
        assertEquals(3, some.size());
        assertTrue(some.get(0).endsWith(",15"));
        assertTrue(some.get(1).endsWith(",29000"));
        assertTrue(some.get(2).endsWith(",1"));
        List<String> none = WasteText.csvLines(null);
        assertEquals(3, none.size());
        assertTrue(none.get(0).endsWith(",0") && none.get(1).endsWith(",0") && none.get(2).endsWith(",0"));
    }
}
