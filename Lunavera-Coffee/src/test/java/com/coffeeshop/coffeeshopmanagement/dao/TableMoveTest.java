package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.CheckoutService;
import com.coffeeshop.coffeeshopmanagement.service.SplitBillRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 4.10, rule D10: moving an order to another table, joining two orders, and paying only some pieces of an order. */
public class TableMoveTest {

    private final OrderDAO orderDAO = new OrderDAO();
    private final TableDAO tableDAO = new TableDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final TableMoveDAO moves = new TableMoveDAO();
    private int t1;
    private int t2;
    private int t3;
    private Product coffee;
    private Product tea;

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
    public void freshTables() {
        TestSession.admin();
        List<DiningTable> tables = tableDAO.findAll();
        t1 = tables.get(0).getTableNumber();
        t2 = tables.get(1).getTableNumber();
        t3 = tables.get(2).getTableNumber();
        for (int t : new int[]{t1, t2, t3}) tableDAO.setEmpty(t);
        Category category = new Category();
        category.setName("test-move-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        coffee = product(category, "20000", 50);
        tea = product(category, "30000", 50);
    }

    private Product product(Category category, String price, int stock) {
        Product p = new Product();
        p.setName("test-move-" + UUID.randomUUID());
        p.setCategoryId(category.getId());
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setActive(true);
        return productDAO.insert(p);
    }

    private OrderItem line(Product p, int quantity, String note) {
        OrderItem item = new OrderItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(p.getPrice());
        item.setLineTotal(p.getPrice().multiply(BigDecimal.valueOf(quantity)));
        item.setNote(note);
        return item;
    }

    private Order openAt(int table, Integer customerId, OrderItem... lines) {
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setStatus(OrderStatus.OPEN);
        order.setTableNumber(table);
        order.setCustomerId(customerId);
        BigDecimal total = SplitBillRules.sum(List.of(lines));
        order.setSubtotal(total);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(total);
        Order saved = orderDAO.insert(order, List.of(lines));
        tableDAO.setOccupied(table, saved.getId());
        return saved;
    }

    private DiningTable table(int number) {
        return tableDAO.findAll().stream().filter(t -> t.getTableNumber() == number).findFirst().orElseThrow();
    }

    private Customer customer() {
        Customer c = new Customer();
        c.setFullName("Khách bàn " + UUID.randomUUID().toString().substring(0, 6));
        c.setPhone("09" + String.format(java.util.Locale.ROOT, "%08d", new java.util.Random().nextInt(100_000_000)));
        c.setCreatedAt(LocalDateTime.now());
        return new CustomerDAO().insert(c);
    }

    // ------------------------------------------------------------------ transfer

    @Test
    public void anOrderMovesToAFreeTableAndTheTablesChangeTogether() {
        Order order = openAt(t1, null, line(coffee, 2, null));
        moves.transfer(t1, t2);
        assertEquals(Integer.valueOf(t2), orderDAO.findById(order.getId()).orElseThrow().getTableNumber());
        assertFalse(table(t1).isOccupied());
        assertNull(table(t1).getCurrentOrderId());
        assertTrue(table(t2).isOccupied());
        assertEquals(Integer.valueOf(order.getId()), table(t2).getCurrentOrderId());
        assertEquals(2, orderDAO.findItemsByOrderId(order.getId()).get(0).getQuantity());
        assertTrue(orderDAO.findOpenOrderByTable(t1).isEmpty());
        assertEquals(order.getId(), orderDAO.findOpenOrderByTable(t2).orElseThrow().getId());
    }

    @Test
    public void aTransferToATakenTableOrFromAnEmptyOneIsRefusedAndChangesNothing() {
        Order a = openAt(t1, null, line(coffee, 1, null));
        Order b = openAt(t2, null, line(tea, 1, null));
        try {
            moves.transfer(t1, t2);
            fail("the target is taken");
        } catch (DataAccessException expected) {
            assertFalse(expected.getMessage().isBlank());
        }
        try {
            moves.transfer(t3, t1);
            fail("nothing to move");
        } catch (DataAccessException expected) {
            // fine
        }
        try {
            moves.transfer(t1, t1);
            fail("same table");
        } catch (DataAccessException expected) {
            // fine
        }
        try {
            moves.transfer(t1, 99999);
            fail("no such table");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(Integer.valueOf(t1), orderDAO.findById(a.getId()).orElseThrow().getTableNumber());
        assertEquals(Integer.valueOf(t2), orderDAO.findById(b.getId()).orElseThrow().getTableNumber());
    }

    @Test
    public void aTransferIsWrittenToTheAuditLog() {
        Order order = openAt(t1, null, line(coffee, 1, null));
        moves.transfer(t1, t3);
        var entries = new AuditDAO().find(new AuditDAO.Filter(null, null, AuditAction.TABLE_TRANSFERRED, null, "#" + order.getId()), 10, 0);
        assertEquals(1, entries.size());
        assertEquals(String.valueOf(t1), entries.get(0).oldValue());
        assertEquals(String.valueOf(t3), entries.get(0).newValue());
    }

    // ------------------------------------------------------------------ merge

    @Test
    public void twoOrdersMergeIntoOneAndTheSecondTableIsFree() {
        Customer kept = customer();
        Order a = openAt(t1, kept.getId(), line(coffee, 2, null), line(tea, 1, "ít đường"));
        Order b = openAt(t2, null, line(coffee, 1, null), line(tea, 2, null));

        moves.merge(t1, t2);

        List<OrderItem> lines = orderDAO.findItemsByOrderId(a.getId());
        assertEquals(3, lines.size());
        assertEquals(3, lines.stream().filter(l -> l.getProductId() == coffee.getId()).findFirst().orElseThrow().getQuantity());
        Order merged = orderDAO.findById(a.getId()).orElseThrow();
        assertEquals(OrderStatus.OPEN, merged.getStatus());
        assertEquals("3 coffee + 1 tea (note) + 2 tea = 60000 + 30000 + 60000", 0, new BigDecimal("150000").compareTo(merged.getTotal()));
        assertEquals(0, new BigDecimal("150000").compareTo(merged.getSubtotal()));
        assertEquals(kept.getId(), merged.getCustomerId().intValue());
        Order gone = orderDAO.findById(b.getId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, gone.getStatus());
        assertTrue(orderDAO.findOpenOrderByTable(t2).isEmpty());
        assertFalse(table(t2).isOccupied());
        assertTrue(table(t1).isOccupied());
        assertEquals(1, new AuditDAO().find(new AuditDAO.Filter(null, null, AuditAction.TABLES_MERGED, null, "#" + a.getId()), 10, 0).size());
    }

    @Test
    public void theMergedOrderTakesTheOtherCustomerWhenItHasNone() {
        Customer other = customer();
        Order a = openAt(t1, null, line(coffee, 1, null));
        openAt(t2, other.getId(), line(tea, 1, null));
        moves.merge(t1, t2);
        assertEquals(other.getId(), orderDAO.findById(a.getId()).orElseThrow().getCustomerId().intValue());
    }

    @Test
    public void aMergeNeedsTwoOpenOrders() {
        openAt(t1, null, line(coffee, 1, null));
        try {
            moves.merge(t1, t3);
            fail("the other table has no order");
        } catch (DataAccessException expected) {
            // fine
        }
        try {
            moves.merge(t1, t1);
            fail("same table");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(1, orderDAO.findItemsByOrderId(orderDAO.findOpenOrderByTable(t1).orElseThrow().getId()).size());
    }

    @Test
    public void nobodySignedInCannotMoveATable() {
        openAt(t1, null, line(coffee, 1, null));
        TestSession.clear();
        try {
            moves.transfer(t1, t2);
            fail("not signed in");
        } catch (RuntimeException expected) {
            // fine
        }
        TestSession.admin();
        assertTrue(orderDAO.findOpenOrderByTable(t1).isPresent());
    }

    // ------------------------------------------------------------------ split bill

    private static CheckoutService.Payment cash(String total) {
        return new CheckoutService.Payment(PaymentMethod.CASH, BigDecimal.ZERO, null, new BigDecimal(total), null, null);
    }

    @Test
    public void payingOnePartLeavesTheRestOpenOnTheTable() {
        Order open = openAt(t1, null, line(coffee, 3, null), line(tea, 1, null));
        List<OrderItem> cart = orderDAO.findItemsByOrderId(open.getId());
        SplitBillRules.Split split = SplitBillRules.split(cart, Map.of(0, 1)); // one coffee: 20000
        int stockBefore = productDAO.findAll().stream().filter(p -> p.getId() == coffee.getId()).findFirst().orElseThrow().getStock();

        CheckoutService.PaidOrder paid = new CheckoutService().paySplit(t1, open, split.part(), split.remainder(), null, "", "", cash("20000"), null, Role.ADMIN);

        Order paidOrder = orderDAO.findById(paid.order().getId()).orElseThrow();
        assertEquals(OrderStatus.PAID, paidOrder.getStatus());
        assertEquals(0, new BigDecimal("20000").compareTo(paidOrder.getTotal()));
        assertEquals(1, orderDAO.findItemsByOrderId(paidOrder.getId()).size());
        Order rest = orderDAO.findById(open.getId()).orElseThrow();
        assertEquals(OrderStatus.OPEN, rest.getStatus());
        assertEquals("2 coffee + 1 tea", 0, new BigDecimal("70000").compareTo(rest.getTotal()));
        assertEquals(2, orderDAO.findItemsByOrderId(open.getId()).size());
        assertTrue("the table is still taken by the open rest", table(t1).isOccupied());
        assertEquals(Integer.valueOf(open.getId()), table(t1).getCurrentOrderId());
        assertEquals(open.getId(), orderDAO.findOpenOrderByTable(t1).orElseThrow().getId());
        assertEquals("only the paid piece left the stock", stockBefore - 1,
                productDAO.findAll().stream().filter(p -> p.getId() == coffee.getId()).findFirst().orElseThrow().getStock());
        assertEquals(1, new AuditDAO().find(new AuditDAO.Filter(null, null, AuditAction.BILL_SPLIT, null, "#" + paidOrder.getId()), 10, 0).size());
    }

    @Test
    public void thePaidPartAndTheRestLaterPayTheWholeOrder() {
        Order open = openAt(t1, null, line(coffee, 2, null), line(tea, 2, null));
        List<OrderItem> cart = orderDAO.findItemsByOrderId(open.getId());
        SplitBillRules.Split split = SplitBillRules.split(cart, Map.of(0, 1, 1, 1)); // 20000 + 30000 now
        CheckoutService checkout = new CheckoutService();
        checkout.paySplit(t1, open, split.part(), split.remainder(), null, "", "", cash("50000"), null, Role.ADMIN);
        Order rest = orderDAO.findById(open.getId()).orElseThrow();
        List<OrderItem> restLines = orderDAO.findItemsByOrderId(rest.getId());
        CheckoutService.PaidOrder second = checkout.pay(t1, restLines, SplitBillRules.sum(restLines), null, "", "", rest, cash("50000"), null, Role.ADMIN);
        assertEquals(OrderStatus.PAID, orderDAO.findById(second.order().getId()).orElseThrow().getStatus());
        assertFalse("paying the rest frees the table", table(t1).isOccupied());
    }

    @Test
    public void aSplitOfAnOrderThatIsNotOpenAnyMoreRollsTheWholePaymentBack() {
        Order open = openAt(t1, null, line(coffee, 2, null), line(tea, 1, null));
        List<OrderItem> cart = orderDAO.findItemsByOrderId(open.getId());
        SplitBillRules.Split split = SplitBillRules.split(cart, Map.of(0, 1));
        tableDAO.setEmpty(t1); // another till emptied the table: the open order is cancelled
        int stockBefore = productDAO.findAll().stream().filter(p -> p.getId() == coffee.getId()).findFirst().orElseThrow().getStock();
        try {
            new CheckoutService().paySplit(t1, open, split.part(), split.remainder(), null, "", "", cash("20000"), null, Role.ADMIN);
            fail("the order is not open");
        } catch (DataAccessException expected) {
            assertFalse(expected.getMessage().isBlank());
        }
        assertEquals("nothing was paid and no stock was taken", stockBefore,
                productDAO.findAll().stream().filter(p -> p.getId() == coffee.getId()).findFirst().orElseThrow().getStock());
    }

    @Test
    public void aSplitNeedsAnOpenOrderAndBothParts() {
        Order open = openAt(t1, null, line(coffee, 2, null));
        List<OrderItem> cart = orderDAO.findItemsByOrderId(open.getId());
        CheckoutService checkout = new CheckoutService();
        for (Runnable bad : List.<Runnable>of(
                () -> checkout.paySplit(t1, null, cart, cart, null, "", "", cash("20000"), null, Role.ADMIN),
                () -> checkout.paySplit(t1, open, cart, List.of(), null, "", "", cash("40000"), null, Role.ADMIN),
                () -> checkout.paySplit(t1, open, List.of(), cart, null, "", "", cash("0"), null, Role.ADMIN))) {
            try {
                bad.run();
                fail("refused");
            } catch (DataAccessException expected) {
                // fine
            }
        }
        assertEquals(OrderStatus.OPEN, orderDAO.findById(open.getId()).orElseThrow().getStatus());
    }
}
