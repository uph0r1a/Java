package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Expense;
import com.coffeeshop.coffeeshopmanagement.service.ExpenseRules;
import com.coffeeshop.coffeeshopmanagement.service.StockRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.Money;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Expenses and cash taken out of the drawer (TODO 4.38). The rules (who may write, what is valid, how net profit is worked out) are
 * in {@link ExpenseRules}. An administrator writes any expense and may delete one; any signed-in person may write down cash they took
 * out of the drawer, always dated today, and then it is taken off the cash the drawer should hold when the shift is closed
 * ({@code ShiftDAO.close}).
 */
public class ExpenseDAO {

    /** The newest lines shown by default. */
    public static final int DEFAULT_LIMIT = 500;

    /**
     * Writes an expense down.
     *
     * @param spentOn    the day it was spent; ignored (today is used) when {@code fromDrawer}
     * @param fromDrawer paid from the cash drawer, so it counts in the shift close
     * @throws DataAccessException with a sentence fit to show when the values are not allowed, or the person may not do this
     */
    public Expense add(LocalDate spentOn, BigDecimal amount, String category, String note, boolean fromDrawer) {
        if (fromDrawer) {
            if (Session.getCurrentUser() == null) {
                Permissions.requireAdmin("record cash taken out of the drawer"); // nobody signed in: refused like any other action
            }
        } else {
            Permissions.requireAdmin("record an expense");
        }
        LocalDate today = AppClock.today();
        LocalDate day = fromDrawer ? today : spentOn;
        String problem = ExpenseRules.problem(day, amount, category, today);
        if (problem != null) {
            throw new DataAccessException(problem);
        }
        String cleanNote = StockRules.cleanText(note);
        Integer by = Session.getCurrentEmployee() == null ? null : Session.getCurrentEmployee().getId();
        String byName = Session.getDisplayName() == null ? "" : Session.getDisplayName();
        var createdAt = AppClock.now();
        String sql = "INSERT INTO expenses (spent_on, amount, category, note, from_drawer, recorded_by, recorded_by_name, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, day.toString());
            statement.setLong(2, Money.toDong(amount));
            statement.setString(3, category);
            statement.setString(4, cleanNote);
            statement.setInt(5, fromDrawer ? 1 : 0);
            if (by == null) statement.setNull(6, Types.INTEGER); else statement.setInt(6, by);
            statement.setString(7, byName);
            statement.setString(8, DbTime.write(createdAt));
            statement.executeUpdate();
            int id;
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                id = keys.getInt(1);
            }
            SecurityLog.event(SecurityLog.actor() + " recorded an expense of " + Money.toDong(amount) + " (" + category
                    + (fromDrawer ? ", from the drawer" : "") + ")");
            return new Expense(id, day, amount, category, cleanNote, fromDrawer, by, byName, createdAt);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to record the expense", e);
        }
    }

    /** Deletes a line written by mistake (administrators only). A shift already closed keeps the cash-out figure it stored. */
    public boolean delete(int id) {
        Permissions.requireAdmin("delete an expense");
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM expenses WHERE id = ?")) {
            statement.setInt(1, id);
            boolean deleted = statement.executeUpdate() > 0;
            if (deleted) {
                SecurityLog.event(SecurityLog.actor() + " deleted expense " + id);
            }
            return deleted;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete the expense", e);
        }
    }

    /** The expenses spent on the days {@code from} to {@code to}, both included, newest day first, at most {@code limit} (administrators only). */
    public List<Expense> find(LocalDate from, LocalDate to, int limit) {
        Permissions.requireAdmin("view the expenses");
        String sql = "SELECT id, spent_on, amount, category, note, from_drawer, recorded_by, recorded_by_name, created_at "
                + "FROM expenses WHERE spent_on >= ? AND spent_on <= ? ORDER BY spent_on DESC, id DESC LIMIT ?";
        List<Expense> list = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, from.toString());
            statement.setString(2, to.toString());
            statement.setInt(3, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int by = rs.getInt(7);
                    Integer byId = rs.wasNull() ? null : by;
                    String day = rs.getString(2);
                    list.add(new Expense(rs.getInt(1), LocalDate.parse(day.substring(0, 10)), Money.fromDong(rs.getLong(3)),
                            rs.getString(4), rs.getString(5), rs.getInt(6) != 0, byId, rs.getString(8), DbTime.parse(rs.getString(9))));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the expenses", e);
        }
        return list;
    }
}
