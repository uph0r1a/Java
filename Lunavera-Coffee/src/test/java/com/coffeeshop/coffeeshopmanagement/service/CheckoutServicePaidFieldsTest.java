package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 6.78 (mutation sweep, profile pit-db): what {@link CheckoutService#pay} writes for a bill made at the till and not saved
 * before: the cash handed over, the time of payment and the table that is freed; and that an order object without a saved id is
 * never taken for the saved open order. Runs on the shared test database.
 */
public class CheckoutServicePaidFieldsTest {

    private final CheckoutService checkout = new CheckoutService();
    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private Category category;

    @BeforeClass
    public static void setUpDatabase() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @AfterClass
    public static void signOut() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.clear();
    }

    @Before
    public void createCategory() {
        category = new Category();
        category.setName("test-checkout-fields-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
    }

    private Product newProduct(int stock, BigDecimal price) {
        Product product = new Product();
        product.setName("test-checkout-fields-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(price);
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

    private static CheckoutService.Payment cashHandedOver(String total, String handedOver) {
        return new CheckoutService.Payment(PaymentMethod.CASH, BigDecimal.ZERO, null, new BigDecimal(total),
                new BigDecimal(handedOver), null, 0, null, null, null);
    }

    @Test
    public void aBillMadeAtTheTillSavesTheCashHandedOverAndTheTimeAndFreesTheTable() {
        Product product = newProduct(10, new BigDecimal("50000"));
        TableDAO tables = new TableDAO();
        tables.setOccupied(6, null);
        assertTrue(tables.findAll().stream().anyMatch(t -> t.getTableNumber() == 6 && t.isOccupied()));

        CheckoutService.PaidOrder paid = checkout.pay(6, List.of(lineFor(product, 2)), new BigDecimal("100000"),
                null, null, null, null, cashHandedOver("100000", "200000"), null, Role.ADMIN);

        Order read = orderDAO.findById(paid.order().getId()).orElseThrow();
        assertEquals(OrderStatus.PAID, read.getStatus());
        assertNotNull("the cash handed over is saved", read.getCashReceived());
        assertEquals(0, new BigDecimal("200000").compareTo(read.getCashReceived()));
        assertNotNull("the time of payment is saved", read.getPaidAt());
        assertTrue("paying frees the table", tables.findAll().stream().anyMatch(t -> t.getTableNumber() == 6 && !t.isOccupied()));
    }

    @Test
    public void anOrderObjectWithoutASavedIdIsPaidAsANewOrder() {
        Product product = newProduct(10, new BigDecimal("50000"));
        Order notSaved = new Order();
        assertEquals(0, notSaved.getId());

        CheckoutService.PaidOrder paid = checkout.pay(null, List.of(lineFor(product, 1)), new BigDecimal("50000"),
                null, null, null, notSaved, cashHandedOver("50000", "50000"), null, Role.ADMIN);

        assertTrue("a new row was made", paid.order().getId() > 0);
        assertEquals(OrderStatus.PAID, orderDAO.findById(paid.order().getId()).orElseThrow().getStatus());
    }

    @Test
    public void aSplitFromAnOrderWithoutASavedIdIsRefusedWithTheNoLinesSentence() {
        Product product = newProduct(10, new BigDecimal("50000"));
        List<OrderItem> lines = List.of(lineFor(product, 1));
        try {
            checkout.paySplit(6, new Order(), lines, lines, null, null, null, cashHandedOver("50000", "50000"), null, Role.ADMIN);
            fail("an order that was never saved cannot be split");
        } catch (DataAccessException refused) {
            assertEquals(I18n.t("split.val.nolines"), refused.getMessage());
        }
    }
}
