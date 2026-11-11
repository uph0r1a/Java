package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.AuditDAO;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Duplicate customers and customers who ask to be forgotten (TODO 4.8 and 3.4). Two actions, both for an administrator, each one
 * transaction, each refusing in the database itself (not only on the screen) when the data changed since the screen was loaded.
 *
 * <p><b>Never delete (decision D21, as the owner answered).</b> There is no "delete a customer": a customer who asks to be forgotten is
 * <em>anonymised</em>: the name becomes "Former customer #12", the phone and e-mail are cleared, the points are set to zero, a login
 * is locked and renamed, and the orders keep pointing at the row, so every total stays the same. Two rows that are the same person
 * are <em>merged</em>: the duplicate's orders and points move to the row that is kept (which keeps its own name and phone; it only
 * takes the duplicate's phone or e-mail when it has none) and the duplicate disappears. Merging and anonymising cannot be undone.
 * This is the same pattern as for former staff ({@code AccountService.anonymiseStaff}, TODO 4.29).
 *
 * <p>A merge is written as one line of the audit log and of the security log (the two ids, how many orders and points moved, who
 * and when). There is no table of merges: the audit line names the duplicate, and it is scrubbed when the kept customer is made
 * anonymous later. The duplicate's phone and e-mail are not written anywhere (a mistyped phone can belong to a stranger, TODO 3.29).
 */
public class CustomerMergeService {

    /** What a customer holds, to word the confirmation and to see what a button would refuse. */
    public record Impact(int orders, int openOrders, int points, boolean hasLogin, boolean anonymous) {
    }

    /** What one merge moved. */
    public record MergeResult(int ordersMoved, int pointsMoved, String keptName) {
    }

    private final CustomerDAO customerDAO;

    public CustomerMergeService() {
        this(new CustomerDAO());
    }

    public CustomerMergeService(CustomerDAO customerDAO) {
        this.customerDAO = customerDAO;
    }

