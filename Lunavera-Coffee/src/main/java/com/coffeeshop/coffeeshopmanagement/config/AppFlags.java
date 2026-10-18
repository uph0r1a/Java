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

    private AppFlags() {
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

    static void set(Connection connection, String name) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(SqlDialect.of(connection).insertIgnoreInto("app_flags") + " (name) VALUES (?)")) {
            insert.setString(1, name);
            insert.executeUpdate();
        }
    }
}
