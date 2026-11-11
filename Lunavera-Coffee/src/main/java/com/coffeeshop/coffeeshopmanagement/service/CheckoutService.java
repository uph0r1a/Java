package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.dao.ComplimentaryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import com.coffeeshop.coffeeshopmanagement.dao.VoucherDAO;
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
                          BigDecimal cashReceived, String transferRef, int pointsRedeemed, BigDecimal cashPart, String voucherCode,
                          List<com.coffeeshop.coffeeshopmanagement.model.PaymentLine> lines, java.time.LocalDateTime pricedAt, boolean birthday,
                          ComplimentaryRules.Give complimentary) {
        public Payment {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }

        /** Every field but the free drink (TODO 4.61): an ordinary payment, the shape all the older callers use. */
        public Payment(PaymentMethod method, BigDecimal discount, String discountReason, BigDecimal total,
                       BigDecimal cashReceived, String transferRef, int pointsRedeemed, BigDecimal cashPart, String voucherCode,
                       List<com.coffeeshop.coffeeshopmanagement.model.PaymentLine> lines, java.time.LocalDateTime pricedAt, boolean birthday) {
            this(method, discount, discountReason, total, cashReceived, transferRef, pointsRedeemed, cashPart, voucherCode, lines, pricedAt, birthday, null);
        }

        /**
         * A bill closed with no money (TODO 4.61, rule D26): the method is {@link PaymentMethod#COMPLIMENTARY}, nothing is discounted, spent or
         * split, and {@code give} says what it is for. {@link CheckoutService} saves it through {@code payComplimentary}.
         */
        public static Payment free(ComplimentaryRules.Give give) {
            return new Payment(PaymentMethod.COMPLIMENTARY, BigDecimal.ZERO, null, BigDecimal.ZERO, null, null, 0, null, null, null, null, false, give);
        }

        /** Without a time the payment window was opened at: the automatic promotions are worked out at the moment of paying. */
        public Payment(PaymentMethod method, BigDecimal discount, String discountReason, BigDecimal total,
                       BigDecimal cashReceived, String transferRef, int pointsRedeemed, BigDecimal cashPart, String voucherCode,
                       List<com.coffeeshop.coffeeshopmanagement.model.PaymentLine> lines) {
            this(method, discount, discountReason, total, cashReceived, transferRef, pointsRedeemed, cashPart, voucherCode, lines, null, false);
        }

        /** The same payment, priced at the moment the payment window was opened (TODO 4.55). */
        public Payment withPricedAt(java.time.LocalDateTime openedAt) {
            return new Payment(method, discount, discountReason, total, cashReceived, transferRef, pointsRedeemed, cashPart, voucherCode,
                    lines, openedAt, birthday, complimentary);
        }

        /**
         * The same payment, marked as the customer's birthday discount (TODO 3.66, rule D25 of {@link BirthdayRules}): the typed discount is
         * the birthday one, and paying records that it was given today. The service checks that the discount really is that amount.
         */
        public Payment withBirthday() {
            return new Payment(method, discount, discountReason, total, cashReceived, transferRef, pointsRedeemed, cashPart, voucherCode,
                    lines, pricedAt, true, complimentary);
        }

        /** A payment with the cash part given the first way (cash plus {@code method}), points and a voucher, but no list of lines. */
        public Payment(PaymentMethod method, BigDecimal discount, String discountReason, BigDecimal total,
                       BigDecimal cashReceived, String transferRef, int pointsRedeemed, BigDecimal cashPart, String voucherCode) {
            this(method, discount, discountReason, total, cashReceived, transferRef, pointsRedeemed, cashPart, voucherCode, null);
        }

        /** Paid with several methods (TODO 4.32, D4): {@code lines} say what each paid; {@code method} is the main one (not cash). */
        public Payment(PaymentMethod method, BigDecimal discount, String discountReason, BigDecimal total,
                       BigDecimal cashReceived, String transferRef, int pointsRedeemed, String voucherCode,
                       List<com.coffeeshop.coffeeshopmanagement.model.PaymentLine> lines) {
            this(method, discount, discountReason, total, cashReceived, transferRef, pointsRedeemed, null, voucherCode, lines);
        }

        /** A payment with points and a split but no voucher. */
        public Payment(PaymentMethod method, BigDecimal discount, String discountReason, BigDecimal total,
                       BigDecimal cashReceived, String transferRef, int pointsRedeemed, BigDecimal cashPart) {
            this(method, discount, discountReason, total, cashReceived, transferRef, pointsRedeemed, cashPart, null);
        }

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
        return pay(tableNumber, items, subtotal, knownCustomer, typedName, typedPhone, openOrder, payment, employeeId, actingRole, null);
    }

    /**
     * Pays only some pieces of an open order (TODO 4.10, rule D10; the rules are in {@link SplitBillRules}): {@code part} is paid now as
     * a new paid order with the payment's discount, voucher and points, and in the same transaction the open order {@code fromOrder}
     * keeps only {@code remainder} and its table stays taken. Everything else (the discount rule, vouchers, points, the cash part) is
     * checked exactly as in {@link #pay}.
     */
    public PaidOrder paySplit(int tableNumber, Order fromOrder, List<OrderItem> part, List<OrderItem> remainder, Customer knownCustomer,
                              String typedName, String typedPhone, Payment payment, Integer employeeId, Role actingRole) {
        if (fromOrder == null || fromOrder.getId() <= 0 || remainder == null || remainder.isEmpty() || part == null || part.isEmpty()) {
            throw new DataAccessException(com.coffeeshop.coffeeshopmanagement.util.I18n.t("split.val.nolines"));
        }
        return pay(tableNumber, part, SplitBillRules.sum(part), knownCustomer, typedName, typedPhone, null, payment, employeeId, actingRole,
                new SplitOf(fromOrder.getId(), fromOrder.getRev(), remainder));
    }

    /** The open order a split comes from and the lines that stay on it. */
    private record SplitOf(int fromOrderId, int fromRev, List<OrderItem> remainder) {
    }

    private PaidOrder pay(Integer tableNumber, List<OrderItem> items, BigDecimal subtotal, Customer knownCustomer,
                          String typedName, String typedPhone, Order openOrder, Payment payment, Integer employeeId,
                          Role actingRole, SplitOf split) {
        if (payment.complimentary() != null || payment.method() == PaymentMethod.COMPLIMENTARY) { // TODO 4.61: closed with no money
            return payComplimentary(tableNumber, items, openOrder, payment, employeeId, actingRole, split);
        }
        // First, before a customer is found or created: a refused discount must change nothing.
        DiscountPolicy.require(actingRole, subtotalOf(items), payment.discount(), payment.discountReason());
        // The reason is saved the way the policy defines it (trimmed; none when there is no discount), whatever the caller sent.
        final String manualReason = DiscountPolicy.reasonToStore(payment.discount(), payment.discountReason());
        // Automatic promotions (TODO 4.55): worked out here from the shop's own list and the clock, never taken from the screen, and
        // written on the lines. They come off first: the cashier's limit above is on the price before them, but the cashier's discount,
        // a voucher and points can only take what is still left to pay.
        // At the moment the payment window was opened (checked to be recent), so what the window showed is what is charged.
        final AutoPromotionService.Result promotions = AutoPromotionService.evaluate(items,
                AutoPromotionService.pricingInstant(payment.pricedAt(), AppClock.now()));
        promotions.writeOnto(items);
        final BigDecimal payable = subtotalOf(items).subtract(promotions.value());
        if (payment.discount().compareTo(payable) > 0) {
            throw new DataAccessException(com.coffeeshop.coffeeshopmanagement.util.I18n.t("discount.problem.abovesubtotal"));
        }
        // The birthday discount (TODO 3.66, rule D25): needs a customer already attached, and the discount must be exactly what the
        // birthday button fills in. Whether it is their birthday and whether it was already given today is settled inside the
        // payment's transaction, against the database, so two tills cannot both give it.
        if (payment.birthday()) {
            String birthdayProblem = BirthdayRules.problemToUse(knownCustomer == null ? null : knownCustomer.getId(), subtotalOf(items), payment.discount());
            if (birthdayProblem != null) {
                throw new DataAccessException(birthdayProblem);
            }
        }
        // A voucher (TODO 4.11, rule D6): looked up and checked here, never trusted from the screen. It is the shop's gift, so the
        // cashier's limit does not apply to it; what it takes off is worked out here and the order must add up with it.
        VoucherRules.Use voucherUse = VoucherRules.Use.NONE;
        if (payment.voucherCode() != null && !payment.voucherCode().isBlank()) {
            com.coffeeshop.coffeeshopmanagement.model.Voucher voucher = new VoucherDAO().findByCode(payment.voucherCode()).orElse(null);
            VoucherRules.Problem voucherProblem = VoucherRules.check(voucher, payable, AppClock.today());
            if (voucherProblem != VoucherRules.Problem.OK) {
                throw new DataAccessException(VoucherRules.message(voucherProblem, voucher));
            }
            voucherUse = new VoucherRules.Use(voucher.code(), VoucherRules.discountFor(voucher, payable, payment.discount()));
        }
        final VoucherRules.Use voucherApplied = voucherUse.isNone() ? VoucherRules.Use.NONE : voucherUse;
        // Points spent (TODO 4.16, rule D5): checked against the customer and the bill before anything is written. The discount rule
        // above applies to the cashier's own discount only; points are the customer's, not the cashier's to give. What they are worth
        // is added to the discount that is saved, so the order still adds up (total = subtotal - discount).
        final LoyaltyPolicy.Redemption redemption = LoyaltyPolicy.redemptionOf(payment.pointsRedeemed());
        if (!redemption.isNone()) {
            if (knownCustomer == null) {
                throw new DataAccessException(com.coffeeshop.coffeeshopmanagement.util.I18n.t("pos.points.nocustomer"));
            }
            String problem = LoyaltyPolicy.redemptionProblem(redemption.points(), knownCustomer.getLoyaltyPoints(),
                    payable.subtract(payment.discount()).subtract(voucherApplied.value()));
            if (problem != null) {
                throw new DataAccessException(problem);
            }
        } else if (payment.pointsRedeemed() < 0) {
            throw new DataAccessException(com.coffeeshop.coffeeshopmanagement.util.I18n.t("pos.points.invalid"));
        }
        final BigDecimal savedDiscount = payment.discount().add(voucherApplied.value()).add(redemption.value()).add(promotions.value());
        final String discountReason = joinedReason(manualReason, voucherApplied.isNone() ? null : voucherApplied.code(), redemption.points(), promotions.names());
        // A customer typed in at the till is found or created here, then linked to the
        // order inside the payment transaction so the loyalty points reach them.
        CustomerService.Resolved resolved = knownCustomer != null
                ? new CustomerService.Resolved(knownCustomer, false) : customerService.resolve(typedName, typedPhone);
        Customer customer = resolved.customer();
        Integer customerId = customer != null ? customer.getId() : null;
        if (!voucherApplied.isNone()) { // TODO 4.54: a once-per-customer code is refused here, before anything is written (the payment checks again)
            com.coffeeshop.coffeeshopmanagement.model.Voucher once = new VoucherDAO().findByCode(voucherApplied.code()).orElse(null);
            VoucherRules.Problem customerProblem = VoucherRules.checkCustomer(once, customerId,
                    once != null && once.oncePerCustomer() && customerId != null && new VoucherDAO().usedBy(once.code(), customerId));
            if (customerProblem != VoucherRules.Problem.OK) throw new DataAccessException(VoucherRules.message(customerProblem, once));
        }
        Order savedOrder;
        try {
            if (openOrder != null && openOrder.getId() > 0) {
                // Also frees the table, in the same transaction.
                savedOrder = orderDAO.payExistingOrder(openOrder.getId(), customerId,
                        payment.method(), savedDiscount, discountReason, payment.total(), items, employeeId,
                        payment.cashReceived(), payment.transferRef(), redemption, payment.cashPart(), payment.lines(), voucherApplied,
                        openOrder.getRev(), payment.birthday()); // 2.91: refused when another till saved the order since this screen loaded it; 3.66: the birthday discount
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
                order.setVoucherCode(voucherApplied.isNone() ? null : voucherApplied.code());
                order.setVoucherValue(voucherApplied.isNone() ? null : voucherApplied.value());
                order.setTotal(payment.total());
                order.setPaymentMethod(payment.method());
                order.setCashReceived(payment.cashReceived());
                order.setTransferRef(payment.transferRef());
                order.setCashPart(payment.cashPart());
                order.setPaymentLines(payment.lines());
                order.setBirthdayDiscount(payment.birthday());
                order.setPaidAt(AppClock.now());
                order.setTableNumber(tableNumber);
                if (split != null) {
                    // The part is paid and the rest stays open, in one transaction; the table is not freed (TODO 4.10).
                    savedOrder = orderDAO.insert(order, items, (connection, newId) ->
                            com.coffeeshop.coffeeshopmanagement.dao.TableMoveDAO.keepRemainder(connection, split.fromOrderId(), split.fromRev(), split.remainder(),
                                    newId, payment.total(), tableNumber));
                } else {
                    savedOrder = orderDAO.insert(order, items);
                    if (tableNumber != null) tableDAO.setEmpty(tableNumber);
                }
            }
        } catch (RuntimeException failed) {
            // A customer made only for this payment must not stay behind when the payment fails (TODO 3.27).
            if (resolved.created()) customerService.discardIfUnused(customer);
            throw failed;
        }
        return new PaidOrder(savedOrder, customer);
    }

    /**
     * Closes a bill with no money (TODO 4.61, rule D26; the rules are in {@link ComplimentaryRules}). Nothing else may be on the bill, and the
     * customer attached at the till is left out (a staff drink earns no points and is not a visit). The lines are saved at 0, so revenue, the
     * discounts and the average bill are untouched; stock is taken as for any sale; the person's limit for the day is checked again, against the
     * database, inside the payment's transaction ({@link ComplimentaryDAO#record}), together with the marking and the audit line.
     */
    private PaidOrder payComplimentary(Integer tableNumber, List<OrderItem> items, Order openOrder, Payment payment, Integer employeeId,
                                       Role actingRole, SplitOf split) {
        final ComplimentaryRules.Give give = payment.complimentary();
        final boolean somethingElse = split != null || give == null || payment.method() != PaymentMethod.COMPLIMENTARY
                || payment.discount() == null || payment.discount().signum() != 0 || payment.pointsRedeemed() != 0 || payment.birthday()
                || payment.cashPart() != null || !payment.lines().isEmpty() || (payment.voucherCode() != null && !payment.voucherCode().isBlank());
        if (somethingElse) {
            throw new DataAccessException(com.coffeeshop.coffeeshopmanagement.util.I18n.t("complimentary.problem.only"));
        }
        final int cups = ComplimentaryRules.cups(items);
        final BigDecimal listValue = ComplimentaryRules.listValue(items);
        // The checks that need no database, first, so a refused request changes nothing; the limit is checked inside the transaction.
        ComplimentaryRules.Problem early = ComplimentaryRules.check(actingRole, give, cups, 0, ComplimentaryRules.cashierCupsPerDay());
        if (early != ComplimentaryRules.Problem.OK && early != ComplimentaryRules.Problem.OVER_LIMIT) {
            throw new DataAccessException(ComplimentaryRules.message(early, null, 0, cups, ComplimentaryRules.cashierCupsPerDay()));
        }
        final List<OrderItem> free = ComplimentaryRules.atNoCharge(items);
        final OrderDAO.InTransaction mark = (connection, orderId) -> ComplimentaryDAO.record(connection, orderId, give, actingRole, cups, listValue);
        if (openOrder != null && openOrder.getId() > 0) {
            // Also frees the table, in the same transaction.
            Order saved = orderDAO.payExistingOrder(openOrder.getId(), null, PaymentMethod.COMPLIMENTARY, BigDecimal.ZERO, null, BigDecimal.ZERO, free,
                    employeeId, null, null, LoyaltyPolicy.Redemption.NONE, null, null, VoucherRules.Use.NONE, openOrder.getRev(), false, mark);
            return new PaidOrder(saved, null);
        }
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setEmployeeId(employeeId);
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        order.setPaymentMethod(PaymentMethod.COMPLIMENTARY);
        order.setPaidAt(AppClock.now());
        order.setTableNumber(tableNumber);
        Order saved = orderDAO.insert(order, free, mark);
        if (tableNumber != null) tableDAO.setEmpty(tableNumber);
        return new PaidOrder(saved, null);
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

    /**
     * The text saved in {@code orders.discount_reason} (VARCHAR(255) on MariaDB, TODO 3.86): the cashier's reason, then the voucher,
     * points and promotion notes. When the whole is longer than the column, the cashier's own reason is shortened first (the notes
     * say what was really taken off the bill), and the result is cut at {@link DiscountPolicy#COLUMN_MAX_LENGTH} as a last resort.
     */
    static String joinedReason(String manualReason, String voucherCode, int points, java.util.List<String> promotionNames) {
        String manual = manualReason;
        String joined = join(manual, voucherCode, points, promotionNames);
        if (joined != null && joined.length() > DiscountPolicy.COLUMN_MAX_LENGTH && manual != null) {
            int excess = joined.length() - DiscountPolicy.COLUMN_MAX_LENGTH;
            manual = AuditRules.clip(manual, Math.max(0, manual.length() - excess));
            joined = join(manual, voucherCode, points, promotionNames);
        }
        return AuditRules.clip(joined, DiscountPolicy.COLUMN_MAX_LENGTH);
    }

    private static String join(String manual, String voucherCode, int points, java.util.List<String> promotionNames) {
        return AutoPromotionService.reasonWithPromotions(
                LoyaltyPolicy.reasonWithPoints(VoucherRules.reasonWithVoucher(manual, voucherCode), points), promotionNames);
    }
}
