package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.util.BusinessDay;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.model.ShiftClose;
import com.coffeeshop.coffeeshopmanagement.service.AuditRules;
import com.coffeeshop.coffeeshopmanagement.service.ShiftCash;
import com.coffeeshop.coffeeshopmanagement.service.ShiftOpenOrders;
import com.coffeeshop.coffeeshopmanagement.service.ShiftSheet;
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

    /**
     * The orders that are still unpaid, tables first by number, then take-away orders, for the warning of the close box (TODO 5.48).
     * Every signed-in person may close a shift, so every signed-in person may read this, like {@link #summarize}.
     */
    public List<ShiftOpenOrders.Open> openOrders() {
        String sql = "SELECT id, table_number, queue_no, total FROM orders WHERE status = " + SqlStatus.OPEN
                + " ORDER BY CASE WHEN table_number IS NULL THEN 1 ELSE 0 END, table_number, id";
        List<ShiftOpenOrders.Open> open = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                int table = rs.getInt("table_number");
                Integer tableNumber = rs.wasNull() ? null : table;
                int queue = rs.getInt("queue_no");
                Integer queueNo = rs.wasNull() ? null : queue;
                open.add(new ShiftOpenOrders.Open(rs.getInt("id"), tableNumber, queueNo, Money.fromDong(rs.getLong("total"))));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list the unpaid orders", e);
        }
        return open;
    }

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

    /** Where the shift being closed now starts: the end of the previous close, or the start of today's shop day (TODO 4.50). */
    public LocalDateTime currentShiftStart() {
        return lastCloseEnd().orElse(BusinessDay.startOf(BusinessDay.today()));
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
        // A refunded line (TODO 4.37) takes money off this shift, cash only when it was given back in cash, and changes no order count.
        String sql = "SELECT COALESCE(o.paid_by, o.employee_id) AS who, MAX(e.full_name) AS who_name, COALESCE(SUM(o." + SalesLedger.COUNT + "), 0) AS orders, "
                + "COALESCE(SUM(o." + SalesLedger.TOTAL + " * " + sign + "), 0) AS revenue, "
                + "COALESCE(SUM(o." + SalesLedger.CASH + " * " + sign + "), 0) AS cash "
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
        // Gift cards sold for cash in the window (TODO 7): the money is in the drawer though it is no sale, so it counts as cash here
        // (the per-person shares and the revenue stay sales only).
        cash += Money.toDong(GiftCardDAO.soldBetween(connection, from, to));
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
                AuditDAO.write(connection, com.coffeeshop.coffeeshopmanagement.model.AuditAction.SHIFT_CLOSED,
                        com.coffeeshop.coffeeshopmanagement.model.AuditTarget.SHIFT, id, "#" + id, AuditRules.money(expected),
                        AuditRules.money(countedCash), AuditRules.money(difference)); // TODO 4.2: expected -> counted, detail = the difference
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

    /**
     * The detail behind a closed shift's sheet (TODO 4.53) for the window {@code from <= time < to}: the takings per method of
     * payment, the refunds made in the window (their sum, how many, and the part given back in cash), the discounts given on the
     * sales of the window, and the net cash taken for gift cards. Worked out from the same ledger as the shift's own totals
     * ({@link SalesLedger#forWindow()}), so a refund made after the close is in the NEXT shift, not in this one, and a reprint of an
     * old sheet shows the same figures as the first print. Any signed-in person may read it: the cashier prints the sheet right
     * after closing.
     */
    public ShiftSheet.Extras sheetExtras(LocalDateTime from, LocalDateTime to) {
        String sign = "o." + SalesLedger.SIGN;
        String sql = "SELECT COALESCE(SUM(CASE WHEN " + sign + " < 0 THEN o." + SalesLedger.TOTAL + " ELSE 0 END), 0) AS refunds, "
                + "COALESCE(SUM(CASE WHEN " + sign + " < 0 THEN 1 ELSE 0 END), 0) AS refund_count, "
                + "COALESCE(SUM(CASE WHEN " + sign + " < 0 THEN o." + SalesLedger.CASH + " ELSE 0 END), 0) AS refunds_cash, "
                + "COALESCE(SUM(o.discount * o." + SalesLedger.COUNT + "), 0) AS discounts "
                + "FROM " + SalesLedger.forWindow();
        long refunds;
        int refundCount;
        long refundsCash;
        long discounts;
        BigDecimal giftCards;
        List<ShiftSheet.DiscountShare> people;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            SalesLedger.bindWindow(statement, 1, DbTime.write(from), DbTime.write(to));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                refunds = rs.getLong("refunds");
                refundCount = rs.getInt("refund_count");
                refundsCash = rs.getLong("refunds_cash");
                discounts = rs.getLong("discounts");
            }
            giftCards = GiftCardDAO.soldBetween(connection, from, to);
            people = discountsByPerson(connection, from, to);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to total the shift sheet", e);
        }
        List<ShiftSheet.Method> methods = new ArrayList<>();
        for (ReportDAO.PaymentSplit part : new ReportDAO().paymentSplit(from, to)) {
            methods.add(new ShiftSheet.Method(part.method(), part.orders(), part.revenue()));
        }
        return new ShiftSheet.Extras(Money.fromDong(refunds), refundCount, Money.fromDong(refundsCash),
                Money.fromDong(discounts), giftCards, methods, people);
    }

    /**
     * The discounts of the window per person (TODO 4.53): the cashier who took the payment of each order (the one the shift totals
     * of 4.6 name too; an order with no cashier is the employee who opened it), the orders of theirs that had a discount, and the
     * discounts' sum, net of refunds like the rest of the sheet. Someone whose discounts cancel out (all refunded) is left out;
     * the biggest comes first.
     */
    private static List<ShiftSheet.DiscountShare> discountsByPerson(Connection connection, LocalDateTime from, LocalDateTime to)
            throws SQLException {
        String who = "COALESCE(o.paid_by, o.employee_id)";
        String sql = "SELECT MAX(e.full_name) AS who_name, "
                + "COALESCE(SUM(CASE WHEN o.discount > 0 THEN o." + SalesLedger.COUNT + " ELSE 0 END), 0) AS orders, "
                + "COALESCE(SUM(o.discount * o." + SalesLedger.COUNT + "), 0) AS amount "
                + "FROM " + SalesLedger.forWindow() + " LEFT JOIN employees e ON e.id = " + who + " "
                + "GROUP BY " + who + " HAVING COALESCE(SUM(o.discount * o." + SalesLedger.COUNT + "), 0) <> 0 "
                + "ORDER BY amount DESC, " + who;
        List<ShiftSheet.DiscountShare> people = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            SalesLedger.bindWindow(statement, 1, DbTime.write(from), DbTime.write(to));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    people.add(new ShiftSheet.DiscountShare(rs.getString("who_name"), rs.getInt("orders"), Money.fromDong(rs.getLong("amount"))));
                }
            }
        }
        return people;
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
