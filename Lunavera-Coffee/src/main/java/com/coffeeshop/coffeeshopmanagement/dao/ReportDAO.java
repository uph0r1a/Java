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

    /**
     * Profit of one paid order line (TODO 3.53): what it sold for, minus the cost recorded when it was paid
     * ({@code order_items.unit_cost}), minus the line's share of the order's discount. The discount is spread over the lines in
     * proportion to {@code line_total}, so the report's profit always equals revenue (the order totals, after the discount) minus
     * costs. The share is fractional and the sums are rounded once at the end. Use only where {@code oi.unit_cost IS NOT NULL}.
     */
    private static final String LINE_PROFIT = "(oi.line_total - oi.quantity * oi.unit_cost - "
            + "(CASE WHEN o.subtotal > 0 THEN o.discount * 1.0 * oi.line_total / o.subtotal ELSE 0 END))";

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
     * Gross profit = what the line sold for, minus quantity times the cost recorded on the line when it was paid
     * ({@code order_items.unit_cost}), minus the line's share of the order's discount (TODO 3.53), so revenue (the order
     * totals after the discount) minus costs equals the profit printed. Changing a product's cost later does not change an
     * earlier day. Lines with no cost known at the time (a product with no cost entered, a deleted product) are left out and
     * counted in {@code linesWithoutCost} (TODO 4.20). Lines paid before the cost was recorded got their product's cost at the
     * time of the upgrade (see {@code DatabaseConfig.backfillUnitCostOnce}).
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

    /** Every product that sold in the range, best seller first: what the report's CSV needs (TODO 5.34). */
    public List<ProductSales> allProducts(LocalDate from, LocalDate to) {
        return topProducts(from, to, Integer.MAX_VALUE);
    }

    /** Best sellers by units sold. Grouped by the name snapshotted on the order item. */
    public List<ProductSales> topProducts(LocalDate from, LocalDate to, int limit) {
        String sql = "SELECT COALESCE(p.name, oi.product_name) AS name, SUM(oi.quantity) AS qty, SUM(oi.line_total) AS revenue, " +
                "SUM(CASE WHEN oi.unit_cost IS NOT NULL THEN " + LINE_PROFIT + " END) AS profit " +
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
                    long profit = Math.round(rs.getDouble("profit"));
                    boolean noProfit = rs.wasNull(); // must be read right after the profit column (Math.round(0.0) does not touch it)
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
        String profitSql = "SELECT " + OrderDAO.EFFECTIVE_DAY + " AS d, SUM(" + LINE_PROFIT + ") AS profit " +
                "FROM order_items oi JOIN orders o ON oi.order_id = o.id " +
                "WHERE o.status = " + SqlStatus.PAID + " AND oi.unit_cost IS NOT NULL AND " + OrderDAO.EFFECTIVE_DAY + " BETWEEN ? AND ? GROUP BY " + OrderDAO.EFFECTIVE_DAY;
        List<DailySales> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection()) {
            java.util.Map<String, Long> profitByDay = new java.util.HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(profitSql)) {
                bindRange(statement, from, to);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) profitByDay.put(rs.getString("d"), Math.round(rs.getDouble("profit")));
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
        String sql = "SELECT COALESCE(SUM(CASE WHEN oi.unit_cost IS NOT NULL THEN " + LINE_PROFIT + " END), 0) AS profit, " +
                "COALESCE(SUM(CASE WHEN oi.unit_cost IS NOT NULL THEN 1 ELSE 0 END), 0) AS known, " +
                "COALESCE(SUM(CASE WHEN oi.unit_cost IS NULL THEN 1 ELSE 0 END), 0) AS unknown " +
                "FROM order_items oi JOIN orders o ON oi.order_id = o.id " +
                "WHERE o.status = " + SqlStatus.PAID + " AND " + OrderDAO.EFFECTIVE_DAY + " BETWEEN ? AND ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, from, to);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new Profit(Money.fromDong(Math.round(rs.getDouble("profit"))), rs.getInt("known"), rs.getInt("unknown"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load profit", e);
        }
    }

    /** Revenue and units of one category; {@code categoryName} is null for lines whose product has no category (or was deleted). */
    public record CategorySales(String categoryName, int quantity, BigDecimal revenue) {
    }

    /**
     * Sales per category (TODO 4.40): the line totals BEFORE the order discount (a discount belongs to the whole order, not to
     * a category), biggest first. A line whose product is gone or has no category is grouped under a null name.
     */
    public List<CategorySales> categorySales(LocalDate from, LocalDate to) {
        String sql = "SELECT c.name AS name, SUM(oi.quantity) AS qty, SUM(oi.line_total) AS revenue " +
                "FROM order_items oi JOIN orders o ON oi.order_id = o.id LEFT JOIN products p ON oi.product_id = p.id " +
                "LEFT JOIN categories c ON p.category_id = c.id " +
                "WHERE o.status = " + SqlStatus.PAID + " AND " + OrderDAO.EFFECTIVE_DAY + " BETWEEN ? AND ? " +
                "GROUP BY c.id, c.name ORDER BY revenue DESC";
        List<CategorySales> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, from, to);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new CategorySales(rs.getString("name"), rs.getInt("qty"), Money.fromDong(rs.getLong("revenue"))));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load sales by category", e);
        }
        return result;
    }

    /**
     * When each paid order of the range was paid (its opening time when it has no paid time), for the busiest-hours and
     * weekday figures (TODO 4.40). Hours and weekdays are worked out in Java ({@code SalesPatterns}) so SQLite and MariaDB
     * give the same answer; a row whose stored time cannot be read is skipped, never a failed report.
     */
    public List<com.coffeeshop.coffeeshopmanagement.service.SalesPatterns.PaidOrder> paidOrders(LocalDate from, LocalDate to) {
        String sql = "SELECT COALESCE(paid_at, order_date) AS paid_time, total FROM orders WHERE status = " + SqlStatus.PAID
                + " AND " + OrderDAO.EFFECTIVE_DAY + " BETWEEN ? AND ?";
        List<com.coffeeshop.coffeeshopmanagement.service.SalesPatterns.PaidOrder> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindRange(statement, from, to);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    java.time.LocalDateTime time;
                    try {
                        time = com.coffeeshop.coffeeshopmanagement.util.DbTime.parse(rs.getString("paid_time"));
                    } catch (RuntimeException e) {
                        continue;
                    }
                    if (time != null) {
                        result.add(new com.coffeeshop.coffeeshopmanagement.service.SalesPatterns.PaidOrder(
                                time, Money.fromDong(rs.getLong("total"))));
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load paid order times", e);
        }
        return result;
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
