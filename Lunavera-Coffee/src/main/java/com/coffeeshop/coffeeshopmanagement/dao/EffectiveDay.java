package com.coffeeshop.coffeeshopmanagement.dao;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * "Which day is this order?" written so that the database can use an index (TODO 3.36).
 *
 * <p>The rule is {@link OrderDAO#EFFECTIVE_DAY}: the day the order was paid, else the day it was opened. As a filter,
 * {@code DATE(COALESCE(paid_at, order_date)) = ?} cannot use any index, because the column is wrapped in functions, so every
 * history, report and dashboard query read the whole orders table. The same question asked as ranges on the bare columns can:
 *
 * <pre>
 *   (paid_at &gt;= '2026-10-10' AND paid_at &lt; '2026-10-11')
 *   OR (paid_at IS NULL AND order_date &gt;= '2026-10-10' AND order_date &lt; '2026-10-11')
 * </pre>
 *
 * Each half is served by an index of its own ({@code idx_orders_paid_at}; for the second half the rows with no paid time are
 * few), which SQLite does as a "multi-index OR" and MariaDB as an index merge. The result is exactly the same orders.
 *
 * <p>The bounds are plain {@code YYYY-MM-DD} text. SQLite stores {@code 2026-10-10T14:30:05} (the seconds are left out when
 * they are zero), so every time of day compares greater than or equal to its own date and less than the next date;
 * MariaDB reads the text as midnight of that day. The upper bound is "before the next day", so an order at 23:59:59 is in and
 * one at 00:00:00 the next day is out.
 *
 * <p>Use this class in the {@code WHERE} only. The day shown or grouped by ({@code SELECT} and {@code GROUP BY}) still uses
 * {@link OrderDAO#EFFECTIVE_DAY}: that is a value to show, not a condition to look up.
 */
final class EffectiveDay {

    private EffectiveDay() {
    }

    /** Orders of the days {@code from} to {@code to}, both included: four {@code ?}, bound by {@link #bind}. */
    static String between(String prefix) {
        return condition(prefix, true, true);
    }

    /**
     * The condition for an open or closed range: {@code from} and/or {@code to} may be null (no limit on that side), but not
     * both. The {@code ?} are bound in the order {@link #params} lists them.
     */
    static String condition(String prefix, boolean hasFrom, boolean hasTo) {
        if (!hasFrom && !hasTo) {
            throw new IllegalArgumentException("a range needs at least one end");
        }
        String paid = prefix + "paid_at";
        String opened = prefix + "order_date";
        return "((" + bounds(paid, hasFrom, hasTo) + ") OR (" + paid + " IS NULL AND " + bounds(opened, hasFrom, hasTo) + "))";
    }

    private static String bounds(String column, boolean hasFrom, boolean hasTo) {
        StringBuilder sql = new StringBuilder();
        if (hasFrom) {
            sql.append(column).append(" >= ?");
        }
        if (hasTo) {
            if (hasFrom) {
                sql.append(" AND ");
            }
            sql.append(column).append(" < ?");
        }
        return sql.toString();
    }

    /**
     * The values for {@link #condition}, in the order of its {@code ?}: the bounds for {@code paid_at}, then the same bounds
     * for {@code order_date}. A null {@code from} or {@code to} is left out, as in the condition.
     */
    static List<String> params(LocalDate from, LocalDate to) {
        List<String> values = new ArrayList<>(4);
        for (int half = 0; half < 2; half++) {
            if (from != null) {
                values.add(from.toString());
            }
            if (to != null) {
                values.add(to.plusDays(1).toString()); // "before the next day" keeps the whole last day
            }
        }
        return values;
    }

    /** Binds {@link #params} from {@code index} on; returns the index of the next free {@code ?}. */
    static int bind(PreparedStatement statement, int index, LocalDate from, LocalDate to) throws SQLException {
        for (String value : params(from, to)) {
            statement.setString(index++, value);
        }
        return index;
    }
}
