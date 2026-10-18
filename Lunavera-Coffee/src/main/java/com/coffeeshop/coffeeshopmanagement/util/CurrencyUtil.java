package com.coffeeshop.coffeeshopmanagement.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formats money the way the existing UI labels do: "12.000đ" (dot thousands separator, no decimals). */
public final class CurrencyUtil {

    /**
     * {@link DecimalFormat} keeps working state inside the object and is not thread-safe, so every thread gets its own
     * copy (TODO 6.19). Before, one shared instance was fine only because the UI thread was the one caller.
     */
    private static final ThreadLocal<DecimalFormat> FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        return new DecimalFormat("#,##0", symbols);
    });

    private CurrencyUtil() {
    }

    public static String format(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        return FORMAT.get().format(amount) + "đ";
    }
}
