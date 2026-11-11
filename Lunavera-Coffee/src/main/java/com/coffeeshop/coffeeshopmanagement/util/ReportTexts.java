package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.service.SalesPatterns;
import com.coffeeshop.coffeeshopmanagement.service.SalesPatternsText;

import java.math.BigDecimal;
import java.util.List;

/**
 * The small pure pieces of text and numbers the sales report window shows (busiest hour, profit, payment split, category name, the
 * longest bar of a chart). They stood at the bottom of {@code ReportWindow} until TODO 2.86 (that class was over its size ceiling);
 * nothing in them needs a screen, so they are tested without JavaFX.
 */
public final class ReportTexts {

    private ReportTexts() {
    }

    public static BigDecimal maxOrders(List<SalesPatterns.Slot> slots) {
        return slots.stream().map(x -> BigDecimal.valueOf(x.orders())).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    public static BigDecimal maxAverage(List<SalesPatterns.WeekdaySlot> slots) {
        return slots.stream().map(SalesPatterns.WeekdaySlot::average).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    public static BigDecimal maxRevenue(List<ReportDAO.CategorySales> list) {
        return list.stream().map(ReportDAO.CategorySales::revenue).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    public static String categoryName(ReportDAO.CategorySales category) {
        return category.categoryName() == null ? I18n.t("report.category.none")
                : com.coffeeshop.coffeeshopmanagement.config.SeedNames.category(category.categoryName());
    }

    /** "Busiest hour: 10:00 - 11:00 (12 orders) • Best weekday: Saturday (about 1.200.000đ per day)". */
    public static String busiestText(List<SalesPatterns.Slot> hours, List<SalesPatterns.WeekdaySlot> weekdays) {
        SalesPatterns.Slot hour = SalesPatterns.busiest(hours);
        SalesPatterns.WeekdaySlot day = SalesPatterns.bestWeekday(weekdays);
        if (hour == null || day == null) return I18n.t("report.busiest.none");
        return I18n.t("report.busiest.hour", SalesPatternsText.hourLabel(hour.key()), hour.orders()) + "   •   "
                + I18n.t("report.busiest.weekday", SalesPatternsText.weekdayName(day.day()), CurrencyUtil.format(day.average()));
    }

    /** "-" when no cost is known, so a missing cost is never shown as a profit of 0. */
    public static String profitText(BigDecimal profit) {
        return profit == null ? "-" : CurrencyUtil.format(profit);
    }

    public static String profitNote(ReportDAO.Profit profit) {
        return profit.linesWithoutCost() == 0 ? ""
                : "  " + I18n.t("report.profitnote", profit.linesWithoutCost());
    }

    public static String paymentText(List<ReportDAO.PaymentSplit> split) {
        if (split.isEmpty()) return I18n.t("report.payment.none");
        StringBuilder text = new StringBuilder(I18n.t("report.payment.prefix") + " ");
        for (int i = 0; i < split.size(); i++) {
            ReportDAO.PaymentSplit part = split.get(i);
            if (i > 0) text.append("   •   ");
            text.append(part.method() == null ? I18n.t("report.payment.unknown") : part.method().label()).append(": ")
                    .append(CurrencyUtil.format(part.revenue())).append(" (").append(I18n.t("report.orders.count", part.orders())).append(")");
        }
        return text.toString();
    }

    /** The person's name, or "unknown" for an order that has no person on record. */
    public static String employeeName(com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO.EmployeeShare x) {
        return x.employeeName() == null || x.employeeName().isBlank() ? I18n.t("report.employee.unknown") : x.employeeName();
    }
}
