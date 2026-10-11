package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.Order;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReceiptQrTest {

    private Order order() {
        Order order = new Order();
        order.setId(12);
        order.setTotal(new BigDecimal("120000"));
        order.setOrderDate(LocalDateTime.of(2026, 10, 10, 14, 3));
        return order;
    }

    @Test
    public void payloadIsAReadableSummaryOfTheReceipt() {
        String payload = ReceiptQr.payload(order());
        String[] lines = payload.split("\n");
        assertEquals("Lunavera Coffee", lines[0]);
        assertEquals("Hoa don #12", lines[1]);
        assertEquals("Tong: 120.000 VND", lines[2]);
        assertEquals("14:03 10/10/2026", lines[3]);
    }

    @Test
    public void payloadIsPlainAscii() {
        // No accents and no the dong sign: phone scanners that guess the wrong character set
        // would otherwise show jumbled text (TODO 1.1).
        String payload = ReceiptQr.payload(order());
        for (char c : payload.toCharArray()) {
            assertTrue("non-ASCII character U+" + Integer.toHexString(c) + " in the QR text",
                    c == '\n' || (c >= 0x20 && c < 0x7F));
        }
        assertEquals("the QR holds exactly one byte per character",
                payload.length(), payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    @Test
    public void amountUsesDotsAndNoLocale() {
        assertEquals("0", ReceiptQr.amount(withTotal("0")));
        assertEquals("999", ReceiptQr.amount(withTotal("999")));
        assertEquals("1.000", ReceiptQr.amount(withTotal("1000")));
        assertEquals("25.000", ReceiptQr.amount(withTotal("25000")));
        assertEquals("1.250.000", ReceiptQr.amount(withTotal("1250000")));
        Order noTotal = order();
        noTotal.setTotal(null);
        assertEquals("0", ReceiptQr.amount(noTotal));
    }

    @Test
    public void payloadDoesNotDependOnTheDefaultLocale() {
        java.util.Locale saved = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY);
            String german = ReceiptQr.payload(order());
            java.util.Locale.setDefault(new java.util.Locale("vi", "VN"));
            assertEquals(german, ReceiptQr.payload(order()));
        } finally {
            java.util.Locale.setDefault(saved);
        }
    }

    private Order withTotal(String dong) {
        Order order = order();
        order.setTotal(new BigDecimal(dong));
        return order;
    }

    @Test
    public void payloadIsNotAPaymentRequest() {
        String payload = ReceiptQr.payload(order());
        assertFalse(payload.contains("000201")); // EMVCo / VietQR start marker
    }

    @Test
    public void payloadWithoutADateOmitsTheTimeLine() {
        Order order = order();
        order.setOrderDate(null);
        assertEquals(3, ReceiptQr.payload(order).split("\n").length);
    }

    @Test
    public void payloadFitsInAQrCode() {
        QrCode qr = QrCode.encodeText(ReceiptQr.payload(order()));
        assertTrue(qr.getSize() <= 41);
    }
}
