package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.service.SalesPatterns;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.40: paid order times and sales per category, on fixed days far from the other tests. */
public class ReportPatternsDaoTest {

    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
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

    private Category category(String name) {
        Category c = new Category();
        c.setName(name + " " + System.nanoTime());
        c.setActive(true);
        return categoryDAO.insert(c);
    }

    private Product product(Category category, long price) {
        Product p = new Product();
        p.setName("P4.40 " + System.nanoTime());
        p.setCategoryId(category == null ? null : category.getId());
        p.setPrice(BigDecimal.valueOf(price));
        p.setStock(100);
        p.setActive(true);
        return productDAO.insert(p);
    }

    private Order pay(LocalDateTime paidAt, OrderStatus status, BigDecimal discount, Object... productAndQty) {
        List<OrderItem> items = new java.util.ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (int i = 0; i < productAndQty.length; i += 2) {
            Product p = (Product) productAndQty[i];
            int qty = (Integer) productAndQty[i + 1];
            OrderItem item = new OrderItem();
            item.setProductId(p.getId());
            item.setProductName(p.getName());
            item.setQuantity(qty);
            item.setUnitPrice(p.getPrice());
            item.setLineTotal(p.getPrice().multiply(BigDecimal.valueOf(qty)));
            items.add(item);
            subtotal = subtotal.add(item.getLineTotal());
        }
        Order o = new Order();
        o.setOrderDate(paidAt.minusMinutes(5));
        o.setPaidAt(paidAt);
        o.setStatus(status);
        o.setSubtotal(subtotal);
        o.setDiscount(discount);
        o.setTotal(subtotal.subtract(discount));
        o.setPaymentMethod(PaymentMethod.CASH);
        return orderDAO.insert(o, items);
    }

    @Test
    public void paidOrderTimesCarryTheHourAndSkipCancelledOrders() {
        LocalDate day = LocalDate.of(2001, 6, 4); // a Monday
        Product p = product(null, 20_000);
        pay(day.atTime(9, 15), OrderStatus.PAID, BigDecimal.ZERO, p, 1);
        pay(day.atTime(9, 45), OrderStatus.PAID, BigDecimal.ZERO, p, 2);
        pay(day.atTime(17, 5), OrderStatus.PAID, BigDecimal.ZERO, p, 1);
        pay(day.atTime(17, 30), OrderStatus.CANCELLED, BigDecimal.ZERO, p, 1);
        List<SalesPatterns.PaidOrder> paid = reports.paidOrders(day, day);
        assertEquals(3, paid.size());
        List<SalesPatterns.Slot> hours = SalesPatterns.byHour(paid);
        assertEquals(2, hours.size());
        assertEquals(9, hours.get(0).key());
        assertEquals(2, hours.get(0).orders());
        assertEquals(0, BigDecimal.valueOf(60_000).compareTo(hours.get(0).revenue()));
        assertEquals(java.time.DayOfWeek.MONDAY, SalesPatterns.byWeekday(paid, day, day).get(0).day());
    }

    @Test
    public void anOrderPaidJustAfterMidnightBelongsToTheNewDayAndHour() {
        // Its own day, and the day before it is used by no other test in this class (the first test fills 2001-06-04).
        LocalDate day = LocalDate.of(2001, 6, 11);
        Product p = product(null, 10_000);
        pay(day.atTime(0, 10), OrderStatus.PAID, BigDecimal.ZERO, p, 1);
        assertEquals(1, reports.paidOrders(day, day).size());
        assertEquals(0, SalesPatterns.byHour(reports.paidOrders(day, day)).get(0).key());
        assertTrue(reports.paidOrders(day.minusDays(1), day.minusDays(1)).isEmpty());
    }

    @Test
    public void categorySalesAddUpTheLinesPerCategoryBeforeTheDiscount() {
        LocalDate day = LocalDate.of(2001, 6, 6);
        Category coffee = category("Coffee 4.40");
        Category cake = category("Cake 4.40");
        Product latte = product(coffee, 30_000);
        Product mocha = product(coffee, 40_000);
        Product tart = product(cake, 50_000);
        Product loose = product(null, 5_000);
        pay(day.atTime(10, 0), OrderStatus.PAID, BigDecimal.valueOf(7_000), latte, 2, tart, 1);   // 60.000 + 50.000, discount 7.000
        pay(day.atTime(11, 0), OrderStatus.PAID, BigDecimal.ZERO, mocha, 1, loose, 3);             // 40.000 ; 15.000
        pay(day.atTime(12, 0), OrderStatus.CANCELLED, BigDecimal.ZERO, tart, 9);                    // not counted
        List<ReportDAO.CategorySales> sales = reports.categorySales(day, day);
        assertEquals(3, sales.size());
        ReportDAO.CategorySales first = sales.get(0);
        assertEquals(coffee.getName(), first.categoryName());
        assertEquals(3, first.quantity());
        assertEquals("a discount is not taken off a category", 0, BigDecimal.valueOf(100_000).compareTo(first.revenue()));
        ReportDAO.CategorySales second = sales.get(1);
        assertEquals(cake.getName(), second.categoryName());
        assertEquals(0, BigDecimal.valueOf(50_000).compareTo(second.revenue()));
        ReportDAO.CategorySales third = sales.get(2);
        assertNull("a product with no category", third.categoryName());
        assertEquals(3, third.quantity());
    }
}
