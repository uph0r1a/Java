package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.service.StockCheck;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 3.1: the stock re-read before the payment box, against the database. */
public class StockRecheckDbTest {

    private final ProductDAO productDAO = new ProductDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void signIn() {
        TestSession.admin();
    }

    private Product product(int stock) {
        Category category = new Category();
        category.setName("test-recheck-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        Product p = new Product();
        p.setName("test-recheck-" + UUID.randomUUID());
        p.setCategoryId(category.getId());
        p.setPrice(new BigDecimal("10000"));
        p.setStock(stock);
        p.setActive(true);
        return productDAO.insert(p);
    }

    private static OrderItem line(Product p, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(quantity);
        return item;
    }

    /** What another till's sale does, without going through a whole payment. */
    private static void setStock(Product p, int stock) throws Exception {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("UPDATE products SET stock = ? WHERE id = ?")) {
            st.setInt(1, stock);
            st.setInt(2, p.getId());
            st.executeUpdate();
        }
    }

    @Test
    public void findByIdsReturnsOnlyTheAskedProductsWithTheirCurrentStock() throws Exception {
        Product a = product(5);
        Product b = product(2);
        product(9); // not asked for
        setStock(a, 4);

        List<Product> found = productDAO.findByIds(List.of(a.getId(), b.getId(), 2_000_000_000));

        assertEquals(2, found.size());
        assertEquals(4, found.stream().filter(p -> p.getId() == a.getId()).findFirst().orElseThrow().getStock());
        assertTrue(productDAO.findByIds(List.of()).isEmpty());
    }

    @Test
    public void theCheckSeesTheStockThatAnotherTillTookAfterTheProductWasAdded() throws Exception {
        Product p = product(3);
        List<OrderItem> cart = List.of(line(p, 2));
        assertTrue(StockCheck.check(cart, productDAO).isEmpty());

        setStock(p, 1); // sold at another table

        List<StockCheck.Shortage> result = StockCheck.check(cart, productDAO);
        assertEquals(1, result.size());
        assertEquals(p.getName(), result.get(0).name());
        assertEquals(2, result.get(0).wanted());
        assertEquals(1, result.get(0).available());
    }

    @Test
    public void theCheckSeesAProductSwitchedOffMeanwhile() {
        Product p = product(10);
        List<OrderItem> cart = List.of(line(p, 1));
        productDAO.setActive(p.getId(), false);

        List<StockCheck.Shortage> result = StockCheck.check(cart, productDAO);

        assertEquals(1, result.size());
        assertEquals(StockCheck.Kind.UNAVAILABLE, result.get(0).kind());
    }
}
