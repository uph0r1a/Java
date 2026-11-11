package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.DiscountLine;

import java.util.ArrayList;
import java.util.List;

/** The words and the CSV block of the "discounts given" report (TODO 2.37); kept out of {@code ReportWindow} (TODO 6.54). */
public final class DiscountText {

    private DiscountText() {
    }

    public static String person(DiscountLine line) {
        return line.employeeName() == null || line.employeeName().isBlank() ? I18n.t("report.employee.unknown") : line.employeeName();
    }

    public static String reason(DiscountLine line) {
        return line.reason() == null || line.reason().isBlank() ? I18n.t("report.discount.noreason") : line.reason();
    }

    /** A blank line, the header and one line per person and reason: person, reason, orders, amount in whole dong. */
    public static List<String> csvLines(List<DiscountLine> lines) {
        List<String> out = new ArrayList<>();
        out.add("");
        out.add(I18n.t("report.csv.discount.header"));
        for (DiscountLine line : lines) {
            out.add(Csv.cell(person(line)) + "," + Csv.cell(reason(line)) + "," + line.orders() + "," + line.amount().toPlainString());
        }
        return out;
    }
}
