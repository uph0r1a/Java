package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.DailySales;
import com.coffeeshop.coffeeshopmanagement.model.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

/**
 * TODO 6.32 (bug 3.23): the one case of that list that had no test of its own. An order that is PAID but has no {@code paid_at}
 * (an old row, from before the paid time was recorded) must be counted by {@code ReportDAO.summary} and shown by
 * {@code ReportDAO.dailySales} on the SAME day, the day it was opened; before 3.23 the two disagreed and the report's total did not
 * match the sum of its own daily table. The "sale across midnight" half of the case is {@code EdgeCasesTest}.
 *
 * <p>The year 2043 is used by no other test, so the counts here are not disturbed by the orders other classes leave in the shared
 * database.
 */
public class ReportLegacyPaidTimeTest {

    private static final LocalDate DAY = LocalDate.of(2043, 4, 5);
    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final ReportDAO reports = new ReportDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
        com.coffeeshop.coffeeshopmanagement.util.TestSession.admin();
    }

    @AfterClass
    public static void signOut() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.clear();
    }

    /** A PAID order of 20,000 opened at {@code opened}; {@code paidAt} null stores it the way an old row is (no paid time). */
    private void paidOrder(LocalDateTime opened, LocalDateTime paidAt) {
        Product p = new Product();
        p.setName("Legacy " + System.nanoTime());
        p.setPrice(BigDecimal.valueOf(20_000));
        p.setStock(5);
        p.setActive(true);
        p = productDAO.insert(p);
        OrderItem item = new OrderItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(20_000));
        item.setLineTotal(BigDecimal.valueOf(20_000));
        Order o = new Order();
        o.setOrderDate(opened);
        o.setStatus(OrderStatus.PAID);
        o.setSubtotal(BigDecimal.valueOf(20_000));
        o.setDiscount(BigDecimal.ZERO);
        o.setTotal(BigDecimal.valueOf(20_000));
        o.setPaymentMethod(PaymentMethod.CASH);
        o.setPaidAt(paidAt == null ? opened : paidAt);
        Order saved = orderDAO.insert(o, List.of(item));
        if (paidAt == null) forgetThePaidTime(saved.getId());
    }

    private static void forgetThePaidTime(int orderId) {
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement("UPDATE orders SET paid_at = NULL WHERE id = ?")) {
            st.setInt(1, orderId);
            assertEquals(1, st.executeUpdate());
        } catch (java.sql.SQLException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void aPaidOrderWithNoPaidTimeIsCountedAndShownOnTheDayItWasOpened() {
        paidOrder(DAY.atTime(10, 0), null);
        assertEquals(1, reports.summary(DAY, DAY).paidOrders());
        assertEquals(0, reports.summary(DAY.minusDays(1), DAY.minusDays(1)).paidOrders());
        assertEquals(0, reports.summary(DAY.plusDays(1), DAY.plusDays(1)).paidOrders());
        List<DailySales> daily = reports.dailySales(DAY, DAY);
        assertEquals("one row, on the opening day", 1, daily.size());
        assertEquals(DAY, daily.get(0).date());
        assertEquals(1, daily.get(0).orders());
        assertEquals(0, new BigDecimal("20000").compareTo(daily.get(0).revenue()));
        assertTrue("no row on the neighbouring days", reports.dailySales(DAY.minusDays(1), DAY.minusDays(1)).isEmpty());
        assertTrue(reports.dailySales(DAY.plusDays(1), DAY.plusDays(1)).isEmpty());
    }

    @Test
    public void theTotalOfTheRangeIsTheSumOfItsDailyRowsWithOldAndNewRowsMixed() {
        LocalDate first = DAY.plusDays(10);
        paidOrder(first.atTime(23, 50), null);                                  // old row opened late in the evening: it stays on this day
        paidOrder(first.plusDays(1).atTime(9, 0), first.plusDays(1).atTime(9, 5)); // a normal sale the next day
        paidOrder(first.plusDays(1).atTime(23, 55), first.plusDays(2).atTime(0, 10)); // paid after midnight: the day it was paid
        paidOrder(first.plusDays(2).atTime(12, 0), null);                       // another old row
        LocalDate last = first.plusDays(2);
        ReportDAO.Summary summary = reports.summary(first, last);
        List<DailySales> daily = reports.dailySales(first, last);
        assertEquals(4, summary.paidOrders());
        assertEquals("the report total agrees with its own daily table", summary.paidOrders(), daily.stream().mapToInt(DailySales::orders).sum());
        assertEquals(0, summary.revenue().compareTo(daily.stream().map(DailySales::revenue).reduce(BigDecimal.ZERO, BigDecimal::add)));
        assertEquals(List.of(first, first.plusDays(1), last), daily.stream().map(DailySales::date).toList());
        assertEquals(List.of(1, 1, 2), daily.stream().map(DailySales::orders).toList());
    }
}
