package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * The money of a period as a ledger (TODO 3.13): a sale counts on the day it was PAID, a refund counts, as a negative line, on the day
 * it was MADE ({@code orders.cancelled_at}). The sale stays on its own day, so cancelling a paid order never changes a day whose cash
 * was already counted; today shows the refund instead.
 *
 * <p>Every query that adds up money or counts sales takes its rows from here instead of from {@code orders}: the derived table has
 * one row per sale ({@code ledger_sign} = 1, {@code event_time} = the time it was paid, else opened) and one more row per refund
 * ({@code ledger_sign} = -1, {@code event_time} = {@code cancelled_at}), with every column of the order. Multiply an amount by
 * {@code ledger_sign} and the sale and its refund cancel out when both are in the period.
 *
 * <p>A refunded order WITHOUT {@code cancelled_at} (refunded before the column existed) is not in the ledger at all, exactly as it
 * was not counted before: its refund day is unknown, so it is not guessed. An order cancelled while it was still open (a table
 * emptied) has no paid time and is never in it.
 *
 * <p>The period is inside the derived table, as ranges on the bare columns, so the indexes can still be used (TODO 3.36). The
 * {@code ?} of one ledger are bound by {@link #bindDays} or {@link #bindWindow}, which must be called before any other parameter of
 * the statement that follows the derived table in the text (the ledger comes first, in the {@code FROM}).
 */
final class SalesLedger {

    /** The column that is 1 for a sale and -1 for a refund. */
    static final String SIGN = "ledger_sign";
    /** The column with the time the line counts under: when it was paid (or opened), or when it was refunded. */
    static final String TIME = "event_time";
    /** The day of a line, to show or group by. */
    static final String DAY = "DATE(o." + TIME + ")";

    /** An order that was paid and then refunded, with the time of the refund recorded (see the class text). */
    private static final String REFUNDED = "(o.status = " + SqlStatus.CANCELLED + " AND o.paid_at IS NOT NULL AND o.cancelled_at IS NOT NULL)";
    private static final String SALE = "(o.status = " + SqlStatus.PAID + " OR " + REFUNDED + ")";

    private SalesLedger() {
    }

    /** The ledger of the days {@code from} to {@code to}, both included, to put in a {@code FROM} as {@code o}: six {@code ?}, bound by {@link #bindDays}. */
    static String forDays() {
        return "(SELECT o.*, 1 AS " + SIGN + ", COALESCE(o.paid_at, o.order_date) AS " + TIME + " FROM orders o WHERE "
                + SALE + " AND " + EffectiveDay.between("o.")
                + " UNION ALL SELECT o.*, -1 AS " + SIGN + ", o.cancelled_at AS " + TIME + " FROM orders o WHERE "
                + REFUNDED + " AND o.cancelled_at >= ? AND o.cancelled_at < ?) o";
    }

    /** Binds {@link #forDays}; returns the index of the next free {@code ?}. */
    static int bindDays(PreparedStatement statement, int first, LocalDate from, LocalDate to) throws SQLException {
        int next = EffectiveDay.bind(statement, first, from, to);
        statement.setString(next, from.toString());
        statement.setString(next + 1, to.plusDays(1).toString()); // "before the next day" keeps the whole last day
        return next + 2;
    }

    /** The ledger of the time window {@code start <= time < end} (a shift): four {@code ?}, bound by {@link #bindWindow}. */
    static String forWindow() {
        return "(SELECT o.*, 1 AS " + SIGN + ", o.paid_at AS " + TIME + " FROM orders o WHERE "
                + SALE + " AND o.paid_at >= ? AND o.paid_at < ?"
                + " UNION ALL SELECT o.*, -1 AS " + SIGN + ", o.cancelled_at AS " + TIME + " FROM orders o WHERE "
                + REFUNDED + " AND o.cancelled_at >= ? AND o.cancelled_at < ?) o";
    }

    /** Binds {@link #forWindow} with the two bounds as the database stores times ({@code DbTime.write}); returns the next free index. */
    static int bindWindow(PreparedStatement statement, int first, String start, String end) throws SQLException {
        statement.setString(first, start);
        statement.setString(first + 1, end);
        statement.setString(first + 2, start);
        statement.setString(first + 3, end);
        return first + 4;
    }
}
