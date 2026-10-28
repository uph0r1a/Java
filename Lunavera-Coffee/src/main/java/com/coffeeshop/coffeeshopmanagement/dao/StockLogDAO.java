package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.StockLogEntry;
import com.coffeeshop.coffeeshopmanagement.service.Limits;
import com.coffeeshop.coffeeshopmanagement.service.StockRules;
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
import java.util.ArrayList;
import java.util.List;

/**
 * The stock log (TODO 4.5): goods received, hand-typed stock numbers and (TODO 4.33) waste and breakage, with who and when. Receiving changes the product's stock and
 * writes the log line in one transaction, so the log and the number can never disagree, and the stock is raised with
 * {@code stock = stock + ?} in the database, so a sale at another till at the same moment is not overwritten.
 */
public class StockLogDAO {

    /**
     * Goods received: adds {@code quantity} to the product's stock and logs it.
     *
     * @param unitCost what one piece cost, or null when not said
     * @return the stock after the receipt
     */
    public int receive(int productId, int quantity, BigDecimal unitCost, String supplier, String note) {
        Permissions.requireAdmin("receive stock");
        if (StockRules.parseQuantity(String.valueOf(quantity)) < 0) {
            throw new DataAccessException(I18n.t("stock.val.quantity"));
        }
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String name;
                try (PreparedStatement read = connection.prepareStatement("SELECT name FROM products WHERE id = ?")) {
                    read.setInt(1, productId);
                    try (ResultSet rs = read.executeQuery()) {
                        if (!rs.next()) throw new DataAccessException(I18n.t("stock.product.gone"));
                        name = rs.getString(1);
                    }
                }
                try (PreparedStatement raise = connection.prepareStatement(
                        "UPDATE products SET stock = stock + ? WHERE id = ? AND stock + ? <= ?")) {
                    raise.setInt(1, quantity);
                    raise.setInt(2, productId);
                    raise.setInt(3, quantity);
                    raise.setInt(4, Limits.MAX_STOCK);
                    if (raise.executeUpdate() == 0) {
                        throw new DataAccessException(I18n.t("stock.val.ceiling", Limits.MAX_STOCK));
                    }
                }
                int after;
                try (PreparedStatement read = connection.prepareStatement("SELECT stock FROM products WHERE id = ?")) {
                    read.setInt(1, productId);
                    try (ResultSet rs = read.executeQuery()) {
                        rs.next();
                        after = rs.getInt(1);
                    }
                }
                insertLine(connection, StockLogEntry.RECEIPT, productId, name, quantity, after - quantity, after, unitCost,
                        StockRules.cleanText(supplier), StockRules.cleanText(note));
                connection.commit();
                return after;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to receive stock", e);
        }
    }

    /**
     * Waste and breakage (TODO 4.33): takes {@code quantity} pieces off the product's stock and logs it, in one transaction, with the
     * reason, who and when. The line carries the product's cost price of that moment, so the report can price the loss and a later
     * change of the price does not rewrite it; a product with no cost price gives a line with no cost, which the report counts
     * apart instead of pricing it at 0. The stock is lowered with {@code stock = stock - ?} guarded by {@code stock >= ?} in the
     * database, so a sale at another till at the same moment cannot take it below 0.
     *
     * @param reason why (required: a loss nobody can explain is the thing this log is for)
     * @return the stock after the waste
     */
    public int writeOff(int productId, int quantity, String reason) {
        Permissions.requireAdmin("record waste");
        if (StockRules.parseQuantity(String.valueOf(quantity)) < 0) {
            throw new DataAccessException(I18n.t("stock.val.quantity"));
        }
        String cleanReason = StockRules.cleanText(reason);
        if (cleanReason == null) {
            throw new DataAccessException(I18n.t("stock.waste.val.reason"));
        }
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String name;
                int before;
                BigDecimal cost;
                try (PreparedStatement read = connection.prepareStatement("SELECT name, stock, cost FROM products WHERE id = ?")) {
                    read.setInt(1, productId);
                    try (ResultSet rs = read.executeQuery()) {
                        if (!rs.next()) throw new DataAccessException(I18n.t("stock.product.gone"));
                        name = rs.getString(1);
                        before = rs.getInt(2);
                        long costDong = rs.getLong(3);
                        cost = Money.fromDongOrNull(costDong, rs.wasNull());
                    }
                }
                if (before < quantity) {
                    throw new DataAccessException(I18n.t("stock.waste.val.more", before));
                }
                try (PreparedStatement lower = connection.prepareStatement(
                        "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?")) {
                    lower.setInt(1, quantity);
                    lower.setInt(2, productId);
                    lower.setInt(3, quantity);
                    if (lower.executeUpdate() == 0) {
                        // somebody sold the last pieces between the read and the update: say how many there are now
                        throw new DataAccessException(I18n.t("stock.waste.val.more", currentStock(connection, productId)));
                    }
                }
                int after = currentStock(connection, productId);
                insertLine(connection, StockLogEntry.WASTE, productId, name, -quantity, after + quantity, after, cost, null, cleanReason);
                connection.commit();
                return after;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to record waste", e);
        }
    }

    private static int currentStock(Connection connection, int productId) throws SQLException {
        try (PreparedStatement read = connection.prepareStatement("SELECT stock FROM products WHERE id = ?")) {
            read.setInt(1, productId);
            try (ResultSet rs = read.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Logs a stock number somebody typed over on the product form. Called by {@link ProductDAO} on the connection that saves the
     * product, so the number and its log line are one transaction. Does nothing when the number did not change.
     */
    static void logAdjustment(Connection connection, int productId, String productName, int before, int after) throws SQLException {
        if (before == after) return;
        insertLine(connection, StockLogEntry.ADJUST, productId, productName, after - before, before, after, null, null, null);
    }

    private static void insertLine(Connection connection, String kind, int productId, String productName, int quantity, int before,
                                   int after, BigDecimal unitCost, String supplier, String note) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO stock_log (logged_at, kind, product_id, product_name, quantity, stock_before, stock_after, unit_cost, "
                        + "supplier, note, employee_id, employee_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, DbTime.write(AppClock.now()));
            statement.setString(2, kind);
            statement.setInt(3, productId);
            statement.setString(4, productName);
            statement.setInt(5, quantity);
            statement.setInt(6, before);
            statement.setInt(7, after);
            if (unitCost == null) statement.setNull(8, Types.BIGINT); else statement.setLong(8, Money.toDong(unitCost));
            statement.setString(9, supplier);
            statement.setString(10, note);
            Integer employeeId = Session.getCurrentEmployee() == null ? null : Session.getCurrentEmployee().getId();
            if (employeeId == null) statement.setNull(11, Types.INTEGER); else statement.setInt(11, employeeId);
            statement.setString(12, Session.getDisplayName());
            statement.executeUpdate();
        }
    }

    /** The newest lines first, at most {@code limit}; {@code productId} null = every product. */
    public List<StockLogEntry> findRecent(Integer productId, int limit) {
        Permissions.requireAdmin("view the stock log");
        String sql = "SELECT id, logged_at, kind, product_id, product_name, quantity, stock_before, stock_after, unit_cost, supplier, "
                + "note, employee_id, employee_name FROM stock_log" + (productId == null ? "" : " WHERE product_id = ?")
                + " ORDER BY logged_at DESC, id DESC LIMIT ?";
        List<StockLogEntry> list = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int i = 1;
            if (productId != null) statement.setInt(i++, productId);
            statement.setInt(i, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long cost = rs.getLong(9);
                    BigDecimal unitCost = rs.wasNull() ? null : Money.fromDong(cost);
                    int pid = rs.getInt(4);
                    Integer productRef = rs.wasNull() ? null : pid;
                    int eid = rs.getInt(12);
                    Integer employeeRef = rs.wasNull() ? null : eid;
                    list.add(new StockLogEntry(rs.getInt(1), DbTime.parse(rs.getString(2)), rs.getString(3), productRef,
                            rs.getString(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), unitCost, rs.getString(10),
                            rs.getString(11), employeeRef, rs.getString(13)));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the stock log", e);
        }
        return list;
    }
}
