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
        LocalDate day = LocalDate.of(2003, 4, 4);
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
        LocalDate day = LocalDate.of(2003, 5, 5);
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

    @Test
    public void aCustomersHistoryHoldsOnlyTheirOrders() {
        Customer mine = new Customer();
        mine.setFullName("S4 khach " + System.nanoTime());
        mine = customerDAO.insert(mine);
        Customer other = new Customer();
        other.setFullName("S4 khac " + System.nanoTime());
        other = customerDAO.insert(other);
        Product p = product("1000");
        LocalDate day = LocalDate.of(2003, 6, 6);
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
        LocalDate day = LocalDate.of(2003, 7, 7);
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
    public void historySearchFindsACustomerByPhoneInAnyWayOfWritingIt() {
        Customer c = new Customer();
        c.setFullName("S5 phone " + System.nanoTime());
        c.setPhone("09" + String.format("%08d", System.nanoTime() % 100_000_000L));
        c = customerDAO.insert(c);
        Product p = product("1000");
        sellOn(LocalDate.of(2003, 8, 8), p, 1, PaymentMethod.CASH, c.getId());
        String phone = c.getPhone();
        String spaced = phone.substring(0, 4) + " " + phone.substring(4);
        String international = "+84 " + phone.substring(1);
        for (String typed : new String[]{phone, spaced, international, phone.substring(0, 6)}) {
            assertTrue("found by " + typed, orderDAO.countHistory(new OrderDAO.HistoryFilter(typed, null, null, null)) >= 1);
        }
        assertEquals("three digits are still an order number search", 0,
                orderDAO.countHistory(new OrderDAO.HistoryFilter(phone.substring(1, 4) + "999999999", null, null, null)));
    }
}
