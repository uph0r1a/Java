package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.util.ExportCsv;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.4 (purchase history of one customer), 4.20 (profit), 4.21 (CSV) and 4.30 (revenue by payment method). */
public class Section4ReportsTest {

    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final ReportDAO reports = new ReportDAO();

    @BeforeClass
    public static void setUp() {
        TestDatabaseSupport.ensureReady();
        TestSession.admin();
    }

    @AfterClass
    public static void tearDown() {
        TestSession.clear();
    }

    /**
     * A day of its own for each test (TODO 2.90): every test class of a run shares one database, so two tests that use the same day
     * count each other's orders. Far in the past, ten days apart, so a test may also use the day after.
     */
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_DAY = new java.util.concurrent.atomic.AtomicInteger();

    private static LocalDate freshDay() {
        return LocalDate.of(2003, 1, 1).plusDays(10L * NEXT_DAY.incrementAndGet());
    }

    private Product product(String cost) {
        Product p = new Product();
        p.setName("S4 " + System.nanoTime());
        p.setPrice(BigDecimal.valueOf(30_000));
        if (cost != null) p.setCost(new BigDecimal(cost));
        p.setStock(100);
        p.setActive(true);
        return productDAO.insert(p);
    }

    @Test
    public void paymentSplitCountsPaidOrdersPerMethod() {
        Product p = product("10000");
        LocalDate day = freshDay();
        Order a = sellOn(day, p, 1, PaymentMethod.CASH, null);
        Order b = sellOn(day, p, 2, PaymentMethod.CASH, null);
        Order c = sellOn(day, p, 1, PaymentMethod.TRANSFER, null);
        List<ReportDAO.PaymentSplit> split = reports.paymentSplit(day, day);
        assertEquals(2, split.size());
        ReportDAO.PaymentSplit cash = split.stream().filter(s -> s.method() == PaymentMethod.CASH).findFirst().orElseThrow();
        assertEquals(2, cash.orders());
        assertEquals(0, BigDecimal.valueOf(90_000).compareTo(cash.revenue()));
        BigDecimal sum = split.stream().map(ReportDAO.PaymentSplit::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, reports.summary(day, day).revenue().compareTo(sum));
        assertNotNull(a); assertNotNull(b); assertNotNull(c);
    }

    private Order sellOn(LocalDate day, Product p, int qty, PaymentMethod method, Integer customerId) {
        OrderItem item = new OrderItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(qty);
        item.setUnitPrice(BigDecimal.valueOf(30_000));
        item.setLineTotal(BigDecimal.valueOf(30_000L * qty));
        Order o = new Order();
        o.setOrderDate(day.atTime(10, 0));
        o.setPaidAt(day.atTime(10, 5));
        o.setStatus(OrderStatus.PAID);
        o.setSubtotal(item.getLineTotal());
        o.setDiscount(BigDecimal.ZERO);
        o.setTotal(item.getLineTotal());
        o.setPaymentMethod(method);
        o.setCustomerId(customerId);
        return orderDAO.insert(o, List.of(item));
    }

    @Test
    public void profitUsesTheProductCostAndSaysWhenACostIsMissing() {
        LocalDate day = freshDay();
        Product withCost = product("12000");
        Product noCost = product(null);
        sellOn(day, withCost, 3, PaymentMethod.CASH, null); // 90,000 - 3 x 12,000 = 54,000
        sellOn(day, noCost, 1, PaymentMethod.CASH, null);
        ReportDAO.Profit profit = reports.profit(day, day);
        assertEquals(0, BigDecimal.valueOf(54_000).compareTo(profit.grossProfit()));
        assertEquals(1, profit.linesWithCost());
        assertEquals(1, profit.linesWithoutCost());
        List<ReportDAO.ProductSales> top = reports.topProducts(day, day, 10);
        ReportDAO.ProductSales costed = top.stream().filter(t -> t.productName().equals(withCost.getName())).findFirst().orElseThrow();
        ReportDAO.ProductSales uncosted = top.stream().filter(t -> t.productName().equals(noCost.getName())).findFirst().orElseThrow();
        assertEquals(0, BigDecimal.valueOf(54_000).compareTo(costed.profit()));
        assertNull("no cost entered: no profit is shown, not 0", uncosted.profit());
        ReportDAO.DailySales daily = reports.dailySales(day, day).get(0);
        assertEquals(0, BigDecimal.valueOf(54_000).compareTo(daily.profit()));
    }

