package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.model.StockLogEntry;
import com.coffeeshop.coffeeshopmanagement.service.StockHistory;
import com.coffeeshop.coffeeshopmanagement.service.StockHistory.Kind;
import com.coffeeshop.coffeeshopmanagement.service.StockHistory.Move;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * One list of everything that moved the stock of a product or an ingredient (TODO 4.59), newest first, with the stock each line
 * left behind. <b>No new table:</b> the list is built from what the program already writes.
 * <ul>
 * <li>{@code stock_log}: goods received, numbers typed over, waste (products only; TODO 4.5, 4.33).</li>
 * <li>{@code stock_takes} with the order: what each paid line took off, per piece ({@code StockTaker.take}; TODO 4.41, 2.96). One
 *     line of the list per order.</li>
 * <li>{@code refunds} ({@code restocked = 1}) and cancelled paid orders: what {@code StockTaker.putBack} gave back. A refund of goods
 *     that did not go back on the shelf moved nothing and is not listed. A whole-order refund keeps no flag of its own; its audit
 *     line says whether the goods went back ({@code ORDER_REFUNDED} against {@code ORDER_REFUNDED_NO_STOCK}), and that is read here.</li>
 * <li>{@code audit_log} {@code INGREDIENT_STOCK}: a delivery or a count of an ingredient (ingredients have no stock log of their own).</li>
 * </ul>
 * A line paid before {@code stock_takes} existed has no take rows. For a product counted in cups it is still listed (its pieces are
 * the cups it took); for an ingredient it cannot be known what it took and it is left out.
 *
 * <p>Reading is for administrators, like the stock log. Every source is cut to its newest {@code limit} lines in the database, and
 * the balance is counted backwards from today's stock over the merged list ({@link StockHistory#page}), so a line shown is never
 * wrong because older ones are not.
 */
public class StockHistoryDAO {

    /** How many lines the screen asks for. */
    public static final int SHOWN_LINES = 500;

    /** The audit line of a whole-order refund that put the goods back: it exists for every such refund (OrderDAO.cancelPaidOrder). */
    private static final String REFUND_PUT_BACK = "EXISTS (SELECT 1 FROM audit_log al WHERE al.action = '" + AuditAction.ORDER_REFUNDED.name()
            + "' AND al.target_type = '" + AuditTarget.ORDER + "' AND al.target_id = o.id)";

    /** An order line that took nothing recorded: paid before {@code stock_takes} existed. */
    private static final String NO_TAKE_ROWS = "NOT EXISTS (SELECT 1 FROM stock_takes st WHERE st.order_item_id = oi.id)";

    /** A product counted in cups (no recipe), the only kind whose old lines can be listed without a take record. */
    private static final String COUNTED_IN_CUPS = "NOT EXISTS (SELECT 1 FROM recipes rc WHERE rc.product_id = oi.product_id)";

    /** The take rows that belong to what is being listed: a product's cups ({@code ingredient_id} NULL) or one ingredient. */
    private static String takeRows(boolean product) {
        return product ? "oi.product_id = ? AND st.ingredient_id IS NULL" : "st.ingredient_id = ?";
    }

    private interface RowReader {
        Move read(ResultSet rs) throws SQLException;
    }

    /** The history of a product's own stock number (cups), newest {@code limit} lines. */
    public StockHistory.Page ofProduct(int productId, int limit) {
        return build(true, productId, limit);
    }

    /** The history of an ingredient's stock, newest {@code limit} lines. */
    public StockHistory.Page ofIngredient(int ingredientId, int limit) {
        return build(false, ingredientId, limit);
    }

