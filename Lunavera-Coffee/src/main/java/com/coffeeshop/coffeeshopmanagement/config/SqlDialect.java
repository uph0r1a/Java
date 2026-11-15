package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

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
                    I18n.t("db.type.invalid", type));
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

    /**
     * A name column in an ORDER BY in Vietnamese order (TODO 3.84): {@code Ă Â Đ Ê Ô Ơ Ư} next to their base letters, capitals and
     * small letters alike. On SQLite the column is compared with the {@code VN_ORDER} collation that {@code DatabaseConfig} registers on
     * every connection (SQLite itself compares by code point and would put the accented letters after Z); on MariaDB
     * {@code utf8mb4_vietnamese_ci} does (under {@code utf8mb4_unicode_ci} {@code An}, {@code Ăn} and {@code Ân} are ties and {@code Đ} is not
     * after {@code D}). Use it in the ORDER BY of any list of names, never in a table definition.
     */
    public String vietnameseOrder(String column) {
        return this == SQLITE ? column + " COLLATE " + DatabaseConfig.VIETNAMESE_COLLATION : column + " COLLATE utf8mb4_vietnamese_ci";
    }

    /** The statement that creates the small key/flag table used by the one-time seeders. */
    public String createFlagsTable() {
        return this == SQLITE
                ? "CREATE TABLE IF NOT EXISTS app_flags (name TEXT PRIMARY KEY)"
                : "CREATE TABLE IF NOT EXISTS app_flags (name VARCHAR(100) NOT NULL PRIMARY KEY) "
                        + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    /** The statement that creates the settings table (TODO 6.74), for code that may run before the start-up schema pass. */
    public String createSettingsTable() {
        return this == SQLITE
                ? "CREATE TABLE IF NOT EXISTS settings (name TEXT PRIMARY KEY, value TEXT NOT NULL)"
                : "CREATE TABLE IF NOT EXISTS settings (name VARCHAR(100) NOT NULL PRIMARY KEY, value VARCHAR(500) NOT NULL) "
                        + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
    }

    /** SQL that is true when the named table exists in the current database (one {@code ?} = table name). */
    public String tableExistsQuery() {
        return this == SQLITE
                ? "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?"
                : "SELECT 1 FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
    }
}
