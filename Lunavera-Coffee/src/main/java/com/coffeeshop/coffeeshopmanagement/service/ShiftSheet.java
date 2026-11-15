package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.ShiftClose;
import com.coffeeshop.coffeeshopmanagement.util.Csv;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The one-page sheet of a closed shift, the Z-report (TODO 4.53): what was taken, how it was paid, what went back, what was taken
 * out of the drawer, and the cash expected against the cash counted, with a line for the cashier and one for the owner to sign.
 * It is kept apart from the screen, the printer and the database so it can be tested on its own: this class only turns a stored
 * {@link ShiftClose} and the {@link Extras} of its time window into {@link Line}s. The paper layout is drawn from the lines by
 * {@code util.ShiftSheetPage}, a saved file is written from them by {@link #csvLines}, and the same lines are what a reprint uses, so
 * every way out shows the same figures.
 *
 * <p>The money of the shift itself (revenue, cash taken, cash out, expected, counted, difference) is the stored line of the close,
 * exactly as the cashier confirmed it. Only the extra detail (by method, refunds, discounts, gift cards) is worked out again for
 * the same window of time; a reprint months later therefore shows what was closed, not what a later correction would give.
 *
 * <p>The words follow the language passed in (the language of the screen when it is printed), unlike a customer's receipt, which is
 * always English (TODO 1.7): this sheet is for the shop's own people.
 */
public final class ShiftSheet {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private ShiftSheet() {
    }

    /** What a line of the sheet is: centred text, left text, a label with an amount, a rule, a gap, or a line to sign. */
    public enum Kind { CENTER, LEFT, PAIR, RULE, GAP, SIGNATURE }

    /**
     * One line. {@code left} is the text (the label of a pair, the caption under a signature line), {@code right} the value as
     * shown on paper ("" when the line has none), {@code raw} the same value as plain digits for a file (null when it has none).
     */
    public record Line(Kind kind, String left, String right, boolean bold, String raw) {

        static Line center(String text, boolean bold) {
            return new Line(Kind.CENTER, text, "", bold, null);
        }

        static Line text(String text, boolean bold) {
            return new Line(Kind.LEFT, text, "", bold, null);
        }

        static Line money(String label, BigDecimal amount, boolean bold) {
            return new Line(Kind.PAIR, label, CurrencyUtil.format(amount), bold, amount.toPlainString());
        }

        static Line signedMoney(String label, BigDecimal amount, boolean bold) {
            String shown = (amount.signum() > 0 ? "+" : "") + CurrencyUtil.format(amount);
            return new Line(Kind.PAIR, label, shown, bold, amount.toPlainString());
        }

        static Line count(String label, int number, boolean bold) {
            return new Line(Kind.PAIR, label, String.valueOf(number), bold, String.valueOf(number));
        }

        static Line rule() {
            return new Line(Kind.RULE, "", "", false, null);
        }

        static Line gap() {
            return new Line(Kind.GAP, "", "", false, null);
        }

        static Line signature(String caption) {
            return new Line(Kind.SIGNATURE, caption, "", false, null);
        }
    }

    /** One method of payment in the shift: how many orders it paid and how much (net of refunds), as the report counts it. */
    public record Method(PaymentMethod method, int orders, BigDecimal revenue) {
    }

    /**
     * The discounts one person gave in the shift: the cashier who took the payment of the orders (a discount is given when the order is
     * paid), how many of their orders had a discount, and what those discounts came to, net of refunds like the total.
     */
    public record DiscountShare(String name, int orders, BigDecimal amount) {
    }

    /**
     * The detail of the shift's window of time that the stored close does not hold: the takings per method, the refunds made in
     * the window (what they came to, how many, and the cash part), the discounts given (and by whom), and the cash taken for gift
     * cards sold.
     */
    public record Extras(BigDecimal refunds, int refundCount, BigDecimal refundsInCash, BigDecimal discounts,
                         BigDecimal giftCardsSold, List<Method> byMethod, List<DiscountShare> discountsByPerson) {

        public Extras {
            refunds = nz(refunds);
            refundsInCash = nz(refundsInCash);
            discounts = nz(discounts);
            giftCardsSold = nz(giftCardsSold);
            byMethod = byMethod == null ? List.of() : List.copyOf(byMethod);
            discountsByPerson = discountsByPerson == null ? List.of() : List.copyOf(discountsByPerson);
        }

        /** The detail without who gave the discounts (the form before the discounts were split per person). */
        public Extras(BigDecimal refunds, int refundCount, BigDecimal refundsInCash, BigDecimal discounts,
                      BigDecimal giftCardsSold, List<Method> byMethod) {
            this(refunds, refundCount, refundsInCash, discounts, giftCardsSold, byMethod, null);
        }

        /** No detail: a shift with nothing refunded, discounted or sold by method. */
        public static Extras none() {
            return new Extras(null, 0, null, null, null, null, null);
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * The lines of the sheet of one closed shift.
     *
     * @param close the stored close
     * @param extras the detail of its window of time
     * @param shopName the shop's name (left out when blank)
     * @param shopDetails the address, phone and tax code lines under the name (may be empty)
     * @param language the language of the words ({@code I18n.EN} or {@code I18n.VI})
     * @param copy true for a reprint, which says so at the top
     * @param printedAt when it is printed, shown at the bottom (left out when null)
     */
    public static List<Line> build(ShiftClose close, Extras extras, String shopName, List<String> shopDetails, String language,
                                   boolean copy, LocalDateTime printedAt) {
        Extras more = extras == null ? Extras.none() : extras;
        List<Line> lines = new ArrayList<>();
        if (shopName != null && !shopName.isBlank()) {
            lines.add(Line.center(shopName.toUpperCase(Locale.ROOT), true));
        }
        if (shopDetails != null) {
            for (String detail : shopDetails) {
                if (detail != null && !detail.isBlank()) lines.add(Line.center(detail, false));
            }
        }
        lines.add(Line.center(I18n.in(language, "shiftsheet.title"), true));
        if (copy) {
            lines.add(Line.center(I18n.in(language, "shiftsheet.copy"), true));
        }
        lines.add(Line.rule());
        lines.add(Line.text(I18n.in(language, "shiftsheet.line.no", String.valueOf(close.id())), true));
        lines.add(Line.text(I18n.in(language, "shiftsheet.line.from", TIME.format(close.periodStart())), false));
        lines.add(Line.text(I18n.in(language, "shiftsheet.line.to", TIME.format(close.periodEnd())), false));
        String by = close.closedByName() == null || close.closedByName().isBlank() ? "-" : close.closedByName();
        lines.add(Line.text(I18n.in(language, "shiftsheet.line.by", by), false));
        lines.add(Line.rule());

        // What was taken.
        lines.add(Line.text(I18n.in(language, "shiftsheet.sales"), true));
        lines.add(Line.count(I18n.in(language, "shiftsheet.line.orders"), close.paidOrders(), false));
        lines.add(Line.money(I18n.in(language, "shiftsheet.line.revenue"), close.revenue(), true));
        if (!more.byMethod().isEmpty()) {
            lines.add(Line.text(I18n.in(language, "shiftsheet.bymethod"), false));
            for (Method part : more.byMethod()) {
                String name = part.method() == null ? I18n.in(language, "report.payment.unknown")
                        : PaymentMethod.labelOf(part.method(), language);
                lines.add(Line.money(I18n.in(language, "shiftsheet.method.line", name, String.valueOf(part.orders())),
                        part.revenue(), false));
            }
        }
        lines.add(Line.money(I18n.in(language, "shiftsheet.line.refunds", String.valueOf(more.refundCount())),
                more.refunds().negate(), false));
        if (more.refundsInCash().signum() != 0) {
            lines.add(Line.money(I18n.in(language, "shiftsheet.line.refunds.cash"), more.refundsInCash().negate(), false));
        }
        lines.add(Line.money(I18n.in(language, "shiftsheet.line.discounts"), more.discounts(), false));
        if (!more.discountsByPerson().isEmpty()) {
            lines.add(Line.text(I18n.in(language, "shiftsheet.discounts.by"), false));
            for (DiscountShare share : more.discountsByPerson()) {
                String who = share.name() == null || share.name().isBlank() ? I18n.in(language, "shiftsheet.person.unknown") : share.name();
                lines.add(Line.money(I18n.in(language, "shiftsheet.person.line", who, String.valueOf(share.orders())), share.amount(), false));
            }
        }
        lines.add(Line.money(I18n.in(language, "shiftsheet.line.giftcards"), more.giftCardsSold(), false));
        lines.add(Line.rule());

        // The cash drawer.
        lines.add(Line.text(I18n.in(language, "shiftsheet.drawer"), true));
        lines.add(Line.money(I18n.in(language, "shiftsheet.line.opening"), close.openingCash(), false));
        lines.add(Line.money(I18n.in(language, "shiftsheet.line.cashin"), close.cashSales(), false));
        BigDecimal out = nz(close.cashOut());
        lines.add(Line.money(I18n.in(language, "shiftsheet.line.cashout"), out.signum() == 0 ? out : out.negate(), false));
        lines.add(Line.money(I18n.in(language, "shiftsheet.line.expected"), close.expectedCash(), true));
        lines.add(Line.money(I18n.in(language, "shiftsheet.line.counted"), close.countedCash(), true));
        BigDecimal difference = nz(close.difference());
        String key = difference.signum() == 0 ? "shiftsheet.diff.ok" : difference.signum() > 0 ? "shiftsheet.diff.over" : "shiftsheet.diff.short";
        lines.add(Line.signedMoney(I18n.in(language, key), difference, true));
        if (close.note() != null && !close.note().isBlank()) {
            lines.add(Line.text(I18n.in(language, "shiftsheet.line.note", close.note()), false));
        }
        lines.add(Line.rule());

        // Who signs.
        lines.add(Line.gap());
        lines.add(Line.signature(I18n.in(language, "shiftsheet.sign.cashier")));
        lines.add(Line.gap());
        lines.add(Line.signature(I18n.in(language, "shiftsheet.sign.owner")));
        if (printedAt != null) {
            lines.add(Line.gap());
            lines.add(Line.center(I18n.in(language, "shiftsheet.printed", TIME.format(printedAt)), false));
        }
        return lines;
    }

    /**
     * The sheet as the rows of a CSV file: the text of each line, and for a line with an amount a second cell with the plain
     * number of dong (no "đ", no dots), so a spreadsheet can add it up. Rules, gaps and signature lines are left out. Every cell
     * goes through {@link Csv#cell}.
     */
    public static List<String> csvLines(List<Line> sheet) {
        List<String> rows = new ArrayList<>();
        for (Line line : sheet) {
            switch (line.kind()) {
                case CENTER, LEFT -> rows.add(Csv.cell(line.left()));
                case PAIR -> rows.add(Csv.cell(csvLabel(line.left())) + "," + Csv.cell(line.raw()));
                default -> { }
            }
        }
        return rows;
    }

    /**
     * The label of an amount in a file: without the "+ " or "- " that only helps on paper (the number in the next cell already
     * carries its sign). Left in, a label such as "- Cash (9 orders)" looks like a formula to {@link Csv#cell}, which would put an
     * apostrophe in front of it that Excel then shows.
     */
    private static String csvLabel(String label) {
        String text = label.strip();
        if (text.startsWith("+ ") || text.startsWith("- ")) text = text.substring(2).strip();
        return text;
    }

    /**
     * The closed shifts as a block of the report's CSV (the accountant's side of TODO 4.53): a blank line, a header line, then one
     * row per shift with its period, who closed it, the figures and the difference. Empty when there is no closed shift.
     */
    public static List<String> csvBlock(List<ShiftClose> closes) {
        List<String> rows = new ArrayList<>();
        if (closes == null || closes.isEmpty()) return rows;
        rows.add("");
        rows.add(I18n.t("report.csv.shift.header"));
        for (ShiftClose close : closes) {
            rows.add(close.id() + "," + CSV_TIME.format(close.periodStart()) + "," + CSV_TIME.format(close.periodEnd()) + ","
                    + Csv.cell(close.closedByName()) + "," + close.paidOrders() + "," + close.revenue().toPlainString() + ","
                    + close.cashSales().toPlainString() + "," + nz(close.cashOut()).toPlainString() + ","
                    + close.expectedCash().toPlainString() + "," + close.countedCash().toPlainString() + ","
                    + close.difference().toPlainString() + "," + Csv.cell(close.note()));
        }
        return rows;
    }

    /** A name for the file of one sheet: {@code chot-ca-12-20261104-2205.csv}, from the shift's number and the time it ended. */
    public static String fileName(ShiftClose close) {
        return fileName(close, "csv");
    }

    /** The same name with the extension of the format ({@code "pdf"} or {@code "csv"}). */
    public static String fileName(ShiftClose close, String extension) {
        return "chot-ca-" + close.id() + "-" + FILE_TIME.format(close.periodEnd()) + "." + extension;
    }

    /**
     * Whether a file the person chose is saved as a PDF: when its name ends in .pdf (or .csv, which says no) that decides, whatever
     * filter the dialog showed; a name with neither follows the filter that was selected.
     */
    public static boolean savesAsPdf(String fileName, boolean pdfFilterSelected) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return true;
        if (name.endsWith(".csv")) return false;
        return pdfFilterSelected;
    }

    /** The file name with the extension of the format added when it does not end with it already (some systems do not add one). */
    public static String withExtension(String fileName, boolean pdf) {
        String extension = pdf ? ".pdf" : ".csv";
        return fileName.toLowerCase(Locale.ROOT).endsWith(extension) ? fileName : fileName + extension;
    }
}
