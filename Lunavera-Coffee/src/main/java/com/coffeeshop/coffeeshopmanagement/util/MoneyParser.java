package com.coffeeshop.coffeeshopmanagement.util;

import java.math.BigDecimal;

/**
 * Reads an amount of money typed by a person, the way the app shows it ("25.000đ"): whole dong, with "." or "," or
 * spaces as thousands separators and an optional trailing "đ"/"d"/"VND". Before this, fields used
 * {@code new BigDecimal(text)}, so "25.000" was read as 25 and "1e5" as 100000 (TODO 2.16). Used for the employee salary
 * (TODO 2.10); the other money fields move to it when item 2.16 is done.
 */
public final class MoneyParser {

    /** 15 digits is far beyond any real amount and keeps the value inside a long. */
    public static final int MAX_DIGITS = 15;

    private MoneyParser() {
    }

    /** @return the amount in whole dong, or {@code null} for blank text
     *  @throws IllegalArgumentException with a message fit to show the person, for anything that is not a plain amount */
    public static BigDecimal parseDong(String text) {
        if (text == null || text.isBlank()) return null;
        String s = text.trim();
        String lower = s.toLowerCase();
        for (String suffix : new String[]{"vnđ", "vnd", "đ", "d"}) {
            if (lower.endsWith(suffix)) {
                s = s.substring(0, s.length() - suffix.length()).trim();
                break;
            }
        }
        StringBuilder digits = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (c == '.' || c == ',' || c == ' ' || c == ' ') {
                // thousands separator: dropped
            } else {
                throw new IllegalArgumentException("Số tiền chỉ được gồm chữ số (ví dụ 8000000 hoặc 8.000.000).");
            }
        }
        if (digits.length() == 0) {
            throw new IllegalArgumentException("Số tiền chỉ được gồm chữ số (ví dụ 8000000 hoặc 8.000.000).");
        }
        if (digits.length() > MAX_DIGITS) {
            throw new IllegalArgumentException("Số tiền quá lớn.");
        }
        return new BigDecimal(digits.toString());
    }

    /**
     * Like {@link #parseDong}, for a box that may be left empty (a discount, the cash received): blank is 0 and text that is
     * not a valid amount is {@code null}, so the caller can say "enter a valid number" (TODO 6.5, moved out of the till screen).
     */
    public static BigDecimal parseOptionalDong(String text) {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal value = parseDong(text);
            return value == null ? BigDecimal.ZERO : value;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
