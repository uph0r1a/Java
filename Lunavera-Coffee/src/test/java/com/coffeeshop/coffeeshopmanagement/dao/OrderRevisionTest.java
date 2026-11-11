package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 2.91: a till that still shows an older state of an open order must not delete the lines another till added, neither when it
 * autosaves nor when it takes the payment. Every "till" here is its own copy of the order read from the database, like two screens
 * on one server. Take-away orders, so no table is touched (see TestDatabaseSupport for the shared database).
 */
public class OrderRevisionTest {

    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private Category category;

    @BeforeClass
    public static void setUpDatabase() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void createCategory() {
        category = new Category();
        category.setName("test-rev-category-" + UUID.randomUUID());
        category.setActive(true);
        categoryDAO.insert(category);
    }

    private Product newProduct(int stock) {
        Product product = new Product();
        product.setName("test-rev-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("30000"));
        product.setStock(stock);
        product.setActive(true);
        return productDAO.insert(product);
    }

    private static OrderItem line(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private static BigDecimal sum(List<OrderItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) total = total.add(item.getLineTotal());
        return total;
    }

    private Order openTakeAway(List<OrderItem> items) {
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(sum(items));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(sum(items));
        return orderDAO.insert(order, items);
    }

    /** The order as a screen loads it: a fresh copy with the revision the database holds now. */
    private Order loadedByATill(int orderId) {
        return orderDAO.findOpenTakeAway().stream().filter(o -> o.getId() == orderId).findFirst().orElseThrow();
    }

    private boolean save(Order till, List<OrderItem> items) {
        till.setSubtotal(sum(items));
        till.setTotal(sum(items));
        return orderDAO.updateIfStillOpen(till, items);
    }

    private Order pay(Order till, List<OrderItem> items) {
        return orderDAO.payExistingOrder(till.getId(), null, PaymentMethod.CASH, BigDecimal.ZERO, null, sum(items), items,
                null, null, null, null, null, null, null, till.getRev());
    }

    @Test
    public void aNewOrderStartsAtRevisionZeroAndEveryAutosaveRaisesItByOne() {
        Product coffee = newProduct(50);
        Order till = loadedByATill(openTakeAway(List.of(line(coffee, 1))).getId());
        assertEquals(0, till.getRev());
        assertTrue(save(till, List.of(line(coffee, 2))));
        assertEquals("the screen holds the revision it just saved", 1, till.getRev());
        assertTrue(save(till, List.of(line(coffee, 3))));
        assertEquals(2, till.getRev());
        assertEquals("the database agrees", 2, loadedByATill(till.getId()).getRev());
    }

    @Test
    public void aStaleAutosaveIsRefusedAndKeepsTheOtherTillsLines() {
        Product coffee = newProduct(50);
        Product cake = newProduct(50);
        int id = openTakeAway(List.of(line(coffee, 1))).getId();
        Order tillA = loadedByATill(id);
        Order tillB = loadedByATill(id);
        assertTrue(save(tillB, List.of(line(coffee, 1), line(cake, 2)))); // the waiter adds two cakes

        try {
            save(tillA, List.of(line(coffee, 3))); // till A still shows only the coffee
            fail("a stale autosave must be refused");
        } catch (OrderChangedElsewhereException stale) {
            assertEquals("the exception carries the order as it is saved", 2, stale.saved().size());
        }
        List<OrderItem> saved = orderDAO.findItemsByOrderId(id);
        assertEquals("the two cakes are still there", 2, saved.size());
        assertEquals(1, saved.get(0).getQuantity());
        assertEquals("nothing of till A's save was written", 1, loadedByATill(id).getRev());
    }

    @Test
    public void aStalePaymentIsRefusedTheOrderStaysOpenWithBothLinesAndNoStockIsTaken() {
        Product coffee = newProduct(10);
        Product cake = newProduct(10);
        int id = openTakeAway(List.of(line(coffee, 1))).getId();
        Order tillA = loadedByATill(id);
        Order tillB = loadedByATill(id);
        assertTrue(save(tillB, List.of(line(coffee, 1), line(cake, 2))));

        try {
            pay(tillA, List.of(line(coffee, 1)));
            fail("a stale payment must be refused");
        } catch (OrderChangedElsewhereException stale) {
            assertEquals(2, stale.saved().size());
        }
        assertEquals(OrderStatus.OPEN, loadedByATill(id).getStatus());
        assertEquals(2, orderDAO.findItemsByOrderId(id).size());
        assertEquals("no stock was taken", 10, stockOf(coffee));
        assertEquals(10, stockOf(cake));
    }

    @Test
    public void thePaymentOfTheTillThatIsUpToDateWorks() {
        Product coffee = newProduct(10);
        Product cake = newProduct(10);
        int id = openTakeAway(List.of(line(coffee, 1))).getId();
        Order tillA = loadedByATill(id);
        Order tillB = loadedByATill(id);
        assertTrue(save(tillB, List.of(line(coffee, 1), line(cake, 2))));
        Order reloaded = loadedByATill(id); // till A reloads, as the screen does after the refusal

        Order paid = pay(reloaded, orderDAO.findItemsByOrderId(id));
        assertEquals(OrderStatus.PAID, paid.getStatus());
        assertEquals(2, orderDAO.findItemsByOrderId(id).size());
        assertEquals(8, stockOf(cake));
    }

    @Test
    public void theNormalOneTillFlowNeedsNoReload() {
        Product coffee = newProduct(10);
        Order till = loadedByATill(openTakeAway(List.of(line(coffee, 1))).getId());
        assertTrue(save(till, List.of(line(coffee, 2))));
        assertTrue(save(till, List.of(line(coffee, 3))));
        Order paid = pay(till, List.of(line(coffee, 3)));
        assertEquals(OrderStatus.PAID, paid.getStatus());
        assertEquals(7, stockOf(coffee));
    }

    @Test
    public void anOrderThatIsNoLongerOpenIsStillTheOldAnswerNotTheNewException() {
        Product coffee = newProduct(10);
        int id = openTakeAway(List.of(line(coffee, 1))).getId();
        Order till = loadedByATill(id);
        assertTrue(orderDAO.cancelOpenTakeAway(id));
        assertFalse("a late autosave of a closed order returns false (TODO 3.28)", save(till, List.of(line(coffee, 2))));
        try {
            pay(till, List.of(line(coffee, 2)));
            fail("a closed order cannot be paid");
        } catch (OrderChangedElsewhereException e) {
            fail("a closed order is not 'changed elsewhere'");
        } catch (DataAccessException expected) {
            // "no longer open"
        }
    }

    @Test
    public void thePaymentWithoutARevisionKeepsWorkingForOlderCallers() {
        Product coffee = newProduct(10);
        int id = openTakeAway(List.of(line(coffee, 1))).getId();
        Order tillB = loadedByATill(id);
        assertTrue(save(tillB, List.of(line(coffee, 2))));
        List<OrderItem> items = List.of(line(coffee, 2));
        Order paid = orderDAO.payExistingOrder(id, null, PaymentMethod.CASH, BigDecimal.ZERO, null, sum(items), items, null, null, null,
                null, null, null, null);
        assertEquals(OrderStatus.PAID, paid.getStatus());
    }

    private int stockOf(Product product) {
        return productDAO.findAll().stream().filter(p -> p.getId() == product.getId()).findFirst().orElseThrow().getStock();
    }
}
