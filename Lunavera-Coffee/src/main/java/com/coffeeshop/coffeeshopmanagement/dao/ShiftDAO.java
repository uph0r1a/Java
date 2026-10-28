package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.model.ShiftClose;
import com.coffeeshop.coffeeshopmanagement.service.ShiftCash;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Money;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Closing a shift (TODO 4.6). A shift is the stretch of time since the previous close (since the start of today when nothing was
 * ever closed): the till does not know who "is on shift", only that the money is counted now. The window is {@code paid_at >= start
 * AND paid_at < end}, so every paid order belongs to exactly one closed shift: the next window starts where this one ended.
 *
 * <p>Cash sales are the totals of the paid orders paid in cash; a sale paid by transfer, card or e-wallet is revenue but not cash in
 * the drawer. An order refunded after its shift was closed is not taken out of that shift (there is no refund time on the order),
 * so a refund of cash is the cashier's to note.
 */
public class ShiftDAO {

    /** One person's share of a shift's or a day's sales. {@code employeeName} is null when the order has no person on record. */
    public record EmployeeShare(Integer employeeId, String employeeName, int orders, BigDecimal revenue, BigDecimal cash) {
    }

    /** What was sold in a window of time. */
    public record Summary(int paidOrders, BigDecimal revenue, BigDecimal cashSales, List<EmployeeShare> byEmployee) {
    }

    /**
     * The cash an order put in the drawer, as an SQL value for a query that names the orders table {@code o} (TODO 4.6, 4.32): the
     * whole total of an order paid in cash (an order with no method counts as cash, as before), and for an order paid with two
     * methods only its cash line. Money paid by card, transfer or wallet never counts.
     */
    static final String CASH_OF_ORDER = "(CASE WHEN EXISTS (SELECT 1 FROM order_payments cp WHERE cp.order_id = o.id) "
            + "THEN COALESCE((SELECT SUM(cp.amount) FROM order_payments cp WHERE cp.order_id = o.id AND cp.method = 'CASH'), 0) "
            + "WHEN (o.payment_method = 'CASH' OR o.payment_method IS NULL) THEN o.total ELSE 0 END)";

    /** The end of the newest closed shift: where the next one starts; empty when no shift was ever closed. */
    public Optional<LocalDateTime> lastCloseEnd() {
        try (Connection connection = DatabaseConfig.getConnection()) {
            return Optional.ofNullable(lastCloseEnd(connection));
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the last shift close", e);
        }
    }