    /** TODO 3.54: the streaming read gives the same rows, in the same order, as the paged query, and is for admins only. */
    @Test
    public void theStreamingOrderExportGivesTheSameRowsAsThePagedQuery() {
        Product p = product("1000");
        LocalDate day = freshDay();
        for (int i = 0; i < 4; i++) sellOn(day, p, 1, PaymentMethod.CASH, null);
        OrderDAO.HistoryFilter filter = new OrderDAO.HistoryFilter(null, null, day, day);
        List<Integer> streamed = new java.util.ArrayList<>();
        int count = orderDAO.exportOrders(filter, null, row -> streamed.add(row.order().getId()));
        List<Integer> paged = orderDAO.findHistoryPage(filter, 50, 0).stream().map(r -> r.order().getId()).toList();
        assertEquals(4, count);
        assertEquals(paged, streamed);
        TestSession.clear();
        try {
            orderDAO.exportOrders(filter, null, row -> fail("an employee must not read rows for an export"));
            fail("an employee must not export");
        } catch (RuntimeException expected) {
            // refused
        } finally {
            TestSession.admin();
        }
    }

    /** TODO 5.34: the report's CSV lists every product that sold, not only the 20 best sellers shown on screen. */
    @Test
    public void allProductsListsMoreThanTheTopTwenty() {
        LocalDate day = freshDay();
        for (int i = 0; i < 23; i++) sellOn(day, product("1000"), 1, PaymentMethod.CASH, null);
        assertEquals(20, reports.topProducts(day, day, 20).size());
        List<ReportDAO.ProductSales> all = reports.allProducts(day, day);
        assertEquals(23, all.size());
    }

    @Test
    public void aCustomersHistoryHoldsOnlyTheirOrders() {
        Customer mine = new Customer();
        mine.setFullName("S4 khach " + System.nanoTime());
        mine = customerDAO.insert(mine);
        Customer other = new Customer();
        other.setFullName("S4 khac " + System.nanoTime());
        other = customerDAO.insert(other);
        Product p = product("1000");
        LocalDate day = freshDay();
        sellOn(day, p, 1, PaymentMethod.CASH, mine.getId());
        sellOn(day, p, 1, PaymentMethod.CARD, mine.getId());
        sellOn(day, p, 1, PaymentMethod.CASH, other.getId());
        OrderDAO.HistoryFilter filter = OrderDAO.HistoryFilter.NONE.forCustomer(mine.getId());
        assertEquals(2, orderDAO.countHistory(filter));
        assertEquals(2, orderDAO.findHistoryPage(filter, 50, 0).size());
        assertEquals(1, orderDAO.countHistory(OrderDAO.HistoryFilter.NONE.forCustomer(other.getId())));
    }

    @Test
    public void theOrderExportCoversEveryMatchingOrderAndNeedsAnAdmin() {
        Product p = product("1000");
        LocalDate day = freshDay();
        for (int i = 0; i < 3; i++) sellOn(day, p, 1, PaymentMethod.CASH, null);
        OrderDAO.HistoryFilter filter = new OrderDAO.HistoryFilter(null, null, day, day);
        List<OrderDAO.OrderSummary> rows = orderDAO.findAllForExport(filter, null);
        assertEquals(3, rows.size());
        List<String> lines = ExportCsv.orders(rows);
        assertEquals(4, lines.size());
        assertTrue(lines.get(1).contains("Tiền mặt"));
        TestSession.clear();
        try {
            orderDAO.findAllForExport(filter, null);
            fail("an employee must not export");
        } catch (RuntimeException expected) {
            // refused
        } finally {
            TestSession.admin();
        }
    }

