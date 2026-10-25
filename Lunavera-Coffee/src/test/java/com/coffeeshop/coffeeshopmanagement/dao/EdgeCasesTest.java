package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.service.CustomerService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

/** Section 3 edge cases: a switched-off product is not sold (3.34) and a failed payment leaves no new customer (3.27). */
public class EdgeCasesTest {

    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
        com.coffeeshop.coffeeshopmanagement.util.TestSession.admin();
    }

    @AfterClass
    public static void signOut() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.clear();
    }

    private Product product(boolean active) {
        Product p = new Product();
        p.setName("Edge " + System.nanoTime());
        p.setPrice(BigDecimal.valueOf(20_000));
        p.setStock(5);
        p.setActive(active);
        return productDAO.insert(p);
    }

    private int stockOf(int id) {
        return productDAO.findAll().stream().filter(p -> p.getId() == id).findFirst().orElseThrow().getStock();
    }

    private static OrderItem line(Product p) {
        OrderItem item = new OrderItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(20_000));
        item.setLineTotal(BigDecimal.valueOf(20_000));
        return item;
    }

    private static Order paid() {
        Order o = new Order();
        o.setOrderDate(AppClock.now());
        o.setStatus(OrderStatus.PAID);
        o.setSubtotal(BigDecimal.valueOf(20_000));
        o.setDiscount(BigDecimal.ZERO);
        o.setTotal(BigDecimal.valueOf(20_000));
        o.setPaymentMethod(PaymentMethod.CASH);
        o.setPaidAt(AppClock.now());
        return o;
    }

    @Test
    public void aSwitchedOffProductCannotBePaid() {
        Product off = product(false);
        try {
            orderDAO.insert(paid(), List.of(line(off)));
            fail("a product that is switched off must not be sold");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("ngừng bán"));
        }
        assertEquals(5, stockOf(off.getId()));
    }

    @Test
    public void anActiveProductIsStillSold() {
        Product on = product(true);
        orderDAO.insert(paid(), List.of(line(on)));
        assertEquals(4, stockOf(on.getId()));
    }

    @Test
    public void anUnusedNewCustomerCanBeDiscardedButOneWithOrdersCannot() {
        CustomerService service = new CustomerService();
        String phone = "09" + (10_000_000 + (int) (System.nanoTime() % 80_000_000));
        CustomerService.Resolved first = service.resolve("Khách thử", phone);
        assertTrue(first.created());
        assertFalse("asking again finds the same customer", service.resolve("Khác", phone).created());
        service.discardIfUnused(first.customer());
        assertTrue(customerDAO.findByPhone(phone).isEmpty());

        CustomerService.Resolved second = service.resolve("Khách có đơn", phone);
        Order o = paid();
        o.setCustomerId(second.customer().getId());
        Product p = product(true);
        orderDAO.insert(o, List.of(line(p)));
        service.discardIfUnused(second.customer());
        assertTrue("a customer with an order stays", customerDAO.findByPhone(phone).isPresent());
    }

    @Test
    public void aSaleThatCrossesMidnightCountsOnTheDayItWasPaid() {
        Product p = product(true);
        Order o = paid();
        LocalDateTime paid = LocalDateTime.of(2031, 3, 2, 0, 10);
        o.setOrderDate(paid.minusMinutes(20)); // opened 23:50 the day before
        o.setPaidAt(paid);
        orderDAO.insert(o, List.of(line(p)));
        com.coffeeshop.coffeeshopmanagement.dao.ReportDAO reports = new com.coffeeshop.coffeeshopmanagement.dao.ReportDAO();
        java.time.LocalDate day = paid.toLocalDate();
        assertEquals(1, reports.summary(day, day).paidOrders());
        assertEquals(0, reports.summary(day.minusDays(1), day.minusDays(1)).paidOrders());
        assertEquals("the history filters by the pay date too", 1,
                orderDAO.findHistoryPage(new OrderDAO.HistoryFilter(null, null, day, day), 50, 0).size());
    }

    @Test
    public void anAutosaveOfAnOrderThatWasClosedElsewhereIsReported() {
        Order open = new Order();
        open.setOrderDate(AppClock.now());
        open.setStatus(OrderStatus.OPEN);
        open.setSubtotal(BigDecimal.ZERO);
        open.setDiscount(BigDecimal.ZERO);
        open.setTotal(BigDecimal.ZERO);
        Order saved = orderDAO.insert(open, List.of());
        assertTrue("still open: the save is accepted", orderDAO.updateIfStillOpen(saved, List.of()));
        Product p = product(true);
        orderDAO.payExistingOrder(saved.getId(), null, PaymentMethod.CASH, BigDecimal.ZERO, null,
                BigDecimal.valueOf(20_000), List.of(line(p)), null);
        assertFalse("paid meanwhile: the screen is told", orderDAO.updateIfStillOpen(saved, List.of()));
    }

    @Test
    public void anOrderPaidOnAFutureDayDoesNotAddAnEighthDayToTheWeeklyChart() {
        // A till clock set days ahead leaves an order "paid" in the future (TODO 3.42): it must not become an 8th bar.
        Product on = product(true);
        Order future = paid();
        future.setOrderDate(AppClock.now().plusDays(3));
        future.setPaidAt(AppClock.now().plusDays(3));
        orderDAO.insert(future, List.of(line(on)));

        java.util.Map<java.time.LocalDate, BigDecimal> week = orderDAO.revenueForLast7Days();
        assertEquals("exactly the seven days asked for", 7, week.size());
        assertFalse(week.containsKey(AppClock.today().plusDays(3)));
        assertTrue(week.containsKey(AppClock.today()));
    }
}

