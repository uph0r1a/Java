package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.service.AuditRules;
import com.coffeeshop.coffeeshopmanagement.service.SplitBillRules;
import com.coffeeshop.coffeeshopmanagement.service.TableMoveRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Money;
import com.coffeeshop.coffeeshopmanagement.util.OrderNumber;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Moving the open order of a table to another table, and joining two tables' orders (TODO 4.10, rule D10). Each is one transaction that
 * changes the orders and both tables together, and writes an audit line (TODO 4.2). Anyone signed in may do them: nothing is paid and
 * no line is lost. (Splitting a bill is {@code CheckoutService.paySplit}, because it ends in a payment.)
 */
public class TableMoveDAO {

    private record Open(int id, Integer customerId) {
    }

    /** Moves the open order of table {@code from} to the free table {@code to}; the first table becomes free, the second taken. */
    public void transfer(int from, int to) {
        requireSignedIn();
        if (from == to) throw new DataAccessException(I18n.t("table.move.same"));
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Open order = openOrderOf(connection, from);
                if (order == null) throw new DataAccessException(I18n.t("table.move.noorder", from));
                if (!isFree(connection, to)) throw new DataAccessException(I18n.t("table.move.busy", to));
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE orders SET table_number = ? WHERE id = ? AND table_number = ? AND status = " + SqlStatus.OPEN)) {
                    statement.setInt(1, to);
                    statement.setInt(2, order.id());
                    statement.setInt(3, from);
                    if (statement.executeUpdate() != 1) throw new DataAccessException(I18n.t("table.move.changed"));
                }
                setTable(connection, to, order.id());
                setTable(connection, from, null);
                AuditDAO.write(connection, AuditAction.TABLE_TRANSFERRED, AuditTarget.ORDER, order.id(), OrderNumber.format(order.id()),
                        String.valueOf(from), String.valueOf(to), null);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to move the table", e);
        }
    }

    /**
     * Puts the order of table {@code from} into the order of table {@code into}: the lines are added together
     * ({@link TableMoveRules#mergeLines}), the second order is closed as cancelled with the reason "merged into #n" (its lines stay on it
     * for the record), and its table becomes free. Both tables must have an open order.
     */
    public void merge(int into, int from) {
        requireSignedIn();
        if (into == from) throw new DataAccessException(I18n.t("table.move.same"));
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Open kept = openOrderOf(connection, into);
                Open joined = openOrderOf(connection, from);
                if (kept == null) throw new DataAccessException(I18n.t("table.move.noorder", into));
                if (joined == null) throw new DataAccessException(I18n.t("table.move.noorder", from));
                List<OrderItem> lines = TableMoveRules.mergeLines(linesOf(connection, kept.id()), linesOf(connection, joined.id()));
                BigDecimal total = SplitBillRules.sum(lines);
                rewriteLines(connection, kept.id(), lines);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE orders SET subtotal = ?, total = ?, customer_id = ?, rev = rev + 1 WHERE id = ? AND status = " + SqlStatus.OPEN)) {
                    statement.setLong(1, Money.toDong(total));
                    statement.setLong(2, Money.toDong(total));
                    Integer customer = TableMoveRules.customerAfterMerge(kept.customerId(), joined.customerId());
                    if (customer == null) statement.setNull(3, java.sql.Types.INTEGER); else statement.setInt(3, customer);
                    statement.setInt(4, kept.id());
                    if (statement.executeUpdate() != 1) throw new DataAccessException(I18n.t("table.move.changed"));
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE orders SET status = " + SqlStatus.CANCELLED + ", cancelled_by = ?, cancelled_at = ?, cancel_reason = ? "
                                + "WHERE id = ? AND status = " + SqlStatus.OPEN)) {
                    OrderDAO.setCancelledBy(statement, 1);
                    statement.setString(2, DbTime.write(AppClock.now()));
                    statement.setString(3, I18n.t("table.merge.reason", OrderNumber.format(kept.id())));
                    statement.setInt(4, joined.id());
                    if (statement.executeUpdate() != 1) throw new DataAccessException(I18n.t("table.move.changed"));
                }
                setTable(connection, from, null);
                AuditDAO.write(connection, AuditAction.TABLES_MERGED, AuditTarget.ORDER, kept.id(), OrderNumber.format(kept.id()),
                        null, AuditRules.money(total), OrderNumber.format(joined.id()) + " (" + from + ") -> " + OrderNumber.format(kept.id()) + " (" + into + ")");
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to merge the tables", e);
        }
    }

    /**
     * For {@code CheckoutService.paySplit}, on the payment's own connection: the open order {@code fromOrderId} keeps only
     * {@code remainder} (its lines and totals are rewritten), its table stays taken, and an audit line says which part was paid.
     * Refused (so the payment rolls back) when that order is no longer open.
     */
    public static void keepRemainder(Connection connection, int fromOrderId, int expectedRev, List<OrderItem> remainder, int paidOrderId,
                              BigDecimal paidTotal, Integer tableNumber) throws SQLException {
        BigDecimal total = SplitBillRules.sum(remainder);
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE orders SET subtotal = ?, total = ?, rev = rev + 1 WHERE id = ? AND status = " + SqlStatus.OPEN + " AND rev = ?")) {
            statement.setLong(1, Money.toDong(total));
            statement.setLong(2, Money.toDong(total));
            statement.setInt(3, fromOrderId);
            statement.setInt(4, expectedRev);
            if (statement.executeUpdate() != 1) {
                // Open but saved by another till since the screen loaded it: its extra lines must not be deleted (TODO 2.91).
                OrderChangedElsewhereException changed = OrderDAO.changedElsewhere(connection, fromOrderId);
                throw changed != null ? changed : new DataAccessException(I18n.t("table.move.changed"));
            }
        }
        rewriteLines(connection, fromOrderId, remainder);
        if (tableNumber != null) setTable(connection, tableNumber, fromOrderId); // the paid part's insert freed the table: it is still taken
        AuditDAO.write(connection, AuditAction.BILL_SPLIT, AuditTarget.ORDER, paidOrderId, OrderNumber.format(paidOrderId),
                null, AuditRules.money(paidTotal), OrderNumber.format(fromOrderId) + " -> " + OrderNumber.format(paidOrderId));
    }

    private static void rewriteLines(Connection connection, int orderId, List<OrderItem> lines) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM order_items WHERE order_id = ?")) {
            delete.setInt(1, orderId);
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, line_total, note"
                        + OrderLineColumns.INSERT_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?" + OrderLineColumns.INSERT_MARKS + ")")) {
            for (OrderItem line : lines) {
                insert.setInt(1, orderId);
                if (line.getProductId() == null) insert.setNull(2, java.sql.Types.INTEGER); else insert.setInt(2, line.getProductId());
                insert.setString(3, line.getProductName());
                insert.setInt(4, line.getQuantity());
                insert.setLong(5, Money.toDong(line.getUnitPrice()));
                insert.setLong(6, Money.toDong(line.getLineTotal()));
                insert.setString(7, line.getNote());
                OrderLineColumns.bind(insert, 8, line);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static List<OrderItem> linesOf(Connection connection, int orderId) throws SQLException {
        List<OrderItem> lines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT product_id, product_name, quantity, unit_price, line_total, note" + OrderLineColumns.SELECT_COLUMNS
                        + " FROM order_items WHERE order_id = ? ORDER BY id")) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OrderItem line = new OrderItem();
                    int product = rs.getInt(1);
                    line.setProductId(rs.wasNull() ? null : product);
                    line.setProductName(rs.getString(2));
                    line.setQuantity(rs.getInt(3));
                    line.setUnitPrice(Money.fromDong(rs.getLong(4)));
                    line.setLineTotal(Money.fromDong(rs.getLong(5)));
                    line.setNote(rs.getString(6));
                    OrderLineColumns.read(rs, line);
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private static Open openOrderOf(Connection connection, int table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, customer_id FROM orders WHERE table_number = ? AND status = " + SqlStatus.OPEN)) {
            statement.setInt(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) return null;
                int id = rs.getInt(1);
                int customer = rs.getInt(2);
                return new Open(id, rs.wasNull() ? null : customer);
            }
        }
    }

    /** The table exists, is marked free and has no open order. */
    private static boolean isFree(Connection connection, int table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT status FROM dining_tables WHERE table_number = ?")) {
            statement.setInt(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next() || !SqlStatus.EMPTY.equals("'" + rs.getString(1) + "'")) return false;
            }
        }
        return openOrderOf(connection, table) == null;
    }

    /** Marks a table taken by {@code orderId}, or free when null. */
    private static void setTable(Connection connection, int table, Integer orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE dining_tables SET status = " + (orderId == null ? SqlStatus.EMPTY : SqlStatus.OCCUPIED) + ", current_order_id = ? WHERE table_number = ?")) {
            if (orderId == null) statement.setNull(1, java.sql.Types.INTEGER); else statement.setInt(1, orderId);
            statement.setInt(2, table);
            statement.executeUpdate();
        }
    }

    private static void requireSignedIn() {
        if (Session.getCurrentUser() == null) {
            Permissions.requireAdmin("move a table"); // nobody signed in: refused and logged like any other action
        }
    }
}
