package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;

/**
 * The words of the report's "when" and "compared with before" parts (TODO 4.40), apart from the window so they can be tested.
 * Numbers are written by hand (never {@code String.format}) so a PC set to another number format shows the same digits (6.62).
 */
public final class SalesPatternsText {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private SalesPatternsText() {
    }

    /** "10:00 - 11:00" for hour 10; hour 23 ends at 00:00. */
    public static String hourLabel(int hour) {
        return two(hour) + ":00 - " + two((hour + 1) % 24) + ":00";
    }

    private static String two(int n) {
        return (n < 10 ? "0" : "") + n;
    }

    public static String weekdayName(DayOfWeek day) {
        return I18n.t(switch (day) {
            case MONDAY -> "weekday.1";
            case TUESDAY -> "weekday.2";
            case WEDNESDAY -> "weekday.3";
            case THURSDAY -> "weekday.4";
            case FRIDAY -> "weekday.5";
            case SATURDAY -> "weekday.6";
            case SUNDAY -> "weekday.7";
        });
    }

    /** "↑ 15%", "↓ 3%", "no change", "new" (nothing before), or "-" (nothing in either period). */
    public static String changeText(SalesPatterns.Change change) {
        if (change.percent() == null) {
            return change.current().signum() == 0 ? I18n.t("report.cmp.none") : I18n.t("report.cmp.new");
        }
        if (change.direction() == 0) {
            return I18n.t("report.cmp.same");
        }
        int percent = Math.abs(change.percent());
        return I18n.t(change.direction() > 0 ? "report.cmp.up" : "report.cmp.down", percent == 0 ? "<1" : String.valueOf(percent));
    }

    /** The comparison paragraph: this period against the one before it, for revenue, orders and the average order. */
    public static String comparison(SalesPatterns.Range previous, ReportDAO.Summary current, ReportDAO.Summary before) {
        return I18n.t("report.cmp.title", previous.from().format(DAY), previous.to().format(DAY)) + "\n"
                + I18n.t("report.cmp.revenue", CurrencyUtil.format(current.revenue()),
                        changeText(SalesPatterns.change(current.revenue(), before.revenue())), CurrencyUtil.format(before.revenue()))
                + "   •   "
                + I18n.t("report.cmp.orders", current.paidOrders(),
                        changeText(SalesPatterns.change(BigDecimal.valueOf(current.paidOrders()), BigDecimal.valueOf(before.paidOrders()))),
                        before.paidOrders())
                + "   •   "
                + I18n.t("report.cmp.average", CurrencyUtil.format(current.averageOrderValue()),
                        changeText(SalesPatterns.change(current.averageOrderValue(), before.averageOrderValue())),
                        CurrencyUtil.format(before.averageOrderValue()));
    }

    /** A bar of block characters, for a quick look at which row is the biggest. */
    public static String bar(BigDecimal value, BigDecimal max) {
        return "█".repeat(SalesPatterns.barLength(value, max, 20));
    }
}
