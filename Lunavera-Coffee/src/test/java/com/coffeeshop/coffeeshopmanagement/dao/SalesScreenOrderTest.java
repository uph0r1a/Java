package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * TODO 5.31: the order of the category tabs and of the products of a category on the sales screen. Every test makes its own
 * categories and products and looks only at those, because the test database is shared by the whole run.
 */
public class SalesScreenOrderTest {

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ProductDAO productDAO = new ProductDAO();

    @BeforeClass
    public static void setUpDatabase() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    private Category newCategory(String label) {
        Category category = new Category();
        category.setName("test-order-" + label + "-" + UUID.randomUUID());
        category.setActive(true);
        return categoryDAO.insert(category);
    }

    private Product newProduct(Category category, String label) {
        Product product = new Product();
        product.setName("test-order-" + label + "-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("20000"));
        product.setStock(5);
        product.setActive(true);
        return productDAO.insert(product);
    }

    /** The ids of {@code mine}, in the order the sales screen gets them. */
    private List<Integer> categoriesInOrder(List<Category> mine) {
        Set<Integer> ids = mine.stream().map(Category::getId).collect(Collectors.toSet());
        return categoryDAO.findAll().stream().map(Category::getId).filter(ids::contains).collect(Collectors.toList());
    }

    private List<Integer> productsInOrder(List<Product> mine) {
        Set<Integer> ids = mine.stream().map(Product::getId).collect(Collectors.toSet());
        return productDAO.findAll().stream().map(Product::getId).filter(ids::contains).collect(Collectors.toList());
    }

    private static List<Integer> idsOf(List<Product> products) {
        return products.stream().map(Product::getId).collect(Collectors.toList());
    }

    private void sql(String sql, Object... params) throws Exception {
        try (Connection connection = DatabaseConfig.getConnection(); PreparedStatement st = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) st.setObject(i + 1, params[i]);
            st.executeUpdate();
        }
    }

    // ------------------------------------------------------------------ categories

    @Test
    public void aNewCategoryIsTheLastTab() {
        Category first = newCategory("a");
        Category second = newCategory("b");
        Category third = newCategory("c");
        assertEquals(List.of(first.getId(), second.getId(), third.getId()), categoriesInOrder(List.of(first, second, third)));
    }

    @Test
    public void anArrowMovesACategoryOnePlaceAndNothingElse() {
        Category a = newCategory("a"), b = newCategory("b"), c = newCategory("c");
        List<Category> mine = List.of(a, b, c);

        assertTrue(categoryDAO.move(c.getId(), -1));
        assertEquals(List.of(a.getId(), c.getId(), b.getId()), categoriesInOrder(mine));

        assertTrue(categoryDAO.move(a.getId(), 1));
        assertEquals(List.of(c.getId(), a.getId(), b.getId()), categoriesInOrder(mine));
    }

    @Test
    public void theFirstCategoryCannotGoUpNorTheLastGoDown() {
        Category a = newCategory("a"), b = newCategory("b");
        List<Category> mine = List.of(a, b);
        // the very first / very last of the whole list; mine are the last two, so b is the last of everything
        assertFalse(categoryDAO.move(b.getId(), 1));
        assertEquals(List.of(a.getId(), b.getId()), categoriesInOrder(mine));
        assertFalse(categoryDAO.move(-12345, 1));
    }

    @Test
    public void categoriesThatAllStillHaveTheOldZeroKeepTheirCreationOrderAndStillMove() throws Exception {
        Category a = newCategory("a"), b = newCategory("b"), c = newCategory("c");
        for (Category category : List.of(a, b, c)) sql("UPDATE categories SET sort_order = 0 WHERE id = ?", category.getId());
        assertEquals("equal numbers fall back to the order of creation",
                List.of(a.getId(), b.getId(), c.getId()), categoriesInOrder(List.of(a, b, c)));

        assertTrue(categoryDAO.move(b.getId(), -1));
        assertEquals(List.of(b.getId(), a.getId(), c.getId()), categoriesInOrder(List.of(a, b, c)));
    }

