package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Tiny named on/off markers kept in the database ({@code app_flags}), e.g. "show this notice once". */
public final class AppFlags {

    /** Set when the starter menu is first added; cleared once the owner has been told to review prices and stock (TODO 2.20). */
    public static final String REVIEW_MENU_NOTICE = "review_menu_notice";

    /** Set when the first administrator was created on an empty database; cleared when the setup window has been used or skipped (TODO 4.15). */
    public static final String SETUP_PENDING = "setup_pending";

    /** Set, with the user's id after it, when a PIN was switched off by wrong tries and its owner has not been told yet (TODO 2A.46). */
    public static final String PIN_OFF_NOTICE_PREFIX = "pin_off_notice:";

    private AppFlags() {
    }

    /** Sets a flag (nothing happens when it is already set). */
    public static void mark(String name) {
        try (Connection connection = DatabaseConfig.getConnection();
             Statement create = connection.createStatement()) {
            create.execute(SqlDialect.of(connection).createFlagsTable());
            set(connection, name);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set flag " + name, e);
        }
    }

    /** Sets a flag on the caller's connection and transaction, so it is saved or lost together with the change it belongs to. */
    public static void markIn(Connection connection, String name) throws SQLException {
        try (Statement create = connection.createStatement()) {
            create.execute(SqlDialect.of(connection).createFlagsTable());
        }
        set(connection, name);
    }

    public static boolean has(String name) {
        try (Connection connection = DatabaseConfig.getConnection();
             Statement create = connection.createStatement()) {
            create.execute(SqlDialect.of(connection).createFlagsTable());
            try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM app_flags WHERE name = ?")) {
                select.setString(1, name);
                try (ResultSet rs = select.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read flag " + name, e);
        }
    }

    public static void clear(String name) {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement delete = connection.prepareStatement("DELETE FROM app_flags WHERE name = ?")) {
            delete.setString(1, name);
            delete.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to clear flag " + name, e);
        }
    }

    /**
     * A small setting of the shop (TODO 2A.34, TODO 6.74). Since 6.74 it lives in the {@code settings} table: one row, the key
     * the {@code prefix} names (without its closing {@code :} or {@code =}) and the value in its own column, so a value is no
     * longer part of a row's name, is not limited by the 100-character name column and cannot exist twice. When there is no such
     * row (a database written before 6.74) the old flag whose name starts with {@code prefix} is read instead; the next save moves
     * it. The prefix must not contain {@code %} or {@code _} (the old lookup treated them as wildcards).
     */
    public static java.util.Optional<String> valueWithPrefix(String prefix) {
        try (Connection connection = DatabaseConfig.getConnection();
             Statement create = connection.createStatement()) {
            SqlDialect dialect = SqlDialect.of(connection);
            create.execute(dialect.createSettingsTable());
            create.execute(dialect.createFlagsTable());
            try (PreparedStatement select = connection.prepareStatement("SELECT value FROM settings WHERE name = ?")) {
                select.setString(1, keyOf(prefix));
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) return java.util.Optional.of(rs.getString(1));
                }
            }
            try (PreparedStatement select = connection.prepareStatement("SELECT name FROM app_flags WHERE name LIKE ?")) {
                select.setString(1, prefix + "%");
                try (ResultSet rs = select.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && name.startsWith(prefix)) return java.util.Optional.of(name.substring(prefix.length()));
                    }
                    return java.util.Optional.empty();
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read setting " + prefix, e);
        }
    }

    /** The name of a setting's row: its prefix without the closing {@code :} or {@code =} ({@code shopname:} is {@code shopname}). */
    static String keyOf(String prefix) {
        return prefix.endsWith(":") || prefix.endsWith("=") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }

    /** Replaces, in one transaction, the value kept under each prefix (see {@link #valueWithPrefix}); keys are prefixes, values the new text. */
    public static void replaceValues(java.util.Map<String, String> valueByPrefix) {
        replaceValues(valueByPrefix, null);
    }

    /** Something to write on the same connection, in the same transaction (an audit line for the change). */
    @FunctionalInterface
    public interface InTransaction {
        void run(Connection connection) throws SQLException;
    }

    /**
     * Like {@link #replaceValues(java.util.Map)}, and {@code alsoWrite} runs before the commit: both are saved or neither. Each
     * value goes to its one row of {@code settings} (the old row is replaced, never a second added) and the old flag rows of that
     * prefix are deleted, so after a save the old way of keeping it is gone.
     */
    public static void replaceValues(java.util.Map<String, String> valueByPrefix, InTransaction alsoWrite) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement create = connection.createStatement()) {
                SqlDialect dialect = SqlDialect.of(connection);
                create.execute(dialect.createSettingsTable());
                create.execute(dialect.createFlagsTable());
                for (java.util.Map.Entry<String, String> entry : valueByPrefix.entrySet()) {
                    try (PreparedStatement delete = connection.prepareStatement("DELETE FROM app_flags WHERE name LIKE ?")) {
                        delete.setString(1, entry.getKey() + "%");
                        delete.executeUpdate();
                    }
                    try (PreparedStatement delete = connection.prepareStatement("DELETE FROM settings WHERE name = ?")) {
                        delete.setString(1, keyOf(entry.getKey()));
                        delete.executeUpdate();
                    }
                    try (PreparedStatement insert = connection.prepareStatement("INSERT INTO settings (name, value) VALUES (?, ?)")) {
                        insert.setString(1, keyOf(entry.getKey()));
                        insert.setString(2, entry.getValue() == null ? "" : entry.getValue());
                        insert.executeUpdate();
                    }
                }
                if (alsoWrite != null) alsoWrite.run(connection);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save settings", e);
        }
    }

    static void set(Connection connection, String name) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(SqlDialect.of(connection).insertIgnoreInto("app_flags") + " (name) VALUES (?)")) {
            insert.setString(1, name);
            insert.executeUpdate();
        }
    }
}
