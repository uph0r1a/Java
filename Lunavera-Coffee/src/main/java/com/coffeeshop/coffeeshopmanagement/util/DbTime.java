package com.coffeeshop.coffeeshopmanagement.util;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.SqlDialect;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Date-times as the database wants them. SQLite stores them as ISO text ({@code 2026-10-10T14:30:05}),
 * exactly as before; MariaDB/MySQL has a real DATETIME column and wants {@code 2026-10-10 14:30:05}
 * (whole seconds). Reading accepts both, so a value is read the same way from either database.
 */
public final class DbTime {

    private static final DateTimeFormatter SQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private DbTime() {
    }

    /** The text to bind for {@code time} for the database in use (null stays null). */
    public static String write(LocalDateTime time) {
        return time == null ? null : format(time, DatabaseConfig.dialect());
    }

    public static String format(LocalDateTime time, SqlDialect dialect) {
        return dialect == SqlDialect.SQLITE
                ? time.toString()
                : time.truncatedTo(ChronoUnit.SECONDS).format(SQL_DATETIME);
    }

    /** Reads {@code 2026-10-10T14:30}, {@code 2026-10-10 14:30:05} or {@code 2026-10-10 14:30:05.0}; null stays null. */
    public static LocalDateTime parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String iso = text.trim().replace(' ', 'T');
        if (iso.length() <= 10) { // a date with no time of day
            return LocalDate.parse(iso).atStartOfDay();
        }
        return LocalDateTime.parse(iso);
    }

    /** Converts a stored SQLite value to the MariaDB DATETIME text (used by the one-time import). */
    public static String toSqlDateTime(String storedText) {
        LocalDateTime time = parse(storedText);
        return time == null ? null : format(time, SqlDialect.MARIADB);
    }
}
