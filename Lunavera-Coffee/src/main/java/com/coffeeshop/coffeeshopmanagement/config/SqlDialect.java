package com.coffeeshop.coffeeshopmanagement.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;

/**
 * The few places where SQLite and MariaDB/MySQL need different SQL. Everything else in the
 * DAOs is plain SQL that both understand, so a DAO only asks the dialect for the pieces below
 * instead of branching on the database itself.
 */
public enum SqlDialect {
    SQLITE,
    MARIADB;

    /** Which database a live connection talks to (from the driver, not from configuration). */
    public static SqlDialect of(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        return product != null && product.toLowerCase(Locale.ROOT).contains("sqlite") ? SQLITE : MARIADB;
    }

    /** Reads the {@code db.type} setting: {@code mariadb} or {@code mysql} (same driver and SQL), or {@code sqlite}; blank means MariaDB, the default. */
    public static SqlDialect fromSetting(String type) {
        String value = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "sqlite" -> SQLITE;
            case "", "mariadb", "mysql" -> MARIADB;
            default -> throw new IllegalArgumentException(
                    "Giá trị db.type không hợp lệ: \"" + type + "\" (chỉ chấp nhận sqlite, mariadb hoặc mysql).");
        };
    }

    /** {@code INSERT OR IGNORE INTO t} / {@code INSERT IGNORE INTO t}: skip a row that already exists. */
    public String insertIgnoreInto(String table) {
        return this == SQLITE ? "INSERT OR IGNORE INTO " + table : "INSERT IGNORE INTO " + table;
    }

    /** A text column in an ORDER BY compared without regard to capitalisation. */
    public String caseInsensitive(String column) {
        return this == SQLITE ? column + " COLLATE NOCASE" : column + " COLLATE utf8mb4_unicode_ci";
    }

    /** The statement that creates the small key/flag table used by the one-time seeders. */
    public String createFlagsTable() {
        return this == SQLITE
                ? "CREATE TABLE IF NOT EXISTS app_flags (name TEXT PRIMARY KEY)"
                : "CREATE TABLE IF NOT EXISTS app_flags (name VARCHAR(100) NOT NULL PRIMARY KEY) "
                        + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    /** SQL that is true when the named table exists in the current database (one {@code ?} = table name). */
    public String tableExistsQuery() {
        return this == SQLITE
                ? "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?"
                : "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
    }
}
