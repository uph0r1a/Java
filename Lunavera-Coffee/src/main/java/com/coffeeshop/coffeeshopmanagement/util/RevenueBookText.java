package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.BookLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The text of the revenue book (TODO 4.58), a sales book in the shape a small business hands to an accountant or the tax office: the day,
 * the receipt number, what it was, the amount, how it was paid, a total at the end of each month and a grand total. The layout follows
 * the usual "sổ doanh thu bán hàng" (date, document number, description, amount); it has not been checked with the shop's accountant.
 * Refunds are negative lines on the day they were made.
 */
public final class RevenueBookText {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private RevenueBookText() {
    }

    /** The description of a line: "Bán hàng - Mang đi", "Hoàn tiền đơn 12", "Hoàn tiền một phần đơn 12". */
    public static String description(BookLine line) {
        if (line.refund()) return I18n.t(line.refundOfLine() ? "book.desc.refundpart" : "book.desc.refund", line.orderId());
        return I18n.t("book.desc.sale") + (line.channel() == null ? "" : " - " + line.channel().label());
    }

    /** The whole book as CSV lines (no BOM: {@code ExportCsv.write} adds it); header, lines, a total per month, and the grand total. */
    public static List<String> lines(List<BookLine> book, LocalDate from, LocalDate to, String shopName) {
        List<String> out = new ArrayList<>();
        out.add(Csv.cell(I18n.t("book.title")));
        if (shopName != null && !shopName.isBlank()) out.add(Csv.cell(I18n.t("book.shop", shopName)));
        out.add(Csv.cell(I18n.t("book.period", from.format(DAY), to.format(DAY))));
        out.add("");
        out.add(I18n.t("book.header"));
        BigDecimal month = BigDecimal.ZERO;
        BigDecimal all = BigDecimal.ZERO;
        YearMonth current = null;
        for (BookLine line : book) {
            YearMonth m = YearMonth.from(line.day());
            if (current != null && !m.equals(current)) {
                out.add(monthTotal(current, month));
                month = BigDecimal.ZERO;
            }
            current = m;
            out.add(line.day().format(DAY) + "," + line.orderId() + "," + Csv.cell(description(line)) + "," + line.amount().toPlainString() + ","
                    + Csv.cell(line.method() == null ? I18n.t("report.payment.unknown") : line.method().label()));
            month = month.add(line.amount());
            all = all.add(line.amount());
        }
        if (current != null) out.add(monthTotal(current, month));
        out.add(",," + Csv.cell(I18n.t("book.total")) + "," + all.toPlainString() + ",");
        return out;
    }

    private static String monthTotal(YearMonth month, BigDecimal total) {
        return ",," + Csv.cell(I18n.t("book.month", month.format(MONTH))) + "," + total.toPlainString() + ",";
    }

    /** The sum of the lines: the revenue of the period, equal to the report's figure. */
    public static BigDecimal total(List<BookLine> book) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BookLine line : book) sum = sum.add(line.amount());
        return sum;
    }
}
