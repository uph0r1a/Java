package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.*;
import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 6.21 / 6.14: removing a category or a product goes through a service that checks the permission and the rules; the
 * screens only call it (from a background thread) and show the result. Runs on the shared test database.
 */
public class CategoryProductServicesTest {

    private final CategoryService categories = new CategoryService();
    private final ProductService products = new ProductService();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ProductDAO productDAO = new ProductDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void signInAsAdmin() {
        TestSession.admin();
    }

    @After
    public void signOut() {
        TestSession.clear();
    }

    private Category newCategory() {
        Category category = new Category();
        category.setName("svc-category-" + UUID.randomUUID());
        category.setActive(true);
        return categoryDAO.insert(category);
    }

    private Product newProduct(Category category) {
        Product product = new Product();
        product.setName("svc-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("15000"));
        product.setStock(5);
        product.setActive(true);
        return productDAO.insert(product);
    }

    private static void signInAsEmployee() {
        User user = new User();
        user.setId(2);
        user.setUsername("test-employee");
        user.setRole(Role.EMPLOYEE);
        Session.start(user, null);
    }

    // ---- categories ----

    @Test
    public void anEmptyCategoryIsDeleted() {
        Category category = newCategory();
        assertEquals(0, categories.productCount(category.getId()));
        assertTrue(categories.delete(category.getId()));
        assertTrue(categoryDAO.findAll().stream().noneMatch(c -> c.getId() == category.getId()));
    }

    @Test
    public void aCategoryWithAProductIsKeptAndTheCountIsReported() {
        Category category = newCategory();
        newProduct(category);
        assertEquals(1, categories.productCount(category.getId()));
        assertFalse(categories.delete(category.getId()));
        assertTrue(categoryDAO.findAll().stream().anyMatch(c -> c.getId() == category.getId()));
    }

    @Test
    public void anEmployeeCannotDeleteACategory() {
        Category category = newCategory();
        signInAsEmployee();
        assertThrows(Permissions.PermissionDeniedException.class, () -> categories.delete(category.getId()));
        assertTrue("nothing was deleted", categoryDAO.findAll().stream().anyMatch(c -> c.getId() == category.getId()));
    }

    // ---- products ----

    @Test
    public void aProductNoOrderUsesIsDeleted() {
        Product product = newProduct(newCategory());
        assertEquals(0, products.orderReferences(product.getId()));
        assertTrue(products.delete(product));
        assertTrue(productDAO.findAll().stream().noneMatch(p -> p.getId() == product.getId()));
    }

    @Test
    public void aProductThatAnOrderUsesIsNeverDeletedOnlyStopped() {
        Product product = newProduct(newCategory());
        OrderItem line = new OrderItem();
        line.setProductId(product.getId());
        line.setProductName(product.getName());
        line.setQuantity(1);
        line.setUnitPrice(product.getPrice());
        line.setLineTotal(product.getPrice());
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(product.getPrice());
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(product.getPrice());
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaidAt(AppClock.now());
        new OrderDAO().insert(order, List.of(line));

        assertEquals(1, products.orderReferences(product.getId()));
        assertFalse("the history still points at it", products.delete(product));
        assertTrue(productDAO.findAll().stream().anyMatch(p -> p.getId() == product.getId()));

        products.deactivate(product.getId());
        assertFalse(productDAO.findAll().stream().filter(p -> p.getId() == product.getId()).findFirst().orElseThrow().isActive());
    }

    @Test
    public void anEmployeeCannotDeleteOrStopAProduct() {
        Product product = newProduct(newCategory());
        signInAsEmployee();
        assertThrows(Permissions.PermissionDeniedException.class, () -> products.delete(product));
        assertThrows(Permissions.PermissionDeniedException.class, () -> products.deactivate(product.getId()));
        TestSession.admin();
        assertTrue("still there and still on sale",
                productDAO.findAll().stream().anyMatch(p -> p.getId() == product.getId() && p.isActive()));
    }

    @Test
    public void aProductWithoutAPictureDeletesWithoutAnyFileWork() {
        Product product = newProduct(newCategory());
        assertNull(product.getImagePath());
        assertTrue(products.delete(product));
    }
}