    /** What the customer has, or a {@link DataAccessException} when the row is gone (another till removed it). */
    public Impact impact(int customerId) {
        String sql = "SELECT (SELECT COUNT(*) FROM orders WHERE customer_id = ?), "
                + "(SELECT COUNT(*) FROM orders WHERE customer_id = ? AND status = " + SqlStatus.OPEN + "), "
                + "loyalty_points, (SELECT COUNT(*) FROM users WHERE customer_id = ?), anonymised_at "
                + "FROM customers WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setInt(1, customerId);
            st.setInt(2, customerId);
            st.setInt(3, customerId);
            st.setInt(4, customerId);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) {
                    throw new DataAccessException(I18n.t("customers.gone"));
                }
                return new Impact(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4) > 0, rs.getString(5) != null);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to look at a customer's records", e);
        }
    }

    /**
     * Moves every order and all the points of {@code removeId} to {@code keepId} and deletes {@code removeId}, in one transaction.
     * The kept customer keeps its own name, phone and e-mail; a phone or e-mail it lacks is taken from the duplicate. Refused when
     * the two are the same row, when either is gone or anonymous, when the duplicate has a login account (move or lock that on
     * the account screen first) or an open order (pay or cancel it first, so no till holds a cart for a row that is about to
     * vanish), and when the points together would not fit a whole number.
     *
     * @throws DataAccessException with a sentence fit to show
     */
    public MergeResult merge(int keepId, int removeId) {
        Permissions.requireAdmin("merge customers");
        if (keepId == removeId) {
            throw new DataAccessException(I18n.t("customers.merge.same"));
        }
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Row kept = read(connection, keepId);
                Row removed = read(connection, removeId);
                if (kept == null || removed == null) {
                    throw new DataAccessException(I18n.t("customers.gone"));
                }
                if (kept.anonymous() || removed.anonymous()) {
                    throw new DataAccessException(I18n.t("customers.merge.anonymous"));
                }
                if (exists(connection, "SELECT 1 FROM users WHERE customer_id = ?", removeId)) {
                    throw new DataAccessException(I18n.t("customers.merge.login"));
                }
                if (exists(connection, "SELECT 1 FROM orders WHERE customer_id = ? AND status = " + SqlStatus.OPEN, removeId)) {
                    throw new DataAccessException(I18n.t("customers.merge.open"));
                }
                if ((long) kept.points() + removed.points() > Integer.MAX_VALUE) {
                    throw new DataAccessException(I18n.t("customers.merge.points"));
                }

                // TODO 3.66: the kept customer keeps the LATER of the two "birthday discount given" days, so a merge cannot give it twice.
                final java.time.LocalDate birthdayUsed = later(usedOn(connection, keepId), usedOn(connection, removeId));
                int ordersMoved;
                try (PreparedStatement st = connection.prepareStatement("UPDATE orders SET customer_id = ? WHERE customer_id = ?")) {
                    st.setInt(1, keepId);
                    st.setInt(2, removeId);
                    ordersMoved = st.executeUpdate();
                }
                try (PreparedStatement st = connection.prepareStatement("UPDATE reservations SET customer_id = ? WHERE customer_id = ?")) {
                    st.setInt(1, keepId); // TODO 2.88: the bookings follow the customer that is kept
                    st.setInt(2, removeId);
                    st.executeUpdate();
                }
                // Removed only if it is still exactly what was read: the same points, no order or login that appeared meanwhile.
                // The foreign keys would stop a delete that still has orders; this makes it a clear message instead of an SQL error.
                try (PreparedStatement st = connection.prepareStatement("DELETE FROM customers WHERE id = ? AND loyalty_points = ? "
                        + "AND anonymised_at IS NULL AND NOT EXISTS (SELECT 1 FROM orders WHERE customer_id = ?) "
                        + "AND NOT EXISTS (SELECT 1 FROM users WHERE customer_id = ?)")) {
                    st.setInt(1, removeId);
                    st.setInt(2, removed.points());
                    st.setInt(3, removeId);
                    st.setInt(4, removeId);
                    if (st.executeUpdate() == 0) {
                        throw new DataAccessException(I18n.t("customers.merge.changed"));
                    }
                }
                // After the delete, so a phone taken from the duplicate is free again for the unique key.
                try (PreparedStatement st = connection.prepareStatement(
                        "UPDATE customers SET loyalty_points = loyalty_points + ?, lifetime_points = lifetime_points + ?, phone = ?, email = ?, birthday_used_on = ? "
                                + "WHERE id = ? AND anonymised_at IS NULL")) {
                    st.setInt(1, removed.points());
                    st.setInt(2, Math.max(removed.lifetime(), removed.points())); // what the duplicate earned goes with its orders
                    st.setString(3, blank(kept.phone()) ? blankToNull(removed.phone()) : kept.phone());
                    st.setString(4, blank(kept.email()) ? blankToNull(removed.email()) : kept.email());
                    st.setString(5, birthdayUsed == null ? null : birthdayUsed.toString());
                    st.setInt(6, keepId);
                    if (st.executeUpdate() == 0) {
                        throw new DataAccessException(I18n.t("customers.merge.changed"));
                    }
                }
                // The merge is one log line (rule D21, as the owner answered): the kept customer is the target, the duplicate's name and
                // number are the "old" value, the detail is "orders|points" and the screen turns it into words. There is no table of
                // merges: the line is scrubbed when the kept customer is made anonymous.
                AuditDAO.write(connection, AuditAction.CUSTOMER_MERGED, AuditTarget.CUSTOMER, keepId, kept.name(),
                        "#" + removeId + " " + removed.name(), null, ordersMoved + "|" + removed.points());
                connection.commit();
                SecurityLog.event(SecurityLog.actor() + " merged customer " + removeId + " into customer " + keepId
                        + " (" + ordersMoved + " orders, " + removed.points() + " points)");
                return new MergeResult(ordersMoved, removed.points(), kept.name());
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException(I18n.t("customers.merge.fail"), e);
        }
    }

    /** The day the birthday discount was given to this customer (TODO 3.66), or null. */
    private static java.time.LocalDate usedOn(Connection connection, int customerId) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement("SELECT birthday_used_on FROM customers WHERE id = ?")) {
            st.setInt(1, customerId);
            try (java.sql.ResultSet rs = st.executeQuery()) {
                return rs.next() ? com.coffeeshop.coffeeshopmanagement.service.BirthdayRules.parse(rs.getString(1)) : null;
            }
        }
    }

    private static java.time.LocalDate later(java.time.LocalDate a, java.time.LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    /**
     * Removes everything personal from a customer who has orders (rule D21): the name becomes "Former customer #id", the phone and
     * e-mail are cleared, the points are set to zero, a login account is locked, renamed and given a password nobody knows, and
     * the names of duplicates merged into this customer are removed from the merge notes. The orders keep pointing at the row, so
     * the sales, the reports and the history stay the same. Cannot be undone. Refused while the customer has an open order.
     *
     * @throws DataAccessException with a sentence fit to show
     */
    public void anonymise(int customerId) {
        Permissions.requireAdmin("make a customer anonymous");
        String newName = I18n.t("customers.former.name", customerId);
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Row row = read(connection, customerId);
                if (row == null) {
                    throw new DataAccessException(I18n.t("customers.gone"));
                }
                if (row.anonymous()) {
                    throw new DataAccessException(I18n.t("customers.anon.already"));
                }
                if (exists(connection, "SELECT 1 FROM orders WHERE customer_id = ? AND status = " + SqlStatus.OPEN, customerId)) {
                    throw new DataAccessException(I18n.t("customers.anon.open"));
                }
                try (PreparedStatement st = connection.prepareStatement("UPDATE customers SET full_name = ?, phone = NULL, email = NULL, "
                        + "loyalty_points = 0, lifetime_points = 0, birthday = NULL, birthday_used_on = NULL, anonymised_at = ? WHERE id = ? AND anonymised_at IS NULL")) {
                    st.setString(1, newName);
                    st.setString(2, DbTime.write(AppClock.now()));
                    st.setInt(3, customerId);
                    if (st.executeUpdate() == 0) {
                        throw new DataAccessException(I18n.t("customers.anon.already"));
                    }
                }
                lockLogins(connection, customerId);
                com.coffeeshop.coffeeshopmanagement.dao.ReservationDAO.anonymiseFor(connection, customerId, row.phone(), newName); // TODO 2.88
                // TODO 4.2: older audit lines about this customer lose the name and the old and new values; this one names nobody
                AuditDAO.scrubTarget(connection, AuditTarget.CUSTOMER, customerId, newName, true);
                AuditDAO.write(connection, AuditAction.CUSTOMER_ANONYMISED, AuditTarget.CUSTOMER, customerId, newName, null, null, null);
                connection.commit();
                SecurityLog.event(SecurityLog.actor() + " made customer " + customerId + " anonymous");
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException(I18n.t("customers.anon.fail"), e);
        }
    }

    private static void lockLogins(Connection connection, int customerId) throws SQLException {
        List<Integer> userIds = new ArrayList<>();
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT id FROM users WHERE customer_id = ? AND role = " + SqlStatus.ROLE_CUSTOMER + " ORDER BY id")) {
            st.setInt(1, customerId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    userIds.add(rs.getInt(1));
                }
            }
        }
        for (int userId : userIds) {
            String loginName = "former-customer-" + customerId;
            if (usernameTaken(connection, loginName)) {
                loginName = loginName + "-" + userId;
            }
            try (PreparedStatement st = connection.prepareStatement("UPDATE users SET username = ?, password_hash = ?, "
                    + "status = " + SqlStatus.LOCKED + ", must_change_password = 0 WHERE id = ?")) {
                st.setString(1, loginName);
                st.setString(2, PasswordUtil.hash(java.util.UUID.randomUUID().toString()));
                st.setInt(3, userId);
                st.executeUpdate();
            }
        }
    }

    private static boolean usernameTaken(Connection connection, String username) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement("SELECT 1 FROM users WHERE lower(username) = lower(?)")) {
            st.setString(1, username);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean exists(Connection connection, String sql, int id) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    private record Row(String name, String phone, String email, int points, int lifetime, boolean anonymous) {
    }

    private static Row read(Connection connection, int id) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT full_name, phone, email, loyalty_points, lifetime_points, anonymised_at FROM customers WHERE id = ?")) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getString(6) != null) : null;
            }
        }
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private static String blankToNull(String text) {
        return blank(text) ? null : text;
    }
}
