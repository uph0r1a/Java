package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Role;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pays one order: finds or creates the customer typed in at the till, saves the paid order with its lines and frees the
 * table. This was the body of {@code OrderController.completeOrder}; it now lives here so it can be tested without JavaFX
 * (TODO 6.5). It touches no screen and no {@code Session}: who is signed in is passed as {@code employeeId} and
 * {@code actingRole}.
 *
 * <p>It is also where the discount rule is enforced (TODO 6.45): {@link DiscountPolicy#require} runs before anything is
 * written, against a subtotal worked out from the order lines, not the figure the screen sent. The payment dialog checks the
 * same rule first only to give the cashier an immediate message.
 */
public class CheckoutService {

    /**
     * What was agreed at the payment dialog. {@code cashReceived} is what a cash customer handed over (null when not asked or not
     * cash) and {@code transferRef} the bank's reference typed for a transfer (null when none); both are optional and are saved
     * with the order (TODO 3.17, 4.31). The change is worked out from them when the order is saved.
     */
    public record Payment(PaymentMethod method, BigDecimal discount, String discountReason, BigDecimal total,
                          BigDecimal cashReceived, String transferRef, int pointsRedeemed, BigDecimal cashPart) {
        /** A payment that spends no points and is not split. */
        public Payment(PaymentMethod method, BigDecimal discount, String discountReason, BigDecimal total,
                       BigDecimal cashReceived, String transferRef) {
            this(method, discount, discountReason, total, cashReceived, transferRef, 0, null);
        }

        /** A payment that spends points and is not split. */
        public Payment(PaymentMethod method, BigDecimal discount, String discountReason, BigDecimal total,
                       BigDecimal cashReceived, String transferRef, int pointsRedeemed) {
            this(method, discount, discountReason, total, cashReceived, transferRef, pointsRedeemed, null);
        }

        /** Paid with two methods (TODO 4.32): {@code cashPart} in cash, the rest with {@code method}; no points spent. */
        public Payment(PaymentMethod method, BigDecimal discount, String discountReason, BigDecimal total,
                       BigDecimal cashReceived, String transferRef, BigDecimal cashPart) {
            this(method, discount, discountReason, total, cashReceived, transferRef, 0, cashPart);
        }

        /** A payment without the optional details. */
        public Payment(PaymentMethod method, BigDecimal discount, String discountReason, BigDecimal total) {
            this(method, discount, discountReason, total, null, null, 0, null);
        }
    }

    /** The saved order and the customer it ended up linked to (null for a sale without a customer). */
    public record PaidOrder(Order order, Customer customer) {
    }

    private final OrderDAO orderDAO;
    private final TableDAO tableDAO;
    private final CustomerService customerService;

    public CheckoutService() {
        this(new OrderDAO(), new TableDAO(), new CustomerService());
    }

    public CheckoutService(OrderDAO orderDAO, TableDAO tableDAO, CustomerService customerService) {
        this.orderDAO = orderDAO;
        this.tableDAO = tableDAO;
        this.customerService = customerService;
    }

    /**
     * @param tableNumber the table, or null for a take-away order that has none (TODO 4.25): nothing is freed then
     * (A {@code cashPart} in the payment makes it a two-method payment: that much cash, the rest with the method, TODO 4.32.)
     * @param openOrder the order already saved for this table while it was being taken, or null if there is none yet
     * @param knownCustomer a customer already attached on screen, or null (then {@code typedName}/{@code typedPhone} are used)
     * @param employeeId who is selling, or null
     * @param actingRole the signed-in person's role, read on the screen thread; it decides how much discount is allowed
     *                   (null is treated as an ordinary employee)
     * @throws DataAccessException if the discount is not allowed for that role, or the payment cannot be saved; in both
     *                             cases nothing is saved and no customer is left behind
     */
    public PaidOrder pay(Integer tableNumber, List<OrderItem> items, BigDecimal subtotal, Customer knownCustomer,
                         String typedName, String typedPhone, Order openOrder, Payment payment, Integer employeeId,
                         Role actingRole) {
        // First, before a customer is found or created: a refused discount must change nothing.
        DiscountPolicy.require(actingRole, subtotalOf(items), payment.discount(), payment.discountReason());
        // The reason is saved the way the policy defines it (trimmed; none when there is no discount), whatever the caller sent.
        final String manualReason = DiscountPolicy.reasonToStore(payment.discount(), payment.discountReason());
        // Points spent (TODO 4.16, rule D5): checked against the customer and the bill before anything is written. The discount rule
        // above applies to the cashier's own discount only; points are the customer's, not the cashier's to give. What they are worth
        // is added to the discount that is saved, so the order still adds up (total = subtotal - discount).
        final LoyaltyPolicy.Redemption redemption = LoyaltyPolicy.redemptionOf(payment.pointsRedeemed());
        if (!redemption.isNone()) {
            if (knownCustomer == null) {
                throw new DataAccessException(com.coffeeshop.coffeeshopmanagement.util.I18n.t("pos.points.nocustomer"));
            }
            String problem = LoyaltyPolicy.redemptionProblem(redemption.points(), knownCustomer.getLoyaltyPoints(),
                    subtotalOf(items).subtract(payment.discount()));
            if (problem != null) {
                throw new DataAccessException(problem);
            }
        } else if (payment.pointsRedeemed() < 0) {
            throw new DataAccessException(com.coffeeshop.coffeeshopmanagement.util.I18n.t("pos.points.invalid"));
        }
        final BigDecimal savedDiscount = payment.discount().add(redemption.value());
        final String discountReason = LoyaltyPolicy.reasonWithPoints(manualReason, redemption.points());
        // A customer typed in at the till is found or created here, then linked to the
        // order inside the payment transaction so the loyalty points reach them.
        CustomerService.Resolved resolved = knownCustomer != null
                ? new CustomerService.Resolved(knownCustomer, false) : customerService.resolve(typedName, typedPhone);
        Customer customer = resolved.customer();
        Integer customerId = customer != null ? customer.getId() : null;
        Order savedOrder;
        try {
            if (openOrder != null && openOrder.getId() > 0) {
                // Also frees the table, in the same transaction.
                savedOrder = orderDAO.payExistingOrder(openOrder.getId(), customerId,
                        payment.method(), savedDiscount, discountReason, payment.total(), items, employeeId,
                        payment.cashReceived(), payment.transferRef(), redemption, payment.cashPart());
            } else {
                Order order = new Order();
                order.setOrderDate(AppClock.now());
                order.setEmployeeId(employeeId);
                order.setCustomerId(customerId);
                order.setStatus(OrderStatus.PAID);
                order.setSubtotal(subtotal);
                order.setDiscount(savedDiscount);
                order.setDiscountReason(discountReason);
                order.setPointsRedeemed(redemption.isNone() ? null : redemption.points());
                order.setPointsValue(redemption.isNone() ? null : redemption.value());
                order.setTotal(payment.total());
                order.setPaymentMethod(payment.method());
                order.setCashReceived(payment.cashReceived());
                order.setTransferRef(payment.transferRef());
                order.setCashPart(payment.cashPart());
                order.setPaidAt(AppClock.now());
                order.setTableNumber(tableNumber);
                savedOrder = orderDAO.insert(order, items);
                if (tableNumber != null) tableDAO.setEmpty(tableNumber);
            }
        } catch (RuntimeException failed) {
            // A customer made only for this payment must not stay behind when the payment fails (TODO 3.27).
            if (resolved.created()) customerService.discardIfUnused(customer);
            throw failed;
        }
        return new PaidOrder(savedOrder, customer);
    }

    /** What the lines add up to (unit price x quantity), the same figure the database layer checks and saves as the subtotal. */
    private static BigDecimal subtotalOf(List<OrderItem> items) {
        BigDecimal sum = BigDecimal.ZERO;
        if (items == null) {
            return sum;
        }
        for (OrderItem item : items) {
            if (item.getUnitPrice() != null) {
                sum = sum.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }
        return sum;
    }
}
