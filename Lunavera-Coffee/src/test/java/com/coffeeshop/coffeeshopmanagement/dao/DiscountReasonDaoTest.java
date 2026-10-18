package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 2.3: the reason for a discount is saved with the order, on both ways of paying. */
public class DiscountReasonDaoTest {

    private final OrderDAO orderDAO = new OrderDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    private Order order(OrderStatus status, String reason) {
        Order o = new Order();
        o.setOrderDate(AppClock.now());
        o.setStatus(status);
        o.setSubtotal(BigDecimal.valueOf(100_000));
        o.setDiscount(BigDecimal.valueOf(reason == null ? 0 : 10_000));
        o.setTotal(BigDecimal.valueOf(reason == null ? 100_000 : 90_000));
        o.setDiscountReason(reason);
        if (status == OrderStatus.PAID) {
            o.setPaymentMethod(PaymentMethod.CASH);
            o.setPaidAt(AppClock.now());
        }
        return o;
    }

    /** One line worth 100,000 so a paid order adds up (subtotal 100,000). */
    private static List<com.coffeeshop.coffeeshopmanagement.model.OrderItem> oneLine() {
        com.coffeeshop.coffeeshopmanagement.model.OrderItem item = new com.coffeeshop.coffeeshopmanagement.model.OrderItem();
        item.setProductName("Test");
        item.setQuantity(1);
        item.setUnitPrice(BigDecimal.valueOf(100_000));
        item.setLineTotal(BigDecimal.valueOf(100_000));
        return List.of(item);
    }

    private String storedReason(int orderId) throws Exception {
        try (Connection c = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT discount_reason FROM orders WHERE id = " + orderId)) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    @Test
    public void aNewPaidOrderKeepsItsDiscountReason() throws Exception {
        Order saved = orderDAO.insert(order(OrderStatus.PAID, "Khách quen"), oneLine());
        assertEquals("Khách quen", storedReason(saved.getId()));
    }

    @Test
    public void anOrderWithoutADiscountHasNoReason() throws Exception {
        Order saved = orderDAO.insert(order(OrderStatus.PAID, null), oneLine());
        assertNull(storedReason(saved.getId()));
    }

    @Test
    public void payingAnOpenOrderSavesTheReason() throws Exception {
        Order open = orderDAO.insert(order(OrderStatus.OPEN, null), List.of());
        orderDAO.payExistingOrder(open.getId(), null, PaymentMethod.CASH, BigDecimal.valueOf(10_000),
                "Sai món, quán xin lỗi", BigDecimal.valueOf(90_000), oneLine());
        assertEquals("Sai món, quán xin lỗi", storedReason(open.getId()));
    }

    @Test
    public void theSchemaHasTheColumnOnAnOldDatabaseToo() throws Exception {
        TestDatabaseSupport.requireSqlite(); // uses PRAGMA table_info
        // initialize() is idempotent and adds the column with addColumnIfMissing; running it again must not fail.
        com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.initialize();
        try (Connection c = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(orders)")) {
            boolean found = false;
            while (rs.next()) found |= "discount_reason".equals(rs.getString("name"));
            assertTrue(found);
        }
    }
}
