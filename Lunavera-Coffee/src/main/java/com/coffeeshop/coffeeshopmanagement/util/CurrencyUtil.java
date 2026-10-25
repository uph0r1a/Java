package com.coffeeshop.coffeeshopmanagement.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        DecimalFormat format = new DecimalFormat("#,##0", symbols);
        // Same rule as Money.toDong, so what the screen shows is what is stored (TODO 3.60); DecimalFormat defaults to HALF_EVEN.
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format;
    });

    private CurrencyUtil() {
    }

    public static String format(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        String text = FORMAT.get().format(amount);
        // A tiny negative amount rounds to zero but DecimalFormat still prints "-0" (TODO 3.60).
        if (text.equals("-0")) {
            text = "0";
        }
        return text + "đ";
    }
}
