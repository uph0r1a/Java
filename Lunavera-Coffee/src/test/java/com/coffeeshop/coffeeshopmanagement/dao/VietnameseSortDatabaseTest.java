package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Ingredient;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * TODO 3.84: the lists that the database sorts put names in Vietnamese order: {@code Ân} and {@code Đặng} before {@code Zung}, not
 * after it (SQLite compares by code point), and the cut that {@code LIMIT} makes after the sort keeps the first names in that order.
 * Every test makes its own rows under a marker nobody else uses (the test database is shared by the whole run).
 */
public class VietnameseSortDatabaseTest {

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final IngredientDAO ingredientDAO = new IngredientDAO();

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @AfterClass
    public static void signOut() {
        TestSession.clear();
    }

    private static String marker() {
        return "vn84" + UUID.randomUUID().toString().replace("-", "");
    }

    private Customer addCustomer(String name) {
        Customer customer = new Customer();
        customer.setFullName(name);
        return customerDAO.insert(customer);
    }

    /** The names that came back, without the marker that keeps this test's rows apart. */
    private static List<String> names(List<Customer> customers, String marker) {
        return customers.stream().map(c -> c.getFullName().replace(" " + marker, "")).collect(Collectors.toList());
    }

    @Test
    public void customersComeOutInVietnameseOrderAndNotAfterZ() {
        String marker = marker();
        for (String name : List.of("Zung", "Đặng", "Ân", "Ba", "An")) addCustomer(name + " " + marker);
        assertEquals(List.of("An", "Ân", "Ba", "Đặng", "Zung"), names(customerDAO.search(marker, 50), marker));
    }

    @Test
    public void theCutAfterTheSortKeepsTheFirstNamesInVietnameseOrder() {
        String marker = marker();
        for (String name : List.of("Zung", "Đặng", "Ân", "Ba", "An")) addCustomer(name + " " + marker);
        assertEquals("by code point the first three would be An, Ba, Zung", List.of("An", "Ân", "Ba"), names(customerDAO.search(marker, 3), marker));
        assertEquals(List.of("An"), names(customerDAO.search(marker, 1), marker));
    }

    @Test
    public void ingredientsComeOutInVietnameseOrder() {
        String marker = marker();
        for (String name : List.of("Zucchini", "Đường", "Ớt", "Ba tiêu", "Ân")) ingredientDAO.insert(name + " " + marker, "g", 100, 10);
        List<String> found = ingredientDAO.findAll().stream().filter(i -> i.name().endsWith(marker))
                .map(i -> i.name().replace(" " + marker, "")).collect(Collectors.toList());
        assertEquals(List.of("Ân", "Ba tiêu", "Đường", "Ớt", "Zucchini"), found);
    }

    @Test
    public void ingredientsThatAreEquallyLowAreListedInVietnameseOrder() {
        String marker = marker();
        for (String name : List.of("Zucchini", "Đường", "Ân")) ingredientDAO.insert(name + " " + marker, "g", 0, 5);
        List<String> found = ingredientDAO.findLow(100_000).stream().filter(i -> i.name().endsWith(marker))
                .map(Ingredient::name).map(n -> n.replace(" " + marker, "")).collect(Collectors.toList());
        assertEquals(List.of("Ân", "Đường", "Zucchini"), found);
    }

    @Test
    public void everySqliteConnectionKnowsTheCollation() throws Exception {
        TestDatabaseSupport.requireSqlite();
        try (Connection c = DatabaseConfig.getConnection(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT 'Ân' < 'Zung' COLLATE VN_ORDER, 'Đặng' < 'Zung' COLLATE VN_ORDER, 'Zung' < 'Ân'")) {
                rs.next();
                assertEquals("Ân before Zung", 1, rs.getInt(1));
                assertEquals("Đặng before Zung", 1, rs.getInt(2));
                assertEquals("without the collation SQLite puts Zung first", 1, rs.getInt(3));
            }
            try (ResultSet rs = st.executeQuery("SELECT 'ba' = 'Ba' COLLATE VN_ORDER, 'ba' = 'Bà' COLLATE VN_ORDER")) {
                rs.next();
                assertEquals("capitals do not count", 1, rs.getInt(1));
                assertEquals("marks do", 0, rs.getInt(2));
            }
        }
    }
}
