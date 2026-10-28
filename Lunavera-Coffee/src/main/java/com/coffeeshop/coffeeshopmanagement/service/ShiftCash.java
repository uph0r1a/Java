package com.coffeeshop.coffeeshopmanagement.service;

import java.math.BigDecimal;

/**
 * The cash arithmetic of closing a shift (TODO 4.6), kept apart from the screen and the database so it can be tested on its own.
 * The cash the till should hold is what was in it at the start plus the cash taken for sales since, minus the cash taken out of the
 * drawer for expenses (TODO 4.38); the difference is what was counted minus that.
 */
public final class ShiftCash {

    private ShiftCash() {
    }

    /** What the drawer should hold: the cash it started with plus the cash sales of the shift. */
    public static BigDecimal expected(BigDecimal openingCash, BigDecimal cashSales) {
        return expected(openingCash, cashSales, BigDecimal.ZERO);
    }

    /** As above, minus the cash taken out of the drawer during the shift (expenses paid from it, TODO 4.38). */
    public static BigDecimal expected(BigDecimal openingCash, BigDecimal cashSales, BigDecimal cashOut) {
        return nz(openingCash).add(nz(cashSales)).subtract(nz(cashOut));
    }

    /** Counted minus expected: negative means cash is missing. */
    public static BigDecimal difference(BigDecimal counted, BigDecimal expected) {
        return nz(counted).subtract(nz(expected));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
