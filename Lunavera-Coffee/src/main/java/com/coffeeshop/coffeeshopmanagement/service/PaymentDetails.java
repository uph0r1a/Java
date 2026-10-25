package com.coffeeshop.coffeeshopmanagement.service;

import java.math.BigDecimal;

/**
 * The small facts a payment leaves besides its amount: what a cash customer handed over (and so what went back) and the bank's
 * reference of a transfer (TODO 3.17, 4.31). Pure functions, so the rules can be tested without a screen or a database.
 */
public final class PaymentDetails {

    /** The longest transfer reference kept; bank references are far shorter, this only stops a pasted paragraph. */
    public static final int REFERENCE_MAX = 64;

    private PaymentDetails() {
    }

    /**
     * What goes back to a cash customer: {@code received - total}. Null when nothing was handed over, or when it is less than the
     * total (that is not a payment). Exactly the total gives zero.
     */
    public static BigDecimal changeFor(BigDecimal received, BigDecimal total) {
        if (received == null || total == null || received.compareTo(total) < 0) {
            return null;
        }
        return received.subtract(total);
    }

    /**
     * The reference as it should be stored: control characters removed, runs of white space made one space, trimmed, cut to
     * {@link #REFERENCE_MAX}; null when nothing is left (the field is optional).
     */
    public static String cleanReference(String typed) {
        if (typed == null) {
            return null;
        }
        String cleaned = typed.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > REFERENCE_MAX ? cleaned.substring(0, REFERENCE_MAX).trim() : cleaned;
    }
}
