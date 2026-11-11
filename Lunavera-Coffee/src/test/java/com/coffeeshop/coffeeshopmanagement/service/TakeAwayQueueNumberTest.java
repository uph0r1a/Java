package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptText;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

/**
 * TODO 4.25, decision D3: a take-away order gets a simple counter of the day, 1, 2, 3 ..., that starts again at 1 every day; an
 * order at a table has none. The tests use random days far in the past (the test database is kept between runs) so the numbers of other tests and of earlier runs cannot interfere.
 */
public class TakeAwayQueueNumberTest {

    private final OrderDAO orderDAO = new OrderDAO();
    private static final java.util.Random RANDOM = new java.util.Random();

    /** A day nobody used before: somewhere between 1900 and 1990, found at random (two days in a row for the midnight test). */
    private static LocalDateTime freshDay(int hour) {
        return java.time.LocalDate.of(1900, 1, 1).plusDays(RANDOM.nextInt(30000)).atTime(hour, 0);
    }

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @AfterClass
    public static void signOut() {
        TestSession.clear();
    }

    private Order open(LocalDateTime when, Integer table) {
        // a table's order is saved as CANCELLED here: only an OPEN one is checked against the table's current order
        return save(when, table, table == null ? OrderStatus.OPEN : OrderStatus.CANCELLED);
    }

    private Order save(LocalDateTime when, Integer table, OrderStatus status) {
        Order order = new Order();
        order.setOrderDate(when);
        order.setStatus(status);
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        order.setTableNumber(table);
        return orderDAO.insert(order, List.of());
    }

    private Integer queueNoOf(int orderId) {
        return orderDAO.findById(orderId).orElseThrow().getQueueNo();
    }

    @Test
    public void takeAwayOrdersOfOneDayAreNumberedOneTwoThree() {
        LocalDateTime day = freshDay(9);
        Order a = open(day, null);
        Order b = open(day.plusHours(2), null);
        Order c = open(day.plusHours(5), null);
        assertEquals(Integer.valueOf(1), queueNoOf(a.getId()));
        assertEquals(Integer.valueOf(2), queueNoOf(b.getId()));
        assertEquals(Integer.valueOf(3), queueNoOf(c.getId()));
    }

    @Test
    public void theCounterStartsAgainAtOneTheNextDay() {
        LocalDateTime late = freshDay(23).withMinute(59).withSecond(30);
        LocalDateTime nextMorning = late.toLocalDate().plusDays(1).atTime(0, 0, 10);
        Order evening = open(late, null);
        Order morning = open(nextMorning, null);
        open(late.minusHours(1), null);
        assertEquals(Integer.valueOf(1), queueNoOf(evening.getId()));
        assertEquals("a new day starts at 1", Integer.valueOf(1), queueNoOf(morning.getId()));
        Order second = open(nextMorning.plusHours(3), null);
        assertEquals(Integer.valueOf(2), queueNoOf(second.getId()));
    }

    @Test
    public void anOrderAtATableHasNoNumberAndDoesNotUseOne() {
        LocalDateTime day = freshDay(10);
        Order takeAway = open(day, null);
        assertEquals(Integer.valueOf(1), queueNoOf(takeAway.getId()));
        Order table = open(day.plusHours(1), 1);
        assertNull(queueNoOf(table.getId()));
        Order next = open(day.plusHours(2), null);
        assertEquals("the table order did not take a number", Integer.valueOf(2), queueNoOf(next.getId()));
    }

    @Test
    public void theReceiptSaysWhichNumberItIs() {
        Order order = new Order();
        order.setQueueNo(7);
        assertTrue(ReceiptText.tableOf(order).contains("7"));
        order.setQueueNo(null);
        assertEquals(ReceiptText.table((Integer) null), ReceiptText.tableOf(order));
        order.setTableNumber(3);
        order.setQueueNo(9);
        assertEquals("a table order never shows a take-away number", ReceiptText.table(3), ReceiptText.tableOf(order));
    }

    @Test
    public void aNumberOver999IsPrintedWithoutAThousandsSeparator() {
        Order order = new Order();
        order.setQueueNo(1234);
        assertTrue(ReceiptText.tableOf(order).contains("1234"));
    }
}