    private StockHistory.Page build(boolean product, int id, int limit) {
        Permissions.requireAdmin("view the stock history");
        int keep = Math.max(1, limit);
        try (Connection connection = DatabaseConfig.getConnection()) {
            long now = currentStock(connection, product, id);
            List<Move> all = new ArrayList<>();
            // sales: what each paid order took, one line per order
            all.addAll(query(connection, perOrderSql(WHEN_PAID, "st.amount", "JOIN stock_takes st ON st.order_item_id = oi.id", takeRows(product)),
                    id, keep, rs -> Move.of(DbTime.parse(rs.getString(2)), Kind.SALE, -rs.getLong(3), rs.getInt(1), null, null)));
            if (product) {
                all.addAll(query(connection, perOrderSql(WHEN_PAID, "1", "", "oi.product_id = ? AND (o.status = " + SqlStatus.PAID
                        + " OR (o.status = " + SqlStatus.CANCELLED + " AND o.paid_at IS NOT NULL)) AND " + NO_TAKE_ROWS + " AND " + COUNTED_IN_CUPS),
                        id, keep, rs -> Move.of(DbTime.parse(rs.getString(2)), Kind.SALE, -rs.getLong(3), rs.getInt(1), null, null)));
            }
            // goods put back: refunds of lines (when asked to restock) and whole orders refunded with the goods returned
            RowReader lineRefund = rs -> Move.of(DbTime.parse(rs.getString(2)), Kind.REFUND, rs.getLong(3), rs.getInt(1), rs.getString(5), rs.getString(4));
            all.addAll(query(connection, lineRefundSql("r.quantity * st.amount", "JOIN stock_takes st ON st.order_item_id = r.order_item_id", takeRows(product)),
                    id, keep, lineRefund));
            RowReader wholeRefund = rs -> Move.of(DbTime.parse(rs.getString(2)), Kind.REFUND, rs.getLong(3), rs.getInt(1), null, rs.getString(4));
            all.addAll(query(connection, perOrderSql(WHEN_CANCELLED, "st.amount", "JOIN stock_takes st ON st.order_item_id = oi.id",
                    "o.status = " + SqlStatus.CANCELLED + " AND " + REFUND_PUT_BACK + " AND " + takeRows(product)), id, keep, wholeRefund));
            if (product) {
                all.addAll(query(connection, lineRefundSql("r.quantity", "", "oi.product_id = ? AND " + NO_TAKE_ROWS + " AND " + COUNTED_IN_CUPS),
                        id, keep, lineRefund));
                all.addAll(query(connection, perOrderSql(WHEN_CANCELLED, "1", "", "oi.product_id = ? AND o.status = " + SqlStatus.CANCELLED + " AND "
                        + REFUND_PUT_BACK + " AND " + NO_TAKE_ROWS + " AND " + COUNTED_IN_CUPS), id, keep, wholeRefund));
                all.addAll(query(connection, "SELECT logged_at, kind, quantity, supplier, note, employee_name FROM stock_log WHERE product_id = ? "
                        + "ORDER BY logged_at DESC, id DESC LIMIT ?", id, keep, StockHistoryDAO::logLine));
            } else {
                all.addAll(query(connection, "SELECT logged_at, actor_name, old_value, new_value, detail FROM audit_log WHERE action = '"
                        + AuditAction.INGREDIENT_STOCK.name() + "' AND target_type = '" + AuditTarget.INGREDIENT + "' AND target_id = ? "
                        + "ORDER BY logged_at DESC, id DESC LIMIT ?", id, keep, StockHistoryDAO::auditLine));
            }
            return StockHistory.page(all, now, keep);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the stock history", e);
        }
    }

    /** The moment a sale counts: when the order was paid, or its date for an order paid before the paid time was kept. */
    private static final String WHEN_PAID = "COALESCE(o.paid_at, o.order_date)";

    /** The moment a whole-order refund counts: when it was made, or (an old one, before that was kept) when the order was paid. */
    private static final String WHEN_CANCELLED = "COALESCE(o.cancelled_at, o.paid_at, o.order_date)";

    /**
     * One row per order: its number, when, how many pieces its lines moved ({@code quantity x amount}) and its refund reason. Every
     * selected column is the grouped one or inside {@code MAX/SUM} (read against ONLY_FULL_GROUP_BY, see GroupByRulesTest).
     */
    private static String perOrderSql(String when, String amount, String join, String where) {
        return "SELECT o.id, MAX(" + when + ") AS moved_at, SUM(oi.quantity * " + amount + ") AS pieces, MAX(o.cancel_reason) AS why "
                + "FROM orders o JOIN order_items oi ON oi.order_id = o.id " + join + " WHERE " + where
                + " GROUP BY o.id ORDER BY moved_at DESC, o.id DESC LIMIT ?";
    }

    /** One row per refunded line that went back on the shelf: order, when, pieces, reason, who. */
    private static String lineRefundSql(String pieces, String join, String where) {
        return "SELECT r.order_id, r.refunded_at, " + pieces + ", r.reason, r.refunded_by_name FROM refunds r "
                + "JOIN order_items oi ON oi.id = r.order_item_id " + join
                + " WHERE r.restocked = 1 AND r.quantity > 0 AND " + where + " ORDER BY r.refunded_at DESC, r.id DESC LIMIT ?";
    }

    private static Move logLine(ResultSet rs) throws SQLException {
        String kind = rs.getString(2);
        Kind moved = StockLogEntry.RECEIPT.equals(kind) ? Kind.RECEIPT : StockLogEntry.WASTE.equals(kind) ? Kind.WASTE : Kind.ADJUST;
        String supplier = rs.getString(4);
        String note = rs.getString(5);
        String text = supplier == null || supplier.isBlank() ? note : note == null || note.isBlank() ? supplier : supplier + " - " + note;
        return Move.of(DbTime.parse(rs.getString(1)), moved, rs.getLong(3), null, rs.getString(6), text);
    }

    /** An ingredient's delivery or count; null for a line whose numbers are not plain numbers (left out, not guessed). */
    private static Move auditLine(ResultSet rs) throws SQLException {
        Long change = StockHistory.auditChange(rs.getString(3), rs.getString(4));
        if (change == null || change == 0) return null;
        return Move.of(DbTime.parse(rs.getString(1)), Kind.CHANGE, change, null, rs.getString(2), rs.getString(5));
    }

    private static List<Move> query(Connection connection, String sql, int id, int limit, RowReader reader) throws SQLException {
        List<Move> moves = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    Move move = reader.read(rs);
                    if (move != null && move.at() != null && move.quantity() != 0) moves.add(move);
                }
            }
        }
        return moves;
    }

    private static long currentStock(Connection connection, boolean product, int id) throws SQLException {
        String sql = product ? "SELECT stock FROM products WHERE id = ?" : "SELECT stock FROM ingredients WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) throw new DataAccessException(I18n.t(product ? "stock.product.gone" : "ingredient.gone"));
                return rs.getLong(1);
            }
        }
    }
}
