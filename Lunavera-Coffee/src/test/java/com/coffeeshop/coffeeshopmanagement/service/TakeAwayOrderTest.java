package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * TODO 4.25: a take-away order has no table. The order is the same OPEN order as a table's with {@code table_number} empty; this
 * checks the data side the till relies on: listing the open ones, paying with no table, cancelling one, and that no table is touched.
 * Like the other DAO tests it shares one database and asserts only about the rows it makes itself.
 */
public class TakeAwayOrderTest {

    private final CheckoutService checkout = new CheckoutService();
    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final TableDAO tableDAO = new TableDAO();
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
        category.setName("test-takeaway-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
    }

    private Product newProduct(int stock) {
        Product product = new Product();
        product.setName("test-takeaway-product-" + UUID.randomUUID());
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

    private Order startOpen(Integer tableNumber) {
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        order.setTableNumber(tableNumber);
        return orderDAO.insert(order, List.of());
    }

    private static CheckoutService.Payment cash(String total) {
        return new CheckoutService.Payment(PaymentMethod.CASH, BigDecimal.ZERO, null, new BigDecimal(total));
    }

    private int stockOf(Product product) {
        return productDAO.findAll().stream().filter(p -> p.getId() == product.getId()).findFirst().orElseThrow().getStock();
    }

    private Map<Integer, Boolean> tableStatuses() {
        return tableDAO.findAll().stream().collect(Collectors.toMap(DiningTable::getTableNumber, DiningTable::isOccupied));
    }

    private boolean listed(int orderId) {
        return orderDAO.findOpenTakeAway().stream().anyMatch(o -> o.getId() == orderId);
    }

    @Test
    public void anOrderWithNoTableIsSavedOpenWithNoTable() {
        Order open = startOpen(null);
        assertNull(open.getTableNumber());
        assertEquals(OrderStatus.OPEN, open.getStatus());
        assertTrue(listed(open.getId()));
        orderDAO.cancelOpenTakeAway(open.getId());
    }

    @Test
    public void theOpenTakeAwayListHoldsOnlyOpenOrdersWithNoTableOldestFirst() {
        int table = tableDAO.add("test-takeaway-table-" + UUID.randomUUID().toString().substring(0, 6), 2);
        try {
            Order first = startOpen(null);
            Order onATable = startOpen(table);
            Order second = startOpen(null);
            Order paidOne = startOpen(null);
            Product product = newProduct(5);
            checkout.pay(null, List.of(lineFor(product, 1)), new BigDecimal("40000"), null, "", "", paidOne, cash("40000"), null, Role.ADMIN);

            List<Integer> ids = orderDAO.findOpenTakeAway().stream().map(Order::getId).toList();
            assertTrue(ids.contains(first.getId()));
            assertTrue(ids.contains(second.getId()));
            assertFalse("a table's order is not a take-away order", ids.contains(onATable.getId()));
            assertFalse("a paid order is not open", ids.contains(paidOne.getId()));
            assertTrue("oldest first", ids.indexOf(first.getId()) < ids.indexOf(second.getId()));

            orderDAO.cancelOpenTakeAway(first.getId());
            orderDAO.cancelOpenTakeAway(second.getId());
        } finally {
            tableDAO.setEmpty(table); // cancels the table's order
            tableDAO.remove(table);
        }
    }

    @Test
    public void aTakeAwayOrderIsPaidWithNoTableAndNoTableIsTouched() {
        Product product = newProduct(10);
        Order open = startOpen(null);
        Map<Integer, Boolean> before = tableStatuses();

        CheckoutService.PaidOrder paid = checkout.pay(null, List.of(lineFor(product, 2)), new BigDecimal("80000"),
                null, "", "", open, cash("80000"), null, Role.ADMIN);

        assertEquals(OrderStatus.PAID, paid.order().getStatus());
        assertEquals(open.getId(), paid.order().getId());
        assertNull(paid.order().getTableNumber());
        assertEquals("stock drops by the quantity sold", 8, stockOf(product));
        assertEquals("no table changed", before, tableStatuses());
        assertFalse("a paid order leaves the list", listed(open.getId()));
    }

    @Test
    public void aTakeAwayOrderWithNothingSavedYetIsPaidAsANewOrderWithNoTable() {
        Product product = newProduct(10);
        Map<Integer, Boolean> before = tableStatuses();

        CheckoutService.PaidOrder paid = checkout.pay(null, List.of(lineFor(product, 1)), new BigDecimal("40000"),
                null, "", "", null, cash("40000"), null, Role.ADMIN);

        assertEquals(OrderStatus.PAID, paid.order().getStatus());
        assertNull(paid.order().getTableNumber());
        assertEquals(9, stockOf(product));
        assertEquals(before, tableStatuses());
    }

    @Test
    public void cancellingAnOpenTakeAwayOrderEmptiesItFromTheListOnce() {
        Order open = startOpen(null);
        assertTrue(orderDAO.cancelOpenTakeAway(open.getId()));
        assertFalse(listed(open.getId()));
        assertEquals(OrderStatus.CANCELLED, orderDAO.findById(open.getId()).orElseThrow().getStatus());
        assertFalse("nothing left to cancel", orderDAO.cancelOpenTakeAway(open.getId()));
    }

    @Test
    public void cancellingNeverTouchesAPaidOrderOrATablesOrder() {
        int table = tableDAO.add("test-takeaway-table-" + UUID.randomUUID().toString().substring(0, 6), 2);
        try {
            Order onATable = startOpen(table);
            assertFalse("a table's order is cancelled by emptying the table, not here", orderDAO.cancelOpenTakeAway(onATable.getId()));
            assertEquals(OrderStatus.OPEN, orderDAO.findById(onATable.getId()).orElseThrow().getStatus());

            Order open = startOpen(null);
            Product product = newProduct(3);
            checkout.pay(null, List.of(lineFor(product, 1)), new BigDecimal("40000"), null, "", "", open, cash("40000"), null, Role.ADMIN);
            assertFalse(orderDAO.cancelOpenTakeAway(open.getId()));
            assertEquals(OrderStatus.PAID, orderDAO.findById(open.getId()).orElseThrow().getStatus());
        } finally {
            tableDAO.setEmpty(table);
            tableDAO.remove(table);
        }
    }

    @Test
    public void aTableStillPaysAndFreesItsTableAsBefore() {
        int table = tableDAO.add("test-takeaway-table-" + UUID.randomUUID().toString().substring(0, 6), 2);
        try {
            Product product = newProduct(5);
            Order open = startOpen(table);
            CheckoutService.PaidOrder paid = checkout.pay(table, List.of(lineFor(product, 1)), new BigDecimal("40000"),
                    null, "", "", open, cash("40000"), null, Role.ADMIN);
            assertEquals(Integer.valueOf(table), paid.order().getTableNumber());
            assertFalse(tableStatuses().get(table));
        } finally {
            tableDAO.remove(table);
        }
    }
}
