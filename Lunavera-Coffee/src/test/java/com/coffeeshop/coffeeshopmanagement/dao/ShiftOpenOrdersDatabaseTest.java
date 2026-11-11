package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.service.ShiftOpenOrders.Open;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 5.48: the shift-close box lists the orders that are still unpaid, read from the orders table. */
public class ShiftOpenOrdersDatabaseTest {

    private final ShiftDAO shifts = new ShiftDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private int tableA;
    private int tableB;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @AfterClass
    public static void signOut() {
        TestSession.clear();
    }

    @Before
    public void freeTables() {
        TestSession.admin();
        List<com.coffeeshop.coffeeshopmanagement.model.DiningTable> tables = new TableDAO().findAll();
        tableA = tables.get(7).getTableNumber();
        tableB = tables.get(8).getTableNumber();
        new TableDAO().setEmpty(tableA);   // empties the table: its open order is cancelled
        new TableDAO().setEmpty(tableB);
    }

    private Order open(Integer table, String total) {
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setTableNumber(table);
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(new BigDecimal(total));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal(total));
        return orderDAO.saveOrUpdateOpenOrder(order, List.of());
    }

    private Open find(List<Open> list, int orderId) {
        return list.stream().filter(o -> o.orderId() == orderId).findFirst().orElse(null);
    }

    @Test
    public void anOpenTableAndAnOpenTakeAwayAreListedWithTheirAmounts() {
        Order onTable = open(tableA, "75000");
        Order takeAway = open(null, "32000");
        List<Open> list = shifts.openOrders();
        Open t = find(list, onTable.getId());
        Open q = find(list, takeAway.getId());
        assertNotNull(t);
        assertNotNull(q);
        assertEquals(Integer.valueOf(tableA), t.tableNumber());
        assertNull(t.queueNo());
        assertEquals(0, new BigDecimal("75000").compareTo(t.total()));
        assertNull("a take-away order has no table", q.tableNumber());
        assertNotNull("it has its number of the day", q.queueNo());
        assertEquals(0, new BigDecimal("32000").compareTo(q.total()));
        assertTrue("tables are listed before take-away orders", list.indexOf(t) < list.indexOf(q));
    }

    @Test
    public void tablesAreInTableOrder() {
        Order high = open(Math.max(tableA, tableB), "1000");
        Order low = open(Math.min(tableA, tableB), "1000");
        List<Open> list = shifts.openOrders();
        assertTrue(list.indexOf(find(list, low.getId())) < list.indexOf(find(list, high.getId())));
    }

    @Test
    public void anOrderThatIsCancelledOrEmptiedIsNoLongerListed() {
        Order onTable = open(tableA, "9000");
        Order takeAway = open(null, "4000");
        assertNotNull(find(shifts.openOrders(), onTable.getId()));
        assertNotNull(find(shifts.openOrders(), takeAway.getId()));
        new TableDAO().setEmpty(tableA);
        assertTrue(orderDAO.cancelOpenTakeAway(takeAway.getId()));
        assertNull(find(shifts.openOrders(), onTable.getId()));
        assertNull(find(shifts.openOrders(), takeAway.getId()));
    }

    @Test
    public void readingItNeedsNoAdministrator() {
        Order onTable = open(tableB, "1000");
        TestSession.clear();
        try {
            // like the other figures of the box (summarize), reading the open orders needs no administrator
            shifts.openOrders();
        } finally {
            TestSession.admin();
        }
        assertNotNull(find(shifts.openOrders(), onTable.getId()));
    }
}
