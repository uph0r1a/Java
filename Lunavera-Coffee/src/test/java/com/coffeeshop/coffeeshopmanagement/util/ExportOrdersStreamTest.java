package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO.OrderSummary;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * TODO 3.54: the order CSV is written while the rows are read. There is no row limit (the old export stopped at 200,000 rows
 * without a word) and no list of every order is held; a read that fails half-way leaves no half-finished file that looks complete.
 */
public class ExportOrdersStreamTest {

    private static OrderSummary row(int id) {
        Order o = new Order();
        o.setId(id);
        o.setOrderDate(LocalDateTime.of(2026, 3, 9, 8, 30));
        o.setStatus(OrderStatus.PAID);
        o.setPaymentMethod(PaymentMethod.CASH);
        o.setTotal(new BigDecimal("45000"));
        return new OrderSummary(o, "Staff", null);
    }

    private static List<String> lines(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue("byte order mark for Excel", text.startsWith("\uFEFF"));
        assertTrue("CRLF line ends", text.endsWith("\r\n"));
        List<String> all = List.of(text.substring(1).split("\r\n"));
        assertEquals("the sep line Excel needs (TODO 3.49)", "sep=,", all.get(0));
        return all.subList(1, all.size());
    }

    @Test
    public void everyRowIsWrittenWithTheSameLinesAsTheListVersion() throws IOException {
        Path dir = Files.createTempDirectory("orderstream");
        Path file = dir.resolve("orders.csv");
        List<OrderSummary> all = new ArrayList<>();
        for (int i = 1; i <= 5; i++) all.add(row(i));

        int written = ExportCsv.writeOrders(file, sink -> {
            all.forEach(sink);
            return all.size();
        });

        assertEquals(5, written);
        assertEquals(ExportCsv.orders(all), lines(file));
        assertFalse("no leftover partial file", Files.exists(dir.resolve("orders.csv.part")));
    }

    @Test
    public void rowsAreHandedOverOneAtATimeAndNotCollected() throws IOException {
        Path file = Files.createTempDirectory("orderstream").resolve("orders.csv");
        int[] liveRows = {0};
        int rows = 20_000; // more than any screen shows; the point is that the source never holds a list of them
        int written = ExportCsv.writeOrders(file, sink -> {
            for (int i = 1; i <= rows; i++) {
                sink.accept(row(i));
                liveRows[0]++;
            }
            return rows;
        });
        assertEquals(rows, written);
        assertEquals(rows, liveRows[0]);
        assertEquals("header and one line per order", rows + 1, lines(file).size());
    }

    @Test
    public void aReadThatFailsHalfWayKeepsTheEarlierFileAndLeavesNoPartialOne() throws IOException {
        Path dir = Files.createTempDirectory("orderstream");
        Path file = dir.resolve("orders.csv");
        Files.writeString(file, "last month's export");
        try {
            ExportCsv.writeOrders(file, sink -> {
                sink.accept(row(1));
                sink.accept(row(2));
                throw new IllegalStateException("the database went away");
            });
            fail("the failure must reach the caller");
        } catch (IllegalStateException expected) {
            assertEquals("the database went away", expected.getMessage());
        }
        assertEquals("last month's export", Files.readString(file));
        assertFalse(Files.exists(dir.resolve("orders.csv.part")));
    }

    @Test
    public void anEmptyResultStillWritesTheHeader() throws IOException {
        Path file = Files.createTempDirectory("orderstream").resolve("orders.csv");
        assertEquals(0, ExportCsv.writeOrders(file, sink -> 0));
        assertEquals(1, lines(file).size());
    }

    @Test
    public void aMissingFolderIsAnErrorTheCallerCanShow() {
        Path file = Path.of("no-such-folder-for-the-export", "orders.csv");
        try {
            ExportCsv.writeOrders(file, sink -> 0);
            fail("a folder that does not exist cannot be written to");
        } catch (IOException expected) {
            assertFalse(Files.exists(file));
        }
    }
}
