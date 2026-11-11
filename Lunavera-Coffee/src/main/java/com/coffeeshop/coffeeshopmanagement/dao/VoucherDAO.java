package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.model.Voucher;
import com.coffeeshop.coffeeshopmanagement.service.VoucherRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Money;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Voucher codes (TODO 4.11, rule D6; the rules are in {@link VoucherRules}). Only an administrator makes or switches one. Counting a use
 * and giving it back happen on the connection of the payment or the refund, so they commit or roll back with the sale.
 */
public class VoucherDAO {

    private static final String COLUMNS = "id, code, kind, value, min_subtotal, valid_from, valid_to, max_uses, used_count, active, once_per_customer";

    /** Makes a voucher. */
    public Voucher add(String code, String kind, Long value, BigDecimal minSubtotal, LocalDate validFrom, LocalDate validTo, Integer maxUses) {
        return add(code, kind, value, minSubtotal, validFrom, validTo, maxUses, false);
    }

    /** Makes a voucher; with {@code oncePerCustomer} (TODO 4.54) a customer may use it once and a bill with no customer may not use it. */
    public Voucher add(String code, String kind, Long value, BigDecimal minSubtotal, LocalDate validFrom, LocalDate validTo, Integer maxUses,
                       boolean oncePerCustomer) {
        Permissions.requireAdmin("create a voucher");
        String problem = VoucherRules.newVoucherProblem(code, kind, value, minSubtotal, validFrom, validTo, maxUses);
        if (problem != null) throw new DataAccessException(problem);
        String clean = VoucherRules.normalise(code);
        if (findByCode(clean).isPresent()) throw new DataAccessException(I18n.t("voucher.val.exists", clean));
        String sql = "INSERT INTO vouchers (code, kind, value, min_subtotal, valid_from, valid_to, max_uses, used_count, active, created_at, once_per_customer) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 1, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false); // the voucher and its audit line are one change (TODO 2A.44)
            try {
                int id;
                try (PreparedStatement statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, clean);
                    statement.setString(2, kind);
                    statement.setLong(3, value);
                    statement.setLong(4, minSubtotal == null ? 0 : Money.toDong(minSubtotal));
                    statement.setString(5, validFrom == null ? null : validFrom.toString());
                    statement.setString(6, validTo == null ? null : validTo.toString());
                    if (maxUses == null) statement.setNull(7, Types.INTEGER); else statement.setInt(7, maxUses);
                    statement.setString(8, DbTime.write(AppClock.now()));
                    statement.setInt(9, oncePerCustomer ? 1 : 0);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getInt(1);
                    }
                }
                AuditDAO.write(connection, AuditAction.VOUCHER_CREATED, AuditTarget.VOUCHER, id, clean, null, kind + ":" + value,
                        createdDetail(minSubtotal == null ? 0 : Money.toDong(minSubtotal), validFrom, validTo, maxUses, oncePerCustomer));
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save the voucher", e);
        }
        SecurityLog.event(SecurityLog.actor() + " created voucher " + clean);
        return findByCode(clean).orElseThrow();
    }

    /**
     * The note of the audit line of a new voucher: {@code smallest bill in dong|first day|last day|most uses|1 if once per customer},
     * an empty part where there is no limit (TODO 2A.44; {@code AuditText} reads it back in words).
     */
    static String createdDetail(long minDong, LocalDate validFrom, LocalDate validTo, Integer maxUses, boolean oncePerCustomer) {
        return minDong + "|" + (validFrom == null ? "" : validFrom.toString()) + "|" + (validTo == null ? "" : validTo.toString())
                + "|" + (maxUses == null ? "" : maxUses.toString()) + "|" + (oncePerCustomer ? "1" : "0");
    }

    /** Switches a voucher on or off; a switched-off voucher is refused at the till. The change is in the audit log (TODO 2A.44). */
    public void setActive(int id, boolean active) {
        Permissions.requireAdmin("switch a voucher");
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String code;
                boolean was;
                try (PreparedStatement read = connection.prepareStatement("SELECT code, active FROM vouchers WHERE id = ?")) {
                    read.setInt(1, id);
                    try (ResultSet rs = read.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            return; // no such voucher: nothing changes, as before
                        }
                        code = rs.getString(1);
                        was = rs.getInt(2) == 1;
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("UPDATE vouchers SET active = ? WHERE id = ?")) {
                    statement.setInt(1, active ? 1 : 0);
                    statement.setInt(2, id);
                    statement.executeUpdate();
                }
                if (was != active) {
                    AuditDAO.write(connection, AuditAction.VOUCHER_SWITCHED, AuditTarget.VOUCHER, id, code, was ? "ON" : "OFF", active ? "ON" : "OFF", null);
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to change the voucher", e);
        }
        SecurityLog.event(SecurityLog.actor() + (active ? " switched on" : " switched off") + " voucher #" + id);
    }

    /** The voucher with this code (typed in any case), if there is one. */
    public Optional<Voucher> findByCode(String typed) {
        String code = VoucherRules.normalise(typed);
        if (code == null) return Optional.empty();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM vouchers WHERE code = ?")) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(read(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up the voucher", e);
        }
    }

    /** Every voucher, newest first. */
    public List<Voucher> findAll() {
        List<Voucher> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT " + COLUMNS + " FROM vouchers ORDER BY id DESC");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) result.add(read(rs));
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the vouchers", e);
        }
        return result;
    }

    /**
     * True when {@code customerId} already has a paid bill that used the voucher {@code code} (TODO 4.54). Used at the till to refuse
     * early with a clear sentence; the payment transaction checks again ({@link #use}), which is the check that counts.
     */
    public boolean usedBy(String code, int customerId) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            return paidBillsOf(connection, code, customerId, 0, false) > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up the voucher", e);
        }
    }

    /** The paid bills of the customer that used the voucher, not counting bill {@code exceptOrderId}; a refunded bill is not paid and gives the use back. */
    private static int paidBillsOf(Connection connection, String code, int customerId, int exceptOrderId, boolean lock) throws SQLException {
        String sql = "SELECT COUNT(*) FROM orders WHERE voucher_code = ? AND customer_id = ? AND status = "
                + com.coffeeshop.coffeeshopmanagement.config.SqlStatus.PAID + " AND id <> ?" + (lock && !DatabaseConfig.isSqlite() ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setInt(2, customerId);
            statement.setInt(3, exceptOrderId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Counts one use inside the caller's transaction, in one guarded statement: nothing happens (and the caller's sale is refused and
     * rolled back) when the voucher is off, out of its days, or used up by now, so two tills cannot both take the last use.
     */
    static void use(Connection connection, String code, LocalDate today, Integer customerId, int orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE vouchers SET used_count = used_count + 1 WHERE code = ? AND active = 1 "
                        + "AND (max_uses IS NULL OR used_count < max_uses) "
                        + "AND (valid_from IS NULL OR valid_from <= ?) AND (valid_to IS NULL OR valid_to >= ?)")) {
            statement.setString(1, code);
            statement.setString(2, today.toString());
            statement.setString(3, today.toString());
            if (statement.executeUpdate() == 0) {
                connection.rollback();
                throw new DataAccessException(I18n.t("voucher.problem.nolonger", code));
            }
        }
        // Once per customer (TODO 4.54). The UPDATE above holds the voucher's row until this transaction ends, so a second till paying
        // for the same customer waits there and then sees this bill (a locking read on MariaDB, which would otherwise read an old snapshot).
        boolean once;
        try (PreparedStatement statement = connection.prepareStatement("SELECT once_per_customer FROM vouchers WHERE code = ?")) {
            statement.setString(1, code);
            try (ResultSet rs = statement.executeQuery()) {
                once = rs.next() && rs.getInt(1) == 1;
            }
        }
        if (!once) return;
        if (customerId == null) {
            connection.rollback();
            throw new DataAccessException(I18n.t("voucher.problem.needscustomer"));
        }
        if (paidBillsOf(connection, code, customerId, orderId, true) > 0) {
            connection.rollback();
            throw new DataAccessException(I18n.t("voucher.problem.onceonly"));
        }
    }

    /** Gives a use back inside a refund's transaction (never below 0). */
    static void release(Connection connection, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE vouchers SET used_count = used_count - 1 WHERE code = ? AND used_count > 0")) {
            statement.setString(1, code);
            statement.executeUpdate();
        }
    }

    private static Voucher read(ResultSet rs) throws SQLException {
        String from = rs.getString("valid_from");
        String to = rs.getString("valid_to");
        int max = rs.getInt("max_uses");
        boolean noMax = rs.wasNull();
        return new Voucher(rs.getInt("id"), rs.getString("code"), rs.getString("kind"), rs.getLong("value"),
                Money.fromDong(rs.getLong("min_subtotal")), from == null ? null : LocalDate.parse(from.substring(0, 10)),
                to == null ? null : LocalDate.parse(to.substring(0, 10)), noMax ? null : max, rs.getInt("used_count"), rs.getInt("active") == 1,
                rs.getInt("once_per_customer") == 1);
    }
}
