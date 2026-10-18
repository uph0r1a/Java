package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.util.Money;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only sales aggregates for the admin Reports window. Only PAID orders count as sales
 * (a cancelled order is a refunded sale); dates compare the calendar day of paid_at, stored
 * as an ISO local timestamp, inclusive on both ends.
 */
public class ReportDAO {

    public record Summary(int paidOrders, BigDecimal revenue, BigDecimal discounts, int cancelledOrders) {
        public BigDecimal averageOrderValue() {
            return paidOrders == 0 ? BigDecimal.ZERO
                    : revenue.divide(BigDecimal.valueOf(paidOrders), 0, java.math.RoundingMode.HALF_UP);
        }
    }

    /** {@code profit} is null when no sold line of this product has a known cost. */
    public record ProductSales(String productName, int quantity, BigDecimal revenue, BigDecimal profit) {
    }

    /** {@code profit} is null when no sold line of that day has a known cost. */
    public record DailySales(LocalDate date, int orders, BigDecimal revenue, BigDecimal profit) {
    }

    /** Paid orders and revenue of one payment method (TODO 4.30). */
    public record PaymentSplit(com.coffeeshop.coffeeshopmanagement.model.PaymentMethod method, int orders, BigDecimal revenue) {
    }

    /**
     * Gross profit = sold line total minus quantity times the product's cost, for the lines whose product
     * has a cost entered. Order-level discounts are not subtracted (they are shown separately). The cost is
     * the product's <em>current</em> cost: the order lines do not keep the cost they had when sold, so an
     * old day is recalculated with today's cost. Lines of products without a cost (or deleted products)
     * are left out and counted in {@code linesWithoutCost} (TODO 4.20).
     */
    public record Profit(BigDecimal grossProfit, int linesWithCost, int linesWithoutCost) {
    }

    public Summary summary(LocalDate from, LocalDate to) {
        String sql = "SELECT " +
                "COALESCE(SUM(CASE WHEN status = " + SqlStatus.PAID + " THEN 1 ELSE 0 END), 0) AS paid_count, " +
                "COALESCE(SUM(CASE WHEN status = " + SqlStatus.PAID + " THEN total ELSE 0 END), 0) AS revenue, " +
                "COALESCE(SUM(CASE WHEN status = " + SqlStatus.PAID + " THEN discount ELSE 0 END), 0) AS discounts, " +
                "COALESCE(SUM(CASE WHEN status = " + SqlStatus.CANCELLED + " AND paid_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS cancelled_count " +
                "FROM orders WHERE " + OrderDAO.EFFECTIVE_DAY + " BETWEEN ? AND ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, from, to);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new Summary(rs.getInt("paid_count"), Money.fromDong(rs.getLong("revenue")),
                        Money.fromDong(rs.getLong("discounts")), rs.getInt("cancelled_count"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load sales summary", e);
        }
    }

