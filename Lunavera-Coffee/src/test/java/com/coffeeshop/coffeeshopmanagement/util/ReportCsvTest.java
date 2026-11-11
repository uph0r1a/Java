package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.GiftCardDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.DailySales;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.ProductSales;
import com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.ShiftClose;
import com.coffeeshop.coffeeshopmanagement.service.SalesPatterns;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

/**
 * TODO 2.86: the lines of the report's CSV file were built inside {@code ReportWindow.exportCsv}; moving them into {@link ReportCsv}
 * must not change the file. The blocks and their order are pinned here, and the file's values for one made-up report.
 */
public class ReportCsvTest {

    @Before
    public void english() {
        I18n.setLanguage(I18n.EN);
    }

    @After
    public void backToVietnamese() {
        I18n.setLanguage(I18n.VI);
    }

    private static BigDecimal n(long v) {
        return BigDecimal.valueOf(v);
    }

    private static ShiftClose shift() {
        return new ShiftClose(7, LocalDateTime.of(2026, 11, 1, 8, 0), LocalDateTime.of(2026, 11, 1, 22, 0), 1, "An", 12, n(900_000),
                n(400_000), n(100_000), n(500_000), n(500_000), BigDecimal.ZERO, "", BigDecimal.ZERO);
    }

    private static ReportCsv.Data data(List<ShiftClose> shifts) {
        return new ReportCsv.Data(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3),
                new ReportDAO.Summary(10, n(900_000), n(30_000), 2),
                new ReportDAO.Profit(n(350_000), 40, 3), new ReportDAO.Waste(0, 0, BigDecimal.ZERO, 0),
                new ReportDAO.Expenses(BigDecimal.ZERO, 0, java.util.Map.of()),
                new SalesPatterns.Range(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 31)),
                new ReportDAO.Summary(8, n(700_000), BigDecimal.ZERO, 0),
                List.of(new ReportDAO.PaymentSplit(PaymentMethod.CASH, 6, n(500_000)), new ReportDAO.PaymentSplit(null, 1, n(50_000))),
                List.of(), List.of(), new GiftCardDAO.OpenBalance(0, 0),
                List.of(new ProductSales("Cà phê, sữa", 25, n(750_000), n(300_000)), new ProductSales("Trà", 3, n(60_000), null)),
                List.of(new SalesPatterns.Slot(9, 4, n(200_000))),
                List.of(new SalesPatterns.WeekdaySlot(DayOfWeek.SATURDAY, 5, n(400_000), 1, n(400_000))),
                List.of(new ReportDAO.CategorySales("Cà phê", 20, n(600_000)), new ReportDAO.CategorySales(null, 1, n(10_000))),
                List.of("OPTION-BLOCK"),
                List.of(new ShiftDAO.EmployeeShare(1, "An", 9, n(800_000), n(300_000)), new ShiftDAO.EmployeeShare(null, null, 1, n(100_000), BigDecimal.ZERO)),
                shifts,
                List.of(new DailySales(LocalDate.of(2026, 11, 1), 4, n(300_000), n(120_000)), new DailySales(LocalDate.of(2026, 11, 2), 2, n(100_000), null)));
    }

    private static int at(List<String> lines, String start) {
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).startsWith(start)) return i;
        fail("no line starts with \"" + start + "\" in " + lines);
        return -1;
    }

    @Test
    public void theTotalsComeFirstWithTheValuesOfTheRange() {
        List<String> lines = ReportCsv.lines(data(List.of()));
        assertEquals(I18n.t("report.title") + ",01/11/2026,03/11/2026", lines.get(0));
        assertTrue(lines.contains(I18n.t("report.csv.paid") + ",10"));
        assertTrue(lines.contains(I18n.t("report.csv.revenue") + ",900000"));
        assertTrue(lines.contains(I18n.t("report.csv.discount") + ",30000"));
        assertTrue(lines.contains(I18n.t("report.csv.cancelled") + ",2"));
        assertTrue(lines.contains(I18n.t("report.csv.profit") + ",350000"));
        assertTrue(lines.contains(I18n.t("report.csv.nocost") + ",3"));
        assertTrue(lines.contains(I18n.t("report.csv.prev") + ",29/10/2026,31/10/2026"));
        assertTrue(lines.contains(I18n.t("report.csv.prev.paid") + ",8"));
        assertTrue(lines.contains(I18n.t("report.csv.prev.revenue") + ",700000"));
    }

    @Test
    public void theBlocksComeInTheOrderTheFileAlwaysHad() {
        List<String> lines = ReportCsv.lines(data(List.of(shift())));
        int[] order = {at(lines, I18n.t("report.csv.method.header")), at(lines, I18n.t("report.csv.product.header")),
                at(lines, I18n.t("report.csv.hour.header")), at(lines, I18n.t("report.csv.weekday.header")),
                at(lines, I18n.t("report.csv.category.header")), at(lines, "OPTION-BLOCK"), at(lines, I18n.t("report.csv.employee.header")),
                at(lines, I18n.t("report.csv.shift.header")), at(lines, I18n.t("report.csv.daily.header"))};
        for (int i = 1; i < order.length; i++) assertTrue("block " + i + " after block " + (i - 1), order[i] > order[i - 1]);
    }

    @Test
    public void everyBlockHoldsItsRowsAsBefore() {
        List<String> lines = ReportCsv.lines(data(List.of(shift())));
        int method = at(lines, I18n.t("report.csv.method.header"));
        assertEquals(PaymentMethod.CASH.label() + ",6,500000", lines.get(method + 1));
        assertEquals(I18n.t("report.payment.unknown") + ",1,50000", lines.get(method + 2));
        int product = at(lines, I18n.t("report.csv.product.header"));
        assertEquals("\"Cà phê, sữa\",25,750000,300000", lines.get(product + 1));
        assertEquals("Trà,3,60000,", lines.get(product + 2)); // a product without a cost has an empty profit cell
        int employee = at(lines, I18n.t("report.csv.employee.header"));
        assertEquals("An,9,800000,300000", lines.get(employee + 1));
        assertEquals(I18n.t("report.employee.unknown") + ",1,100000,0", lines.get(employee + 2));
        int daily = at(lines, I18n.t("report.csv.daily.header"));
        assertEquals("01/11/2026,4,300000,120000", lines.get(daily + 1));
        assertEquals("02/11/2026,2,100000,", lines.get(daily + 2));
        assertEquals("the file ends with the last day", daily + 2, lines.size() - 1);
        int category = at(lines, I18n.t("report.csv.category.header"));
        assertEquals(I18n.t("report.category.none") + ",1,10000", lines.get(category + 2));
    }

    @Test
    public void theClosedShiftsAreOnlyThereWhenThereAreSome() {
        assertFalse(ReportCsv.lines(data(List.of())).stream().anyMatch(l -> l.startsWith(I18n.t("report.csv.shift.header"))));
        List<String> with = ReportCsv.lines(data(List.of(shift())));
        assertTrue(with.stream().anyMatch(l -> l.startsWith("7,01/11/2026 08:00,01/11/2026 22:00,An,12,900000")));
    }
}
