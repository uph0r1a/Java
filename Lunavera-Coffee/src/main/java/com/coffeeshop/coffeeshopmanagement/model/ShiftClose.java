package com.coffeeshop.coffeeshopmanagement.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One closed shift (TODO 4.6): the time it covered, what was sold in it, and the cash the till should hold against the cash counted.
 * {@code difference} is counted minus expected: negative = money missing, positive = more than expected. {@code cashOut} is the cash
 * taken out of the drawer for expenses during the shift (TODO 4.38); it was taken off {@code expectedCash} (0 for a shift closed before
 * it existed).
 */
public record ShiftClose(int id, LocalDateTime periodStart, LocalDateTime periodEnd, Integer closedBy, String closedByName,
                         int paidOrders, BigDecimal revenue, BigDecimal cashSales, BigDecimal openingCash,
                         BigDecimal expectedCash, BigDecimal countedCash, BigDecimal difference, String note,
                         BigDecimal cashOut) {
}