    // ------------------------------------------------------------------ products

    @Test
    public void aNewProductIsTheLastOfItsCategory() {
        Category category = newCategory("p");
        Product a = newProduct(category, "a"), b = newProduct(category, "b"), c = newProduct(category, "c");
        assertEquals(List.of(a.getId(), b.getId(), c.getId()), productsInOrder(List.of(a, b, c)));
    }

    @Test
    public void productsInsertedTogetherKeepTheOrderTheyWereGiven() {
        Category category = newCategory("bulk");
        List<Product> batch = new ArrayList<>();
        for (String label : List.of("x", "y", "z")) {
            Product product = new Product();
            product.setName("test-order-" + label + "-" + UUID.randomUUID());
            product.setCategoryId(category.getId());
            product.setPrice(new BigDecimal("1000"));
            product.setStock(1);
            product.setActive(true);
            batch.add(product);
        }
        productDAO.insertAll(batch); // fills in the ids
        assertEquals(idsOf(batch), productsInOrder(batch));
    }

    @Test
    public void anArrowMovesAProductWithinItsCategory() {
        Category category = newCategory("m");
        Product a = newProduct(category, "a"), b = newProduct(category, "b"), c = newProduct(category, "c");
        List<Product> mine = List.of(a, b, c);

        assertTrue(productDAO.move(b.getId(), -1));
        assertEquals(List.of(b.getId(), a.getId(), c.getId()), productsInOrder(mine));
        assertFalse("already first", productDAO.move(b.getId(), -1));
        assertTrue(productDAO.move(a.getId(), 1));
        assertEquals(List.of(b.getId(), c.getId(), a.getId()), productsInOrder(mine));
        assertFalse("already last", productDAO.move(a.getId(), 1));
    }

    @Test
    public void movingAProductLeavesTheOtherCategoriesWhereTheyWere() {
        Category one = newCategory("one"), two = newCategory("two");
        Product a1 = newProduct(one, "a1"), b1 = newProduct(two, "b1"), a2 = newProduct(one, "a2"), b2 = newProduct(two, "b2");
        List<Product> mine = List.of(a1, b1, a2, b2);
        assertEquals(idsOf(mine), productsInOrder(mine));

        assertTrue(productDAO.move(a2.getId(), -1));

        // only a1 and a2 traded places; b1 and b2 did not move
        assertEquals(List.of(a2.getId(), b1.getId(), a1.getId(), b2.getId()), productsInOrder(mine));
    }

    @Test
    public void productsThatAllStillHaveTheOldZeroKeepTheirCreationOrderAndStillMove() throws Exception {
        Category category = newCategory("zero");
        Product a = newProduct(category, "a"), b = newProduct(category, "b"), c = newProduct(category, "c");
        for (Product product : List.of(a, b, c)) sql("UPDATE products SET sort_order = 0 WHERE id = ?", product.getId());
        assertEquals(List.of(a.getId(), b.getId(), c.getId()), productsInOrder(List.of(a, b, c)));

        assertTrue(productDAO.move(c.getId(), -1));
        assertEquals(List.of(a.getId(), c.getId(), b.getId()), productsInOrder(List.of(a, b, c)));
    }

    @Test
    public void anInactiveProductIsNotSteppedOverByAnActiveOne() {
        Category category = newCategory("inactive");
        Product a = newProduct(category, "a"), hidden = newProduct(category, "hidden"), c = newProduct(category, "c");
        hidden.setActive(false);
        productDAO.update(hidden);
        // a and c are the products on sale; they trade places with each other, over the one that is not on sale
        assertTrue(productDAO.move(c.getId(), -1));
        assertEquals(List.of(c.getId(), hidden.getId(), a.getId()), productsInOrder(List.of(a, hidden, c)));
    }
}
