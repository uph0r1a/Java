package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.*;
import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService.DashboardStats;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 6.3: the dashboard numbers had no test. The first group needs no database (the warning texts); the second moves real
 * rows through the shared test database and asserts only the <em>change</em> in each figure, because other tests leave rows.
 */
public class DashboardStatsServiceTest {

    private final DashboardStatsService service = new DashboardStatsService();
    private final OrderDAO orderDAO = new OrderDAO();

    @BeforeClass
    public static void setUpDatabase() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    // ---- warning texts, no database ----

    private static Order staleOn(Integer table) {
        Order order = new Order();
        order.setTableNumber(table);
        return order;
    }

    @Test
    public void nothingToWarnAboutGivesNull() {
        assertNull(DashboardStatsService.staleOrdersWarning(null));
        assertNull(DashboardStatsService.staleOrdersWarning(List.of()));
        assertNull(DashboardStatsService.heldTablesWarning(List.of()));
        assertNull(DashboardStatsService.sharedPhonesWarning(0));
        assertNull(DashboardStatsService.combinedWarning(List.of(), List.of(), 0));
        assertNull(DashboardStatsService.combinedWarning(null, null));
    }

    @Test
    public void staleOrdersAreCountedAndTablesNamedOnceEach() {
        String text = DashboardStatsService.staleOrdersWarning(List.of(staleOn(3), staleOn(5), staleOn(3), staleOn(null)));
        assertTrue(text, text.contains("4 đơn mở quá 12 giờ"));
        assertTrue(text, text.contains("bàn 3, bàn 5, mang đi"));
        assertEquals("a table is named once however many stale orders it has", text.indexOf("bàn 3"), text.lastIndexOf("bàn 3"));
    }

    @Test
    public void heldTablesAreNamed() {
        String text = DashboardStatsService.heldTablesWarning(List.of(2, 7));
        assertTrue(text, text.contains("bàn 2, bàn 7"));
        assertTrue(text, text.contains("không có đơn"));
    }

    @Test
    public void sharedPhonesAreCounted() {
        String text = DashboardStatsService.sharedPhonesWarning(3);
        assertTrue(text, text.startsWith("⚠ 3 số điện thoại"));
        assertTrue(text, text.contains("màn hình Khách hàng"));
    }

    @Test
    public void theWarningsAreJoinedOnePerLineInAFixedOrder() {
        String all = DashboardStatsService.combinedWarning(List.of(staleOn(1)), List.of(2), 1);
        String[] lines = all.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].contains("đơn mở quá"));
        assertTrue(lines[1].contains("đánh dấu"));
        assertTrue(lines[2].contains("trùng"));
        assertEquals("only the shared phones", 1, DashboardStatsService.combinedWarning(List.of(), List.of(), 2).split("\n").length);
    }

    // ---- the figures, on the shared database ----

    private Product newProduct(int stock) {
        Category category = new Category();
        category.setName("dash-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        Product product = new Product();
        product.setName("dash-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("20000"));
        product.setStock(stock);
        product.setActive(true);
        return new ProductDAO().insert(product);
    }

    private OrderItem line(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private Order order(OrderStatus status, BigDecimal total) {
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setStatus(status);
        order.setSubtotal(total);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(total);
        if (status == OrderStatus.PAID) {
            order.setPaymentMethod(PaymentMethod.CASH);
            order.setPaidAt(AppClock.now());
        }
        return order;
    }

    @Test
    public void aPaidOrderMovesTodaysOrdersRevenueAndThePaidCount() {
        Product product = newProduct(10);
        DashboardStats before = service.loadStats();

        orderDAO.insert(order(OrderStatus.PAID, new BigDecimal("40000")), List.of(line(product, 2)));

        DashboardStats after = service.loadStats();
        assertEquals(before.todayOrders() + 1, after.todayOrders());
        assertEquals(before.paidOrders() + 1, after.paidOrders());
        assertEquals(0, before.todayRevenue().add(new BigDecimal("40000")).compareTo(after.todayRevenue()));
        assertEquals(before.openOrders(), after.openOrders());
        assertEquals(before.todayOrdersList().size() + 1, after.todayOrdersList().size());
    }

    @Test
    public void anOpenOrderIsCountedButAddsNoRevenue() {
        Product product = newProduct(10);
        DashboardStats before = service.loadStats();

        orderDAO.insert(order(OrderStatus.OPEN, new BigDecimal("20000")), List.of(line(product, 1)));

        DashboardStats after = service.loadStats();
        assertEquals(before.openOrders() + 1, after.openOrders());
        assertEquals(before.paidOrders(), after.paidOrders());
        assertEquals("an unpaid order is no revenue", 0, before.todayRevenue().compareTo(after.todayRevenue()));
    }

    @Test
    public void aRefundedOrderLeavesTheRevenueAndIsCountedAsCancelled() {
        Product product = newProduct(10);
        Order paid = orderDAO.insert(order(OrderStatus.PAID, new BigDecimal("20000")), List.of(line(product, 1)));
        DashboardStats beforeRefund = service.loadStats();

        orderDAO.cancelPaidOrder(paid.getId());

        DashboardStats after = service.loadStats();
        assertEquals(beforeRefund.cancelledOrders() + 1, after.cancelledOrders());
        assertEquals(beforeRefund.paidOrders() - 1, after.paidOrders());
        assertEquals(0, beforeRefund.todayRevenue().subtract(new BigDecimal("20000")).compareTo(after.todayRevenue()));
    }

    @Test
    public void aProductThatIsAlmostOutIsCountedAsLowStock() {
        DashboardStats before = service.loadStats();
        newProduct(1);
        DashboardStats after = service.loadStats();
        assertEquals(before.lowStockCount() + 1, after.lowStockCount());
        assertEquals(before.totalProducts() + 1, after.totalProducts());
        assertTrue("the list shows at most five rows", after.lowStockProducts().size() <= 5);
    }

    @Test
    public void theLastSevenDaysIncludeTodayAndNoNegativeRevenue() {
        DashboardStats stats = service.loadStats();
        assertEquals("one entry per day, today last", 7, stats.revenueLast7Days().size());
        assertTrue(stats.revenueLast7Days().containsKey(AppClock.today()));
        for (BigDecimal revenue : stats.revenueLast7Days().values()) {
            assertTrue(revenue.signum() >= 0);
        }
        assertEquals("the test database has no shared customer phones (the unique index is there)", 0, stats.sharedCustomerPhones());
    }
}