    private static LocalDateTime lastCloseEnd(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT period_end FROM shift_closes ORDER BY period_end DESC, id DESC LIMIT 1");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? DbTime.parse(rs.getString(1)) : null;
        }
    }

    /** Where the shift being closed now starts: the end of the previous close, or the start of today. */
    public LocalDateTime currentShiftStart() {
        return lastCloseEnd().orElse(AppClock.today().atStartOfDay());
    }

    /** The cash that was counted at the previous close: what the drawer starts the next shift with (the cashier may change it). */
    public Optional<BigDecimal> lastCountedCash() {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT counted_cash FROM shift_closes ORDER BY period_end DESC, id DESC LIMIT 1");
             ResultSet rs = statement.executeQuery()) {
            return rs.next() ? Optional.of(Money.fromDong(rs.getLong(1))) : Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the last shift close", e);
        }
    }

    /**
     * The cash taken out of the drawer for expenses (TODO 4.38) that was written down with {@code from <= created_at < to}: the shift's
     * window of time, so a cash-out belongs to the shift during which it was written down, whatever day was typed on it.
     */
    public BigDecimal cashOut(LocalDateTime from, LocalDateTime to) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            return cashOut(connection, from, to);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to total the cash taken out of the drawer", e);
        }
    }

    static BigDecimal cashOut(Connection connection, LocalDateTime from, LocalDateTime to) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE from_drawer = 1 AND created_at >= ? AND created_at < ?")) {
            statement.setString(1, DbTime.write(from));
            statement.setString(2, DbTime.write(to));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return Money.fromDong(rs.getLong(1));
            }
        }
    }

    /** Paid orders with {@code from <= paid_at < to}: counts, revenue, cash, and the same per person (who took the money). */
    public Summary summarize(LocalDateTime from, LocalDateTime to) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            return summarize(connection, from, to);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to total the shift", e);
        }
    }

    static Summary summarize(Connection connection, LocalDateTime from, LocalDateTime to) throws SQLException {
        // Sales paid in the window minus refunds made in it (TODO 3.13): a refund made after a close belongs to the NEXT shift.
        String sign = "o." + SalesLedger.SIGN;
        String sql = "SELECT COALESCE(o.paid_by, o.employee_id) AS who, MAX(e.full_name) AS who_name, COALESCE(SUM(" + sign + "), 0) AS orders, "
                + "COALESCE(SUM(o.total * " + sign + "), 0) AS revenue, "
                + "COALESCE(SUM(" + CASH_OF_ORDER + " * " + sign + "), 0) AS cash "
                + "FROM " + SalesLedger.forWindow() + " LEFT JOIN employees e ON e.id = COALESCE(o.paid_by, o.employee_id) "
                + "GROUP BY COALESCE(o.paid_by, o.employee_id) ORDER BY revenue DESC, who";
        List<EmployeeShare> shares = new ArrayList<>();
        int orders = 0;
        long revenue = 0;
        long cash = 0;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            SalesLedger.bindWindow(statement, 1, DbTime.write(from), DbTime.write(to));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int who = rs.getInt("who");
                    Integer whoId = rs.wasNull() ? null : who;
                    int n = rs.getInt("orders");
                    long r = rs.getLong("revenue");
                    long c = rs.getLong("cash");
                    shares.add(new EmployeeShare(whoId, rs.getString("who_name"), n, Money.fromDong(r), Money.fromDong(c)));
                    orders += n;
                    revenue += r;
                    cash += c;
                }
            }
        }
        return new Summary(orders, Money.fromDong(revenue), Money.fromDong(cash), shares);
    }

    /**
     * Closes the shift {@code from} to now: totals the window again inside the transaction (a sale paid while the dialog was open is
     * in), then stores the line. Refused when another close ended after {@code from} (two tills closing at once, or a close
     * made while this dialog was open): the caller opens the dialog again and sees the new window.
     *
     * @param openingCash what the drawer held at the start of the shift
     * @param countedCash what was counted now
     * @return the stored line
     */
    public ShiftClose close(LocalDateTime from, BigDecimal openingCash, BigDecimal countedCash, String note) {
        if (Session.getCurrentUser() == null) {
            Permissions.requireAdmin("close a shift"); // nobody signed in: refused like any other action
        }
        if (openingCash == null || countedCash == null || openingCash.signum() < 0 || countedCash.signum() < 0) {
            throw new DataAccessException(I18n.t("shift.val.amount"));
        }
        LocalDateTime to = AppClock.now();
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LocalDateTime lastEnd = lastCloseEnd(connection);
                if (lastEnd != null && lastEnd.isAfter(from)) {
                    throw new DataAccessException(I18n.t("shift.val.stale"));
                }
                if (!to.isAfter(from)) {
                    to = from.plusSeconds(1);
                }
                Summary summary = summarize(connection, from, to);
                BigDecimal cashOut = cashOut(connection, from, to); // read again inside the transaction, like the sales
                BigDecimal expected = ShiftCash.expected(openingCash, summary.cashSales(), cashOut);
                BigDecimal difference = ShiftCash.difference(countedCash, expected);
                String cleanNote = com.coffeeshop.coffeeshopmanagement.service.StockRules.cleanText(note);
                Integer closedBy = Session.getCurrentEmployee() == null ? null : Session.getCurrentEmployee().getId();
                String closedByName = Session.getDisplayName();
                int id;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO shift_closes (period_start, period_end, closed_by, closed_by_name, paid_orders, revenue, cash_sales, "
                                + "opening_cash, expected_cash, counted_cash, difference, note, cash_out) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, DbTime.write(from));
                    insert.setString(2, DbTime.write(to));
                    if (closedBy == null) insert.setNull(3, Types.INTEGER); else insert.setInt(3, closedBy);
                    insert.setString(4, closedByName == null ? "" : closedByName);
                    insert.setInt(5, summary.paidOrders());
                    insert.setLong(6, Money.toDong(summary.revenue()));
                    insert.setLong(7, Money.toDong(summary.cashSales()));
                    insert.setLong(8, Money.toDong(openingCash));
                    insert.setLong(9, Money.toDong(expected));
                    insert.setLong(10, Money.toDong(countedCash));
                    insert.setLong(11, Money.toDong(difference));
                    insert.setString(12, cleanNote);
                    insert.setLong(13, Money.toDong(cashOut));
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getInt(1);
                    }
                }
                connection.commit();
                com.coffeeshop.coffeeshopmanagement.util.SecurityLog.event("closed a shift: counted " + Money.toDong(countedCash)
                        + ", expected " + Money.toDong(expected) + ", difference " + Money.toDong(difference));
                return new ShiftClose(id, from, to, closedBy, closedByName, summary.paidOrders(), summary.revenue(),
                        summary.cashSales(), openingCash, expected, countedCash, difference, cleanNote, cashOut);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to close the shift", e);
        }
    }

    /** The newest closed shifts first, at most {@code limit} (administrators only). */
    public List<ShiftClose> findRecent(int limit) {
        Permissions.requireAdmin("view shift closes");
        String sql = "SELECT id, period_start, period_end, closed_by, closed_by_name, paid_orders, revenue, cash_sales, opening_cash, "
                + "expected_cash, counted_cash, difference, note, cash_out FROM shift_closes ORDER BY period_end DESC, id DESC LIMIT ?";
        List<ShiftClose> list = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int by = rs.getInt(4);
                    Integer byId = rs.wasNull() ? null : by;
                    list.add(new ShiftClose(rs.getInt(1), DbTime.parse(rs.getString(2)), DbTime.parse(rs.getString(3)), byId,
                            rs.getString(5), rs.getInt(6), Money.fromDong(rs.getLong(7)), Money.fromDong(rs.getLong(8)),
                            Money.fromDong(rs.getLong(9)), Money.fromDong(rs.getLong(10)), Money.fromDong(rs.getLong(11)),
                            Money.fromDong(rs.getLong(12)), rs.getString(13), Money.fromDong(rs.getLong(14)))); // null cash_out reads as 0
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the shift closes", e);
        }
        return list;
    }
}
