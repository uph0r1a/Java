package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.service.SalesPatterns;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 2.86: the small helpers moved out of {@code ReportWindow} still answer as they did. */
public class ReportTextsTest {

    @Before
    public void english() {
        I18n.setLanguage(I18n.EN);
    }

    @After
    public void backToVietnamese() {
        I18n.setLanguage(I18n.VI);
    }

    @Test
    public void aMissingCostIsADashNotAProfitOfZero() {
        assertEquals("-", ReportTexts.profitText(null));
        assertEquals(CurrencyUtil.format(BigDecimal.ZERO), ReportTexts.profitText(BigDecimal.ZERO));
    }

    @Test
    public void theProfitNoteIsEmptyWhenEveryLineHasACost() {
        assertEquals("", ReportTexts.profitNote(new ReportDAO.Profit(BigDecimal.TEN, 5, 0)));
        assertEquals("  " + I18n.t("report.profitnote", 2), ReportTexts.profitNote(new ReportDAO.Profit(BigDecimal.TEN, 5, 2)));
    }

    @Test
    public void theBusiestTextSaysNoneWithoutData() {
        assertEquals(I18n.t("report.busiest.none"), ReportTexts.busiestText(List.of(), List.of()));
    }

    @Test
    public void thePaymentTextNamesEachMethodWithItsAmount() {
        assertEquals(I18n.t("report.payment.none"), ReportTexts.paymentText(List.of()));
        String text = ReportTexts.paymentText(List.of(new ReportDAO.PaymentSplit(null, 2, BigDecimal.valueOf(50_000))));
        assertTrue(text, text.startsWith(I18n.t("report.payment.prefix")));
        assertTrue(text, text.contains(I18n.t("report.payment.unknown") + ": " + CurrencyUtil.format(BigDecimal.valueOf(50_000))));
    }

    @Test
    public void theLongestBarIsTheLargestValueOrZero() {
        assertEquals(BigDecimal.ZERO, ReportTexts.maxOrders(List.of()));
        assertEquals(BigDecimal.valueOf(9), ReportTexts.maxOrders(List.of(new SalesPatterns.Slot(8, 3, BigDecimal.ONE), new SalesPatterns.Slot(9, 9, BigDecimal.ONE))));
        assertEquals(BigDecimal.valueOf(30), ReportTexts.maxRevenue(List.of(new ReportDAO.CategorySales("a", 1, BigDecimal.TEN), new ReportDAO.CategorySales("b", 1, BigDecimal.valueOf(30)))));
    }

    @Test
    public void aMissingCategoryAndAMissingPersonHaveWords() {
        assertEquals(I18n.t("report.category.none"), ReportTexts.categoryName(new ReportDAO.CategorySales(null, 1, BigDecimal.ONE)));
        assertEquals(I18n.t("report.employee.unknown"),
                ReportTexts.employeeName(new com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO.EmployeeShare(null, " ", 1, BigDecimal.ONE, BigDecimal.ZERO)));
    }
}
