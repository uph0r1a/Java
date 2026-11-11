package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import java.util.Optional;

/**
 * Who may cancel an order, decided in one place (TODO 6.21): a PAID order is refunded as a whole only by an administrator, and an
 * open take-away order is thrown away only by an administrator or the cashier who opened it (the same rule as emptying a table,
 * {@link TablePolicy}). The take-away rule is checked against the order the database holds now, not against a till screen that may
 * be old. Meant to be called from a background thread (TODO 6.14).
 */
public class OrderCancelService {

    private final OrderDAO orderDAO;

    public OrderCancelService() {
        this(new OrderDAO());
    }

    OrderCancelService(OrderDAO orderDAO) {
        this.orderDAO = orderDAO;
    }

    /** Whether the signed-in person may refund a paid order as a whole. No database access: a screen uses it to refuse before it asks. */
    public static boolean mayCancelPaid() {
        return Session.isAdmin();
    }

    /**
     * Refunds a paid order as a whole (see {@link OrderDAO#cancelPaidOrder(int, boolean, String)}): stock, loyalty points, voucher and
     * gift card are put back in one transaction.
     *
     * @throws Permissions.PermissionDeniedException if the signed-in person is not an administrator
     */
    public void cancelPaid(int orderId, boolean returnToStock, String reason) {
        Permissions.requireAdmin("refund a paid order");
        orderDAO.cancelPaidOrder(orderId, returnToStock, reason);
    }

    /**
     * Cancels the open take-away order {@code orderId}. Returns false when there was nothing to cancel (it was paid or cancelled
     * on another till in the meantime).
     *
     * @throws Permissions.PermissionDeniedException if it is another cashier's order and the signed-in person is not an administrator
     */
    public boolean cancelTakeAway(int orderId) {
        Optional<Order> open = orderDAO.findOpenTakeAway().stream().filter(o -> o.getId() == orderId).findFirst();
        if (open.isEmpty()) return false;
        if (!TableService.mayEmpty(open.get())) {
            SecurityLog.warnLimited("takeaway-cancel:" + SecurityLog.actor() + ":" + orderId,
                    SecurityLog.actor() + " tried to cancel take-away order " + orderId + " that another cashier opened");
            throw new Permissions.PermissionDeniedException(TablePolicy.refusedMessage());
        }
        return orderDAO.cancelOpenTakeAway(orderId);
    }
}
