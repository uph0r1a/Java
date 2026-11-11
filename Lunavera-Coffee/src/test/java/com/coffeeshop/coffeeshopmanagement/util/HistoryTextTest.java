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
import java.util.List;

import static org.junit.Assert.*;

/** TODO 3.17 / 6.90 (b): the order history table and its CSV show the cash the customer gave and the change handed back. */
public class HistoryTextTest {

    private static OrderSummary row(PaymentMethod method, String cash, String change) {
        Order o = new Order();
        o.setId(7);
        o.setOrderDate(LocalDateTime.of(2026, 3, 9, 8, 30));
        o.setStatus(OrderStatus.PAID);
        o.setPaymentMethod(method);
        o.setTotal(new BigDecimal("45000"));
        o.setCashReceived(cash == null ? null : new BigDecimal(cash));
        o.setChangeGiven(change == null ? null : new BigDecimal(change));
        return new OrderSummary(o, "Staff", null);
    }

    @Test
    public void aCashSaleShowsWhatWasGivenAndWhatCameBack() {
        OrderSummary cash = row(PaymentMethod.CASH, "50000", "5000");
        assertEquals(CurrencyUtil.format(new BigDecimal("50000")), HistoryText.cashGiven(cash));
        assertEquals(CurrencyUtil.format(new BigDecimal("5000")), HistoryText.change(cash));
    }

    @Test
    public void aSaleWithNothingKeptShowsEmptyCellsNotZeros() {
        OrderSummary card = row(PaymentMethod.TRANSFER, null, null);
        assertEquals("", HistoryText.cashGiven(card));
        assertEquals("", HistoryText.change(card));
        assertEquals("", HistoryText.plainDong(null));
    }

    @Test
    public void exactMoneyShowsAZeroChangeBecauseItWasKept() {
        OrderSummary exact = row(PaymentMethod.CASH, "45000", "0");
        assertEquals(CurrencyUtil.format(BigDecimal.ZERO), HistoryText.change(exact));
    }

    @Test
    public void theCsvHasTwoMoreColumnsThanBeforeAndTheHeaderAndEveryRowAgree() throws IOException {
        Path file = Files.createTempFile("history-cash", ".csv");
        try {
            ExportCsv.writeOrders(file, sink -> {
                sink.accept(row(PaymentMethod.CASH, "50000", "5000"));
                sink.accept(row(PaymentMethod.TRANSFER, null, null));
                return 2;
            });
            List<String> lines = List.of(Files.readString(file, StandardCharsets.UTF_8).substring(1).split("\r\n"));
            // line 0 is the sep=, line, line 1 the header
            int columns = lines.get(1).split(",", -1).length;
            assertEquals("the header names the two new columns last", I18n.t("export.orders.header").split(",", -1).length, columns);
            assertTrue(lines.get(1).endsWith(",Cash given (đ),Change (đ)") || lines.get(1).endsWith(",Tiền khách đưa (đ),Tiền thối (đ)"));
            assertTrue(lines.get(2), lines.get(2).endsWith(",50000,5000"));
            assertTrue("empty, not zero, when nothing was kept: " + lines.get(3), lines.get(3).endsWith(",,"));
            assertEquals(columns, lines.get(2).split(",", -1).length);
            assertEquals(columns, lines.get(3).split(",", -1).length);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
