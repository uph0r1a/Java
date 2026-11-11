package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.Assert.*;

/**
 * TODO 6.35 (bugs 2.44 and 2.45): the time and the banner at the top of a receipt. They used to be set inside the screen class
 * ({@code HoaDonController}, JavaFX), where no test could reach them; the rules are in {@link ReceiptText} now.
 */
public class ReceiptHeaderTest {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private static Order order(OrderStatus status) {
        Order o = new Order();
        o.setStatus(status);
        return o;
    }

    // ---- 2.44: the paid time, not the opening time

    @Test
    public void thePaidTimeIsShownNotTheOpeningTime() {
        Order o = order(OrderStatus.PAID);
        o.setOrderDate(LocalDateTime.of(2026, 11, 5, 8, 0));
        o.setPaidAt(LocalDateTime.of(2026, 11, 5, 9, 45));
        assertEquals("09:45 05/11/2026", ReceiptText.timeOf(o, FORMAT));
    }

    @Test
    public void anOldOrderWithNoPaymentTimeShowsTheOpeningTime() {
        Order o = order(OrderStatus.PAID);
        o.setOrderDate(LocalDateTime.of(2026, 11, 5, 8, 0));
        assertEquals("08:00 05/11/2026", ReceiptText.timeOf(o, FORMAT));
    }

    @Test
    public void anOrderWithNoTimeAtAllShowsADash() {
        assertEquals("-", ReceiptText.timeOf(order(OrderStatus.PAID), FORMAT));
    }

    // ---- 2.45: a cancelled order is marked, a paid one says paid

    @Test
    public void aPaidOrderSaysPaid() {
        assertEquals("receipt.paid", ReceiptText.bannerKey(order(OrderStatus.PAID)));
        assertEquals("PAID", ReceiptText.t("receipt.paid"));
    }

    @Test
    public void aCancelledOrderIsMarkedNotAValidPayment() {
        assertEquals("receipt.cancelled", ReceiptText.bannerKey(order(OrderStatus.CANCELLED)));
        String text = ReceiptText.t("receipt.cancelled");
        assertTrue(text, text.contains("CANCELLED"));
        assertTrue(text, text.contains("NOT A VALID PAYMENT"));
    }

    @Test
    public void anOpenOrderOrOneWithNoStatusShowsNoBanner() {
        assertNull(ReceiptText.bannerKey(order(OrderStatus.OPEN)));
        assertNull(ReceiptText.bannerKey(new Order()));
    }

    @Test
    public void theReceiptScreenUsesTheseRulesAndNotItsOwnCopy() throws java.io.IOException {
        String screen = java.nio.file.Files.readString(java.nio.file.Path.of("src", "main", "java", "com", "coffeeshop",
                "coffeeshopmanagement", "controller", "HoaDonController.java"));
        assertTrue(screen.contains("ReceiptText.timeOf(order, TIME_FORMAT)"));
        assertTrue(screen.contains("ReceiptText.bannerKey(order)"));
        assertFalse("the paid time is decided in ReceiptText", screen.contains("order.getPaidAt() != null"));
    }
}
