package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy;
import com.coffeeshop.coffeeshopmanagement.service.RefundMath;
import com.coffeeshop.coffeeshopmanagement.service.RefundSplit;
import com.coffeeshop.coffeeshopmanagement.service.StockRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Money;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Refunding SOME LINES of a paid order, not the whole order (TODO 4.37): one spilled drink on a table of five. One row in
 * {@code refunds} per refunded line (order, line, pieces, the money given back, how, by whom, when, why). In ONE transaction:
 * the rows are written, the stock of those pieces only goes back (when asked: a drink already made does not), and the loyalty points
 * are moved in proportion (what the order earned is taken back, what the customer spent comes back, see {@link RefundMath}).
 *
 * <p>The order stays PAID: its sale stays on the day it was paid and the refund counts, as negative money and no change to the
 * number of orders, on the day it was made ({@code SalesLedger}, TODO 3.13). An order with refunded lines can no longer be refunded
 * as a whole ({@code OrderDAO.cancelPaidOrder} refuses it): refund the remaining lines instead, and a cancelled order cannot have
 * lines refunded. Only an administrator refunds, as for a whole order.
 *
 * <p><b>Gift cards (TODO 2.93):</b> money that the order paid with a gift card never turns into cash. A refund goes back onto the card(s)
 * first, up to what the order still has spent on them ({@link GiftCardDAO#restoreUpTo}, on this transaction, with a {@code gift_card_moves}
 * line), and only the rest by the chosen method ({@link RefundSplit}). Each part is its own {@code refunds} row with its own method; a line
 * that is split between the card and another method is two rows, the card part carrying the pieces and the line's share and the other
 * part 0 of both. So the shift's cash, which counts only rows paid back in cash, and the report by payment method need no special case.
 *
 * <p>The audit log of TODO 4.2 is not built; this table already says who, when and why for the refunds, and each refund is also
 * written to the security log.
 */
public class RefundDAO {

    /** One line of the order with how much of it is left to refund. */
    /** {@code madeFromRecipe}: the product has a recipe, so it is a drink or dish made to order (TODO 2.66 / 6.90 d: the product kind the refund defaults use). */
    public record RefundableLine(int itemId, Integer productId, String productName, int quantity, BigDecimal lineTotal, int refundedQuantity,
                                 boolean madeFromRecipe) {
        public int remaining() {
            return quantity - refundedQuantity;
        }
    }

    /** An order as the refund box needs it. */
    public record State(int orderId, BigDecimal total, BigDecimal subtotal, BigDecimal refundedSoFar, int pointsEarned, int pointsSpent,
                        Integer customerId, List<RefundableLine> lines, long cardLeft) {

        /**
         * How much of {@code amount} goes back onto the gift card(s) the order was paid with (TODO 2.93): all of it, up to what the order
         * still has spent on cards ({@code cardLeft}); only the rest goes back by the method the cashier chooses.
         */
        public long cardPart(long amount) {
            return RefundSplit.cardPart(amount, cardLeft);
        }

        /** The lines for {@link RefundMath}. */
        public List<RefundMath.Line> mathLines() {
            List<RefundMath.Line> list = new ArrayList<>();
            for (RefundableLine line : lines) {
                list.add(new RefundMath.Line(line.itemId(), line.quantity(), Money.toDong(line.lineTotal()), line.refundedQuantity()));
            }
            return list;
        }

        /** What a request would do, for the live total in the box. */
        public RefundMath.Plan preview(Map<Integer, Integer> request) {
            return RefundMath.plan(Money.toDong(total), Money.toDong(subtotal), Money.toDong(refundedSoFar), pointsEarned, pointsSpent,
                    mathLines(), request);
        }
    }

    /** What one refund did. */
    public record Result(BigDecimal amount, int earnedBack, int spentBack, int lines, BigDecimal toCard) {
    }

    /** One refunded line, to list the refunds of an order. */
    public record RefundRow(int itemId, String productName, int quantity, BigDecimal amount, String method, boolean restocked, String reason,
                            String refundedByName, java.time.LocalDateTime refundedAt) {
    }

    /** The order and its lines with what was refunded already; a {@link DataAccessException} when the order is not paid. */
    public State load(int orderId) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            return load(connection, orderId);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the lines of order " + orderId, e);
        }
    }

    private static State load(Connection connection, int orderId) throws SQLException {
        BigDecimal total;
        BigDecimal subtotal;
        Integer customerId;
        int earned;
        int spent;
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT status, customer_id, subtotal, total, points_earned, points_redeemed FROM orders WHERE id = ?")) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    throw new DataAccessException(I18n.t("order.notfound", orderId));
                }
                if (OrderStatus.fromDb(rs.getString("status")) != OrderStatus.PAID) {
                    throw new DataAccessException(I18n.t("refund.notpaid"));
                }
                int cid = rs.getInt("customer_id");
                customerId = rs.wasNull() ? null : cid;
                subtotal = Money.fromDong(rs.getLong("subtotal"));
                total = Money.fromDong(rs.getLong("total"));
                int recorded = rs.getInt("points_earned");
                boolean noRecord = rs.wasNull();
                // an old order with no record falls back to today's rule, as the whole-order refund does (TODO 3.25)
                earned = noRecord ? (customerId == null ? 0 : LoyaltyPolicy.pointsFor(total)) : recorded;
                spent = rs.getInt("points_redeemed"); // NULL reads as 0
            }
        }
        List<RefundableLine> lines = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT oi.id, oi.product_id, oi.product_name, oi.quantity, oi.line_total, oi.size_name, oi.extras, "
                        + "COALESCE((SELECT SUM(r.quantity) FROM refunds r WHERE r.order_item_id = oi.id), 0) AS refunded, "
                        + "EXISTS (SELECT 1 FROM recipes rc WHERE rc.product_id = oi.product_id) AS made "
                        + "FROM order_items oi WHERE oi.order_id = ? ORDER BY oi.id")) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int productId = rs.getInt("product_id");
                    Integer product = rs.wasNull() ? null : productId;
                    lines.add(new RefundableLine(rs.getInt("id"), product, com.coffeeshop.coffeeshopmanagement.service.LineOptions.nameWithOptions(
                            rs.getString("product_name"), rs.getString("size_name"), rs.getString("extras")), rs.getInt("quantity"),
                            Money.fromDong(rs.getLong("line_total")), rs.getInt("refunded"), rs.getInt("made") != 0));
                }
            }
        }
        long refunded;
        try (PreparedStatement statement = connection.prepareStatement("SELECT COALESCE(SUM(amount), 0) FROM refunds WHERE order_id = ?")) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                refunded = rs.getLong(1);
            }
        }
        return new State(orderId, total, subtotal, Money.fromDong(refunded), earned, spent, customerId, lines, GiftCardDAO.spentNet(connection, orderId));
    }

    /**
     * Gives back some pieces of some lines of a paid order, in one transaction. Nothing changes if any step fails.
     *
     * @param quantities    order line id to the number of pieces to refund (a line with 0 is ignored)
     * @param returnToStock whether the pieces go back on the shelf: yes for packaged goods, no for a drink already made (TODO 2.66)
     * @param method        how the money is given back; a refund in cash comes off the cash the drawer should hold ({@code ShiftDAO}).
     *                      <b>An order paid with a gift card (TODO 2.93):</b> the money goes back onto the card(s) FIRST, up to what the order
     *                      still has spent on them, and only the rest by this method (so prepaid value never turns into cash and the result is
     *                      the same as refunding the whole order); the card part is its own {@code refunds} row with method GIFT_CARD
     * @param reason        why, optional, at most 200 characters
     * @throws DataAccessException with a sentence fit to show
     */
    public Result refund(int orderId, Map<Integer, Integer> quantities, boolean returnToStock, PaymentMethod method, String reason) {
        Permissions.requireAdmin("refund lines of a paid order");
        if (method == null) {
            method = PaymentMethod.CASH;
        }
        String cleanReason = StockRules.cleanText(reason);
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Locks the order and proves it is still paid (a whole refund on another till would have changed it).
                try (PreparedStatement lock = connection.prepareStatement(
                        "UPDATE orders SET status = status WHERE id = ? AND status = " + SqlStatus.PAID)) {
                    lock.setInt(1, orderId);
                    if (lock.executeUpdate() == 0) {
                        throw new DataAccessException(I18n.t("refund.notpaid"));
                    }
                }
                try (PreparedStatement free = connection.prepareStatement("SELECT 1 FROM orders WHERE id = ? AND payment_method = ?")) {
                    free.setInt(1, orderId);
                    free.setString(2, PaymentMethod.COMPLIMENTARY.name());
                    try (java.sql.ResultSet rs = free.executeQuery()) {
                        if (rs.next()) throw new DataAccessException(I18n.t("complimentary.problem.norefund")); // TODO 4.61: no money was paid; cancel the whole bill instead
                    }
                }
                State state = load(connection, orderId);
                String problem = RefundMath.problem(state.mathLines(), quantities);
                if (problem != null) {
                    throw new DataAccessException(I18n.t(problem));
                }
                RefundMath.Plan plan = state.preview(quantities);
                // TODO 2.93: card money first. The rows say where each part went, so the shift's cash (CASH rows only) and the report by
                // payment method (the row's method) need no change.
                List<RefundSplit.Row> rows = RefundSplit.rows(plan.lines(), state.cardLeft());
                long toCard = RefundSplit.toCard(rows);
                if (method == PaymentMethod.GIFT_CARD && plan.amount() > toCard) {
                    throw new DataAccessException(I18n.t("refund.method.giftcard")); // a card cannot pay back more than the order took from it
                }
                String at = DbTime.write(AppClock.now());
                Integer by = Session.getCurrentEmployee() == null ? null : Session.getCurrentEmployee().getId();
                String byName = Session.getDisplayName() == null ? "" : Session.getDisplayName();
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO refunds (order_id, order_item_id, quantity, "
                        + "line_amount, amount, method, restocked, reason, refunded_by, refunded_by_name, refunded_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (RefundSplit.Row row : rows) {
                        insert.setInt(1, orderId);
                        insert.setInt(2, row.itemId());
                        insert.setInt(3, row.quantity());
                        insert.setLong(4, row.lineAmount());
                        insert.setLong(5, row.amount());
                        insert.setString(6, row.toCard() ? PaymentMethod.GIFT_CARD.name() : method.name());
                        insert.setInt(7, returnToStock ? 1 : 0);
                        insert.setString(8, cleanReason);
                        if (by == null) insert.setNull(9, Types.INTEGER); else insert.setInt(9, by);
                        insert.setString(10, byName);
                        insert.setString(11, at);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                if (returnToStock) {
                    for (RefundMath.LineRefund refund : plan.lines()) { // once per line, whatever the split of its money
                        Integer productId = productOf(state, refund.itemId());
                        if (productId != null) {
                            StockTaker.putBack(connection, refund.itemId(), productId, refund.quantity()); // what that line took, cups or ingredients (TODO 4.41, 2.96, 4.47)
                        }
                    }
                }
                if (toCard > 0 && GiftCardDAO.restoreUpTo(connection, orderId, toCard) != toCard) {
                    throw new DataAccessException(I18n.t("refund.fail")); // the card money moved under us: nothing is kept
                }
                if (state.customerId() != null) {
                    movePoints(connection, state.customerId(), plan.earnedBack(), plan.spentBack());
                }
                AuditDAO.write(connection, com.coffeeshop.coffeeshopmanagement.model.AuditAction.ORDER_LINES_REFUNDED,
                        com.coffeeshop.coffeeshopmanagement.model.AuditTarget.ORDER, orderId, "#" + orderId,
                        null, com.coffeeshop.coffeeshopmanagement.service.AuditRules.money(Money.fromDong(plan.amount())),
                        plan.lines().size() + " line(s), " + method.name() + (toCard > 0 ? ", " + toCard + " back onto gift card(s)" : "")
                                + (returnToStock ? ", goods returned to stock" : ", goods NOT returned to stock")
                                + (cleanReason == null ? "" : ": " + cleanReason)); // TODO 4.2, 2.93
                connection.commit();
                SecurityLog.event(SecurityLog.actor() + " refunded " + plan.lines().size() + " line(s) of order #" + orderId + " for "
                        + plan.amount() + " by " + method.name() + (toCard > 0 ? " (" + toCard + " of it back onto gift card(s))" : "")
                        + (returnToStock ? ", goods returned to stock" : ", goods NOT returned to stock"));
                return new Result(Money.fromDong(plan.amount()), plan.earnedBack(), plan.spentBack(), plan.lines().size(), Money.fromDong(toCard));
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException(I18n.t("refund.fail"), e);
        }
    }

    private static Integer productOf(State state, int itemId) {
        for (RefundableLine line : state.lines()) {
            if (line.itemId() == itemId) {
                return line.productId();
            }
        }
        return null;
    }

    /** The same two moves as a whole refund: take back what the order earned (never below 0), give back what was spent (not to an anonymous customer). */
    private static void movePoints(Connection connection, int customerId, int earnedBack, int spentBack) throws SQLException {
        if (earnedBack > 0) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE customers SET loyalty_points = CASE WHEN loyalty_points > ? THEN loyalty_points - ? ELSE 0 END, "
                            + "lifetime_points = CASE WHEN lifetime_points > ? THEN lifetime_points - ? ELSE 0 END WHERE id = ?")) {
                statement.setInt(1, earnedBack);
                statement.setInt(2, earnedBack);
                statement.setInt(3, earnedBack);
                statement.setInt(4, earnedBack);
                statement.setInt(5, customerId);
                statement.executeUpdate();
            }
        }
        if (spentBack > 0) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE customers SET loyalty_points = CASE WHEN loyalty_points > ? THEN ? ELSE loyalty_points + ? END "
                            + "WHERE id = ? AND anonymised_at IS NULL")) {
                statement.setInt(1, Integer.MAX_VALUE - spentBack);
                statement.setInt(2, Integer.MAX_VALUE);
                statement.setInt(3, spentBack);
                statement.setInt(4, customerId);
                statement.executeUpdate();
            }
        }
    }

    /** The refunds already made on an order, oldest first (administrators only). */
    public List<RefundRow> forOrder(int orderId) {
        Permissions.requireAdmin("see the refunds of an order");
        String sql = "SELECT r.order_item_id, oi.product_name, r.quantity, r.amount, r.method, r.restocked, r.reason, r.refunded_by_name, "
                + "r.refunded_at, oi.size_name, oi.extras FROM refunds r JOIN order_items oi ON oi.id = r.order_item_id WHERE r.order_id = ? ORDER BY r.id";
        List<RefundRow> rows = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new RefundRow(rs.getInt(1), com.coffeeshop.coffeeshopmanagement.service.LineOptions.nameWithOptions(rs.getString(2), rs.getString(10), rs.getString(11)), rs.getInt(3), Money.fromDong(rs.getLong(4)), rs.getString(5),
                            rs.getInt(6) != 0, rs.getString(7), rs.getString(8), DbTime.parse(rs.getString(9))));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the refunds of order " + orderId, e);
        }
        return rows;
    }
}
