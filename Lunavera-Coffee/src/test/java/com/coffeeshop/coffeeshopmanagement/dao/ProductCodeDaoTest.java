package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 4.42: a product code is stored in capitals, found exactly, and belongs to one product. */
public class ProductCodeDaoTest {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private Category category;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void makeCategory() {
        TestSession.admin();
        category = new Category();
        category.setName("test-code-category-" + UUID.randomUUID());
        category.setActive(true);
        categoryDAO.insert(category);
    }

    private static String uniqueCode() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private Product product(String code) {
        Product p = new Product();
        p.setName("test-code-product-" + UUID.randomUUID());
        p.setCategoryId(category.getId());
        p.setPrice(new BigDecimal("20000"));
        p.setStock(5);
        p.setActive(true);
        p.setCode(code);
        return p;
    }

    @Test
    public void aCodeIsStoredInCapitalsAndFoundByAnySpelling() {
        String code = uniqueCode();
        Product saved = productDAO.insert(product("  " + code.toLowerCase() + "\n"));
        assertEquals(code, saved.getCode());
        assertEquals(saved.getId(), productDAO.findByCode(code.toLowerCase()).orElseThrow().getId());
        assertEquals(code, productDAO.findById(saved.getId()).orElseThrow().getCode());
    }

    @Test
    public void unknownOrEmptyCodesFindNothing() {
        assertTrue(productDAO.findByCode(uniqueCode()).isEmpty());
        assertTrue(productDAO.findByCode(null).isEmpty());
        assertTrue(productDAO.findByCode("  ").isEmpty());
    }

    @Test
    public void manyProductsMayHaveNoCode() {
        Product a = productDAO.insert(product(null));
        Product b = productDAO.insert(product("   "));
        assertNull(productDAO.findById(a.getId()).orElseThrow().getCode());
        assertNull(productDAO.findById(b.getId()).orElseThrow().getCode());
    }

    @Test
    public void aSecondProductCannotTakeTheCode() {
        String code = uniqueCode();
        productDAO.insert(product(code));
        assertTrue(productDAO.codeTaken(code.toLowerCase(), 0));
        try {
            productDAO.insert(product(code.toLowerCase()));
            fail("the code is taken");
        } catch (DataAccessException expected) {
            assertEquals(com.coffeeshop.coffeeshopmanagement.util.I18n.t("products.code.taken"), expected.getMessage());
        }
    }

    @Test
    public void aProductMayKeepItsOwnCodeWhenSaved() {
        String code = uniqueCode();
        Product saved = productDAO.insert(product(code));
        assertFalse(productDAO.codeTaken(code, saved.getId()));
        saved.setPrice(new BigDecimal("21000"));
        productDAO.update(saved);
        assertEquals(code, productDAO.findById(saved.getId()).orElseThrow().getCode());
    }

    @Test
    public void aCodeCanBeRemovedAndTakenByAnother() {
        String code = uniqueCode();
        Product first = productDAO.insert(product(code));
        first.setCode(null);
        productDAO.update(first);
        assertNull(productDAO.findById(first.getId()).orElseThrow().getCode());
        Product second = productDAO.insert(product(code));
        assertEquals(second.getId(), productDAO.findByCode(code).orElseThrow().getId());
    }

    @Test
    public void aBadCodeIsRefusedByTheDaoToo() {
        try {
            productDAO.insert(product("not a code!"));
            fail("spaces and signs are refused");
        } catch (DataAccessException expected) {
            // fine
        }
    }
}