    @Test
    public void customerCsvKeepsTheLeadingZeroAndDefusesFormulas() {
        Customer c = new Customer();
        c.setFullName("=cmd|' /C calc'!A0");
        c.setPhone("0901234567");
        c.setEmail("a,b@x.vn");
        List<String> lines = ExportCsv.customers(List.of(c));
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).startsWith("'=cmd"));
        assertTrue(lines.get(1).contains("=\"0901234567\""));
        assertTrue(lines.get(1).contains("\"a,b@x.vn\""));
    }

    @Test
    public void customerPurchaseFiguresCountPaidOrdersOnly() {
        Customer c = new Customer();
        c.setFullName("S4 stats " + System.nanoTime());
        c = customerDAO.insert(c);
        Product p = product("1000");
        LocalDate day = freshDay();
        sellOn(day, p, 2, PaymentMethod.CASH, c.getId());
        sellOn(day.plusDays(1), p, 1, PaymentMethod.CASH, c.getId());
        final int id = c.getId();
        var figures = customerDAO.figures();
        var mine = com.coffeeshop.coffeeshopmanagement.service.CustomerStats.of(figures, id);
        assertEquals(2, mine.visits());
        assertEquals("three cups at 30,000", 0, new java.math.BigDecimal(90_000).compareTo(mine.spent()));
        assertEquals(day.plusDays(1), mine.lastVisit().toLocalDate());
    }

    @Test
    public void customerTotalSpentTakesLineRefundsOffAndKeepsTheVisit() {
        Customer c = new Customer();
        c.setFullName("S6 refunds " + System.nanoTime());
        c = customerDAO.insert(c);
        Product p = product("1000");
        Order paid = sellOn(LocalDate.of(2003, 9, 11), p, 3, PaymentMethod.CASH, c.getId()); // 90.000
        int itemId = orderDAO.findItemsByOrderId(paid.getId()).get(0).getId();
        new RefundDAO().refund(paid.getId(), java.util.Map.of(itemId, 1), true, PaymentMethod.CASH, "test");
        var mine = customerDAO.figures().get(c.getId());
        assertEquals("the order still stands, so it is still a visit", 1, mine.visits());
        assertEquals("one of three cups given back: 90.000 less 30.000", 0, new java.math.BigDecimal(60000).compareTo(mine.spent()));
        new RefundDAO().refund(paid.getId(), java.util.Map.of(itemId, 2), true, PaymentMethod.CASH, "test");
        var after = customerDAO.figures().get(c.getId());
        assertEquals(1, after.visits());
        assertEquals("everything given back", 0, java.math.BigDecimal.ZERO.compareTo(after.spent()));
    }

    @Test
    public void figuresComeOneRowPerCustomerNotOnePerOrder() {
        Customer c = new Customer();
        c.setFullName("S6 grouped " + System.nanoTime());
        c = customerDAO.insert(c);
        Product p = product("1000");
        for (int day = 1; day <= 4; day++) sellOn(LocalDate.of(2003, 10, day), p, 1, PaymentMethod.CASH, c.getId());
        var mine = customerDAO.figures().get(c.getId());
        assertEquals(4, mine.visits());
        assertEquals(0, new java.math.BigDecimal(120000).compareTo(mine.spent()));
        assertEquals(LocalDate.of(2003, 10, 4), mine.lastVisit().toLocalDate());
    }

    @Test
    public void theLastPaidOrderOfACustomerIsTheNewestOneWithRefundedPiecesTakenOff() {
        Customer c = new Customer();
        c.setFullName("S6 repeat " + System.nanoTime());
        c = customerDAO.insert(c);
        Product latte = product("1000");
        Product tea = product("1000");
        sellOn(LocalDate.of(2003, 8, 1), tea, 1, PaymentMethod.CASH, c.getId());               // an older order
        Order newest = sellOn(LocalDate.of(2003, 8, 9), latte, 3, PaymentMethod.CASH, c.getId()); // the one to repeat
        assertEquals(newest.getId(), orderDAO.lastPaidOrder(c.getId()).orElseThrow().orderId());
        var lines = orderDAO.lastPaidOrder(c.getId()).orElseThrow().items();
        assertEquals(1, lines.size());
        assertEquals(3, lines.get(0).getQuantity());
        assertEquals(Integer.valueOf(latte.getId()), lines.get(0).getProductId());
        int itemId = orderDAO.findItemsByOrderId(newest.getId()).get(0).getId();
        new RefundDAO().refund(newest.getId(), java.util.Map.of(itemId, 1), true, PaymentMethod.CASH, "test");
        assertEquals("one cup given back, two are repeated", 2, orderDAO.lastPaidOrder(c.getId()).orElseThrow().items().get(0).getQuantity());
        new RefundDAO().refund(newest.getId(), java.util.Map.of(itemId, 2), true, PaymentMethod.CASH, "test");
        assertTrue("a line given back whole is not repeated", orderDAO.lastPaidOrder(c.getId()).orElseThrow().items().isEmpty());
    }

    @Test
    public void aCustomerWithNoPaidOrderHasNothingToRepeat() {
        Customer c = new Customer();
        c.setFullName("S6 no repeat " + System.nanoTime());
        c = customerDAO.insert(c);
        assertTrue(orderDAO.lastPaidOrder(c.getId()).isEmpty());
    }

    @Test
    public void historySearchFindsACustomerByPhoneInAnyWayOfWritingIt() {
        Customer c = new Customer();
        c.setFullName("S5 phone " + System.nanoTime());
        c.setPhone("09" + String.format(java.util.Locale.ROOT, "%08d", System.nanoTime() % 100_000_000L));
        c = customerDAO.insert(c);
        Product p = product("1000");
        sellOn(freshDay(), p, 1, PaymentMethod.CASH, c.getId());
        String phone = c.getPhone();
        String spaced = phone.substring(0, 4) + " " + phone.substring(4);
        String international = "+84 " + phone.substring(1);
        for (String typed : new String[]{phone, spaced, international, phone.substring(0, 6)}) {
            assertTrue("found by " + typed, orderDAO.countHistory(new OrderDAO.HistoryFilter(typed, null, null, null)) >= 1);
        }
        assertEquals("three digits are still an order number search", 0,
                orderDAO.countHistory(new OrderDAO.HistoryFilter(phone.substring(1, 4) + "999999999", null, null, null)));
    }

    // ---- TODO 3.53: profit uses the cost recorded at the sale and the order's discount ----

    private Order sellTwoLines(LocalDate day, Product a, Product b, long discount) {
        OrderItem first = lineOf(a, 1);
        OrderItem second = lineOf(b, 1);
        Order o = new Order();
        o.setOrderDate(day.atTime(10, 0));
        o.setPaidAt(day.atTime(10, 5));
        o.setStatus(OrderStatus.PAID);
        o.setSubtotal(BigDecimal.valueOf(60_000));
        o.setDiscount(BigDecimal.valueOf(discount));
        o.setTotal(BigDecimal.valueOf(60_000 - discount));
        o.setPaymentMethod(PaymentMethod.CASH);
        return orderDAO.insert(o, List.of(first, second));
    }

    private OrderItem lineOf(Product p, int qty) {
        OrderItem item = new OrderItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(qty);
        item.setUnitPrice(BigDecimal.valueOf(30_000));
        item.setLineTotal(BigDecimal.valueOf(30_000L * qty));
        return item;
    }

    @Test
    public void aDiscountedOrdersProfitIsItsRevenueMinusItsCosts() {
        LocalDate day = freshDay();
        Product p = product("12000");
        OrderItem item = lineOf(p, 3); // 90,000 at cost 3 x 12,000 = 36,000
        Order o = new Order();
        o.setOrderDate(day.atTime(10, 0));
        o.setPaidAt(day.atTime(10, 5));
        o.setStatus(OrderStatus.PAID);
        o.setSubtotal(item.getLineTotal());
        o.setDiscount(BigDecimal.valueOf(9_000)); // 10 % off
        o.setTotal(BigDecimal.valueOf(81_000));
        o.setPaymentMethod(PaymentMethod.CASH);
        orderDAO.insert(o, List.of(item));

        BigDecimal revenue = reports.summary(day, day).revenue();
        assertEquals(0, BigDecimal.valueOf(81_000).compareTo(revenue));
        assertEquals("revenue 81,000 minus costs 36,000, not 90,000 minus 36,000",
                0, BigDecimal.valueOf(45_000).compareTo(reports.profit(day, day).grossProfit()));
        assertEquals(0, BigDecimal.valueOf(45_000).compareTo(reports.dailySales(day, day).get(0).profit()));
    }

    @Test
    public void theDiscountIsSpreadOverTheLinesInProportionToTheirValue() {
        LocalDate day = freshDay();
        Product costed = product("12000");
        Product uncosted = product(null);
        sellTwoLines(day, costed, uncosted, 6_000); // each line carries 3,000 of the discount
        ReportDAO.Profit profit = reports.profit(day, day);
        assertEquals("30,000 - 12,000 - 3,000", 0, BigDecimal.valueOf(15_000).compareTo(profit.grossProfit()));
        assertEquals(1, profit.linesWithCost());
        assertEquals(1, profit.linesWithoutCost());
    }

    @Test
    public void aCostEditedLaterDoesNotMoveAnEarlierDay() {
        LocalDate day = freshDay();
        Product p = product("12000");
        sellOn(day, p, 3, PaymentMethod.CASH, null); // 90,000 - 36,000 = 54,000
        assertEquals(0, BigDecimal.valueOf(54_000).compareTo(reports.profit(day, day).grossProfit()));

        p.setCost(BigDecimal.valueOf(50_000)); // the supplier raised the price today
        productDAO.update(p);

        assertEquals("the sale keeps the cost it had", 0, BigDecimal.valueOf(54_000).compareTo(reports.profit(day, day).grossProfit()));
        LocalDate tomorrow = day.plusDays(1);
        sellOn(tomorrow, p, 1, PaymentMethod.CASH, null); // 30,000 - 50,000
        assertEquals("a later sale uses the new cost", 0, BigDecimal.valueOf(-20_000).compareTo(reports.profit(tomorrow, tomorrow).grossProfit()));
    }

    @Test
    public void aLineSoldWithoutACostStaysUnknownEvenIfACostIsEnteredLater() {
        LocalDate day = freshDay();
        Product p = product(null);
        sellOn(day, p, 1, PaymentMethod.CASH, null);
        assertEquals(1, reports.profit(day, day).linesWithoutCost());

        p.setCost(BigDecimal.valueOf(10_000));
        productDAO.update(p);

        ReportDAO.Profit after = reports.profit(day, day);
        assertEquals("nobody knows what it cost then", 1, after.linesWithoutCost());
        assertEquals(0, after.linesWithCost());
    }

    @Test
    public void walletsAreSeparateLinesFromTheBankTransferInThePaymentSplit() { // TODO 4.34
        Product p = product("10000");
        LocalDate day = freshDay();
        sellOn(day, p, 1, PaymentMethod.TRANSFER, null);
        sellOn(day, p, 2, PaymentMethod.MOMO, null);
        sellOn(day, p, 3, PaymentMethod.MOMO, null);
        sellOn(day, p, 1, PaymentMethod.ZALOPAY, null);
        sellOn(day, p, 1, PaymentMethod.VNPAY, null);
        List<ReportDAO.PaymentSplit> split = reports.paymentSplit(day, day);
        assertEquals(4, split.size());
        ReportDAO.PaymentSplit momo = split.stream().filter(s -> s.method() == PaymentMethod.MOMO).findFirst().orElseThrow();
        assertEquals(2, momo.orders());
        assertEquals(0, BigDecimal.valueOf(150_000).compareTo(momo.revenue()));
        ReportDAO.PaymentSplit bank = split.stream().filter(s -> s.method() == PaymentMethod.TRANSFER).findFirst().orElseThrow();
        assertEquals(1, bank.orders());
        assertEquals(0, BigDecimal.valueOf(30_000).compareTo(bank.revenue()));
        BigDecimal sum = split.stream().map(ReportDAO.PaymentSplit::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, reports.summary(day, day).revenue().compareTo(sum));
    }
}
