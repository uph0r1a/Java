package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 2.50 and 2.52: emptying a table is not a refund; a refund is counted once and cannot be repeated. */
public class RefundCountTest {

    private final OrderDAO orderDAO = new OrderDAO();

    @BeforeClass
    public static void setUpDatabase() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    private Order paid() {
        OrderItem line = new OrderItem();
        line.setProductName("Test");
        line.setQuantity(1);
        line.setUnitPrice(BigDecimal.valueOf(20_000));
        line.setLineTotal(BigDecimal.valueOf(20_000));
        Order o = new Order();
        o.setOrderDate(AppClock.now());
        o.setStatus(OrderStatus.PAID);
        o.setSubtotal(BigDecimal.valueOf(20_000));
        o.setDiscount(BigDecimal.ZERO);
        o.setTotal(BigDecimal.valueOf(20_000));
        o.setPaymentMethod(PaymentMethod.CASH);
        o.setPaidAt(AppClock.now());
        return orderDAO.insert(o, List.of(line));
    }

    @Test
    public void refundedCountMovesOnlyForAPaidOrderThatIsCancelled() {
        int before = orderDAO.countRefunded();
        Order abandoned = new Order();
        abandoned.setOrderDate(AppClock.now());
        abandoned.setStatus(OrderStatus.OPEN);
        abandoned.setSubtotal(BigDecimal.ZERO);
        abandoned.setDiscount(BigDecimal.ZERO);
        abandoned.setTotal(BigDecimal.ZERO);
        abandoned.setTableNumber(9);
        orderDAO.insert(abandoned, List.of());
        new TableDAO().setEmpty(9); // the open order becomes CANCELLED but was never paid
        assertEquals(before, orderDAO.countRefunded());

        Order sold = paid();
        orderDAO.cancelPaidOrder(sold.getId());
        assertEquals(before + 1, orderDAO.countRefunded());
    }

    @Test
    public void aSecondCancelIsRefused() {
        Order sold = paid();
        orderDAO.cancelPaidOrder(sold.getId());
        try {
            orderDAO.cancelPaidOrder(sold.getId());
            fail("a refunded order cannot be refunded again");
        } catch (DataAccessException expected) {
            // fine
        }
    }

    @Test
    public void paidByIsKeptForThePersonWhoTookTheMoney() throws Exception {
        Order open = new Order();
        open.setOrderDate(AppClock.now());
        open.setStatus(OrderStatus.OPEN);
        open.setSubtotal(BigDecimal.ZERO);
        open.setDiscount(BigDecimal.ZERO);
        open.setTotal(BigDecimal.ZERO);
        Order saved = orderDAO.insert(open, List.of());
        OrderItem line = new OrderItem();
        line.setProductName("Test");
        line.setQuantity(1);
        line.setUnitPrice(BigDecimal.valueOf(10_000));
        line.setLineTotal(BigDecimal.valueOf(10_000));
        orderDAO.payExistingOrder(saved.getId(), null, PaymentMethod.CASH, BigDecimal.ZERO, null,
                BigDecimal.valueOf(10_000), List.of(line), null);
        try (java.sql.Connection c = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection();
             java.sql.Statement st = c.createStatement();
             java.sql.ResultSet rs = st.executeQuery("SELECT subtotal FROM orders WHERE id = " + saved.getId())) {
            assertTrue(rs.next());
            assertEquals("the subtotal is written at payment (TODO 2.25)", 10_000L, rs.getLong(1));
        }
    }

    @org.junit.AfterClass
    public static void signOut() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.clear();
    }
}
