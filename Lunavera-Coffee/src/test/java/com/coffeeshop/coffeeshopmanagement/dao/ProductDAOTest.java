package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public class ProductDAOTest {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private Category category;

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void createCategory() {
        category = new Category();
        category.setName("test-product-category-" + UUID.randomUUID());
        category.setActive(true);
        categoryDAO.insert(category);
    }

    private Product newProduct(BigDecimal price, int stock) {
        Product product = new Product();
        product.setName("test-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(price);
        product.setStock(stock);
        product.setActive(true);
        return productDAO.insert(product);
    }

    @Test
    public void insertThenFindAllJoinsTheCategoryName() {
        Product product = newProduct(new BigDecimal("25000"), 10);
        Product reloaded = productDAO.findAll().stream()
                .filter(p -> p.getId() == product.getId()).findFirst().orElseThrow();
        assertEquals(category.getName(), reloaded.getCategoryName());
        assertEquals(0, new BigDecimal("25000").compareTo(reloaded.getPrice()));
        assertEquals(10, reloaded.getStock());
        assertTrue(reloaded.isActive());
    }

    @Test
    public void updatePersistsEveryField() {
        Product product = newProduct(new BigDecimal("10000"), 5);
        product.setName("renamed-" + UUID.randomUUID());
        product.setPrice(new BigDecimal("15000"));
        product.setStock(20);
        product.setActive(false);
        productDAO.update(product);

        Product reloaded = productDAO.findAll().stream()
                .filter(p -> p.getId() == product.getId()).findFirst().orElseThrow();
        assertEquals(product.getName(), reloaded.getName());
        assertEquals(0, new BigDecimal("15000").compareTo(reloaded.getPrice()));
        assertEquals(20, reloaded.getStock());
        assertFalse(reloaded.isActive());
    }

    @Test
    public void countOrderReferencesIsZeroForAProductNeverSold() {
        Product product = newProduct(new BigDecimal("1000"), 1);
        assertEquals(0, productDAO.countOrderReferences(product.getId()));
    }

    @Test
    public void deleteRemovesTheRow() {
        Product product = newProduct(new BigDecimal("1000"), 1);
        productDAO.delete(product.getId());
        boolean stillThere = productDAO.findAll().stream().anyMatch(p -> p.getId() == product.getId());
        assertFalse(stillThere);
    }

    // --- editing a product must not undo sales (TODO 3.2) ---

    private Product reload(Product product) {
        return productDAO.findAll().stream().filter(p -> p.getId() == product.getId()).findFirst().orElseThrow();
    }

    /** What a payment does while an edit dialog is open: the stock changes behind the dialog's back. */
    private void saleTakesStockTo(Product product, int newStock) throws Exception {
        try (java.sql.Connection connection = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection();
             java.sql.PreparedStatement statement = connection.prepareStatement("UPDATE products SET stock = ? WHERE id = ?")) {
            statement.setInt(1, newStock);
            statement.setInt(2, product.getId());
            statement.executeUpdate();
        }
    }

    @Test
    public void savingAProductWithoutTouchingItsStockKeepsSalesMadeSinceItWasRead() throws Exception {
        Product created = newProduct(new BigDecimal("10000"), 10);
        Product inDialog = reload(created);          // the dialog reads stock 10
        saleTakesStockTo(created, 7);                // a sale of 3 is paid meanwhile
        inDialog.setName("renamed-" + UUID.randomUUID());
        productDAO.update(inDialog);                 // only the name was changed

        assertEquals("the sold items must not come back", 7, reload(created).getStock());
        productDAO.update(inDialog);                 // saving the same object again changes nothing either
        assertEquals(7, reload(created).getStock());
    }

    @Test
    public void aStockNumberTypedInIsStillWrittenAsGiven() throws Exception {
        Product created = newProduct(new BigDecimal("10000"), 10);
        Product inDialog = reload(created);
        saleTakesStockTo(created, 7);
        inDialog.setStock(25);                       // a stock-take: the person counted the shelf
        productDAO.update(inDialog);

        assertEquals(25, reload(created).getStock());
    }
}
