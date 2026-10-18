package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 2.11: open orders that were started long ago and never closed are found and reported. */
public class StaleOrdersTest {

    private final OrderDAO orderDAO = new OrderDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private Order open(LocalDateTime when, Integer table) {
        Order o = new Order();
        o.setOrderDate(when);
        o.setStatus(OrderStatus.OPEN);
        o.setSubtotal(BigDecimal.ZERO);
        o.setDiscount(BigDecimal.ZERO);
        o.setTotal(BigDecimal.ZERO);
        o.setTableNumber(table);
        return orderDAO.insert(o, List.of());
    }

    private boolean listed(int orderId) {
        return orderDAO.findStaleOpenOrders(Duration.ofHours(12)).stream().anyMatch(o -> o.getId() == orderId);
    }

    @Test
    public void anOldOpenOrderIsStaleAndARecentOneIsNot() {
        Order old = open(AppClock.now().minusHours(30), null);
        Order recent = open(AppClock.now().minusHours(1), null);
        assertTrue(listed(old.getId()));
        assertFalse(listed(recent.getId()));
    }

    @Test
    public void paidAndCancelledOrdersAreNeverStale() {
        Order paid = open(AppClock.now().minusDays(3), null);
        paid.setStatus(OrderStatus.PAID);
        paid.setSubtotal(BigDecimal.valueOf(50_000));
        paid.setTotal(BigDecimal.valueOf(50_000));
        com.coffeeshop.coffeeshopmanagement.model.OrderItem line = new com.coffeeshop.coffeeshopmanagement.model.OrderItem();
        line.setProductName("Test");
        line.setQuantity(1);
        line.setUnitPrice(BigDecimal.valueOf(50_000));
        line.setLineTotal(BigDecimal.valueOf(50_000));
        Order saved = new OrderDAO().insert(paid, List.of(line)); // a second row, saved as PAID
        assertFalse(listed(saved.getId()));
    }

    @Test
    public void theWarningNamesTheTablesAndIsNullWhenThereIsNothing() {
        assertNull(DashboardStatsService.staleOrdersWarning(List.of()));
        assertNull(DashboardStatsService.staleOrdersWarning(null));
        Order a = new Order(); a.setTableNumber(3);
        Order b = new Order(); b.setTableNumber(3);
        Order c = new Order(); c.setTableNumber(null);
        String text = DashboardStatsService.staleOrdersWarning(List.of(a, b, c));
        assertTrue(text, text.contains("3 đơn"));
        assertTrue(text, text.contains("bàn 3"));
        assertTrue(text, text.contains("mang đi"));
        assertEquals("a table is named once", text.indexOf("bàn 3"), text.lastIndexOf("bàn 3"));
        assertTrue(text.contains("12 giờ"));
    }
}
