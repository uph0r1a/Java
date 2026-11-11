package com.coffeeshop.coffeeshopmanagement.service;

import java.math.BigDecimal;

/**
 * How an audit line's text is kept (TODO 4.2), with no screen and no database so the writer and the tests agree: one short line,
 * cut at the column size, money as plain whole dong.
 */
public final class AuditRules {

    /** Stored as the actor when nobody is signed in (start-up, first-run setup). */
    public static final String SYSTEM_ACTOR = "(system)";
    public static final int LABEL_MAX = 255;
    public static final int VALUE_MAX = 500;
    /** The reason typed for a refund; the {@code orders.cancel_reason} column holds 200. */
    public static final int REASON_MAX = 200;

    /** A refund must say why, in at least this many characters (TODO 4.2): "ok" is not a reason. */
    public static final int REASON_MIN = 3;

    private AuditRules() {
    }

    /** The language key of what is wrong with a typed refund reason, or null when it is fine. */
    public static String reasonProblem(String typed) {
        String reason = clip(typed, REASON_MAX);
        return reason == null || reason.length() < REASON_MIN ? "history.cancel.reason.required" : null;
    }

    /**
     * The text as it is stored: line breaks, tabs and runs of spaces become one space, control characters go, the ends are trimmed,
     * and a longer text is cut at {@code max}. Empty means "nothing" and is null.
     */
    public static String clip(String raw, int max) {
        if (raw == null) return null;
        String text = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFC); // one way of storing an accent (TODO 3.18)
        StringBuilder out = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                pendingSpace = out.length() > 0;
            } else if (!Character.isISOControl(c)) {
                if (pendingSpace) out.append(' ');
                pendingSpace = false;
                out.append(c);
            }
        }
        String clean = com.coffeeshop.coffeeshopmanagement.util.TextClip.clip(out.toString(), max).stripTrailing();
        return clean.isEmpty() ? null : clean;
    }

    /** A sum of money as the digits of whole dong ("45000"), or null for no amount. */
    public static String money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
