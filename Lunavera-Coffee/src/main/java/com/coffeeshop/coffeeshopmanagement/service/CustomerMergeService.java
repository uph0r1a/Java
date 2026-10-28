package com.coffeeshop.coffeeshopmanagement.service;

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
 * Duplicate customers and customers who ask to be forgotten (TODO 4.8 and 3.4). Three actions, all for an administrator, each one
 * transaction, each refusing in the database itself (not only on the screen) when the data changed since the screen was loaded.
 *
 * <p><b>The rule between deleting and anonymising (decision D21).</b> A customer row that nothing points at (no order, no points,
 * no login) is <em>deleted</em>: there is nothing to keep. A customer who has orders can never be deleted, because the orders are
 * the shop's sales and the reports must not change; on request such a customer is <em>anonymised</em> instead: the name becomes
 * "Former customer #12", the phone and e-mail are cleared, the points are set to zero, a login is locked and renamed, and the
 * orders keep pointing at the row, so every total stays the same. Two rows that are the same person are <em>merged</em>: the
 * duplicate's orders and points move to the row that is kept and the duplicate disappears. Merging and anonymising cannot be
 * undone. This is the same pattern as for former staff ({@code AccountService.anonymiseStaff}, TODO 4.29).
 *
 * <p>A merge is written down in {@code customer_merges}: the two ids, the name the duplicate had, how many orders and points moved,
 * who did it and when. The duplicate's phone and e-mail are not kept there (a mistyped phone can belong to a stranger, TODO 3.29);
 * when the kept customer is anonymised or deleted later, the names in its merge notes are removed with it.
 */
public class CustomerMergeService {

    /** What a customer holds, to word the confirmation and to see what a button would refuse. */
    public record Impact(int orders, int openOrders, int points, boolean hasLogin, boolean anonymous) {
        /** True when a delete is allowed: nothing points at the row (the same test as {@link CustomerDAO#deleteIfUnused}). */
        public boolean deletable() {
            return orders == 0 && points == 0 && !hasLogin;
        }
    }

    /** What one merge moved. */
    public record MergeResult(int ordersMoved, int pointsMoved, String keptName) {
    }

    /** One line of the merge history of a kept customer. */
    public record MergeNote(int removedId, String removedName, int ordersMoved, int pointsMoved, LocalDateTime mergedAt) {
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

                int ordersMoved;
                try (PreparedStatement st = connection.prepareStatement("UPDATE orders SET customer_id = ? WHERE customer_id = ?")) {
                    st.setInt(1, keepId);
                    st.setInt(2, removeId);
                    ordersMoved = st.executeUpdate();
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
                        "UPDATE customers SET loyalty_points = loyalty_points + ?, phone = ?, email = ? WHERE id = ? AND anonymised_at IS NULL")) {
                    st.setInt(1, removed.points());
                    st.setString(2, blank(kept.phone()) ? blankToNull(removed.phone()) : kept.phone());
                    st.setString(3, blank(kept.email()) ? blankToNull(removed.email()) : kept.email());
                    st.setInt(4, keepId);
                    if (st.executeUpdate() == 0) {
                        throw new DataAccessException(I18n.t("customers.merge.changed"));
                    }
                }
                try (PreparedStatement st = connection.prepareStatement("INSERT INTO customer_merges "
                        + "(kept_id, removed_id, removed_name, orders_moved, points_moved, merged_by, merged_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    User me = Session.getCurrentUser();
                    st.setInt(1, keepId);
                    st.setInt(2, removeId);
                    st.setString(3, removed.name());
                    st.setInt(4, ordersMoved);
                    st.setInt(5, removed.points());
                    if (me == null) {
                        st.setNull(6, Types.INTEGER);
                    } else {
                        st.setInt(6, me.getId());
                    }
                    st.setString(7, DbTime.write(AppClock.now()));
                    st.executeUpdate();
                }
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
                        + "loyalty_points = 0, anonymised_at = ? WHERE id = ? AND anonymised_at IS NULL")) {
                    st.setString(1, newName);
                    st.setString(2, DbTime.write(AppClock.now()));
                    st.setInt(3, customerId);
                    if (st.executeUpdate() == 0) {
                        throw new DataAccessException(I18n.t("customers.anon.already"));
                    }
                }
                lockLogins(connection, customerId);
                try (PreparedStatement st = connection.prepareStatement("UPDATE customer_merges SET removed_name = ? WHERE kept_id = ?")) {
                    st.setString(1, newName);
                    st.setInt(2, customerId);
                    st.executeUpdate();
                }
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

    /**
     * Deletes a customer that nothing points at (rule D21): no order, no points, no login. Its merge notes go with it.
     *
     * @return false when something points at it, so it was not deleted (the person then has {@link #anonymise}); also false when
     *         it is already gone
     */
    public boolean delete(int customerId) {
        Permissions.requireAdmin("delete a customer");
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean deleted = customerDAO.deleteIfUnused(connection, customerId);
                if (deleted) {
                    try (PreparedStatement st = connection.prepareStatement("DELETE FROM customer_merges WHERE kept_id = ?")) {
                        st.setInt(1, customerId);
                        st.executeUpdate();
                    }
                }
                connection.commit();
                if (deleted) {
                    SecurityLog.event(SecurityLog.actor() + " deleted customer " + customerId);
                }
                return deleted;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            // the orders' foreign key stops a customer that got an order between the check and the delete
            throw new DataAccessException(I18n.t("customers.delete.fail"), e);
        }
    }

    /** The merges into this customer, oldest first. */
    public List<MergeNote> notesFor(int keptId) {
        Permissions.requireAdmin("read the customer merge notes");
        String sql = "SELECT removed_id, removed_name, orders_moved, points_moved, merged_at FROM customer_merges "
                + "WHERE kept_id = ? ORDER BY id";
        List<MergeNote> notes = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement st = connection.prepareStatement(sql)) {
            st.setInt(1, keptId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    notes.add(new MergeNote(rs.getInt(1), rs.getString(2), rs.getInt(3), rs.getInt(4), DbTime.parse(rs.getString(5))));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the customer merge notes", e);
        }
        return notes;
    }

    /** Every customer-role login of the customer: locked, a login name that says nothing, a password nobody knows. */
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

    private record Row(String name, String phone, String email, int points, boolean anonymous) {
    }

    private static Row read(Connection connection, int id) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT full_name, phone, email, loyalty_points, anonymised_at FROM customers WHERE id = ?")) {
            st.setInt(1, id);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? new Row(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getString(5) != null) : null;
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
