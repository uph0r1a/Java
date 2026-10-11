package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.model.Order;

import java.time.format.DateTimeFormatter;

/**
 * Text encoded in the receipt's QR code. It is a plain, readable summary of the receipt, so a phone
 * scan shows something meaningful. It is deliberately not a payment request: the receipt is for an
 * order that is already paid, and a payment QR on it could be paid a second time.
 *
 * <p>The text is plain ASCII on purpose (no accents, no {@code đ} sign). {@link QrCode} stores the
 * text as UTF-8 bytes without a character-set marker, and many phone scanners then read those bytes
 * as Latin-1 and show jumbled text such as {@code HÃ³a Ä‘Æ¡n}. ASCII reads the same in every
 * character set, so every scanner shows the same thing.
 */
public final class ReceiptQr {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private ReceiptQr() {
    }

    public static String payload(Order order) {
        StringBuilder text = new StringBuilder("Lunavera Coffee\n")
                .append("Hoa don #").append(order.getId()).append('\n')
                .append("Tong: ").append(amount(order)).append(" VND");
        if (order.getOrderDate() != null) {
            text.append('\n').append(order.getOrderDate().format(TIME));
        }
        return text.toString();
    }

    /** Whole dong with a dot between thousands ("1.250.000"), independent of the computer's locale. */
    static String amount(Order order) {
        long dong = order.getTotal() == null ? 0 : Money.toDong(order.getTotal());
        String digits = Long.toString(Math.abs(dong));
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                grouped.append('.');
            }
            grouped.append(digits.charAt(i));
        }
        return (dong < 0 ? "-" : "") + grouped;
    }
}
