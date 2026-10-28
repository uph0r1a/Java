package com.coffeeshop.coffeeshopmanagement.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One line of the stock log (TODO 4.5): goods received ({@link #RECEIPT}), a stock number someone typed over ({@link #ADJUST}) or
 * goods thrown away or broken ({@link #WASTE}, TODO 4.33).
 * The product's and the person's names are copies, so the line reads the same after either is renamed or removed.
 *
 * @param quantity how many pieces were added (negative when a typed number lowered the stock, and always negative for a waste line)
 * @param unitCost what one piece cost: when the receipt said so; for a waste line the product's cost price at that moment (so the
 *                 loss does not change when the price is edited later); null when none was known
 * @param note     free text; for a waste line it is the reason, which is required
 */
public record StockLogEntry(int id, LocalDateTime loggedAt, String kind, Integer productId, String productName,
                            int quantity, int stockBefore, int stockAfter, BigDecimal unitCost, String supplier,
                            String note, Integer employeeId, String employeeName) {

    public static final String RECEIPT = "RECEIPT";
    public static final String ADJUST = "ADJUST";
    /** Milk that spoiled, a cup that was dropped (TODO 4.33): lowers the stock, and is a cost in the report. */
    public static final String WASTE = "WASTE";
}
