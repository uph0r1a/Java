package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 4.9: the note of an order line is saved, read back, changed and paid with the order. */
public class OrderNotesDaoTest {

    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private Product latte;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void makeProduct() {
        TestSession.admin();
        Category category = new Category();
        category.setName("test-note-category-" + UUID.randomUUID());
        category.setActive(true);
        categoryDAO.insert(category);
        latte = new Product();
        latte.setName("test-note-product-" + UUID.randomUUID());
        latte.setCategoryId(category.getId());
        latte.setPrice(new BigDecimal("45000"));
        latte.setStock(100);
        latte.setActive(true);
        productDAO.insert(latte);
    }

    private OrderItem line(int quantity, String note) {
        OrderItem item = new OrderItem();
        item.setProductId(latte.getId());
        item.setProductName(latte.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(latte.getPrice());
        item.setLineTotal(latte.getPrice().multiply(BigDecimal.valueOf(quantity)));
        item.setNote(note);
        return item;
    }

    private Order order(OrderStatus status, String total) {
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setStatus(status);
        order.setSubtotal(new BigDecimal(total));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal(total));
        if (status == OrderStatus.PAID) {
            order.setPaymentMethod(PaymentMethod.CASH);
            order.setPaidAt(AppClock.now());
        }
        return order;
    }

    @Test
    public void twoLinesOfOneProductKeepTheirOwnNotes() {
        Order saved = orderDAO.insert(order(OrderStatus.PAID, "135000"),
                List.of(line(1, "less sugar"), line(2, null)));
        List<OrderItem> read = orderDAO.findItemsByOrderId(saved.getId());
        assertEquals(2, read.size());
        assertEquals("less sugar", read.get(0).getNote());
        assertNull(read.get(1).getNote());
        assertEquals(2, read.get(1).getQuantity());
    }

    @Test
    public void aNoteIsCleanedBeforeItIsStored() {
        Order saved = orderDAO.insert(order(OrderStatus.PAID, "45000"), List.of(line(1, "  no \n ice  ")));
        assertEquals("no ice", orderDAO.findItemsByOrderId(saved.getId()).get(0).getNote());
        Order other = orderDAO.insert(order(OrderStatus.PAID, "45000"), List.of(line(1, "   ")));
        assertNull("a blank note is stored as nothing", orderDAO.findItemsByOrderId(other.getId()).get(0).getNote());
    }

    @Test
    public void aChangedNoteOnAnOpenOrderIsSavedAndAnUnchangedOneKeepsItsRow() {
        Order saved = orderDAO.insert(order(OrderStatus.OPEN, "90000"), List.of(line(1, "no ice"), line(1, null)));
        List<OrderItem> first = orderDAO.findItemsByOrderId(saved.getId());
        int plainRow = first.get(1).getId();

        assertTrue(orderDAO.updateIfStillOpen(saved, List.of(line(1, "extra ice"), line(1, null))));
        List<OrderItem> second = orderDAO.findItemsByOrderId(saved.getId());
        assertEquals(2, second.size());
        assertEquals("a changed note is a changed line", "extra ice", second.get(0).getNote());
        assertEquals("the untouched line keeps its row", plainRow, second.get(1).getId());

        assertTrue(orderDAO.updateIfStillOpen(saved, List.of(line(1, null), line(1, null))));
        List<OrderItem> third = orderDAO.findItemsByOrderId(saved.getId());
        assertNull(third.get(0).getNote());
    }

    @Test
    public void payingAnOpenOrderKeepsTheNotes() {
        Order saved = orderDAO.insert(order(OrderStatus.OPEN, "90000"), List.of(line(1, "take away"), line(1, null)));
        orderDAO.payExistingOrder(saved.getId(), null, PaymentMethod.CASH, BigDecimal.ZERO, null,
                new BigDecimal("90000"), List.of(line(1, "take away"), line(1, null)), null);
        List<OrderItem> read = orderDAO.findItemsByOrderId(saved.getId());
        assertEquals(2, read.size());
        assertEquals("take away", read.get(0).getNote());
        assertNull(read.get(1).getNote());
    }

    @Test
    public void stockIsTakenForEveryLineOfTheProduct() {
        orderDAO.insert(order(OrderStatus.PAID, "135000"), List.of(line(1, "less sugar"), line(2, null)));
        assertEquals(97, productDAO.findById(latte.getId()).orElseThrow().getStock());
    }
}
