package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.util.BusinessDay;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * The money of a period as a ledger (TODO 3.13, 4.37): a sale counts on the day it was PAID, a refund counts, as a negative line, on
 * the day it was MADE. The sale stays on its own day, so refunding never changes a day whose cash was already counted; today shows
 * the refund instead.
 *
 * <p>Every query that adds up money or counts sales takes its rows from here instead of from {@code orders}: the derived table has
 * one row per sale, one more row per whole-order refund, and one more row per refunded LINE (TODO 4.37, the {@code refunds} table),
 * with every column of the order plus:
 * <ul>
 *   <li>{@code ledger_sign}: 1 for a sale, -1 for a refund of either kind: multiply an amount by it;</li>
 *   <li>{@code ledger_count}: how the row changes the NUMBER of orders: 1 for a sale, -1 for a whole refund, 0 for a refunded line
 *       (the order itself still stands);</li>
 *   <li>{@code ledger_total}: the money of the row: the order total, or for a refunded line what was given back;</li>
 *   <li>{@code ledger_cash}: the part of it that moved through the cash drawer, {@code ledger_method}: the method it was paid or
 *       given back by, {@code ledger_part}: 1 for a refunded line, {@code refund_id}: its row in {@code refunds} (0 otherwise);</li>
 *   <li>{@code event_time}: when the row counts: when it was paid (else opened), or when it was refunded.</li>
 * </ul>
 * A whole refund and refunded lines never meet on one order: {@code OrderDAO.cancelPaidOrder} refuses an order that already has
 * refunded lines, and a refund of lines is refused on an order that is cancelled.
 *
 * <p>A refunded order WITHOUT {@code cancelled_at} (refunded before the column existed) is not in the ledger at all, exactly as it
 * was not counted before: its refund day is unknown, so it is not guessed. An order cancelled while it was still open (a table
 * emptied) has no paid time and is never in it.
 *
 * <p>The period is inside the derived table, as ranges on the bare columns, so the indexes can still be used (TODO 3.36). The
 * {@code ?} of one ledger are bound by {@link #bindDays} or {@link #bindWindow}, which must be called before any other parameter of
 * the statement that follows the derived table in the text (the ledger comes first, in the {@code FROM}). For the lines of the
 * ledger ({@link #lines}) the ledger is in the text twice: bind with {@link #bindLinesDays}.
 */
final class SalesLedger {

    /** The column that is 1 for a sale and -1 for a refund. */
    static final String SIGN = "ledger_sign";
    /** The column that says how a row changes the number of orders: 1 a sale, -1 a whole refund, 0 a refunded line. */
    static final String COUNT = "ledger_count";
    /** The column with the money of the row (before the sign). */
    static final String TOTAL = "ledger_total";
    /** The column with the part of the money that went through the cash drawer (before the sign). */
    static final String CASH = "ledger_cash";
    /** The column with the payment method of the row. */
    static final String METHOD = "ledger_method";
    /** The column that is 1 for a refunded line. */
    static final String PART = "ledger_part";
    /** The column with the time the line counts under: when it was paid (or opened), or when it was refunded. */
    static final String TIME = "event_time";

    /** The shop's day of a ledger row, to show or group by (TODO 4.50: the day start decides where a day ends, so this is read each time). */
    static String day() {
        return EffectiveDay.shown("o." + TIME);
    }

    /** The shop's day of a line of {@link #lines}, to show or group by. */
    static String lineDay() {
        return EffectiveDay.shown("oi." + TIME);
    }

    /** An order that was paid and then refunded, with the time of the refund recorded (see the class text). */
    private static final String REFUNDED = "(o.status = " + SqlStatus.CANCELLED + " AND o.paid_at IS NOT NULL AND o.cancelled_at IS NOT NULL)";
    private static final String SALE = "(o.status = " + SqlStatus.PAID + " OR " + REFUNDED + ")";
    private static final String CASH_OF_ORDER = ShiftDAO.CASH_OF_ORDER;

    private SalesLedger() {
    }

    /** The three kinds of row, with the time condition of each (six or eight {@code ?}, see the callers). */
    private static String rows(String saleTime, String saleCondition) {
        return "(SELECT o.*, 1 AS " + SIGN + ", 1 AS " + COUNT + ", o.total AS " + TOTAL + ", " + CASH_OF_ORDER + " AS " + CASH
                + ", o.payment_method AS " + METHOD + ", 0 AS " + PART + ", 0 AS refund_id, " + saleTime + " AS " + TIME
                + " FROM orders o WHERE " + SALE + " AND " + saleCondition
                + " UNION ALL SELECT o.*, -1 AS " + SIGN + ", -1 AS " + COUNT + ", o.total AS " + TOTAL + ", " + CASH_OF_ORDER + " AS " + CASH
                + ", o.payment_method AS " + METHOD + ", 0 AS " + PART + ", 0 AS refund_id, o.cancelled_at AS " + TIME
                + " FROM orders o WHERE " + REFUNDED + " AND o.cancelled_at >= ? AND o.cancelled_at < ?"
                + " UNION ALL SELECT o.*, -1 AS " + SIGN + ", 0 AS " + COUNT + ", r.amount AS " + TOTAL
                + ", (CASE WHEN r.method = 'CASH' THEN r.amount ELSE 0 END) AS " + CASH
                + ", r.method AS " + METHOD + ", 1 AS " + PART + ", r.id AS refund_id, r.refunded_at AS " + TIME
                + " FROM refunds r JOIN orders o ON o.id = r.order_id WHERE r.refunded_at >= ? AND r.refunded_at < ?) o";
    }

    /** The ledger of the days {@code from} to {@code to}, both included, to put in a {@code FROM} as {@code o}: eight {@code ?}, bound by {@link #bindDays}. */
    static String forDays() {
        return rows("COALESCE(o.paid_at, o.order_date)", EffectiveDay.between("o."));
    }

    /** Binds {@link #forDays}; returns the index of the next free {@code ?}. */
    static int bindDays(PreparedStatement statement, int first, LocalDate from, LocalDate to) throws SQLException {
        int next = EffectiveDay.bind(statement, first, from, to);
        for (int twice = 0; twice < 2; twice++) { // the whole refunds, then the refunded lines: both by the day they were made
            statement.setString(next, BusinessDay.boundText(from));
            statement.setString(next + 1, BusinessDay.boundText(to.plusDays(1))); // "before the next day" keeps the whole last day
            next += 2;
        }
        return next;
    }

    /** The ledger of the time window {@code start <= time < end} (a shift): six {@code ?}, bound by {@link #bindWindow}. */
    static String forWindow() {
        return rows("o.paid_at", "o.paid_at >= ? AND o.paid_at < ?");
    }

    /** Binds {@link #forWindow} with the two bounds as the database stores times ({@code DbTime.write}); returns the next free index. */
    static int bindWindow(PreparedStatement statement, int first, String start, String end) throws SQLException {
        for (int i = 0; i < 3; i++) {
            statement.setString(first + 2 * i, start);
            statement.setString(first + 2 * i + 1, end);
        }
        return first + 6;
    }

    /**
     * The LINES of the ledger of the days, to put in a {@code FROM} as {@code oi}: for a sale or a whole refund every line of the
     * order, for a refunded line only the refunded pieces. Columns: {@code product_id}, {@code product_name}, {@code quantity},
     * {@code line_total} (before the order's discount), {@code size_name} and {@code extras} (TODO 4.47), {@code unit_cost}, {@code ledger_sign}, {@code line_count} (1 / -1 for a line
     * of a sale / whole refund, 0 for a refunded line, to count lines), {@code event_time}, and the order's {@code discount} and
     * {@code subtotal} (the share of the discount of a line is worked out from them). The ledger is in the text twice: bind with
     * {@link #bindLinesDays}.
     */
    static String lines() {
        String ledger = forDays();
        String cols = "oi.product_id AS product_id, oi.product_name AS product_name, oi.size_name AS size_name, oi.extras AS extras, "; // TODO 4.47
        String tail = ", oi.unit_cost AS unit_cost, o." + SIGN + " AS " + SIGN + ", o." + TIME + " AS " + TIME
                + ", o.discount AS discount, o.subtotal AS subtotal";
        return "(SELECT " + cols + "oi.quantity AS quantity, oi.line_total AS line_total" + tail + ", o." + COUNT + " AS line_count"
                + " FROM " + ledger + " JOIN order_items oi ON oi.order_id = o.id WHERE o." + PART + " = 0"
                + " UNION ALL SELECT " + cols + "r.quantity AS quantity, r.line_amount AS line_total" + tail + ", 0 AS line_count"
                + " FROM " + ledger + " JOIN refunds r ON r.id = o.refund_id JOIN order_items oi ON oi.id = r.order_item_id"
                + " WHERE o." + PART + " = 1) oi";
    }

    /** Binds {@link #lines}; returns the index of the next free {@code ?}. */
    static int bindLinesDays(PreparedStatement statement, int first, LocalDate from, LocalDate to) throws SQLException {
        return bindDays(statement, bindDays(statement, first, from, to), from, to);
    }
}
