package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.PaymentText;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptText;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * TODO 4.32: one order paid with two methods (cash and one other). Every test works on a day of its own far in the future, with the
 * clock fixed there, so the report and shift figures asserted below are exactly those of the orders the test made.
 */
public class SplitPaymentTest {

    private static final AtomicInteger NEXT_DAY = new AtomicInteger(0);

    private final OrderDAO orderDAO = new OrderDAO();
    private final ReportDAO reports = new ReportDAO();
    private final ShiftDAO shifts = new ShiftDAO();

    private LocalDate savedToday;
    private LocalDateTime dayStart;
    private Product product;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void setUp() {
        TestSession.admin();
        savedToday = AppClock.today();
        dayStart = LocalDate.of(2300, 1, 1).plusDays(NEXT_DAY.incrementAndGet() * 3L).atTime(8, 0);
        AppClock.fix(dayStart.plusHours(4));
        Category category = new Category();
        category.setName("test-split-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        product = new Product();
        product.setName("test-split-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("10000"));
        product.setStock(100000);
        product.setActive(true);
        new ProductDAO().insert(product);
    }

    @After
    public void restoreClock() {
        AppClock.pinDate(savedToday);
    }

    private OrderItem line(String total) {
        OrderItem line = new OrderItem();
        line.setProductId(product.getId());
        line.setProductName(product.getName());
        line.setQuantity(1);
        line.setUnitPrice(new BigDecimal(total));
        line.setLineTotal(new BigDecimal(total));
        return line;
    }

    private Order paid(String total, PaymentMethod method, String cashPart, LocalDateTime at) {
        Order order = new Order();
        order.setOrderDate(at);
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(new BigDecimal(total));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal(total));
        order.setPaymentMethod(method);
        order.setPaidAt(at);
        order.setCashPart(cashPart == null ? null : new BigDecimal(cashPart));
        return orderDAO.insert(order, List.of(line(total)));
    }

    private Map<String, Long> lines(int orderId) throws Exception {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement s = c.prepareStatement("SELECT method, amount FROM order_payments WHERE order_id = ?")) {
            s.setInt(1, orderId);
            try (ResultSet rs = s.executeQuery()) {
                Map<String, Long> out = new java.util.TreeMap<>();
                while (rs.next()) out.put(rs.getString(1), rs.getLong(2));
                return out;
            }
        }
    }

    @Test
    public void aSplitOrderHasOneLinePerMethodAndTheLinesAddUpToTheTotal() throws Exception {
        Order saved = paid("100000", PaymentMethod.TRANSFER, "30000", dayStart.plusHours(1));
        assertEquals(Map.of("CASH", 30000L, "TRANSFER", 70000L), lines(saved.getId()));
        Order read = orderDAO.findById(saved.getId()).orElseThrow();
        assertTrue(read.isSplitPayment());
        assertEquals(0, new BigDecimal("30000").compareTo(read.getCashPart()));
        assertEquals(PaymentMethod.TRANSFER, read.getPaymentMethod());
    }

    @Test
    public void anOrderPaidWithOneMethodHasNoLines() throws Exception {
        Order saved = paid("100000", PaymentMethod.CASH, null, dayStart.plusHours(1));
        assertTrue(lines(saved.getId()).isEmpty());
        assertFalse(orderDAO.findById(saved.getId()).orElseThrow().isSplitPayment());
    }

    @Test
    public void anOpenOrderPaidWithTwoMethodsGetsItsLinesInTheSameTransaction() throws Exception {
        Order open = new Order();
        open.setOrderDate(dayStart.plusHours(1));
        open.setTableNumber(null);
        open.setStatus(OrderStatus.OPEN);
        open.setSubtotal(BigDecimal.ZERO);
        open.setDiscount(BigDecimal.ZERO);
        open.setTotal(BigDecimal.ZERO);
        open = orderDAO.saveOrUpdateOpenOrder(open, List.of());
        Order paid = orderDAO.payExistingOrder(open.getId(), null, PaymentMethod.MOMO, BigDecimal.ZERO, null,
                new BigDecimal("80000"), List.of(line("80000")), null, null, "REF1", null, new BigDecimal("50000"));
        assertEquals(Map.of("CASH", 50000L, "MOMO", 30000L), lines(paid.getId()));
        assertEquals(0, new BigDecimal("50000").compareTo(paid.getCashPart()));
        assertEquals("REF1", paid.getTransferRef());
    }

