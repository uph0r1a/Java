package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 3.31: the order-history search ignores case and accents for Vietnamese names. SQLite's own {@code LIKE} folds only ASCII
 * letters, so {@code nguyễn} used to miss {@code Nguyễn} and {@code đào} missed {@code Đào}; a {@code VN_KEY} function now folds
 * both sides. Runs on SQLite and on MariaDB (whose collation does the same job).
 */
public class VietnameseSearchTest {

    private final OrderDAO orderDAO = new OrderDAO();

    @BeforeClass
    public static void setUpDatabase() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    private String token;
    private Customer customer;

    @Before
    public void createAnOrderForACustomerWithAVietnameseName() {
        token = UUID.randomUUID().toString().replace("-", "");
        Category category = new Category();
        category.setName("vn-search-category-" + token);
        category.setActive(true);
        new CategoryDAO().insert(category);
        Product product = new Product();
        product.setName("vn-search-product-" + token);
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("10000"));
        product.setStock(5);
        product.setActive(true);
        product = new ProductDAO().insert(product);

        customer = new Customer();
        customer.setFullName("Nguyễn Đào " + token);
        customer.setLoyaltyPoints(0);
        customer = new CustomerDAO().insert(customer);

        OrderItem line = new OrderItem();
        line.setProductId(product.getId());
        line.setProductName(product.getName());
        line.setQuantity(1);
        line.setUnitPrice(new BigDecimal("10000"));
        line.setLineTotal(new BigDecimal("10000"));
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setCustomerId(customer.getId());
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(new BigDecimal("10000"));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("10000"));
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaidAt(AppClock.now());
        orderDAO.insert(order, List.of(line));
    }

    private int found(String keyword) {
        return orderDAO.countHistory(new OrderDAO.HistoryFilter(keyword, null, null, null));
    }

    @Test
    public void lowerCaseAccentedTextFindsACapitalisedName() {
        assertEquals(1, found("nguyễn đào " + token));
    }

    @Test
    public void upperCaseTextFindsTheSameName() {
        assertEquals(1, found("NGUYỄN ĐÀO " + token.toUpperCase()));
    }

    @Test
    public void textWithoutAccentsFindsAnAccentedName() {
        assertEquals(1, found("nguyen dao " + token));
        assertEquals(1, found("DAO " + token));
    }

    @Test
    public void aDifferentNameStillDoesNotMatch() {
        assertEquals(0, found("tran " + token));
    }

    @Test
    public void theSearchFunctionFoldsTextTheSameWayTheSearchBoxDoes() throws Exception {
        TestDatabaseSupport.requireSqlite();
        try (Connection c = DatabaseConfig.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + DatabaseConfig.searchKeySql("'Nguyễn Đào'") + ", "
                     + DatabaseConfig.searchKeySql("NULL"))) {
            assertTrue(rs.next());
            assertEquals("nguyen dao", rs.getString(1));
            assertEquals("a missing value becomes an empty text", "", rs.getString(2));
        }
    }
}
