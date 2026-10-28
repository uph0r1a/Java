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
 * (a cancelled order is a refunded sale); a sale belongs to the calendar day it was paid (its opening day when there is
 * no paid time, see {@link OrderDAO#EFFECTIVE_DAY}), inclusive on both ends. The date filters are ranges on the bare
 * columns, built by {@link EffectiveDay}, so that the indexes on {@code paid_at} and {@code order_date} can be used.
 */
public class ReportDAO {

    public record Summary(int paidOrders, BigDecimal revenue, BigDecimal discounts, int cancelledOrders) {
        public BigDecimal averageOrderValue() {
            return paidOrders <= 0 ? BigDecimal.ZERO // no orders, or only refunds in the range (TODO 3.13)
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
        // Sales paid in the range minus refunds made in it (TODO 3.13, see SalesLedger): orders, revenue and discounts are NET, so a sale
        // refunded in the same range is gone and one refunded later stays on its own day. cancelled_count is the refunds made in the range,
        // plus the old ones (refunded before the refund time was recorded) whose sale is in the range.
        String sign = "o." + SalesLedger.SIGN;
        String sql = "SELECT " +
                "COALESCE(SUM(" + sign + "), 0) AS paid_count, " +
                "COALESCE(SUM(o.total * " + sign + "), 0) AS revenue, " +
                "COALESCE(SUM(o.discount * " + sign + "), 0) AS discounts, " +
                "COALESCE(SUM(CASE WHEN " + sign + " < 0 THEN 1 ELSE 0 END), 0) AS cancelled_count " +
                "FROM " + SalesLedger.forDays();
        String legacySql = "SELECT COUNT(*) FROM orders WHERE status = " + SqlStatus.CANCELLED
                + " AND paid_at IS NOT NULL AND cancelled_at IS NULL AND " + EffectiveDay.between("");
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             PreparedStatement legacy = connection.prepareStatement(legacySql)) {
            SalesLedger.bindDays(statement, 1, from, to);
            bindRange(legacy, from, to);
            try (ResultSet rs = statement.executeQuery(); ResultSet old = legacy.executeQuery()) {
                rs.next();
                old.next();
                return new Summary(rs.getInt("paid_count"), Money.fromDong(rs.getLong("revenue")),
                        Money.fromDong(rs.getLong("discounts")), rs.getInt("cancelled_count") + old.getInt(1));
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
        // MAX() because the name is not in the GROUP BY: a server with ONLY_FULL_GROUP_BY (MySQL, or MariaDB with that mode) refuses a
        // loose column with error 1055 (TODO 6.47). Every row of one group has the same product, so there is exactly one name to take.
        // Lines of sales minus lines of refunds (TODO 3.13); a product whose sales and refunds cancel out in the range is not listed.
        String sign = "o." + SalesLedger.SIGN;
        String sql = "SELECT MAX(COALESCE(p.name, oi.product_name)) AS name, SUM(oi.quantity * " + sign + ") AS qty, SUM(oi.line_total * " + sign + ") AS revenue, " +
                "SUM(CASE WHEN oi.unit_cost IS NOT NULL THEN " + LINE_PROFIT + " * " + sign + " END) AS profit " +
                "FROM order_items oi JOIN " + SalesLedger.forDays() + " ON oi.order_id = o.id LEFT JOIN products p ON oi.product_id = p.id " +
                // Grouped by product, not by the name saved on the line: a renamed product stays one row (TODO 2.51).
                "GROUP BY COALESCE(CAST(oi.product_id AS CHAR), oi.product_name) " +
                "HAVING SUM(oi.quantity * " + sign + ") <> 0 OR SUM(oi.line_total * " + sign + ") <> 0 " +
                "ORDER BY qty DESC, revenue DESC LIMIT ?";
        List<ProductSales> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int next = SalesLedger.bindDays(statement, 1, from, to);
            statement.setInt(next, limit);
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
        // A sale is on the day it was paid, a refund is a negative line on the day it was made (TODO 3.13).
        String sign = "o." + SalesLedger.SIGN;
        String sql = "SELECT " + SalesLedger.DAY + " AS d, SUM(" + sign + ") AS orders, SUM(o.total * " + sign + ") AS revenue FROM " +
                SalesLedger.forDays() + " GROUP BY " + SalesLedger.DAY + " ORDER BY d";
        String profitSql = "SELECT " + SalesLedger.DAY + " AS d, SUM(" + LINE_PROFIT + " * " + sign + ") AS profit " +
                "FROM order_items oi JOIN " + SalesLedger.forDays() + " ON oi.order_id = o.id " +
                "WHERE oi.unit_cost IS NOT NULL GROUP BY " + SalesLedger.DAY;
        List<DailySales> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection()) {
            java.util.Map<String, Long> profitByDay = new java.util.HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(profitSql)) {
                SalesLedger.bindDays(statement, 1, from, to);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) profitByDay.put(rs.getString("d"), Math.round(rs.getDouble("profit")));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                SalesLedger.bindDays(statement, 1, from, to);
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
        // Net of refunds made in the range (TODO 3.13). An order paid with two methods (TODO 4.32) counts once under each method with that
        // method's amount; any other order under its one method with its whole total. The amounts add up to the net revenue either way.
        String sign = "o." + SalesLedger.SIGN;
        String sql = "SELECT payment_method, COALESCE(SUM(cnt), 0) AS orders, COALESCE(SUM(amount), 0) AS revenue FROM ("
                + "SELECT o.payment_method AS payment_method, " + sign + " AS cnt, o.total * " + sign + " AS amount FROM " + SalesLedger.forDays()
                + " WHERE NOT EXISTS (SELECT 1 FROM order_payments p WHERE p.order_id = o.id) "
                + "UNION ALL SELECT p.method AS payment_method, " + sign + " AS cnt, p.amount * " + sign + " AS amount FROM " + SalesLedger.forDays()
                + " JOIN order_payments p ON p.order_id = o.id) lines "
                + "GROUP BY payment_method ORDER BY revenue DESC";
        List<PaymentSplit> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            SalesLedger.bindDays(statement, SalesLedger.bindDays(statement, 1, from, to), from, to); // the ledger appears twice
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

    /**
     * Sales per person for the days {@code from} to {@code to} (TODO 4.6): the person who took the money ({@code paid_by}, else the
     * one who opened the order), orders, revenue and the cash part of it. Biggest first. {@code employeeName} is null for an order
     * with no person on record.
     */
    public List<ShiftDAO.EmployeeShare> employeeSales(LocalDate from, LocalDate to) {
        String who = "COALESCE(o.paid_by, o.employee_id)";
        // A refund is taken from the person who took the money for the sale, on the day of the refund (TODO 3.13).
        String sign = "o." + SalesLedger.SIGN;
        String sql = "SELECT " + who + " AS who, MAX(e.full_name) AS who_name, COALESCE(SUM(" + sign + "), 0) AS orders, COALESCE(SUM(o.total * " + sign + "), 0) AS revenue, "
                + "COALESCE(SUM(" + ShiftDAO.CASH_OF_ORDER + " * " + sign + "), 0) AS cash "
                + "FROM " + SalesLedger.forDays() + " LEFT JOIN employees e ON e.id = " + who + " "
                + "GROUP BY " + who + " ORDER BY revenue DESC, who";
        List<ShiftDAO.EmployeeShare> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            SalesLedger.bindDays(statement, 1, from, to);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("who");
                    Integer employeeId = rs.wasNull() ? null : id;
                    result.add(new ShiftDAO.EmployeeShare(employeeId, rs.getString("who_name"), rs.getInt("orders"),
                            Money.fromDong(rs.getLong("revenue")), Money.fromDong(rs.getLong("cash"))));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load sales by employee", e);
        }
        return result;
    }

    /** Gross profit of the range, see {@link Profit}. */
    public Profit profit(LocalDate from, LocalDate to) {
        String sign = "o." + SalesLedger.SIGN; // lines of refunds count negative: the lines that still stand (TODO 3.13)
        String sql = "SELECT COALESCE(SUM(CASE WHEN oi.unit_cost IS NOT NULL THEN " + LINE_PROFIT + " * " + sign + " END), 0) AS profit, " +
                "COALESCE(SUM(CASE WHEN oi.unit_cost IS NOT NULL THEN " + sign + " ELSE 0 END), 0) AS known, " +
                "COALESCE(SUM(CASE WHEN oi.unit_cost IS NULL THEN " + sign + " ELSE 0 END), 0) AS unknown " +
                "FROM order_items oi JOIN " + SalesLedger.forDays() + " ON oi.order_id = o.id";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            SalesLedger.bindDays(statement, 1, from, to);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new Profit(Money.fromDong(Math.round(rs.getDouble("profit"))), rs.getInt("known"), rs.getInt("unknown"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load profit", e);
        }
    }

    /**
     * Waste and breakage of the range (TODO 4.33), from the stock log: how many lines and pieces, and what they cost.
     *
     * @param cost             the pieces times the cost price recorded on each line when it was logged (lines with no cost price are
     *                         not in it, see {@code linesWithoutCost}); never null, 0 when there was no waste
     * @param linesWithoutCost waste lines of a product that had no cost price then: counted, not priced at 0 (as in {@link Profit})
     */
    public record Waste(int lines, int pieces, BigDecimal cost, int linesWithoutCost) {
    }

    /**
     * The waste logged on the days {@code from} to {@code to}, both included (the day of the log line, not of any order). The profit
     * of {@link #profit} is <b>before</b> this cost: waste is a loss on top of the cost of what was sold.
     */
    public Waste waste(LocalDate from, LocalDate to) {
        String sql = "SELECT COUNT(*) AS n, COALESCE(SUM(-quantity), 0) AS pieces, "
                + "COALESCE(SUM(CASE WHEN unit_cost IS NOT NULL THEN -quantity * unit_cost END), 0) AS cost, "
                + "COALESCE(SUM(CASE WHEN unit_cost IS NULL THEN 1 ELSE 0 END), 0) AS unknown "
                + "FROM stock_log WHERE kind = ? AND logged_at >= ? AND logged_at < ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, com.coffeeshop.coffeeshopmanagement.model.StockLogEntry.WASTE);
            statement.setString(2, from.toString());
            statement.setString(3, to.plusDays(1).toString()); // "before the next day" keeps the whole last day
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new Waste(rs.getInt("n"), rs.getInt("pieces"), Money.fromDong(rs.getLong("cost")), rs.getInt("unknown"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load waste", e);
        }
    }

    /**
     * The expenses of a range (TODO 4.38).
     *
     * @param total      all expenses spent in the range; never null, 0 when there were none
     * @param byCategory the total per category code, biggest first
     */
    public record Expenses(BigDecimal total, int count, java.util.Map<String, BigDecimal> byCategory) {
    }

    /** The expenses spent on the days {@code from} to {@code to}, both included (the day typed on the expense, not the day it was written). */
    public Expenses expenses(LocalDate from, LocalDate to) {
        String sql = "SELECT category, COALESCE(SUM(amount), 0) AS total, COUNT(*) AS n FROM expenses "
                + "WHERE spent_on >= ? AND spent_on <= ? GROUP BY category ORDER BY total DESC, category";
        java.util.Map<String, BigDecimal> byCategory = new java.util.LinkedHashMap<>();
        long total = 0;
        int count = 0;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, from.toString());
            statement.setString(2, to.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    long sum = rs.getLong("total");
                    byCategory.put(rs.getString("category"), Money.fromDong(sum));
                    total += sum;
                    count += rs.getInt("n");
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the expenses", e);
        }
        return new Expenses(Money.fromDong(total), count, byCategory);
    }

    /** Revenue and units of one category; {@code categoryName} is null for lines whose product has no category (or was deleted). */
    public record CategorySales(String categoryName, int quantity, BigDecimal revenue) {
    }

    /**
     * Sales per category (TODO 4.40): the line totals BEFORE the order discount (a discount belongs to the whole order, not to
     * a category), biggest first. A line whose product is gone or has no category is grouped under a null name.
     */
    public List<CategorySales> categorySales(LocalDate from, LocalDate to) {
        String sign = "o." + SalesLedger.SIGN; // sales minus refunds (TODO 3.13)
        String sql = "SELECT c.name AS name, SUM(oi.quantity * " + sign + ") AS qty, SUM(oi.line_total * " + sign + ") AS revenue " +
                "FROM order_items oi JOIN " + SalesLedger.forDays() + " ON oi.order_id = o.id LEFT JOIN products p ON oi.product_id = p.id " +
                "LEFT JOIN categories c ON p.category_id = c.id " +
                "GROUP BY c.id, c.name " +
                "HAVING SUM(oi.quantity * " + sign + ") <> 0 OR SUM(oi.line_total * " + sign + ") <> 0 ORDER BY revenue DESC";
        List<CategorySales> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            SalesLedger.bindDays(statement, 1, from, to);
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
                + " AND " + EffectiveDay.between("");
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
                "WHERE o.status = " + SqlStatus.PAID + " AND oi.product_id IS NOT NULL AND " + EffectiveDay.between("") + " GROUP BY oi.product_id";
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

    /**
     * Binds the day range of {@link EffectiveDay#between} from the first {@code ?}; returns the index of the next free one.
     * The filter is written as ranges on {@code paid_at} and {@code order_date} so that an index can be used (TODO 3.36).
     */
    private static int bindRange(PreparedStatement statement, LocalDate from, LocalDate to) throws SQLException {
        return EffectiveDay.bind(statement, 1, from, to);
    }

}