    /** Best sellers by units sold. Grouped by the name snapshotted on the order item. */
    public List<ProductSales> topProducts(LocalDate from, LocalDate to, int limit) {
        String sql = "SELECT COALESCE(p.name, oi.product_name) AS name, SUM(oi.quantity) AS qty, SUM(oi.line_total) AS revenue, " +
                "SUM(CASE WHEN p.cost IS NOT NULL THEN oi.line_total - oi.quantity * p.cost END) AS profit " +
                "FROM order_items oi JOIN orders o ON oi.order_id = o.id LEFT JOIN products p ON oi.product_id = p.id " +
                "WHERE o.status = " + SqlStatus.PAID + " AND " + OrderDAO.EFFECTIVE_DAY + " BETWEEN ? AND ? " +
                // Grouped by product, not by the name saved on the line: a renamed product stays one row (TODO 2.51).
                "GROUP BY COALESCE(CAST(oi.product_id AS CHAR), oi.product_name) ORDER BY qty DESC, revenue DESC LIMIT ?";
        List<ProductSales> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, from, to);
            statement.setInt(3, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long revenue = rs.getLong("revenue");
                    long profit = rs.getLong("profit");
                    boolean noProfit = rs.wasNull(); // must be read right after the profit column
                    result.add(new ProductSales(rs.getString("name"), rs.getInt("qty"),
                            Money.fromDong(revenue), noProfit ? null : Money.fromDong(profit)));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load top products", e);
        }
        return result;
    }

    /** Only days that had at least one paid order, oldest first. */
    public List<DailySales> dailySales(LocalDate from, LocalDate to) {
        String sql = "SELECT " + OrderDAO.EFFECTIVE_DAY + " AS d, COUNT(*) AS orders, SUM(total) AS revenue FROM orders " +
                "WHERE status = " + SqlStatus.PAID + " AND " + OrderDAO.EFFECTIVE_DAY + " BETWEEN ? AND ? GROUP BY " + OrderDAO.EFFECTIVE_DAY + " ORDER BY d";
        String profitSql = "SELECT " + OrderDAO.EFFECTIVE_DAY + " AS d, SUM(oi.line_total - oi.quantity * p.cost) AS profit " +
                "FROM order_items oi JOIN orders o ON oi.order_id = o.id JOIN products p ON oi.product_id = p.id " +
                "WHERE o.status = " + SqlStatus.PAID + " AND p.cost IS NOT NULL AND " + OrderDAO.EFFECTIVE_DAY + " BETWEEN ? AND ? GROUP BY " + OrderDAO.EFFECTIVE_DAY;
        List<DailySales> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection()) {
            java.util.Map<String, Long> profitByDay = new java.util.HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(profitSql)) {
                bindRange(statement, from, to);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) profitByDay.put(rs.getString("d"), rs.getLong("profit"));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bindRange(statement, from, to);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        String day = rs.getString("d");
                        Long profit = profitByDay.get(day);
                        result.add(new DailySales(LocalDate.parse(day), rs.getInt("orders"),
                                Money.fromDong(rs.getLong("revenue")), profit == null ? null : Money.fromDong(profit)));
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load daily sales", e);
        }
        return result;
    }

    /** Revenue and order count per payment method for paid orders, biggest first; methods never used are left out. */
    public List<PaymentSplit> paymentSplit(LocalDate from, LocalDate to) {
        String sql = "SELECT payment_method, COUNT(*) AS orders, COALESCE(SUM(total), 0) AS revenue FROM orders " +
                "WHERE status = " + SqlStatus.PAID + " AND " + OrderDAO.EFFECTIVE_DAY + " BETWEEN ? AND ? " +
                "GROUP BY payment_method ORDER BY revenue DESC";
        List<PaymentSplit> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, from, to);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new PaymentSplit(com.coffeeshop.coffeeshopmanagement.model.PaymentMethod.fromDb(rs.getString("payment_method")),
                            rs.getInt("orders"), Money.fromDong(rs.getLong("revenue"))));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load revenue by payment method", e);
        }
        return result;
    }

    /** Gross profit of the range, see {@link Profit}. */
    public Profit profit(LocalDate from, LocalDate to) {
        String sql = "SELECT COALESCE(SUM(CASE WHEN p.cost IS NOT NULL THEN oi.line_total - oi.quantity * p.cost END), 0) AS profit, " +
                "COALESCE(SUM(CASE WHEN p.cost IS NOT NULL THEN 1 ELSE 0 END), 0) AS known, " +
                "COALESCE(SUM(CASE WHEN p.cost IS NULL THEN 1 ELSE 0 END), 0) AS unknown " +
                "FROM order_items oi JOIN orders o ON oi.order_id = o.id LEFT JOIN products p ON oi.product_id = p.id " +
                "WHERE o.status = " + SqlStatus.PAID + " AND " + OrderDAO.EFFECTIVE_DAY + " BETWEEN ? AND ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, from, to);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new Profit(Money.fromDong(rs.getLong("profit")), rs.getInt("known"), rs.getInt("unknown"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load profit", e);
        }
    }

    /** Units sold per product id in the range (paid orders only), for ordering the POS menu by best sellers (TODO 5.10). */
    public java.util.Map<Integer, Integer> unitsSold(LocalDate from, LocalDate to) {
        String sql = "SELECT oi.product_id AS id, SUM(oi.quantity) AS qty FROM order_items oi JOIN orders o ON oi.order_id = o.id " +
                "WHERE o.status = " + SqlStatus.PAID + " AND oi.product_id IS NOT NULL AND " + OrderDAO.EFFECTIVE_DAY + " BETWEEN ? AND ? GROUP BY oi.product_id";
        java.util.Map<Integer, Integer> result = new java.util.HashMap<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, from, to);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) result.put(rs.getInt("id"), rs.getInt("qty"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load units sold", e);
        }
        return result;
    }

    private static void bindRange(PreparedStatement statement, LocalDate from, LocalDate to) throws SQLException {
        statement.setString(1, from.toString());
        statement.setString(2, to.toString());
    }

}
