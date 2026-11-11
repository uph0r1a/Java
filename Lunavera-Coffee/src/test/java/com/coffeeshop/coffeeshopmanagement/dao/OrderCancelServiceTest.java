package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.service.OrderCancelService;
import com.coffeeshop.coffeeshopmanagement.service.StaffService;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 6.21: who may cancel a paid order and an open take-away order, decided by {@link OrderCancelService}. */
public class OrderCancelServiceTest {

    private final OrderCancelService service = new OrderCancelService();
    private final OrderDAO orderDAO = new OrderDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @After
    public void admin() {
        TestSession.admin();
    }

    private static Employee person(String name) {
        Employee e = new Employee();
        e.setFullName(name + " " + UUID.randomUUID().toString().substring(0, 6));
        e.setPhone("09" + String.format(java.util.Locale.ROOT, "%08d", new java.util.Random().nextInt(100_000_000)));
        return new StaffService().save(e);
    }

    private static void signInAsCashier(Employee e) {
        User u = new User();
        u.setId(2);
        u.setUsername("test-cashier");
        u.setRole(Role.EMPLOYEE);
        Session.start(u, e);
    }

    private Order openTakeAwayBy(Employee opener) {
        Category category = new Category();
        category.setName("test-cancel-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        Product product = new Product();
        product.setName("test-cancel-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("30000"));
        product.setStock(10);
        product.setActive(true);
        product = new ProductDAO().insert(product);
        OrderItem line = new OrderItem();
        line.setProductId(product.getId());
        line.setProductName(product.getName());
        line.setQuantity(1);
        line.setUnitPrice(product.getPrice());
        line.setLineTotal(product.getPrice());
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setStatus(OrderStatus.OPEN);
        order.setEmployeeId(opener.getId());
        order.setSubtotal(product.getPrice());
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(product.getPrice());
        return orderDAO.insert(order, List.of(line));
    }

    private boolean isOpen(int id) {
        return orderDAO.findOpenTakeAway().stream().anyMatch(o -> o.getId() == id);
    }

    @Test
    public void anAdministratorCancelsAnyTakeAwayOrder() {
        Order order = openTakeAwayBy(person("Opener"));
        TestSession.admin();
        assertTrue(service.cancelTakeAway(order.getId()));
        assertFalse(isOpen(order.getId()));
    }

    @Test
    public void theCashierWhoOpenedItCancelsIt() {
        Employee mai = person("Mai");
        Order order = openTakeAwayBy(mai);
        signInAsCashier(mai);
        assertTrue(service.cancelTakeAway(order.getId()));
        assertFalse(isOpen(order.getId()));
    }

    @Test
    public void anotherCashierIsRefusedAndTheOrderStaysOpen() {
        Order order = openTakeAwayBy(person("Mai"));
        signInAsCashier(person("Lan"));
        try {
            service.cancelTakeAway(order.getId());
            fail("another cashier's take-away order must not be cancelled");
        } catch (Permissions.PermissionDeniedException expected) {
            // refused
        }
        assertTrue(isOpen(order.getId()));
    }

    @Test
    public void anOrderThatIsAlreadyGoneIsNothingToCancel() {
        Order order = openTakeAwayBy(person("Mai"));
        TestSession.admin();
        assertTrue(service.cancelTakeAway(order.getId()));
        assertFalse("a second cancel finds nothing", service.cancelTakeAway(order.getId()));
    }

    @Test
    public void onlyAnAdministratorMayRefundAPaidOrder() {
        TestSession.admin();
        assertTrue(OrderCancelService.mayCancelPaid());
        signInAsCashier(person("Lan"));
        assertFalse(OrderCancelService.mayCancelPaid());
        try {
            service.cancelPaid(1, true, "x");
            fail("a cashier may not refund a paid order");
        } catch (Permissions.PermissionDeniedException expected) {
            // refused
        }
    }

    @Test
    public void anAdministratorRefundsAPaidOrderAndTheStockComesBack() {
        Order open = openTakeAwayBy(person("Mai"));
        List<OrderItem> items = orderDAO.findItemsByOrderId(open.getId());
        TestSession.admin();
        Order paid = orderDAO.payExistingOrder(open.getId(), null, PaymentMethod.CASH, BigDecimal.ZERO, null, open.getTotal(), items,
                null, null, null, null, null, null, null);
        assertEquals(OrderStatus.PAID, paid.getStatus());
        int productId = items.get(0).getProductId();
        assertEquals(9, stockOf(productId));
        service.cancelPaid(paid.getId(), true, "customer changed their mind");
        assertEquals(10, stockOf(productId));
    }

    private int stockOf(int productId) {
        return new ProductDAO().findAll().stream().filter(p -> p.getId() == productId).findFirst().orElseThrow().getStock();
    }
}
