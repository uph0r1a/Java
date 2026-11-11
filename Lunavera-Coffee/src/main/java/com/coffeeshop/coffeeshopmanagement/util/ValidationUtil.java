package com.coffeeshop.coffeeshopmanagement.util;

import java.util.regex.Pattern;

public final class ValidationUtil {

    // Vietnamese mobile numbers only (TODO 3.6, 3.15): +84 / 84 / 0 and then exactly 9 digits, i.e. 10 digits in the 0... form.
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+840?[1-9]\\d{8}|84[1-9]\\d{8}|0\\d{9})$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.-]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]{4,32}$");

    private ValidationUtil() {
    }

    /** Empty, or only spaces: including the non-breaking and zero-width ones that Java's {@code isBlank()} does not know (TODO 3.71). */
    public static boolean isBlank(String value) {
        return value == null || TextNormalizer.plain(value).isBlank();
    }

    /**
     * A phone with what people put in it taken out (TODO 3.30, 3.71): spaces of every kind, dots, dashes, brackets, invisible
     * characters, and the "(0)" after the country code that some phones write: {@code +84 (0) 901 111 222} is {@code +84901111222}.
     */
    public static String phoneCompact(String phone) {
        if (phone == null) return null;
        String compact = TextNormalizer.plain(phone).replaceAll("[\\s.\\-]", "");
        compact = compact.replaceFirst("^(\\+?84)\\(0\\)", "$1");
        return compact.replaceAll("[()]", "");
    }

    /** A phone as people type it: spaces, dots, dashes and brackets are allowed (TODO 3.30); same rule as {@link #normalizePhone}. */
    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(compactPhone(phone)).matches();
    }

    /** The e-mail as it is kept: trimmed and without invisible characters; null stays null (TODO 3.71). */
    public static String cleanEmail(String email) {
        return email == null ? null : TextNormalizer.strip(email).trim();
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
        String compact = compactPhone(phone);
        if (!PHONE_PATTERN.matcher(compact).matches()) {
            return TextNormalizer.clean(phone).trim();
        }
        return compact.startsWith("0") ? compact : "0" + compact.substring(compact.length() - 9); // +84 (0) 901 111 222, +84901111222, 84901111222 and 0901111222 are one number
    }

    /** The phone without spaces, dots, dashes, brackets and the invisible characters that come with a pasted contact (TODO 3.71). */
    private static String compactPhone(String phone) {
        return TextNormalizer.strip(phone).replaceAll("[\\s.\\-()]", "");
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(TextNormalizer.strip(email).trim()).matches();
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

    /**
     * At least 8 characters, with at least one letter and one digit, not an easy one ({@link PasswordStrength#isEasy}: the commonest
     * few thousand passwords, one repeated character, a keyboard run, a common word with a year behind it, the shop's name, TODO 2A.20
     * and 2A.47), and at most {@value #MAX_PASSWORD_LENGTH} characters (a pasted megabyte would
     * make every one of the 600,000 hashing rounds copy it, TODO 2A.35). (Containing the username is checked by
     * {@link #containsUsername}.)
     */
    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < 8 || password.length() > MAX_PASSWORD_LENGTH) {
            return false;
        }
        if (PasswordStrength.isEasy(password)) {
            return false;
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        return hasLetter && hasDigit;
    }
}
