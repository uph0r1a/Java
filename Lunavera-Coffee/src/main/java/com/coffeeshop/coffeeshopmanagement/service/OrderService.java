package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * The open order of a table (or of a take-away), without the screen (TODO 6.5): starting it, reading it back when a table is
 * selected, and the autosave that follows every change of the cart. These were the database halves of
 * {@code OrderController.startOrderForTable}, {@code loadTableOrderFromDb} and {@code syncOpenOrderToDb}; the controller keeps
 * the labels, the fields and the thread hand-over. Paying the order is {@link CheckoutService}.
 *
 * <p>Like {@link CheckoutService} it touches no {@code Session}: who is selling is passed as {@code employeeId}, read on the
 * screen thread.
 */
public class OrderService {

    /** What a selected table shows: its open order with the lines and the customer on it, or all three empty. */
    public record TableOrder(Order order, List<OrderItem> items, Customer customer) {
        public static final TableOrder NONE = new TableOrder(null, List.of(), null);
    }

    /** A newly started order and the customer it was linked to (null for a walk-in with no valid phone). */
    public record Started(Order order, Customer customer) {
    }

    private final OrderDAO orderDAO;
    private final CustomerDAO customerDAO;
    private final CustomerService customerService;

    public OrderService() {
        this(new OrderDAO(), new CustomerDAO(), new CustomerService());
    }

    public OrderService(OrderDAO orderDAO, CustomerDAO customerDAO, CustomerService customerService) {
        this.orderDAO = orderDAO;
        this.customerDAO = customerDAO;
        this.customerService = customerService;
    }

    /**
     * The open order of {@code tableNumber}, or, when that is null, of the take-away order {@code takeAwayOrderId} (none when that
     * is null too, or when it is no longer open). Reads the lines and the customer of the order it finds.
     */
    public TableOrder load(Integer tableNumber, Integer takeAwayOrderId) {
        Optional<Order> open;
        if (tableNumber != null) {
            open = orderDAO.findOpenOrderByTable(tableNumber);
        } else if (takeAwayOrderId == null) {
            open = Optional.empty();
        } else {
            open = orderDAO.findOpenTakeAway().stream().filter(o -> o.getId() == takeAwayOrderId).findFirst();
        }
        if (open.isEmpty()) {
            return TableOrder.NONE;
        }
        Order order = open.get();
        List<OrderItem> items = orderDAO.findItemsByOrderId(order.getId());
        Customer customer = order.getCustomerId() == null ? null : customerDAO.findById(order.getCustomerId()).orElse(null);
        return new TableOrder(order, items, customer);
    }

    /**
     * Starts a new OPEN order for the table ({@code tableNumber} null: a take-away order with no table, TODO 4.25). The customer typed
     * in at the till is found or created first, then the order is saved, which also marks the table taken.
     */
    public Started start(Integer tableNumber, String typedName, String typedPhone, Integer employeeId) {
        return start(tableNumber, null, typedName, typedPhone, employeeId);
    }

    /** As above, for an order with no table started as {@code channel} (take-away or a delivery app, TODO 4.48); ignored for a table. */
    public Started start(Integer tableNumber, com.coffeeshop.coffeeshopmanagement.model.OrderChannel channel, String typedName,
                         String typedPhone, Integer employeeId) {
        Customer customer = customerService.findOrCreateByPhone(typedName, typedPhone);
        Order opened = newOpenOrder(tableNumber, customer != null ? customer.getId() : null, employeeId);
        opened.setChannel(com.coffeeshop.coffeeshopmanagement.model.OrderChannel.of(channel, tableNumber));
        Order saved = orderDAO.insert(opened, List.of());
        return new Started(saved, customer);
    }

    /** The empty open order that {@link #start} saves: no lines, no discount, dated with the same clock as {@code paid_at} (TODO 6.50). */
    static Order newOpenOrder(Integer tableNumber, Integer customerId, Integer employeeId) {
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setEmployeeId(employeeId);
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        order.setTableNumber(tableNumber);
        return order;
    }

    /**
     * Puts the cart's figures and the attached customer on the open order, before it is autosaved. The order object is the one the
     * screen holds, so the controller calls this on the screen thread and hands the same order to {@link #saveIfStillOpen}.
     */
    public static void applyCart(Order order, BigDecimal subtotal, Customer attached) {
        order.setSubtotal(subtotal);
        order.setTotal(subtotal);
        order.setCustomerId(attached != null ? attached.getId() : null);
    }

    /**
     * The autosave: brings the saved lines to {@code items}. False (nothing written) when the order was paid or cancelled somewhere
     * else in the meantime, so the screen can say so at once (TODO 3.28).
     */
    public boolean saveIfStillOpen(Order order, List<OrderItem> items) {
        return orderDAO.updateIfStillOpen(order, items);
    }

    /** The customer's last paid order with the lines still theirs, for "the same as last time" (TODO 4.56); empty when there is none. */
    public java.util.Optional<OrderDAO.LastOrder> lastOrderOf(int customerId) {
        return orderDAO.lastPaidOrder(customerId);
    }

    /**
     * The autosave of the sales screen (TODO 3.1): {@link #saveIfStillOpen}, and a line that grows past what the other open orders
     * leave free is refused, nothing written, with the products named in the result ({@link StockHolds}).
     */
    public OrderDAO.SaveResult autosave(Order order, List<OrderItem> items) {
        return orderDAO.updateGuarded(order, items);
    }
}
