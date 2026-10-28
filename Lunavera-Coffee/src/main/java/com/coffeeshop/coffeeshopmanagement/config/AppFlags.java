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
     * A small value kept in the flag table (TODO 2A.34): the flag's name is {@code prefix + value}, so a value must fit the
     * 100-character name column together with its prefix. Returns the value of the first flag whose name starts with
     * {@code prefix}, or empty. The prefix must not contain {@code %} or {@code _}.
     */
    public static java.util.Optional<String> valueWithPrefix(String prefix) {
        try (Connection connection = DatabaseConfig.getConnection();
             Statement create = connection.createStatement()) {
            create.execute(SqlDialect.of(connection).createFlagsTable());
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
            throw new DataAccessException("Failed to read flag " + prefix, e);
        }
    }

    /** Replaces, in one transaction, the value kept under each prefix (see {@link #valueWithPrefix}); keys are prefixes, values the new text. */
    public static void replaceValues(java.util.Map<String, String> valueByPrefix) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement create = connection.createStatement()) {
                create.execute(SqlDialect.of(connection).createFlagsTable());
                for (java.util.Map.Entry<String, String> entry : valueByPrefix.entrySet()) {
                    try (PreparedStatement delete = connection.prepareStatement("DELETE FROM app_flags WHERE name LIKE ?")) {
                        delete.setString(1, entry.getKey() + "%");
                        delete.executeUpdate();
                    }
                    set(connection, entry.getKey() + entry.getValue());
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save flags", e);
        }
    }

    static void set(Connection connection, String name) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(SqlDialect.of(connection).insertIgnoreInto("app_flags") + " (name) VALUES (?)")) {
            insert.setString(1, name);
            insert.executeUpdate();
        }
    }
}
