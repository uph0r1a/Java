package com.coffeeshop.coffeeshopmanagement.util;

import javafx.util.StringConverter;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * The one text-to-date converter of every date box (TODO 3.78). JavaFX's own converter reads {@code 1/11/26} as the year 26 in
 * Vietnamese; this one shows {@code dd/MM/yyyy}, reads a two-digit year as 20xx, accepts {@code /}, {@code -}, {@code .} or spaces between the
 * parts, and throws {@link DateTimeParseException} for what it cannot read (a date box then puts the old text back). Only needs
 * {@code javafx.util}, so it is tested without a window.
 */
public final class DateTextConverter extends StringConverter<LocalDate> {

    /** The first day any date box accepts for a business date: a typed year such as 0026 or 1926 is a slip, not a date. */
    public static final LocalDate EARLIEST = DateLimits.EARLIEST;

    public static final String PATTERN = DateLimits.PATTERN;
    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern(PATTERN, Locale.ROOT);

    @Override
    public String toString(LocalDate date) {
        return date == null ? "" : SHOWN.format(date);
    }

    @Override
    public LocalDate fromString(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String typed = TextNormalizer.strip(text).trim();
        if (typed.isEmpty()) {
            return null;
        }
        String[] parts = typed.split("[/\\-. ]+");
        if (parts.length != 3 || !allDigits(parts[0], 1, 2) || !allDigits(parts[1], 1, 2) || !allDigits(parts[2], 1, 4)) {
            throw new DateTimeParseException("Not a day/month/year date", typed, 0);
        }
        int day = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int year = Integer.parseInt(parts[2]);
        if (parts[2].length() <= 2) {
            year += 2000;
        } else if (parts[2].length() == 3) {
            throw new DateTimeParseException("A year has two or four digits", typed, 0);
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            throw new DateTimeParseException("No such day", typed, 0, e);
        }
    }

    private static boolean allDigits(String part, int min, int max) {
        if (part.length() < min || part.length() > max) {
            return false;
        }
        for (int i = 0; i < part.length(); i++) {
            if (part.charAt(i) < '0' || part.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }
}
