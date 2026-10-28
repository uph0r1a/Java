package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.Money;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Order persistence: the order saved for a table while it is being typed ({@link #saveOrUpdateOpenOrder},
 * {@link #updateIfStillOpen}), payment ({@link #payExistingOrder}, normally reached through {@code CheckoutService}),
 * cancelling a paid order ({@link #cancelPaidOrder}), the searchable history, and the aggregate queries the dashboards and
 * reports use. Used by the till ({@code OrderController}), the history window, the customer screen and the services.
 */
public class OrderDAO {

    /**
     * Records each line's product cost at the moment of payment (TODO 3.53): the report then computes profit from what the
     * product cost <em>then</em>, so editing a cost later never rewrites an earlier day. A product with no cost entered (or a
     * line of a deleted product) leaves {@code unit_cost} null, which the report shows as "not known". Runs inside the payment's
     * own transaction.
     */
    static void stampUnitCosts(Connection connection, int orderId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE order_items SET unit_cost = (SELECT p.cost FROM products p WHERE p.id = order_items.product_id) "
                        + "WHERE order_id = ?")) {
            statement.setInt(1, orderId);
            statement.executeUpdate();
        }
    }

    /**
     * What a payment leaves besides the amount (TODO 3.17, 4.31): for cash, what the customer handed over (never less than the total,
     * so the change is never negative) and the change that went back; for a transfer or an e-wallet (TODO 4.34), the bank's or wallet's reference as cleaned by
     * {@link com.coffeeshop.coffeeshopmanagement.service.PaymentDetails#cleanReference}. Anything that does not belong to the
     * method is dropped, so a card sale never keeps a cash figure. Returns {cashReceived, changeGiven} (nulls when not cash); the
     * cleaned reference is returned through {@code referenceOut[0]}.
     */
    static BigDecimal[] paymentDetails(PaymentMethod method, BigDecimal total, BigDecimal cashReceived, String transferRef,
                                       String[] referenceOut) {
        referenceOut[0] = method != null && method.needsReceivedConfirmation()
                ? com.coffeeshop.coffeeshopmanagement.service.PaymentDetails.cleanReference(transferRef) : null;
        if (method != PaymentMethod.CASH || cashReceived == null) {
            return new BigDecimal[]{null, null};
        }
        BigDecimal change = com.coffeeshop.coffeeshopmanagement.service.PaymentDetails.changeFor(cashReceived, total);
        if (change == null) {
            throw new DataAccessException(I18n.t("order.cash.short"));
        }
        return new BigDecimal[]{cashReceived, change};
    }

    /**
     * The rule for an order paid with two methods (TODO 4.32, owner's rule D4 as chosen here): one part in cash, the rest with one
     * other method; both parts above zero, whole dong, adding up to the total. {@code cashPart == null} is an ordinary payment.
     */
    static void checkCashPart(PaymentMethod method, BigDecimal total, BigDecimal cashPart) {
        if (cashPart == null) return;
        boolean wholeDong = cashPart.stripTrailingZeros().scale() <= 0;
        if (method == null || method == PaymentMethod.CASH || !wholeDong || cashPart.signum() <= 0
                || total == null || cashPart.compareTo(total) >= 0) {
            throw new DataAccessException(I18n.t("order.split.invalid"));
        }
    }

    /** Writes the two lines of a split payment inside the order's transaction; nothing for an ordinary payment. */
    private static void writeSplit(Connection connection, int orderId, PaymentMethod method, BigDecimal total, BigDecimal cashPart)
            throws SQLException {
        if (cashPart == null) return;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO order_payments (order_id, method, amount) VALUES (?, ?, ?)")) {
            statement.setInt(1, orderId);
            statement.setString(2, PaymentMethod.CASH.name());
            statement.setLong(3, Money.toDong(cashPart));
            statement.addBatch();
            statement.setInt(1, orderId);
            statement.setString(2, method.name());
            statement.setLong(3, Money.toDong(total.subtract(cashPart)));
            statement.addBatch();
            statement.executeBatch();
        }
    }

    private static void setNullableMoney(PreparedStatement statement, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, Money.toDong(value));
        }
    }

    public Order insert(Order order, List<OrderItem> items) {
        if (order.getStatus() == OrderStatus.PAID) {
            // A sale that is saved as paid must add up (TODO 2.25); open orders are autosaved as they are typed.
            order.setSubtotal(checkTotals(order.getDiscount(), order.getTotal(), items));
        }
        // The points this sale gives, worked out once and saved with the order, so a later refund takes back exactly these (TODO 3.25).
        final boolean paid = order.getStatus() == OrderStatus.PAID;
        if (paid) checkCashPart(order.getPaymentMethod(), order.getTotal(), order.getCashPart());
        final int earnedPoints = paid && order.getCustomerId() != null ? LoyaltyPolicy.pointsFor(order.getTotal()) : 0;
        String[] referenceOut = new String[1];
        BigDecimal[] cash = paid ? paymentDetails(order.getPaymentMethod(), order.getTotal(), order.getCashReceived(),
                order.getTransferRef(), referenceOut) : new BigDecimal[]{null, null};
        order.setCashReceived(cash[0]);
        order.setChangeGiven(cash[1]);
        order.setTransferRef(paid ? referenceOut[0] : null);
        order.setPointsEarned(paid ? earnedPoints : null);
        // Points spent on this sale (TODO 4.16): only a paid sale with a customer, and what they were worth is part of the discount.
        final LoyaltyPolicy.Redemption redemption = redemptionOf(order, paid);
        order.setPointsRedeemed(redemption.isNone() ? null : redemption.points());
        order.setPointsValue(redemption.isNone() ? null : redemption.value());
        String orderSql = "INSERT INTO orders (order_date, employee_id, customer_id, status, subtotal, discount, " +
                "total, payment_method, paid_at, table_number, discount_reason, paid_by, points_earned, cash_received, change_given, " +
                "transfer_ref, points_redeemed, points_value) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String itemSql = "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, line_total, note) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            if (order.getStatus() == OrderStatus.OPEN && order.getTableNumber() != null) {
                rejectIfTableIsGone(connection, order.getTableNumber()); // TODO 4.18: removed on another till
                rejectIfTableHasOpenOrder(connection, order.getTableNumber()); // TODO 3.22
            }
            int orderId;
            try (PreparedStatement statement = connection.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, DbTime.write(order.getOrderDate()));
                setNullableInt(statement, 2, order.getEmployeeId());
                setNullableInt(statement, 3, order.getCustomerId());
                statement.setString(4, order.getStatus().name());
                statement.setLong(5, Money.toDong(order.getSubtotal()));
                statement.setLong(6, Money.toDong(order.getDiscount()));
                statement.setLong(7, Money.toDong(order.getTotal()));
                statement.setString(8, order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null);
                statement.setString(9, DbTime.write(order.getPaidAt()));
                setNullableInt(statement, 10, order.getTableNumber());
                statement.setString(11, order.getDiscountReason());
                // The one who creates a paid order is also the one who took the money.
                setNullableInt(statement, 12, order.getStatus() == OrderStatus.PAID ? order.getEmployeeId() : null);
                setNullableInt(statement, 13, order.getPointsEarned());
                setNullableMoney(statement, 14, order.getCashReceived());
                setNullableMoney(statement, 15, order.getChangeGiven());
                statement.setString(16, order.getTransferRef());
                setNullableInt(statement, 17, order.getPointsRedeemed());
                setNullableMoney(statement, 18, order.getPointsValue());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    keys.next();
                    orderId = keys.getInt(1);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(itemSql)) {
                for (OrderItem item : items) {
                    statement.setInt(1, orderId);
                    setNullableInt(statement, 2, item.getProductId());
                    statement.setString(3, item.getProductName());
                    statement.setInt(4, item.getQuantity());
                    statement.setLong(5, Money.toDong(item.getUnitPrice()));
                    statement.setLong(6, Money.toDong(item.getLineTotal()));
                    statement.setString(7, com.coffeeshop.coffeeshopmanagement.service.OrderNote.clean(item.getNote())); // TODO 4.9
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            if (order.getStatus() == OrderStatus.PAID) {
                stampUnitCosts(connection, orderId); // TODO 3.53: the cost is recorded with the sale
                writeSplit(connection, orderId, order.getPaymentMethod(), order.getTotal(), order.getCashPart());
            }
            // Stock is taken inside the same transaction, and only if enough is left: a sale that
            // would drive stock below zero fails as a whole (nothing saved) instead of going negative.
            if (order.getStatus() == OrderStatus.PAID) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ? AND active = 1")) {
                    for (OrderItem item : items) {
                        if (item.getProductId() == null) continue;
                        statement.setInt(1, item.getQuantity());
                        statement.setInt(2, item.getProductId());
                        statement.setInt(3, item.getQuantity());
                        if (statement.executeUpdate() == 0) {
                            boolean off = isSwitchedOff(connection, item.getProductId());
                            connection.rollback();
                            if (off) {
                                throw new DataAccessException(I18n.t("order.item.stopped", item.getProductName()));
                            }
                            throw new DataAccessException(I18n.t("order.item.nostock", item.getProductName()));
                        }
                    }
                }
            }
            // Points spent are taken inside the same transaction too, and only if the customer still has them: two tills paying for
            // the same customer at once cannot spend the same points twice (TODO 4.16).
            if (!redemption.isNone()) {
                spendPoints(connection, order.getCustomerId(), redemption.points());
            }
            // Loyalty points are awarded inside the same transaction as the order itself, so a
            // failed save can never leave points granted for an order that doesn't exist.
            if (order.getCustomerId() != null && order.getStatus() == OrderStatus.PAID) {
                int points = earnedPoints;
                if (points > 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "UPDATE customers SET loyalty_points = loyalty_points + ? WHERE id = ?")) {
                        statement.setInt(1, points);
                        statement.setInt(2, order.getCustomerId());
                        statement.executeUpdate();
                    }
                }
            }
            if (order.getStatus() == OrderStatus.PAID && order.getTableNumber() != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE dining_tables SET status = " + SqlStatus.EMPTY + ", current_order_id = NULL WHERE table_number = ?")) {
                    statement.setInt(1, order.getTableNumber());
                    statement.executeUpdate();
                }
            }
            connection.commit();
            order.setId(orderId);
            return order;
        } catch (SQLException e) {
            if (order.getStatus() == OrderStatus.OPEN && order.getTableNumber() != null
                    && UniqueViolation.isUnique(e)) {
                // Two tills saved the same table at the same moment and the database's one-open-order rule (TODO 3.22) caught it.
                throw new DataAccessException(I18n.t("order.table.collision", order.getTableNumber()), e);
            }
            throw new DataAccessException("Failed to save order", e);
        }
    }

    /**
     * A table has at most one OPEN order (TODO 3.22): a second one made the table map and the order that opens disagree.
     * Checked inside the insert's transaction. This is a check, not a database rule, so two tills inserting for the same
     * free table at the very same moment on a shared MariaDB server could still both pass; a database-level unique rule
     * (partial index on SQLite, generated column on MariaDB) is left for a run that can test both.
     */
    /**
     * A new open order needs a table that exists (TODO 4.18). With two or more tills, one can still show a table that the
     * owner has just removed on another; its order would belong to no table on any map and nobody would find it.
     */
    private static void rejectIfTableIsGone(Connection connection, int tableNumber) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM dining_tables WHERE table_number = ?")) {
            statement.setInt(1, tableNumber);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    connection.rollback();
                    throw new DataAccessException(I18n.t("order.table.gone", tableNumber));
                }
            }
        }
    }

    private static void rejectIfTableHasOpenOrder(Connection connection, int tableNumber) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM orders WHERE table_number = ? AND status = " + SqlStatus.OPEN + " ORDER BY id DESC LIMIT 1")) {
            statement.setInt(1, tableNumber);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    int existing = rs.getInt(1);
                    connection.rollback();
                    throw new DataAccessException(I18n.t("order.table.hasopen", tableNumber, existing));
                }
            }
        }
    }

    public Order saveOrUpdateOpenOrder(Order order, List<OrderItem> items) {
        if (order.getId() <= 0) {
            order.setStatus(OrderStatus.OPEN);
            // The table is occupied because it now has an OPEN order (TableDAO.findAll derives it); no second write (TODO 2.53).
            return insert(order, items);
        }
        updateIfStillOpen(order, items);
        return order;
    }

    /** The lines of an order as saved, in the order they were saved (row id), for {@link OrderItemDiff}. */
    private static List<OrderItem> readItems(Connection connection, int orderId) throws SQLException {
        List<OrderItem> saved = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, product_id, product_name, quantity, unit_price, line_total, note FROM order_items WHERE order_id = ? ORDER BY id")) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OrderItem item = new OrderItem();
                    item.setId(rs.getInt(1));
                    int productId = rs.getInt(2);
                    item.setProductId(rs.wasNull() ? null : productId);
                    item.setProductName(rs.getString(3));
                    item.setQuantity(rs.getInt(4));
                    item.setUnitPrice(Money.fromDong(rs.getLong(5)));
                    item.setLineTotal(Money.fromDong(rs.getLong(6)));
                    item.setNote(rs.getString(7));
                    saved.add(item);
                }
            }
        }
        return saved;
    }

    /**
     * Brings an open order's lines to the given list (only the difference is written, see {@link OrderItemDiff}). Returns false (and writes nothing) when the order is no longer OPEN because it was paid
     * or cancelled somewhere else in the meantime, so the screen can tell the cashier at once (TODO 3.28).
     */
    public boolean updateIfStillOpen(Order order, List<OrderItem> items) {
        String updateOrderSql = "UPDATE orders SET subtotal = ?, discount = ?, total = ?, customer_id = ? WHERE id = ? AND status = " + SqlStatus.OPEN;
        String insertItemSql = "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, line_total, note) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(updateOrderSql)) {
                statement.setLong(1, Money.toDong(order.getSubtotal()));
                statement.setLong(2, Money.toDong(order.getDiscount()));
                statement.setLong(3, Money.toDong(order.getTotal()));
                setNullableInt(statement, 4, order.getCustomerId());
                statement.setInt(5, order.getId());
                if (statement.executeUpdate() == 0) {
                    // The order is no longer OPEN (paid or cancelled in the meantime). A late autosave
                    // must not touch the items of a finished order.
                    connection.rollback();
                    return false;
                }
            }
            // Only the difference is written (TODO 6.37), not "delete everything and insert it again" on every autosave.
            OrderItemDiff plan = OrderItemDiff.plan(readItems(connection, order.getId()), items);
            if (!plan.deletes.isEmpty()) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM order_items WHERE id = ? AND order_id = ?")) {
                    for (int rowId : plan.deletes) {
                        statement.setInt(1, rowId);
                        statement.setInt(2, order.getId());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
            }
            if (!plan.updates.isEmpty()) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE order_items SET quantity = ?, line_total = ? WHERE id = ? AND order_id = ?")) {
                    for (OrderItemDiff.Change change : plan.updates) {
                        statement.setInt(1, change.item.getQuantity());
                        statement.setLong(2, Money.toDong(change.item.getLineTotal()));
                        statement.setInt(3, change.rowId);
                        statement.setInt(4, order.getId());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
            }
            if (!plan.inserts.isEmpty()) {
                try (PreparedStatement statement = connection.prepareStatement(insertItemSql)) {
                    for (OrderItem item : plan.inserts) {
                        statement.setInt(1, order.getId());
                        setNullableInt(statement, 2, item.getProductId());
                        statement.setString(3, item.getProductName());
                        statement.setInt(4, item.getQuantity());
                        statement.setLong(5, Money.toDong(item.getUnitPrice()));
                        statement.setLong(6, Money.toDong(item.getLineTotal()));
                        statement.setString(7, com.coffeeshop.coffeeshopmanagement.service.OrderNote.clean(item.getNote())); // TODO 4.9
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
            }
            connection.commit();
            return true;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update open order #" + order.getId(), e);
        }
    }

    /**
     * Pays an OPEN order in one transaction: marks it PAID (only if it is still OPEN), links the
     * customer, rewrites the items, takes stock, awards loyalty points and frees the table.
     *
     * @param customerId customer to attach to the order, or {@code null} to keep whatever the order has
     * @throws DataAccessException if the order is not OPEN any more, stock is short, or the save fails -
     *                             in every case nothing is saved
     */
    public Order payExistingOrder(int orderId, Integer customerId, PaymentMethod method, BigDecimal discount,
                                  BigDecimal total, List<OrderItem> items) {
        return payExistingOrder(orderId, customerId, method, discount, null, total, items);
    }

    /** Same, and also saves why the discount was given ({@code discountReason}, null when there is none). */
    public Order payExistingOrder(int orderId, Integer customerId, PaymentMethod method, BigDecimal discount,
                                  String discountReason, BigDecimal total, List<OrderItem> items) {
        return payExistingOrder(orderId, customerId, method, discount, discountReason, total, items, null);
    }

    public Order payExistingOrder(int orderId, Integer customerId, PaymentMethod method, BigDecimal discount,
                                  String discountReason, BigDecimal total, List<OrderItem> items, Integer paidByEmployeeId) {
        return payExistingOrder(orderId, customerId, method, discount, discountReason, total, items, paidByEmployeeId, null, null);
    }

    /**
     * Checks that the figures it was given add up before anything is written (TODO 2.25): at least one line, every
     * line positive and equal to price x quantity, a discount between 0 and the subtotal, and a total equal to
     * subtotal - discount. The subtotal is saved too, and {@code paidByEmployeeId} records who took the money (TODO 2.47).
     */
    public Order payExistingOrder(int orderId, Integer customerId, PaymentMethod method, BigDecimal discount,
                                  String discountReason, BigDecimal total, List<OrderItem> items, Integer paidByEmployeeId,
                                  BigDecimal cashReceived, String transferRef) {
        return payExistingOrder(orderId, customerId, method, discount, discountReason, total, items, paidByEmployeeId,
                cashReceived, transferRef, LoyaltyPolicy.Redemption.NONE);
    }

    /**
     * As above, and {@code redemption} says how many loyalty points the customer spends on this bill (TODO 4.16). Their value is part
     * of {@code discount} (so {@code total} is still subtotal minus discount); the points are taken from the customer in the same
     * transaction, and only if they still have them. A redemption needs a customer on the order.
     */
    public Order payExistingOrder(int orderId, Integer customerId, PaymentMethod method, BigDecimal discount,
                                  String discountReason, BigDecimal total, List<OrderItem> items, Integer paidByEmployeeId,
                                  BigDecimal cashReceived, String transferRef, LoyaltyPolicy.Redemption redemption) {
        return payExistingOrder(orderId, customerId, method, discount, discountReason, total, items, paidByEmployeeId,
                cashReceived, transferRef, redemption, null);
    }

    /** Same, paid with two methods when {@code cashPart} is given: that much in cash, the rest with {@code method} (TODO 4.32). */
    public Order payExistingOrder(int orderId, Integer customerId, PaymentMethod method, BigDecimal discount,
                                  String discountReason, BigDecimal total, List<OrderItem> items, Integer paidByEmployeeId,
                                  BigDecimal cashReceived, String transferRef, LoyaltyPolicy.Redemption redemption, BigDecimal cashPart) {
        final LoyaltyPolicy.Redemption spent = redemption == null ? LoyaltyPolicy.Redemption.NONE : redemption;
        if (!spent.isNone() && (spent.value() == null || spent.value().signum() < 0 || discount == null || spent.value().compareTo(discount) > 0)) {
            throw new DataAccessException(I18n.t("order.points.mismatch"));
        }
        BigDecimal subtotal = checkTotals(discount, total, items);
        checkCashPart(method, total, cashPart);
        String[] referenceOut = new String[1];
        BigDecimal[] cash = paymentDetails(method, total, cashReceived, transferRef, referenceOut); // TODO 3.17, 4.31
        String orderSql = "UPDATE orders SET status = " + SqlStatus.PAID + ", paid_at = ?, payment_method = ?, discount = ?, total = ?, "
                + "customer_id = COALESCE(?, customer_id), discount_reason = ?, subtotal = ?, paid_by = ?, "
                + "cash_received = ?, change_given = ?, transfer_ref = ?, points_redeemed = ?, points_value = ? "
                + "WHERE id = ? AND status = " + SqlStatus.OPEN;
        String deleteItemsSql = "DELETE FROM order_items WHERE order_id = ?";
        String itemSql = "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, line_total, note) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(orderSql)) {
                statement.setString(1, DbTime.write(AppClock.now()));
                statement.setString(2, method != null ? method.name() : null);
                statement.setLong(3, Money.toDong(discount));
                statement.setLong(4, Money.toDong(total));
                setNullableInt(statement, 5, customerId);
                statement.setString(6, discountReason);
                statement.setLong(7, Money.toDong(subtotal));
                setNullableInt(statement, 8, paidByEmployeeId);
                setNullableMoney(statement, 9, cash[0]);
                setNullableMoney(statement, 10, cash[1]);
                statement.setString(11, referenceOut[0]);
                setNullableInt(statement, 12, spent.isNone() ? null : spent.points());
                setNullableMoney(statement, 13, spent.isNone() ? null : spent.value());
                statement.setInt(14, orderId);
                if (statement.executeUpdate() == 0) {
                    connection.rollback();
                    throw new DataAccessException(I18n.t("order.notopen", orderId));
                }
            }
            Integer finalCustomerId = null;
            Integer tableNumber = null;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT customer_id, table_number FROM orders WHERE id = ?")) {
                statement.setInt(1, orderId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        int c = rs.getInt(1);
                        finalCustomerId = rs.wasNull() ? null : c;
                        int t = rs.getInt(2);
                        tableNumber = rs.wasNull() ? null : t;
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(deleteItemsSql)) {
                statement.setInt(1, orderId);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(itemSql)) {
                for (OrderItem item : items) {
                    statement.setInt(1, orderId);
                    setNullableInt(statement, 2, item.getProductId());
                    statement.setString(3, item.getProductName());
                    statement.setInt(4, item.getQuantity());
                    statement.setLong(5, Money.toDong(item.getUnitPrice()));
                    statement.setLong(6, Money.toDong(item.getLineTotal()));
                    statement.setString(7, com.coffeeshop.coffeeshopmanagement.service.OrderNote.clean(item.getNote())); // TODO 4.9
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            stampUnitCosts(connection, orderId); // TODO 3.53: the cost is recorded with the sale
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ? AND active = 1")) {
                for (OrderItem item : items) {
                    if (item.getProductId() == null) continue;
                    statement.setInt(1, item.getQuantity());
                    statement.setInt(2, item.getProductId());
                    statement.setInt(3, item.getQuantity());
                    if (statement.executeUpdate() == 0) {
                        boolean off = isSwitchedOff(connection, item.getProductId());
                        connection.rollback();
                        if (off) {
                            throw new DataAccessException(I18n.t("order.item.stopped", item.getProductName()));
                        }
                        throw new DataAccessException(I18n.t("order.item.nostock", item.getProductName()));
                    }
                }
            }
            if (!spent.isNone()) {
                if (finalCustomerId == null) {
                    connection.rollback();
                    throw new DataAccessException(I18n.t("pos.points.nocustomer"));
                }
                spendPoints(connection, finalCustomerId, spent.points());
            }
            // The points this sale gives are saved with the order (0 without a customer), so a refund takes back exactly these (TODO 3.25).
            int points = finalCustomerId != null ? LoyaltyPolicy.pointsFor(total) : 0;
            writeSplit(connection, orderId, method, total, cashPart);
            try (PreparedStatement statement = connection.prepareStatement("UPDATE orders SET points_earned = ? WHERE id = ?")) {
                statement.setInt(1, points);
                statement.setInt(2, orderId);
                statement.executeUpdate();
            }
            if (finalCustomerId != null && points > 0) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE customers SET loyalty_points = loyalty_points + ? WHERE id = ?")) {
                    statement.setInt(1, points);
                    statement.setInt(2, finalCustomerId);
                    statement.executeUpdate();
                }
            }
            if (tableNumber != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE dining_tables SET status = " + SqlStatus.EMPTY + ", current_order_id = NULL WHERE table_number = ?")) {
                    statement.setInt(1, tableNumber);
                    statement.executeUpdate();
                }
            }
            connection.commit();
            return findById(orderId).orElse(null);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to complete payment for order #" + orderId, e);
        }
    }

    /** The redemption an order being inserted carries, checked: only a paid order with a customer, and the value inside its discount. */
    private static LoyaltyPolicy.Redemption redemptionOf(Order order, boolean paid) {
        Integer points = order.getPointsRedeemed();
        if (!paid || points == null || points <= 0) return LoyaltyPolicy.Redemption.NONE;
        BigDecimal value = order.getPointsValue();
        if (order.getCustomerId() == null) throw new DataAccessException(I18n.t("pos.points.nocustomer"));
        if (value == null || value.signum() < 0 || order.getDiscount() == null || value.compareTo(order.getDiscount()) > 0) {
            throw new DataAccessException(I18n.t("order.points.mismatch"));
        }
        return new LoyaltyPolicy.Redemption(points, value);
    }

    /**
     * Takes {@code points} off a customer's balance in one guarded statement: nothing happens (and the payment is refused) when the
     * customer has fewer by now, or has been made anonymous. The caller rolls the whole sale back by throwing.
     */
    private static void spendPoints(Connection connection, int customerId, int points) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE customers SET loyalty_points = loyalty_points - ? WHERE id = ? AND loyalty_points >= ? AND anonymised_at IS NULL")) {
            statement.setInt(1, points);
            statement.setInt(2, customerId);
            statement.setInt(3, points);
            if (statement.executeUpdate() == 0) {
                connection.rollback();
                throw new DataAccessException(I18n.t("order.points.notenough"));
            }
        }
    }

    /** Validates the lines and totals of a sale and returns the subtotal (sum of the lines). */
    static BigDecimal checkTotals(BigDecimal discount, BigDecimal total, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new DataAccessException(I18n.t("order.empty"));
        }
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem item : items) {
            if (item.getQuantity() <= 0) {
                throw new DataAccessException(I18n.t("order.qty.invalid", item.getProductName()));
            }
            if (item.getUnitPrice() == null || item.getUnitPrice().signum() < 0) {
                throw new DataAccessException(I18n.t("order.price.invalid", item.getProductName()));
            }
            BigDecimal expected = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            if (item.getLineTotal() == null || item.getLineTotal().compareTo(expected) != 0) {
                throw new DataAccessException(I18n.t("order.line.mismatch", item.getProductName()));
            }
            subtotal = subtotal.add(expected);
        }
        if (discount == null || discount.signum() < 0 || discount.compareTo(subtotal) > 0) {
            throw new DataAccessException(I18n.t("order.discount.range"));
        }
        if (total == null || total.compareTo(subtotal.subtract(discount)) != 0) {
            throw new DataAccessException(I18n.t("order.total.mismatch"));
        }
        return subtotal;
    }

    /** True for digits only, no leading zero, at most 9 digits: the text then means "order number N" (TODO 3.35). */
    static boolean looksLikeOrderNumber(String typed) {
        return typed != null && typed.matches("[1-9][0-9]{0,8}");
    }

    /**
     * Makes the text match literally in a {@code LIKE ... ESCAPE '!'} query: a typed %, _ or ! is not a wildcard.
     * The escape character is "!" rather than a backslash because MariaDB/MySQL treat a backslash inside a quoted
     * string as an escape of their own, so the same SQL would not mean the same thing in both databases.
     */
    static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** The cash line of a split payment, as a column of an orders query that names the table {@code o} (TODO 4.32). */
    static final String CASH_PART = "(SELECT SUM(op.amount) FROM order_payments op WHERE op.order_id = o.id AND op.method = 'CASH')";

    public Optional<Order> findById(int id) {
        String sql = "SELECT o.*, " + CASH_PART + " AS cash_part FROM orders o WHERE o.id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load order", e);
        }
        return Optional.empty();
    }

    /**
     * The one rule for "which day is this order?" (TODO 3.5, 3.23): a sale belongs to the day it was PAID (that is when the
     * money came in); an order that was never paid (still open, or abandoned) belongs to the day it was opened.
     * An order opened at 23:50 and paid at 00:10 therefore counts on the second day everywhere.
     *
     * <p>This is the day to SHOW or GROUP BY. To FILTER by day use {@link EffectiveDay} instead: the same rule written as
     * ranges on the bare columns, so that an index can be used (TODO 3.36). A function around the column cannot use one.
     */
    public static final String EFFECTIVE_DAY = "DATE(COALESCE(paid_at, order_date))";

    /** All orders of today by {@link #EFFECTIVE_DAY}, newest first. */
    public List<Order> findTodayOrders() {
        List<Order> result = new ArrayList<>();
        String sql = "SELECT * FROM orders WHERE " + EffectiveDay.between("") + " ORDER BY id DESC";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            LocalDate today = AppClock.today();
            EffectiveDay.bind(statement, 1, today, today);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load today's orders", e);
        }
        return result;
    }

    /** Find open order for a table if exists. */
    public Optional<Order> findOpenOrderByTable(int tableNumber) {
        String sql = "SELECT * FROM orders WHERE table_number = ? AND status = " + SqlStatus.OPEN + " ORDER BY id DESC LIMIT 1";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, tableNumber);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load open order for table " + tableNumber, e);
        }
        return Optional.empty();
    }

    /** The take-away orders still open (no table), oldest first (TODO 4.25): the till shows each as a button beside the tables. */
    public List<Order> findOpenTakeAway() {
        String sql = "SELECT * FROM orders WHERE table_number IS NULL AND status = " + SqlStatus.OPEN + " ORDER BY id";
        List<Order> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the open take-away orders", e);
        }
        return result;
    }

    /**
     * Cancels an open take-away order (TODO 4.25), the take-away way of emptying a table. Only an order that is still OPEN and has
     * no table is touched, in one statement, so a table's order or an order paid a moment ago on another till is never cancelled
     * by mistake. Returns false when there was nothing to cancel.
     */
    public boolean cancelOpenTakeAway(int orderId) {
        String sql = "UPDATE orders SET status = " + SqlStatus.CANCELLED + " WHERE id = ? AND table_number IS NULL AND status = " + SqlStatus.OPEN;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to cancel the take-away order", e);
        }
    }

    /** An order plus the display names the history screen needs (avoids N+1 lookups per row). */
    public record OrderSummary(Order order, String employeeName, String customerName) {
    }

    /** Search/status/date-range criteria for the history screen. Any field left null/blank is
     *  not applied. Its own type rather than passing four loose parameters, since
     *  {@link #findHistoryPage} and {@link #countHistory} both need to build the exact same
     *  WHERE clause from it and must never drift apart. */
    public record HistoryFilter(String keyword, OrderStatus status, LocalDate from, LocalDate to, Integer customerId) {
        public static final HistoryFilter NONE = new HistoryFilter(null, null, null, null);

        public HistoryFilter(String keyword, OrderStatus status, LocalDate from, LocalDate to) {
            this(keyword, status, from, to, null);
        }

        /** Both dates are set and "from" is after "to": no order can match (TODO 3.58). The screens warn instead of showing an empty list. */
        public boolean hasReversedDates() {
            return from != null && to != null && from.isAfter(to);
        }

        /** Same filter limited to one customer's orders (TODO 4.4). */
        public HistoryFilter forCustomer(int id) {
            return new HistoryFilter(keyword, status, from, to, id);
        }
    }

    private static final class FilterSql {
        final String whereClause;
        final List<Object> params = new ArrayList<>();

        FilterSql(HistoryFilter filter) {
            List<String> conditions = new ArrayList<>();
            if (filter.status() != null) {
                conditions.add("o.status = ?");
                params.add(filter.status().name());
            }
            if (filter.from() != null || filter.to() != null) {
                // One condition for the day range, written as ranges on the bare columns so that an index can be used (TODO 3.36).
                conditions.add(EffectiveDay.condition("o.", filter.from() != null, filter.to() != null));
                params.addAll(EffectiveDay.params(filter.from(), filter.to()));
            }
            if (filter.customerId() != null) {
                conditions.add("o.customer_id = ?");
                params.add(String.valueOf(filter.customerId()));
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                // The customer can be found by phone too (TODO 5.23): "+84 901 234 567" matches the stored "0901234567".
                // A phone is only tried once the text has 4 digits, so "12" still means order #12 and not every phone with 12.
                String typed = filter.keyword().trim().replace("#", "");
                String compact = typed.replaceAll("[\\s.\\-()]", "");
                String phone = com.coffeeshop.coffeeshopmanagement.util.ValidationUtil.isValidPhone(compact)
                        ? com.coffeeshop.coffeeshopmanagement.util.ValidationUtil.normalizePhone(compact) : compact;
                boolean phoneSearch = phone != null && phone.length() >= 4 && phone.chars().allMatch(Character::isDigit);
                // Digits alone are an order number and match it exactly: "#12" finds order 12, not 112, 120 or 312 (TODO 3.35).
                // A number with a leading zero or more than 9 digits is no order number here (it can still be a phone).
                // "DH000012" (the note on a bank transfer), "000012" and "# 12" find order 12 as well (TODO 3.56).
                Integer reference = com.coffeeshop.coffeeshopmanagement.util.OrderNumber.parseReference(filter.keyword());
                boolean orderNumber = reference != null;
                // A bank reference (TODO 4.31): from 5 characters on, so that a short number still means an order; ASCII letters, as banks print them.
                boolean referenceSearch = typed.length() >= 5;
                conditions.add("(" + (orderNumber ? "o.id = ? OR " : "")
                        + DatabaseConfig.searchKeySql("COALESCE(pe.full_name, e.full_name)") + " LIKE ? ESCAPE '!' OR "
                        + DatabaseConfig.searchKeySql("c.full_name") + " LIKE ? ESCAPE '!'"
                        + (phoneSearch ? " OR c.phone LIKE ? ESCAPE '!'" : "")
                        + (referenceSearch ? " OR UPPER(o.transfer_ref) LIKE ? ESCAPE '!'" : "") + ")");
                // Names are compared folded on both sides (TODO 3.31): "nguyen" and "NGUYỄN" both find "Nguyễn", "dao" finds "Đào".
                String like = "%" + escapeLike(com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(typed)) + "%";
                if (orderNumber) params.add(String.valueOf(reference));
                params.add(like);
                params.add(like);
                if (phoneSearch) params.add("%" + escapeLike(phone) + "%");
                if (referenceSearch) params.add("%" + escapeLike(typed.toUpperCase(java.util.Locale.ROOT)) + "%");
            }
            whereClause = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        }

        int bind(PreparedStatement statement) throws SQLException {
            int index = 1;
            for (Object param : params) {
                statement.setString(index++, (String) param);
            }
            return index;
        }
    }

    private static final String HISTORY_FROM =
            " FROM orders o LEFT JOIN employees e ON o.employee_id = e.id LEFT JOIN customers c ON o.customer_id = c.id"
            + " LEFT JOIN employees pe ON o.paid_by = pe.id";

    /** One page of order history, newest first, matching {@code filter} - real server-side
     *  paging (TODO.md: the old {@code findHistory(int limit)} loaded up to 500 rows into
     *  memory and filtered client-side, so a date range further back than the most recent 500
     *  orders silently showed nothing even when matching orders existed). Use with
     *  {@link #countHistory} for the total row count driving the page controls. */
    public List<OrderSummary> findHistoryPage(HistoryFilter filter, int pageSize, int offset) {
        return findHistoryPage(filter, HistorySort.NEWEST_FIRST, pageSize, offset);
    }

    /** Sortable columns of the history screen. Whitelisted here (never built from UI text) so
     *  the ORDER BY can't be used for SQL injection. */
    public enum HistorySortKey {
        ID("o.id", false), DATE("COALESCE(o.paid_at, o.order_date)", false), EMPLOYEE("COALESCE(pe.full_name, e.full_name)", true),
        CUSTOMER("c.full_name", true), PAYMENT("o.payment_method", false),
        TOTAL("o.total", false), STATUS("o.status", false);

        private final String column;
        private final boolean text;

        HistorySortKey(String column, boolean text) {
            this.column = column;
            this.text = text;
        }

        /** The ORDER BY expression; text columns sort ignoring capitalisation, which is spelled differently per database. */
        String sql() {
            return text ? DatabaseConfig.dialect().caseInsensitive(column) : column;
        }
    }

    /** Sorting is done in SQL, over the whole filtered result, <em>before</em> LIMIT/OFFSET cuts a page. */
    public record HistorySort(HistorySortKey key, boolean descending) {
        public static final HistorySort NEWEST_FIRST = new HistorySort(HistorySortKey.ID, true);
    }

    /** The history query with its filter and sort, before any LIMIT: shared by the paged screen and the streaming export. */
    private static String historySelect(FilterSql filterSql, HistorySort effective) {
        return "SELECT o.*, " + CASH_PART + " AS cash_part, COALESCE(pe.full_name, e.full_name) AS employee_name, c.full_name AS customer_name"
                + HISTORY_FROM + filterSql.whereClause
                + " ORDER BY " + effective.key().sql() + (effective.descending() ? " DESC" : " ASC")
                + ", o.id DESC";
    }

    public List<OrderSummary> findHistoryPage(HistoryFilter filter, HistorySort sort, int pageSize, int offset) {
        FilterSql filterSql = new FilterSql(filter);
        HistorySort effective = sort != null ? sort : HistorySort.NEWEST_FIRST;
        String sql = historySelect(filterSql, effective) + " LIMIT ? OFFSET ?";
        List<OrderSummary> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = filterSql.bind(statement);
            statement.setInt(index++, pageSize);
            statement.setInt(index, offset);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(new OrderSummary(map(rs), rs.getString("employee_name"), rs.getString("customer_name")));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load order history", e);
        }
        return result;
    }

    /**
     * Hands every order matching {@code filter}, in {@code sort} order, to {@code sink} one row at a time while the result is read,
     * and returns how many there were (TODO 3.54). Nothing is collected in a list and nothing is cut off: the export used to read
     * at most 200,000 rows into memory and write a file that ended there without a word. On MariaDB the driver is told to stream
     * (it would otherwise load the whole result before the first row); SQLite reads row by row anyway. Admin only.
     */
    public int exportOrders(HistoryFilter filter, HistorySort sort, java.util.function.Consumer<OrderSummary> sink) {
        Permissions.requireAdmin("export orders");
        FilterSql filterSql = new FilterSql(filter);
        HistorySort effective = sort != null ? sort : HistorySort.NEWEST_FIRST;
        int count = 0;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(historySelect(filterSql, effective))) {
            if (DatabaseConfig.dialect() == com.coffeeshop.coffeeshopmanagement.config.SqlDialect.MARIADB) {
                statement.setFetchSize(Integer.MIN_VALUE); // MariaDB Connector/J: one row at a time
            }
            filterSql.bind(statement);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    sink.accept(new OrderSummary(map(rs), rs.getString("employee_name"), rs.getString("customer_name")));
                    count++;
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to export orders", e);
        }
        return count;
    }

    /** Every order matching {@code filter} in {@code sort} order, as a list: for small results only. Use {@link #exportOrders} for files. */
    public List<OrderSummary> findAllForExport(HistoryFilter filter, HistorySort sort) {
        List<OrderSummary> rows = new ArrayList<>();
        exportOrders(filter, sort, rows::add);
        return rows;
    }

    /** Total rows matching {@code filter}, ignoring paging - for the page-count/"N đơn" label. */
    public int countHistory(HistoryFilter filter) {
        FilterSql filterSql = new FilterSql(filter);
        String sql = "SELECT COUNT(*)" + HISTORY_FROM + filterSql.whereClause;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            filterSql.bind(statement);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count order history", e);
        }
    }

    /**
     * Cancels a PAID order as a full refund, in one transaction: stock is put back, the loyalty
     * points that order earned are taken back (never below zero), and the status becomes
     * CANCELLED (which the dashboard's revenue query already excludes). Nothing changes if any
     * step fails. The order row and its items are kept, so the history stays intact.
     */
    public void cancelPaidOrder(int orderId) {
        cancelPaidOrder(orderId, true);
    }

    /**
     * Refunds a paid order. {@code returnToStock} says whether the goods go back on the shelf: yes for packaged goods that
     * were not opened, no for a drink that was already made and thrown away, which would otherwise raise the stock by cups
     * that do not exist (TODO 2.66). The loyalty points are taken back either way.
     */
    public void cancelPaidOrder(int orderId, boolean returnToStock) {
        com.coffeeshop.coffeeshopmanagement.util.Permissions.requireAdmin("refund a paid order");
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Integer customerId;
                BigDecimal total;
                Integer recordedPoints; // what the order gave when it was paid; null for an order paid before that was recorded (TODO 3.25)
                int spentPoints; // what the customer spent on it (TODO 4.16): 0 when none
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT status, customer_id, total, points_earned, points_redeemed FROM orders WHERE id = ?")) {
                    statement.setInt(1, orderId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (!rs.next()) {
                            throw new DataAccessException(I18n.t("order.notfound", orderId));
                        }
                        if (OrderStatus.fromDb(rs.getString("status")) != OrderStatus.PAID) {
                            throw new DataAccessException(I18n.t("order.cancel.onlypaid"));
                        }
                        int cid = rs.getInt("customer_id");
                        customerId = rs.wasNull() ? null : cid;
                        total = Money.fromDong(rs.getLong("total"));
                        int recorded = rs.getInt("points_earned");
                        recordedPoints = rs.wasNull() ? null : recorded;
                        spentPoints = rs.getInt("points_redeemed"); // NULL reads as 0
                    }
                }
                if (returnToStock) try (PreparedStatement items = connection.prepareStatement(
                        "SELECT product_id, quantity FROM order_items WHERE order_id = ? AND product_id IS NOT NULL");
                     PreparedStatement restock = connection.prepareStatement(
                             "UPDATE products SET stock = stock + ? WHERE id = ?")) {
                    items.setInt(1, orderId);
                    try (ResultSet rs = items.executeQuery()) {
                        while (rs.next()) {
                            restock.setInt(1, rs.getInt("quantity"));
                            restock.setInt(2, rs.getInt("product_id"));
                            restock.addBatch();
                        }
                    }
                    restock.executeBatch();
                }
                if (customerId != null) {
                    // Exactly what this order gave; only an old order with no record falls back to today's rule (TODO 3.25).
                    int points = recordedPoints != null ? recordedPoints : LoyaltyPolicy.pointsFor(total);
                    if (points > 0) {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "UPDATE customers SET loyalty_points = CASE WHEN loyalty_points > ? THEN loyalty_points - ? ELSE 0 END "
                                        + "WHERE id = ?")) {
                            statement.setInt(1, points);
                            statement.setInt(2, points);
                            statement.setInt(3, customerId);
                            statement.executeUpdate();
                        }
                    }
                    // The points the customer spent on this order come back (TODO 4.16, rule D5), after the earned ones were taken
                    // back, so the balance never goes below 0 on the way. A customer made anonymous since has no balance to give them to.
                    if (spentPoints > 0) {
                        try (PreparedStatement statement = connection.prepareStatement(
                                "UPDATE customers SET loyalty_points = CASE WHEN loyalty_points > ? THEN ? ELSE loyalty_points + ? END "
                                        + "WHERE id = ? AND anonymised_at IS NULL")) {
                            statement.setInt(1, Integer.MAX_VALUE - spentPoints);
                            statement.setInt(2, Integer.MAX_VALUE);
                            statement.setInt(3, spentPoints);
                            statement.setInt(4, customerId);
                            statement.executeUpdate();
                        }
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE orders SET status = " + SqlStatus.CANCELLED + ", cancelled_at = ? WHERE id = ? AND status = " + SqlStatus.PAID)) {
                    statement.setString(1, DbTime.write(AppClock.now())); // the refund counts on this day, the sale stays on its own (TODO 3.13)
                    statement.setInt(2, orderId);
                    if (statement.executeUpdate() == 0) {
                        // Somebody else cancelled it between the read and this write: undo the restock/points above.
                        throw new DataAccessException(I18n.t("order.cancel.already", orderId));
                    }
                }
                connection.commit();
                com.coffeeshop.coffeeshopmanagement.util.SecurityLog.event(
                        com.coffeeshop.coffeeshopmanagement.util.SecurityLog.actor() + " refunded order #" + orderId
                                + (returnToStock ? ", goods returned to stock" : ", goods NOT returned to stock"));
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to cancel order", e);
        }
    }

    public List<OrderItem> findItemsByOrderId(int orderId) {
        String sql = "SELECT * FROM order_items WHERE order_id = ? ORDER BY id";
        List<OrderItem> items = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, orderId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    OrderItem item = new OrderItem();
                    item.setId(rs.getInt("id"));
                    int productId = rs.getInt("product_id");
                    item.setProductId(rs.wasNull() ? null : productId);
                    item.setProductName(rs.getString("product_name"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(Money.fromDong(rs.getLong("unit_price")));
                    item.setLineTotal(Money.fromDong(rs.getLong("line_total")));
                    item.setNote(rs.getString("note"));
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load order items", e);
        }
        return items;
    }

    /** The latest time recorded on any order (paid time, else opened time), or null when there are no orders. */
    public LocalDateTime newestActivity() {
        String sql = "SELECT MAX(COALESCE(paid_at, order_date)) FROM orders";
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next() || rs.getString(1) == null) return null;
            return DbTime.parse(rs.getString(1));
        } catch (SQLException | RuntimeException e) {
            return null; // the check is only a courtesy
        }
    }

    public int countToday() {
        return countWhere("status <> " + SqlStatus.CANCELLED + " AND " + EffectiveDay.between(""), todayParams());
    }

    /**
     * OPEN orders that were started more than {@code age} ago, oldest first (TODO 2.11). Nothing closes an order whose table
     * was never paid or emptied, so these are the ones to look at.
     */
    public List<Order> findStaleOpenOrders(java.time.Duration age) {
        // The cutoff is written the way the database stores order_date (ISO text in SQLite, DATETIME text in MariaDB).
        String cutoff = DbTime.write(AppClock.now().minus(age));
        String sql = "SELECT * FROM orders WHERE status = " + SqlStatus.OPEN + " AND order_date < ? ORDER BY order_date";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, cutoff);
            try (ResultSet rs = statement.executeQuery()) {
                List<Order> orders = new ArrayList<>();
                while (rs.next()) orders.add(map(rs));
                return orders;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load stale open orders", e);
        }
    }

    /** Orders that were paid and then refunded; tables emptied with an unpaid order are not counted (TODO 2.50). */
    public int countRefunded() {
        return countWhere("status = " + SqlStatus.CANCELLED + " AND paid_at IS NOT NULL");
    }

    public int countByStatus(OrderStatus status) {
        String sql = "SELECT COUNT(*) FROM orders WHERE status = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count orders by status", e);
        }
    }

    public BigDecimal sumRevenueToday() {
        // Sales paid today minus refunds made today (TODO 3.13): a sale refunded today still counts on the day it was paid.
        String sql = "SELECT COALESCE(SUM(o.total * o." + SalesLedger.SIGN + "), 0) FROM " + SalesLedger.forDays();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            LocalDate today = AppClock.today();
            SalesLedger.bindDays(statement, 1, today, today);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return Money.fromDong(rs.getLong(1)); // COALESCE(...,0) guarantees a non-null row
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to sum today's revenue", e);
        }
    }

    public int countDistinctCustomersToday() {
        String sql = "SELECT COUNT(DISTINCT customer_id) FROM orders " +
                "WHERE customer_id IS NOT NULL AND status <> " + SqlStatus.CANCELLED + " AND " + EffectiveDay.between("");
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            LocalDate today = AppClock.today();
            EffectiveDay.bind(statement, 1, today, today);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count today's customers", e);
        }
    }

    /**
     * Paid revenue for each of the last 7 days (oldest first), keyed by date. Days with no
     * paid orders are included with a value of zero so charts don't skip gaps.
     */
    public Map<LocalDate, BigDecimal> revenueForLast7Days() {
        Map<LocalDate, BigDecimal> byDate = new LinkedHashMap<>();
        LocalDate today = AppClock.today();
        for (int i = 6; i >= 0; i--) {
            byDate.put(today.minusDays(i), BigDecimal.ZERO);
        }
        String sql = "SELECT " + SalesLedger.DAY + " AS d, SUM(o.total * o." + SalesLedger.SIGN + ") AS revenue FROM "
                + SalesLedger.forDays() + " GROUP BY " + SalesLedger.DAY; // a refund is a negative amount on the day it was made (TODO 3.13)
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            // Not after today either (TODO 3.42): an order paid on a day in the future, left by a till clock that was set too
            // far ahead, must not add an 8th bar to the chart.
            SalesLedger.bindDays(statement, 1, today.minusDays(6), today);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = LocalDate.parse(rs.getString("d"));
                    if (byDate.containsKey(date)) { // belt and braces: only the 7 days asked for
                        byDate.put(date, Money.fromDong(rs.getLong("revenue")));
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load 7-day revenue", e);
        }
        return byDate;
    }

    /** The values of {@link EffectiveDay#between} for today alone. */
    private static String[] todayParams() {
        LocalDate today = AppClock.today();
        return EffectiveDay.params(today, today).toArray(new String[0]);
    }

    private int countWhere(String whereClause, String... params) {
        String sql = "SELECT COUNT(*) FROM orders WHERE " + whereClause;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setString(i + 1, params[i]);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count orders", e);
        }
    }

    private void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private Order map(ResultSet rs) throws SQLException {
        Order order = new Order();
        order.setId(rs.getInt("id"));
        order.setOrderDate(DbTime.parse(rs.getString("order_date")));
        int employeeId = rs.getInt("employee_id");
        order.setEmployeeId(rs.wasNull() ? null : employeeId);
        int customerId = rs.getInt("customer_id");
        order.setCustomerId(rs.wasNull() ? null : customerId);
        order.setStatus(OrderStatus.fromDb(rs.getString("status")));
        order.setSubtotal(Money.fromDong(rs.getLong("subtotal")));
        order.setDiscount(Money.fromDong(rs.getLong("discount")));
        order.setTotal(Money.fromDong(rs.getLong("total")));
        order.setPaymentMethod(PaymentMethod.fromDb(rs.getString("payment_method")));
        String paidAt = rs.getString("paid_at");
        order.setPaidAt(DbTime.parse(paidAt));
        // Every caller selects the whole orders row (SELECT * / o.*), and the column is in the CREATE TABLE and added by a
        // checked migration to older databases, so a failure here is a real error and must not be swallowed (TODO 6.12).
        int tableNumber = rs.getInt("table_number");
        order.setTableNumber(rs.wasNull() ? null : tableNumber);
        try {
            order.setDiscountReason(rs.getString("discount_reason"));
        } catch (SQLException ignored) {
            // a query that does not select this column
        }
        int points = rs.getInt("points_earned");
        order.setPointsEarned(rs.wasNull() ? null : points);
        int redeemed = rs.getInt("points_redeemed");
        order.setPointsRedeemed(rs.wasNull() ? null : redeemed);
        long redeemedValue = rs.getLong("points_value");
        order.setPointsValue(rs.wasNull() ? null : Money.fromDong(redeemedValue));
        long cashReceived = rs.getLong("cash_received");
        order.setCashReceived(Money.fromDongOrNull(cashReceived, rs.wasNull()));
        long changeGiven = rs.getLong("change_given");
        order.setChangeGiven(Money.fromDongOrNull(changeGiven, rs.wasNull()));
        order.setTransferRef(rs.getString("transfer_ref"));
        try {
            long cashPart = rs.getLong("cash_part");
            order.setCashPart(Money.fromDongOrNull(cashPart, rs.wasNull()));
        } catch (SQLException ignored) {
            // a query that does not select the split payment (TODO 4.32)
        }
        return order;
    }

    /** True when the product exists but was switched off ("ngừng bán"): the sale is refused, with a clearer message (TODO 3.34). */
    private static boolean isSwitchedOff(Connection connection, int productId) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement("SELECT active FROM products WHERE id = ?")) {
            st.setInt(1, productId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() && rs.getInt(1) == 0;
            }
        }
    }
}
