package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.service.ProductFormRules;
import com.coffeeshop.coffeeshopmanagement.service.ProductService;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 6.21: saving a product from the box goes through {@link ProductService#save}: administrator only, names and codes checked in the database. */
public class ProductServiceSaveTest {

    private final ProductService service = new ProductService();
    private final ProductDAO productDAO = new ProductDAO();
    private Category category;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void aCategory() {
        TestSession.admin();
        category = new Category();
        category.setName("test-save-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
    }

    @After
    public void signOut() {
        TestSession.admin();
    }

    private ProductFormRules.Values values(String name, String code, int stock) {
        return new ProductFormRules.Values(name, category, new BigDecimal("40000"), null, stock, null, code, "desc", null, true);
    }

    @Test
    public void aNewProductIsInsertedWithWhatTheBoxHeld() {
        String name = "test-save-product-" + UUID.randomUUID();
        Product saved = service.save(null, values(name, null, 12));
        assertTrue(saved.getId() > 0);
        Product read = productDAO.findAll().stream().filter(p -> p.getId() == saved.getId()).findFirst().orElseThrow();
        assertEquals(name, read.getName());
        assertEquals(12, read.getStock());
        assertEquals((long) category.getId(), (long) read.getCategoryId());
        assertEquals(0, new BigDecimal("40000").compareTo(read.getPrice()));
    }

    @Test
    public void anExistingProductIsChangedNotDuplicated() {
        Product saved = service.save(null, values("test-save-edit-" + UUID.randomUUID(), null, 3));
        String renamed = "test-save-renamed-" + UUID.randomUUID();
        service.save(saved, values(renamed, null, 3));
        long same = productDAO.findAll().stream().filter(p -> p.getId() == saved.getId()).count();
        assertEquals(1, same);
        assertEquals(renamed, productDAO.findAll().stream().filter(p -> p.getId() == saved.getId()).findFirst().orElseThrow().getName());
    }

    @Test
    public void theSameNameInTheSameCategoryIsRefusedButAnotherCategoryIsFine() {
        String name = "test-save-dup-" + UUID.randomUUID();
        service.save(null, values(name, null, 1));
        try {
            service.save(null, values(name, null, 1));
            fail("the same name in the same category must be refused");
        } catch (ProductService.Refused refused) {
            assertNotNull(refused.title());
            assertTrue(refused.getMessage().contains(name));
        }
        Category other = new Category();
        other.setName("test-save-other-" + UUID.randomUUID());
        other.setActive(true);
        new CategoryDAO().insert(other);
        Product elsewhere = service.save(null, new ProductFormRules.Values(name, other, new BigDecimal("1000"), null, 1, null, null, "", null, true));
        assertTrue(elsewhere.getId() > 0);
    }

    @Test
    public void aCodeAnotherProductHasIsRefused() {
        String code = "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        service.save(null, values("test-save-code-a-" + UUID.randomUUID(), code, 1));
        try {
            service.save(null, values("test-save-code-b-" + UUID.randomUUID(), code, 1));
            fail("a code in use must be refused");
        } catch (ProductService.Refused expected) {
            assertNotNull(expected.title());
        }
    }

    @Test
    public void aProductIsSavedOnlyByAnAdministrator() {
        User cashier = new User();
        cashier.setId(2);
        cashier.setUsername("test-cashier");
        cashier.setRole(Role.EMPLOYEE);
        Session.start(cashier, null);
        try {
            service.save(null, values("test-save-cashier-" + UUID.randomUUID(), null, 1));
            fail("a cashier may not save a product");
        } catch (Permissions.PermissionDeniedException expected) {
            // refused
        }
    }
}
