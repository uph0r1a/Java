package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.5: starting, reading back and autosaving the open order, the database halves of what {@code OrderController} did itself.
 * It uses take-away orders (no table) so it touches no table's state. Like the other DAO tests it shares one database and asserts
 * only about the rows it creates itself.
 */
public class OrderServiceTest {

    private final OrderService orders = new OrderService();
    private final CheckoutService checkout = new CheckoutService();
    private final CustomerService customers = new CustomerService();
    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private Category category;

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
    public void createCategory() {
        category = new Category();
        category.setName("test-orderservice-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
    }

    private Product newProduct(int stock) {
        Product product = new Product();
        product.setName("test-orderservice-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("40000"));
        product.setStock(stock);
        product.setActive(true);
        return productDAO.insert(product);
    }

    private static OrderItem lineFor(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private static String newPhone() {
        return "09" + String.format(java.util.Locale.ROOT, "%08d", new Random().nextInt(100_000_000));
    }

    // ------------------------------------------------------------------ start

    @Test
    public void startingATakeAwayOrderSavesAnOpenOrderWithNoTableAndMakesTheTypedCustomer() {
        String phone = newPhone();
        OrderService.Started started = orders.start(null, "Khách thử", phone, null);
        try {
            Order order = started.order();
            assertTrue(order.getId() > 0);
            assertEquals(OrderStatus.OPEN, order.getStatus());
            assertNull("a take-away order has no table", order.getTableNumber());
            assertEquals(0, BigDecimal.ZERO.compareTo(order.getTotal()));
            assertNotNull("the typed phone makes a customer", started.customer());
            assertEquals(phone, started.customer().getPhone());
            assertEquals(Integer.valueOf(started.customer().getId()), order.getCustomerId());
            assertTrue(orderDAO.findOpenTakeAway().stream().anyMatch(o -> o.getId() == order.getId()));
        } finally {
            orderDAO.cancelOpenTakeAway(started.order().getId());
        }
    }

    @Test
    public void startingWithNoPhoneIsAWalkInWithNoCustomerRow() {
        OrderService.Started started = orders.start(null, "Anh Nam", "", null);
        try {
            assertNull(started.customer());
            assertNull(started.order().getCustomerId());
        } finally {
            orderDAO.cancelOpenTakeAway(started.order().getId());
        }
    }

    @Test
    public void aNewOpenOrderIsEmptyAndOpen() {
        Order order = OrderService.newOpenOrder(7, 3, 5);
        assertEquals(OrderStatus.OPEN, order.getStatus());
        assertEquals(Integer.valueOf(7), order.getTableNumber());
        assertEquals(Integer.valueOf(3), order.getCustomerId());
        assertEquals(Integer.valueOf(5), order.getEmployeeId());
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getSubtotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getDiscount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getTotal()));
        assertNotNull("it is dated", order.getOrderDate());
        assertNull(OrderService.newOpenOrder(null, null, null).getTableNumber());
    }

    // ------------------------------------------------------------------ autosave and load

    @Test
    public void applyCartPutsTheTotalsAndTheAttachedCustomerOnTheOrder() {
        Order order = new Order();
        Customer customer = new Customer();
        customer.setId(42);
        OrderService.applyCart(order, new BigDecimal("80000"), customer);
        assertEquals(0, new BigDecimal("80000").compareTo(order.getSubtotal()));
        assertEquals(0, new BigDecimal("80000").compareTo(order.getTotal()));
        assertEquals(Integer.valueOf(42), order.getCustomerId());
        OrderService.applyCart(order, BigDecimal.ZERO, null);
        assertNull("detaching the customer clears the link", order.getCustomerId());
    }

    @Test
    public void whatWasAutosavedIsReadBackWithTheLinesAndTheCustomer() {
        Product product = newProduct(10);
        OrderService.Started started = orders.start(null, "Khách thử", newPhone(), null);
        try {
            Order order = started.order();
            List<OrderItem> lines = List.of(lineFor(product, 2));
            OrderService.applyCart(order, new BigDecimal("80000"), started.customer());
            assertTrue("the order is still open", orders.saveIfStillOpen(order, lines));

            OrderService.TableOrder loaded = orders.load(null, order.getId());
            assertNotNull(loaded.order());
            assertEquals(order.getId(), loaded.order().getId());
            assertEquals(1, loaded.items().size());
            assertEquals(2, loaded.items().get(0).getQuantity());
            assertEquals(Integer.valueOf(product.getId()), loaded.items().get(0).getProductId());
            assertNotNull(loaded.customer());
            assertEquals(started.customer().getId(), loaded.customer().getId());
        } finally {
            orderDAO.cancelOpenTakeAway(started.order().getId());
        }
    }

    @Test
    public void loadingAWalkInOrderGivesNoCustomer() {
        OrderService.Started started = orders.start(null, "", "", null);
        try {
            OrderService.TableOrder loaded = orders.load(null, started.order().getId());
            assertNotNull(loaded.order());
            assertTrue(loaded.items().isEmpty());
            assertNull(loaded.customer());
        } finally {
            orderDAO.cancelOpenTakeAway(started.order().getId());
        }
    }

    @Test
    public void loadingWithNoTableAndNoTakeAwayOrderGivesNothing() {
        OrderService.TableOrder loaded = orders.load(null, null);
        assertNull(loaded.order());
        assertTrue(loaded.items().isEmpty());
        assertNull(loaded.customer());
    }

    @Test
    public void aTakeAwayOrderThatIsNoLongerOpenIsNotLoaded() {
        OrderService.Started started = orders.start(null, "", "", null);
        orderDAO.cancelOpenTakeAway(started.order().getId());
        assertNull(orders.load(null, started.order().getId()).order());
    }

    @Test
    public void theAutosaveSaysSoOnceTheOrderHasBeenPaidElsewhere() {
        Product product = newProduct(10);
        OrderService.Started started = orders.start(null, "", "", null);
        Order order = started.order();
        List<OrderItem> lines = List.of(lineFor(product, 1));
        OrderService.applyCart(order, new BigDecimal("40000"), null);
        assertTrue(orders.saveIfStillOpen(order, lines));

        checkout.pay(null, lines, new BigDecimal("40000"), null, "", "", order,
                new CheckoutService.Payment(PaymentMethod.CASH, BigDecimal.ZERO, null, new BigDecimal("40000")), null, Role.ADMIN);

        assertFalse("a late autosave must not touch a paid order", orders.saveIfStillOpen(order, List.of()));
        assertNull("and it is no longer there to load", orders.load(null, order.getId()).order());
    }

    // ------------------------------------------------------------------ the points the dialog shows

    @Test
    public void currentPointsAreReadAgainFromTheDatabaseNotFromTheOldObject() {
        Product product = newProduct(10);
        Customer stale = new Customer();
        stale.setFullName("test-orderservice-customer-" + UUID.randomUUID());
        stale.setLoyaltyPoints(0);
        stale = customerDAO.insert(stale);
        Customer attached = stale; // the screen keeps holding this object, with 0 points on it
        OrderService.Started started = orders.start(null, "", "", null);
        Order order = started.order();
        List<OrderItem> lines = List.of(lineFor(product, 1));
        OrderService.applyCart(order, new BigDecimal("40000"), attached);
        orders.saveIfStillOpen(order, lines);
        checkout.pay(null, lines, new BigDecimal("40000"), attached, "", "", order,
                new CheckoutService.Payment(PaymentMethod.CASH, BigDecimal.ZERO, null, new BigDecimal("40000")), null, Role.ADMIN);

        int expected = customerDAO.findById(attached.getId()).orElseThrow().getLoyaltyPoints();
        assertEquals(LoyaltyPolicy.pointsFor(new BigDecimal("40000")), expected);
        Customer old = new Customer();
        old.setId(attached.getId());
        old.setLoyaltyPoints(0);
        assertEquals("the balance now, not the 0 the screen holds", expected, customers.currentPoints(old));
    }

    @Test
    public void currentPointsFallBackToTheObjectWhenTheCustomerIsNotInTheDatabase() {
        Customer ghost = new Customer();
        ghost.setId(2_000_000_000);
        ghost.setLoyaltyPoints(7);
        assertEquals(7, customers.currentPoints(ghost));
    }
}
