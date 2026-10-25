package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 2.13: the same product name twice in one category is detected (any case, extra spaces), in another category it is fine. */
public class ProductNameCheckTest {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
        com.coffeeshop.coffeeshopmanagement.util.TestSession.admin();
    }

    @AfterClass
    public static void signOut() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.clear();
    }

    private int newCategory() {
        Category c = new Category();
        c.setName("test-cat-" + UUID.randomUUID());
        c.setActive(true);
        return categoryDAO.insert(c).getId();
    }

    private Product newProduct(String name, int categoryId) {
        Product p = new Product();
        p.setName(name);
        p.setCategoryId(categoryId);
        p.setPrice(BigDecimal.valueOf(30000));
        p.setStock(5);
        p.setActive(true);
        return productDAO.insert(p);
    }

    @Test
    public void findsTheSameNameInTheSameCategoryIgnoringCaseAndSpaces() {
        int cat = newCategory();
        String base = "test-latte-" + UUID.randomUUID();
        Product latte = newProduct(base, cat);
        assertTrue(productDAO.existsByNameInCategory(base, cat, 0));
        assertTrue(productDAO.existsByNameInCategory(base.toUpperCase(), cat, 0));
        assertTrue(productDAO.existsByNameInCategory("  " + base.replace("-", "-") + "  ", cat, 0));
        assertFalse("editing the product itself is not a duplicate", productDAO.existsByNameInCategory(base, cat, latte.getId()));
    }

    @Test
    public void vietnameseLettersAreComparedWithoutCase() {
        int cat = newCategory();
        String tag = UUID.randomUUID().toString();
        newProduct("Cà Phê Sữa " + tag, cat);
        assertTrue(productDAO.existsByNameInCategory("cà phê sữa " + tag, cat, 0));
        assertTrue(productDAO.existsByNameInCategory("CÀ  PHÊ SỮA " + tag, cat, 0));
        assertFalse("different accents are a different name", productDAO.existsByNameInCategory("Ca phe sua " + tag, cat, 0));
    }

    @Test
    public void theSameNameInAnotherCategoryIsAllowed() {
        int cat1 = newCategory();
        int cat2 = newCategory();
        String name = "test-mocha-" + UUID.randomUUID();
        newProduct(name, cat1);
        assertFalse(productDAO.existsByNameInCategory(name, cat2, 0));
    }

    @Test
    public void aNameThatDoesNotExistIsFree() {
        assertFalse(productDAO.existsByNameInCategory("test-nothing-" + UUID.randomUUID(), newCategory(), 0));
    }
}
