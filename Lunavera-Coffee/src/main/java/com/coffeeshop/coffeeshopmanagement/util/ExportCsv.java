package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO.OrderSummary;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Order;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** CSV text of orders and customers for an accountant (TODO 4.21). Every cell goes through {@link Csv#cell}. */
public final class ExportCsv {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Tells Excel which character separates the cells, whatever the PC's list separator is. */
    static final String SEP_LINE = "sep=,";

    private ExportCsv() {}

    public static List<String> orders(List<OrderSummary> rows) {
        List<String> lines = new ArrayList<>();
        lines.add(ordersHeader());
        for (OrderSummary row : rows) {
            lines.add(orderLine(row));
        }
        return lines;
    }

    private static String ordersHeader() {
        return I18n.t("export.orders.header");
    }

    private static String orderLine(OrderSummary row) {
        Order o = row.order();
        return String.join(",",
                String.valueOf(o.getId()),
                o.getOrderDate() == null ? "" : o.getOrderDate().format(TIME),
                o.getPaidAt() == null ? "" : o.getPaidAt().format(TIME),
                Csv.cell(row.employeeName()),
                Csv.cell(row.customerName() == null ? I18n.t("receipt.guest") : row.customerName()),
                Csv.cell(PaymentText.of(o)),
                o.getDiscount() == null ? "0" : o.getDiscount().toPlainString(),
                Csv.cell(o.getDiscountReason()),
                o.getTotal() == null ? "0" : o.getTotal().toPlainString(),
                Csv.cell(statusText(o)));
    }

    /** Where the order rows come from: reads them one at a time into {@code sink} and returns how many it gave (TODO 3.54). */
    @FunctionalInterface
    public interface RowSource {
        int readInto(java.util.function.Consumer<OrderSummary> sink);
    }

    /**
     * Writes the order CSV while the rows are being read, so no list of every order is held in memory and there is no row limit
     * (TODO 3.54). The lines go to {@code <file>.part} first and that file replaces {@code file} only when the last row is
     * written: a database error or a full disk half-way leaves the person's earlier file as it was and no half-finished CSV
     * that looks complete. Same format as {@link #write}: UTF-8 with a byte order mark, the {@code sep=,} line, CRLF line ends, and the same
     * message when Excel has the file open. Returns the number of orders written.
     */
    public static int writeOrders(Path file, RowSource source) throws IOException {
        Path part = file.resolveSibling(file.getFileName() + ".part");
        int written;
        try {
            try (java.io.Writer out = Files.newBufferedWriter(part, StandardCharsets.UTF_8)) {
                out.write("\uFEFF" + SEP_LINE + "\r\n" + ordersHeader() + "\r\n"); // TODO 3.49: same first line as write()
                try {
                    written = source.readInto(row -> {
                        try {
                            out.write(orderLine(row));
                            out.write("\r\n");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });
                } catch (java.io.UncheckedIOException e) {
                    throw e.getCause();
                }
            }
            Files.move(part, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(part);
            } catch (IOException ignored) {
                // the half-written copy is only a leftover; the original error is the one to report
            }
            if (e instanceof IOException io && fileInUse(io)) {
                throw new IOException(I18n.t("export.file.inuse"), io);
            }
            throw e;
        }
        return written;
    }

    /** The customer list with visits, total spent and last visit (TODO 4.39). */
    public static List<String> customers(List<Customer> customers,
                                         java.util.Map<Integer, com.coffeeshop.coffeeshopmanagement.service.CustomerStats.Figures> figures) {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.t("export.customers.header") + "," + I18n.t("export.customers.header.more"));
        List<String> plain = customers(customers);
        for (int i = 0; i < customers.size(); i++) {
            var f = com.coffeeshop.coffeeshopmanagement.service.CustomerStats.of(figures, customers.get(i).getId());
            lines.add(plain.get(i + 1) + "," + f.visits() + "," + Money.toDong(f.spent()) + ","
                    + (f.lastVisit() == null ? "" : f.lastVisit().format(TIME)));
        }
        return lines;
    }

    public static List<String> customers(List<Customer> customers) {
        List<String> lines = new ArrayList<>();
        lines.add(I18n.t("export.customers.header"));
        for (Customer c : customers) {
            lines.add(String.join(",",
                    Csv.cell(c.getFullName()),
                    phone(c.getPhone()),
                    Csv.cell(c.getEmail()),
                    String.valueOf(c.getLoyaltyPoints()),
                    c.getCreatedAt() == null ? "" : c.getCreatedAt().format(TIME)));
        }
        return lines;
    }

    /**
     * Excel drops the leading 0 of a phone number it reads as a number. A number made only of digits
     * (and an optional +) is written as ="0901234567", which Excel shows as text; anything else is an ordinary cell.
     */
    static String phone(String phone) {
        if (phone == null || phone.isBlank()) return "";
        return phone.matches("\\+?\\d{6,15}") ? "=\"" + phone + "\"" : Csv.cell(phone);
    }

    private static String statusText(Order o) {
        return switch (o.getStatus()) {
            case PAID -> I18n.t("orders.status.paid");
            case CANCELLED -> I18n.t("orders.status.cancelled");
            case OPEN -> I18n.t("orders.status.open");
        };
    }

    /**
     * UTF-8 with a byte order mark so Excel shows Vietnamese letters correctly; lines end with CRLF. The first line is
     * {@code sep=,}: Excel honours it in every regional setting, so a Windows set to the Vietnamese number format (list
     * separator {@code ;}) no longer puts each row into one column (TODO 3.49). Other programs may show it as one odd line;
     * {@code ProductCsv} skips it when its own export is imported again. A file that Excel has open
     * cannot be written on Windows; that case fails with a text that says what to do instead of the JDK's text with the path
     * in it, which the error filter would turn into the generic "something went wrong" (TODO 5.30).
     */
    public static void write(Path file, List<String> lines) throws IOException {
        try {
            Files.writeString(file, "\uFEFF" + SEP_LINE + "\r\n" + String.join("\r\n", lines) + "\r\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (fileInUse(e)) {
                throw new IOException(I18n.t("export.file.inuse"), e);
            }
            throw e;
        }
    }

    /** True for the failure Windows gives when another program (Excel) has the file open: a sharing or lock violation. */
    static boolean fileInUse(IOException e) {
        if (!(e instanceof java.nio.file.FileSystemException fse)) {
            return false;
        }
        String reason = fse.getReason() == null ? "" : fse.getReason().toLowerCase(java.util.Locale.ROOT);
        return reason.contains("used by another process") || reason.contains("sharing violation")
                || reason.contains("locked by another process") || reason.contains("another process has locked");
    }
}
