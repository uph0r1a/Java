package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.GiftCardDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.DailySales;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.ProductSales;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.Summary;
import com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO;
import com.coffeeshop.coffeeshopmanagement.model.ShiftClose;
import com.coffeeshop.coffeeshopmanagement.service.SalesPatterns;
import com.coffeeshop.coffeeshopmanagement.service.SalesPatternsText;
import com.coffeeshop.coffeeshopmanagement.service.ShiftSheet;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The lines of the sales report's CSV file, in order: the totals, the comparison with the period before, then one block each for
 * the methods of payment, channels, discounts, gift cards, products, hours, weekdays, categories, options, employees, closed shifts
 * and days. They were built inside {@code ReportWindow.exportCsv} until TODO 2.86 (that class was over its size ceiling); the lines are
 * the same, the window now only hands over what it shows ({@link Data}) and writes the file. Pure text, tested without JavaFX.
 */
public final class ReportCsv {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private ReportCsv() {
    }

    /** Everything the report window has loaded for the range on screen, and the option and shift blocks it already holds. */
    public record Data(LocalDate from, LocalDate to, Summary summary, ReportDAO.Profit profit, ReportDAO.Waste waste,
                       ReportDAO.Expenses expenses, SalesPatterns.Range previousRange, Summary previousSummary,
                       List<ReportDAO.PaymentSplit> split, List<ReportDAO.ChannelSales> channels, List<ReportDAO.DiscountLine> discounts,
                       GiftCardDAO.OpenBalance giftCards, List<ProductSales> allProducts, List<SalesPatterns.Slot> hours,
                       List<SalesPatterns.WeekdaySlot> weekdays, List<ReportDAO.CategorySales> categories, List<String> optionLines,
                       List<ShiftDAO.EmployeeShare> employees, List<ShiftClose> shifts, List<DailySales> daily) {
    }

    /** The lines of the file for what is on screen. */
    public static List<String> lines(Data data) {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.t("report.title") + "," + data.from().format(DAY_FORMAT) + "," + data.to().format(DAY_FORMAT));
        if (!BusinessDay.isCalendarDay()) { // which day an evening belongs to (TODO 4.50)
            lines.add(I18n.t("report.csv.daystart") + "," + BusinessDay.format(
                    BusinessDay.startMinutes()));
        }
        lines.add(I18n.t("report.csv.paid") + "," + data.summary().paidOrders());
        lines.add(I18n.t("report.csv.revenue") + "," + data.summary().revenue().toPlainString());
        lines.add(I18n.t("report.csv.discount") + "," + data.summary().discounts().toPlainString());
        lines.add(I18n.t("report.csv.cancelled") + "," + data.summary().cancelledOrders());
        lines.add(I18n.t("report.csv.profit") + "," + data.profit().grossProfit().toPlainString());
        lines.add(I18n.t("report.csv.nocost") + "," + data.profit().linesWithoutCost());
        lines.addAll(WasteText.csvLines(data.waste())); // TODO 4.33
        lines.addAll(ComplimentaryText.csvLines(data.summary())); // TODO 4.61
        lines.addAll(ExpenseText.csvLines(data.profit().grossProfit(), data.waste(), data.expenses())); // TODO 4.38
        lines.add(I18n.t("report.csv.prev") + "," + data.previousRange().from().format(DAY_FORMAT) + "," + data.previousRange().to().format(DAY_FORMAT));
        lines.add(I18n.t("report.csv.prev.paid") + "," + data.previousSummary().paidOrders());
        lines.add(I18n.t("report.csv.prev.revenue") + "," + data.previousSummary().revenue().toPlainString());
        lines.add("");
        lines.add(I18n.t("report.csv.method.header"));
        for (ReportDAO.PaymentSplit part : data.split()) {
            lines.add(csv(part.method() == null ? I18n.t("report.payment.unknown") : part.method().label()) + "," + part.orders() + "," + part.revenue().toPlainString());
        }
        lines.addAll(ChannelText.csvLines(data.channels())); // TODO 4.48
        lines.addAll(DiscountText.csvLines(data.discounts())); // TODO 2.37
        lines.addAll(GiftCardText.csvLines(data.giftCards())); // TODO 4.45
        lines.add("");
        lines.add(I18n.t("report.csv.product.header"));
        for (ProductSales p : data.allProducts()) { // every product, not only the top 20 on screen (TODO 5.34)
            lines.add(csv(p.productName()) + "," + p.quantity() + "," + p.revenue().toPlainString() + ","
                    + (p.profit() == null ? "" : p.profit().toPlainString()));
        }
        lines.add("");
        lines.add(I18n.t("report.csv.hour.header"));
        for (SalesPatterns.Slot h : data.hours()) {
            lines.add(csv(SalesPatternsText.hourLabel(h.key())) + "," + h.orders() + "," + h.revenue().toPlainString());
        }
        lines.add("");
        lines.add(I18n.t("report.csv.weekday.header"));
        for (SalesPatterns.WeekdaySlot d : data.weekdays()) {
            lines.add(csv(SalesPatternsText.weekdayName(d.day())) + "," + d.orders() + "," + d.revenue().toPlainString() + ","
                    + d.daysInRange() + "," + d.average().toPlainString());
        }
        lines.add("");
        lines.add(I18n.t("report.csv.category.header"));
        for (ReportDAO.CategorySales c : data.categories()) {
            lines.add(csv(ReportTexts.categoryName(c)) + "," + c.quantity() + "," + c.revenue().toPlainString());
        }
        lines.addAll(data.optionLines()); // TODO 4.47
        lines.add("");
        lines.add(I18n.t("report.csv.employee.header")); // TODO 4.6
        for (com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO.EmployeeShare x : data.employees()) {
            lines.add(csv(ReportTexts.employeeName(x)) + "," + x.orders() + "," + x.revenue().toPlainString() + "," + x.cash().toPlainString());
        }
        lines.addAll(ShiftSheet.csvBlock(data.shifts())); // the closed shifts (TODO 4.53)
        lines.add("");
        lines.add(I18n.t("report.csv.daily.header"));
        for (DailySales d : data.daily()) {
            lines.add(d.date().format(DAY_FORMAT) + "," + d.orders() + "," + d.revenue().toPlainString() + ","
                    + (d.profit() == null ? "" : d.profit().toPlainString()));
        }
        return lines;
    }

    private static String csv(String value) {
        return Csv.cell(value);
    }
}
