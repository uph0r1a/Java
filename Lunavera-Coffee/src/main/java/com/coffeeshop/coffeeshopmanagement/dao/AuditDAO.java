package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditEntry;
import com.coffeeshop.coffeeshopmanagement.service.AuditRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The audit log (TODO 4.2): who did what, when, and what it was before and after.
 *
 * <p><b>Writing.</b> {@link #write} adds a line on the caller's own connection, so the line and the change it describes are one
 * transaction: if the change is rolled back there is no line, and a change that was committed always has its line. It throws
 * {@link SQLException} like the statements around it, so a failure to log stops the change; for a simple insert that is rare, and
 * a change nobody can account for is worse than one that is refused. {@link #recordQuietly} is for things that are not database
 * changes (a backup file was made): it opens its own connection and only logs a warning if it cannot write.
 *
 * <p><b>Who.</b> The signed-in account and its staff record are read from {@link Session}; nobody signed in means the line says
 * {@link AuditRules#SYSTEM_ACTOR}. Names are copies, and a person made anonymous has theirs rewritten ({@link #scrubActor},
 * {@link #scrubTarget}), so the log never keeps what 4.29 and 4.8 removed.
 *
 * <p><b>Reading.</b> Administrators only. Passwords and password hashes are never written here.
 */
public final class AuditDAO {

    private static final Logger LOGGER = Logger.getLogger(AuditDAO.class.getName());

    public AuditDAO() {
    }

    /** Adds one line on {@code connection} (the caller commits). Text is cleaned and cut by {@link AuditRules#clip}. */
    public static void write(Connection connection, AuditAction action, String targetType, Integer targetId, String targetLabel,
                             String oldValue, String newValue, String detail) throws SQLException {
        var user = Session.getCurrentUser();
        var employee = Session.getCurrentEmployee();
        String actorName = user == null ? AuditRules.SYSTEM_ACTOR : AuditRules.clip(Session.getDisplayName(), AuditRules.LABEL_MAX);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO audit_log (logged_at, actor_user_id, actor_employee_id, actor_name, action, target_type, target_id, "
                        + "target_label, old_value, new_value, detail) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, DbTime.write(AppClock.now()));
            setInt(statement, 2, user == null ? null : user.getId());
            setInt(statement, 3, employee == null ? null : employee.getId());
            statement.setString(4, actorName == null ? AuditRules.SYSTEM_ACTOR : actorName);
            statement.setString(5, action.name());
            statement.setString(6, targetType);
            setInt(statement, 7, targetId);
            statement.setString(8, AuditRules.clip(targetLabel, AuditRules.LABEL_MAX));
            statement.setString(9, AuditRules.clip(oldValue, AuditRules.VALUE_MAX));
            statement.setString(10, AuditRules.clip(newValue, AuditRules.VALUE_MAX));
            statement.setString(11, AuditRules.clip(detail, AuditRules.VALUE_MAX));
            statement.executeUpdate();
        }
    }

    /** For something that is not a database change (a backup file, a restore at start-up): own connection, never throws. */
    public static void recordQuietly(AuditAction action, String targetType, Integer targetId, String targetLabel,
                                     String oldValue, String newValue, String detail) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            write(connection, action, targetType, targetId, targetLabel, oldValue, newValue, detail);
        } catch (SQLException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "The audit line for " + action + " could not be written", e);
        }
    }

    private static void setInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) statement.setNull(index, Types.INTEGER); else statement.setInt(index, value);
    }

    /**
     * Rewrites the name copied into every line this person did, when they are made anonymous (TODO 4.29), on the caller's
     * connection and transaction.
     */
    public static void scrubActor(Connection connection, int employeeId, String newName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE audit_log SET actor_name = ? WHERE actor_employee_id = ?")) {
            statement.setString(1, AuditRules.clip(newName, AuditRules.LABEL_MAX));
            statement.setInt(2, employeeId);
            statement.executeUpdate();
        }
    }

    /**
     * Rewrites the label of every line about one person's record (a staff record, an account, a customer) when they are made
     * anonymous, and drops the values and detail too when {@code clearValues} (a customer's old and new names, a staff member's
     * login name are personal; a role is not).
     */
    public static void scrubTarget(Connection connection, String targetType, int targetId, String newLabel, boolean clearValues)
            throws SQLException {
        String sql = "UPDATE audit_log SET target_label = ?" + (clearValues ? ", old_value = NULL, new_value = NULL, detail = NULL" : "")
                + " WHERE target_type = ? AND target_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, AuditRules.clip(newLabel, AuditRules.LABEL_MAX));
            statement.setString(2, targetType);
            statement.setInt(3, targetId);
            statement.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ reading

    /**
     * What the screen asks for. Every part is optional: {@code from} and {@code to} are whole days (the last one included),
     * {@code actor} and {@code text} are matched as text anywhere in the name (the text also in the target, the values and the detail).
     */
    public record Filter(LocalDate from, LocalDate to, AuditAction action, String actor, String text) {
        public static final Filter NONE = new Filter(null, null, null, null, null);

        public boolean hasReversedDates() {
            return from != null && to != null && from.isAfter(to);
        }
    }

    private record Where(String sql, List<Object> params) {
    }

    private static Where where(Filter filter) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (filter.from() != null) {
            sql.append(" AND logged_at >= ?");
            params.add(DbTime.write(filter.from().atStartOfDay()));
        }
        if (filter.to() != null) {
            sql.append(" AND logged_at < ?");
            params.add(DbTime.write(filter.to().plusDays(1).atStartOfDay()));
        }
        if (filter.action() != null) {
            sql.append(" AND action = ?");
            params.add(filter.action().name());
        }
        String actor = AuditRules.clip(filter.actor(), 100);
        if (actor != null) {
            sql.append(" AND LOWER(actor_name) LIKE ? ESCAPE '!'");
            params.add("%" + OrderDAO.escapeLike(actor.toLowerCase(java.util.Locale.ROOT)) + "%");
        }
        String text = AuditRules.clip(filter.text(), 100);
        if (text != null) {
            String like = "%" + OrderDAO.escapeLike(text.toLowerCase(java.util.Locale.ROOT)) + "%";
            sql.append(" AND (LOWER(target_label) LIKE ? ESCAPE '!' OR LOWER(old_value) LIKE ? ESCAPE '!' "
                    + "OR LOWER(new_value) LIKE ? ESCAPE '!' OR LOWER(detail) LIKE ? ESCAPE '!'");
            for (int i = 0; i < 4; i++) params.add(like);
            if (text.matches("[0-9]{1,9}")) { // an order, product or account number
                sql.append(" OR target_id = ?");
                params.add(Integer.parseInt(text));
            }
            sql.append(")");
        }
        return new Where(sql.toString(), params);
    }

    private static void bind(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof Integer n) statement.setInt(i + 1, n); else statement.setString(i + 1, (String) p);
        }
    }

    /** The newest lines first that match, {@code limit} of them from {@code offset} (administrators only). */
    public List<AuditEntry> find(Filter filter, int limit, int offset) {
        Permissions.requireAdmin("read the audit log");
        Where w = where(filter == null ? Filter.NONE : filter);
        String sql = "SELECT id, logged_at, actor_user_id, actor_employee_id, actor_name, action, target_type, target_id, "
                + "target_label, old_value, new_value, detail FROM audit_log" + w.sql()
                + " ORDER BY logged_at DESC, id DESC LIMIT ? OFFSET ?";
        List<AuditEntry> list = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, w.params());
            statement.setInt(w.params().size() + 1, Math.max(1, limit));
            statement.setInt(w.params().size() + 2, Math.max(0, offset));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int user = rs.getInt(3);
                    Integer userId = rs.wasNull() ? null : user;
                    int employee = rs.getInt(4);
                    Integer employeeId = rs.wasNull() ? null : employee;
                    int target = rs.getInt(8);
                    Integer targetId = rs.wasNull() ? null : target;
                    list.add(new AuditEntry(rs.getInt(1), DbTime.parse(rs.getString(2)), userId, employeeId, rs.getString(5),
                            rs.getString(6), rs.getString(7), targetId, rs.getString(9), rs.getString(10), rs.getString(11),
                            rs.getString(12)));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the audit log", e);
        }
        return list;
    }

    /** How many lines match (administrators only). */
    public int count(Filter filter) {
        Permissions.requireAdmin("read the audit log");
        Where w = where(filter == null ? Filter.NONE : filter);
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM audit_log" + w.sql())) {
            bind(statement, w.params());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count the audit log", e);
        }
    }
}