    @Test
    public void aBadSplitIsRefusedAndNothingIsSaved() throws Exception {
        String[][] bad = {{"0"}, {"100000"}, {"150000"}, {"-5"}, {"1000.5"}};
        for (String[] cashPart : bad) {
            try {
                paid("100000", PaymentMethod.TRANSFER, cashPart[0], dayStart.plusHours(1));
                fail("cash part " + cashPart[0] + " should be refused");
            } catch (DataAccessException expected) {
                // fine
            }
        }
        try {
            paid("100000", PaymentMethod.CASH, "30000", dayStart.plusHours(1)); // cash plus cash is not two methods
            fail();
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(0, reports.paymentSplit(dayStart.toLocalDate(), dayStart.toLocalDate()).size());
    }

    @Test
    public void theReportCountsEachMethodWithItsOwnAmountAndTheTotalsStillAddUp() {
        paid("100000", PaymentMethod.TRANSFER, "30000", dayStart.plusHours(1));   // 30 000 cash + 70 000 transfer
        paid("50000", PaymentMethod.CASH, null, dayStart.plusHours(2));           // 50 000 cash
        paid("20000", PaymentMethod.CARD, null, dayStart.plusHours(3));           // 20 000 card
        LocalDate day = dayStart.toLocalDate();
        Map<PaymentMethod, ReportDAO.PaymentSplit> byMethod = reports.paymentSplit(day, day).stream()
                .collect(Collectors.toMap(ReportDAO.PaymentSplit::method, x -> x));
        assertEquals(0, new BigDecimal("80000").compareTo(byMethod.get(PaymentMethod.CASH).revenue()));
        assertEquals(2, byMethod.get(PaymentMethod.CASH).orders());
        assertEquals(0, new BigDecimal("70000").compareTo(byMethod.get(PaymentMethod.TRANSFER).revenue()));
        assertEquals(0, new BigDecimal("20000").compareTo(byMethod.get(PaymentMethod.CARD).revenue()));
        BigDecimal sum = byMethod.values().stream().map(ReportDAO.PaymentSplit::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals("by method adds up to the revenue of the three orders", 0, new BigDecimal("170000").compareTo(sum));
    }

    @Test
    public void theShiftsCashIsOnlyTheCashLine() {
        paid("100000", PaymentMethod.TRANSFER, "30000", dayStart.plusHours(1));
        paid("50000", PaymentMethod.CASH, null, dayStart.plusHours(2));
        paid("20000", PaymentMethod.MOMO, null, dayStart.plusHours(3));
        ShiftDAO.Summary s = shifts.summarize(dayStart, dayStart.plusHours(4));
        assertEquals(3, s.paidOrders());
        assertEquals(0, new BigDecimal("170000").compareTo(s.revenue()));
        assertEquals("30 000 of the split and the 50 000 cash sale", 0, new BigDecimal("80000").compareTo(s.cashSales()));
        LocalDate day = dayStart.toLocalDate();
        BigDecimal cashByPerson = reports.employeeSales(day, day).stream().map(ShiftDAO.EmployeeShare::cash)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("80000").compareTo(cashByPerson));
    }

    @Test
    public void aRefundedSplitOrderLeavesTheReportAndTheDrawer() {
        Order saved = paid("100000", PaymentMethod.TRANSFER, "30000", dayStart.plusHours(1));
        orderDAO.cancelPaidOrder(saved.getId());
        LocalDate day = dayStart.toLocalDate();
        // the sale stays on its own day and the refund cancels it on the same day (TODO 3.13), so every method nets to nothing
        for (ReportDAO.PaymentSplit split : reports.paymentSplit(day, day)) {
            assertEquals(split.method().name(), 0, split.orders());
            assertEquals(split.method().name(), 0, split.revenue().signum());
        }
        assertEquals(0, shifts.summarize(dayStart, dayStart.plusHours(5)).cashSales().signum());
    }

    @Test
    public void theHistoryAndTheReceiptNameBothMethods() {
        Order saved = paid("100000", PaymentMethod.TRANSFER, "30000", dayStart.plusHours(1));
        OrderDAO.OrderSummary row = orderDAO.findHistoryPage(
                new OrderDAO.HistoryFilter(String.valueOf(saved.getId()), null, null, null), 10, 0).get(0);
        assertTrue(row.order().isSplitPayment());
        String names = PaymentText.of(row.order());
        assertTrue(names, names.contains(PaymentMethod.TRANSFER.label()) && names.contains(PaymentMethod.CASH.label()));
        String receipt = ReceiptText.paymentOf(orderDAO.findById(saved.getId()).orElseThrow());
        assertTrue(receipt, receipt.contains("30") && receipt.contains("70"));
        assertEquals(PaymentMethod.CASH.label(), PaymentText.of(paid("1000", PaymentMethod.CASH, null, dayStart.plusHours(2))));
    }

    @Test
    public void theCheckoutServicePassesTheCashPartOnForADirectSaleAndRefusesABadOne() throws Exception {
        com.coffeeshop.coffeeshopmanagement.service.CheckoutService checkout = new com.coffeeshop.coffeeshopmanagement.service.CheckoutService();
        com.coffeeshop.coffeeshopmanagement.service.CheckoutService.Payment payment =
                new com.coffeeshop.coffeeshopmanagement.service.CheckoutService.Payment(PaymentMethod.CARD, BigDecimal.ZERO, null,
                        new BigDecimal("60000"), null, null, new BigDecimal("10000"));
        Order paid = checkout.pay(null, List.of(line("60000")), new BigDecimal("60000"), null, "", "", null, payment, null,
                com.coffeeshop.coffeeshopmanagement.model.Role.ADMIN).order();
        assertEquals(Map.of("CARD", 50000L, "CASH", 10000L), lines(paid.getId()));
        com.coffeeshop.coffeeshopmanagement.service.CheckoutService.Payment bad =
                new com.coffeeshop.coffeeshopmanagement.service.CheckoutService.Payment(PaymentMethod.CARD, BigDecimal.ZERO, null,
                        new BigDecimal("60000"), null, null, new BigDecimal("60000"));
        try {
            checkout.pay(null, List.of(line("60000")), new BigDecimal("60000"), null, "", "", null, bad, null,
                    com.coffeeshop.coffeeshopmanagement.model.Role.ADMIN);
            fail();
        } catch (DataAccessException expected) {
            // nothing saved
        }
    }
}
