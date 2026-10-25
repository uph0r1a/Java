package com.coffeeshop.coffeeshopmanagement.util;

import java.util.regex.Pattern;

public final class ValidationUtil {

    // Vietnamese mobile numbers only (TODO 3.6, 3.15): +84 / 84 / 0 and then exactly 9 digits, i.e. 10 digits in the 0... form.
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+?84|0)\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]{4,32}$");

    private ValidationUtil() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** A phone as people type it: spaces, dots, dashes and brackets are allowed (TODO 3.30); same rule as {@link #normalizePhone}. */
    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone.replaceAll("[\\s.\\-()]", "")).matches();
    }

    /**
     * One canonical form for a Vietnamese phone number, so the same person is never stored twice:
     * spaces, dots and dashes are dropped and a leading {@code +84} or {@code 84} becomes {@code 0}
     * ({@code +84 901 111 222} and {@code 0901111222} are the same customer). Anything that is not
     * a phone number is returned trimmed and otherwise unchanged; null stays null.
     */
    public static String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String compact = phone.replaceAll("[\\s.\\-()]", "");
        if (!PHONE_PATTERN.matcher(compact).matches()) {
            return phone.trim();
        }
        if (compact.startsWith("+84")) {
            return "0" + compact.substring(3);
        }
        if (compact.startsWith("84")) {
            return "0" + compact.substring(2);
        }
        return compact;
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidUsername(String username) {
        // A name that is a phone number is refused: login also accepts a phone, so it would be ambiguous (TODO 2.17).
        return username != null && USERNAME_PATTERN.matcher(username.trim()).matches()
                && !isValidPhone(normalizePhone(username));
    }

    /** True when the password contains the username (any letter case), e.g. {@code admin123} for {@code admin} (TODO 3.16). */
    public static boolean containsUsername(String password, String username) {
        if (password == null || username == null) return false;
        String u = username.trim().toLowerCase(java.util.Locale.ROOT);
        return u.length() >= 3 && password.toLowerCase(java.util.Locale.ROOT).contains(u);
    }

    public static final int MAX_PASSWORD_LENGTH = 128;

    /** Passwords everybody tries first (TODO 2A.20); compared in lower case. */
    private static final java.util.Set<String> COMMON_PASSWORDS = java.util.Set.of(
            "12345678", "123456789", "1234567890", "12345678a", "123456789a", "a12345678", "abc12345", "abcd1234",
            "password1", "password12", "password123", "matkhau1", "matkhau12", "matkhau123", "qwerty123", "qwerty12",
            "1q2w3e4r", "1qaz2wsx", "admin123", "admin1234", "iloveyou1", "lunavera1", "lunavera123",
            "caphe123", "coffee123");

    /**
     * At least 8 characters, with at least one letter and one digit, not on a short list of passwords everybody tries
     * first, and not containing the shop's name, and at most {@value #MAX_PASSWORD_LENGTH} characters (a pasted megabyte would
     * make every one of the 600,000 hashing rounds copy it, TODO 2A.35). (Containing the username is checked by
     * {@link #containsUsername}.)
     */
    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 8 || password.length() > MAX_PASSWORD_LENGTH) {
            return false;
        }
        String lower = password.toLowerCase(java.util.Locale.ROOT);
        if (COMMON_PASSWORDS.contains(lower) || lower.contains("lunavera")) {
            return false;
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        return hasLetter && hasDigit;
    }
}
