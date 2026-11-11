package com.coffeeshop.coffeeshopmanagement.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A voucher code the shop hands out (TODO 4.11, rule D6). {@code kind} is {@link #PERCENT} (value = percent of the subtotal, 1 to 100) or
 * {@link #AMOUNT} (value = dong off the bill). It can be limited to a range of days, a smallest subtotal, and a number of uses, and (TODO 4.54)
 * to one use per customer: then the bill must have a customer and a customer whose earlier paid bill used the code is refused.
 */
public record Voucher(int id, String code, String kind, long value, BigDecimal minSubtotal, LocalDate validFrom, LocalDate validTo,
                      Integer maxUses, int usedCount, boolean active, boolean oncePerCustomer) {

    /** A voucher that every customer may use as often as the other limits allow (the form before TODO 4.54). */
    public Voucher(int id, String code, String kind, long value, BigDecimal minSubtotal, LocalDate validFrom, LocalDate validTo,
                   Integer maxUses, int usedCount, boolean active) {
        this(id, code, kind, value, minSubtotal, validFrom, validTo, maxUses, usedCount, active, false);
    }

    public static final String PERCENT = "PERCENT";
    public static final String AMOUNT = "AMOUNT";
}
