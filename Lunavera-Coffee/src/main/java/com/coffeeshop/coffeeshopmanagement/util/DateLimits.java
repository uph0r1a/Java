package com.coffeeshop.coffeeshopmanagement.util;

import java.time.LocalDate;

/**
 * The two facts about dates that the rules need (TODO 3.78): the earliest date the program accepts and the way a date is written on screen.
 * They live here, without JavaFX, so that {@code VoucherRules} and {@code StaffRules} can compile and be tested without it (TODO 6.83);
 * {@link DateTextConverter}, which needs JavaFX, takes them from here.
 */
public final class DateLimits {

    /** No date before this is accepted (a typo such as 0024 or 1925 is refused). */
    public static final LocalDate EARLIEST = LocalDate.of(2000, 1, 1);

    /** How a date is shown and typed. */
    public static final String PATTERN = "dd/MM/yyyy";

    private DateLimits() {
    }
}
