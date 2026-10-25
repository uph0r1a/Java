package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.*;

/**
 * TODO 3.53: the one-time job that gives the lines paid before {@code order_items.unit_cost} existed the cost their product has
 * now, once, and never again. Runs on a scratch SQLite file with only the three tables it touches (needs the SQLite driver).
 */
public class UnitCostBackfillTest {

    private static Connection scratch() throws Exception {
        Path file = Files.createTempDirectory("lunavera-backfill").resolve("scratch.db");
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE products (id INTEGER PRIMARY KEY, name TEXT, cost INTEGER)");
            st.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, status TEXT)");
            st.execute("CREATE TABLE order_items (id INTEGER PRIMARY KEY, order_id INTEGER, product_id INTEGER, unit_cost INTEGER)");
            st.execute("INSERT INTO products VALUES (1, 'A', 10000), (2, 'B', NULL)");
            st.execute("INSERT INTO orders VALUES (1, 'PAID'), (2, 'CANCELLED'), (3, 'OPEN')");
            st.execute("INSERT INTO order_items (order_id, product_id) VALUES (1, 1), (1, 2), (2, 1), (3, 1), (1, NULL)");
        }
        return c;
    }

    private static Integer cost(Connection c, int itemId) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT unit_cost FROM order_items WHERE id = " + itemId)) {
            rs.next();
            int v = rs.getInt(1);
            return rs.wasNull() ? null : v;
        }
    }

    @Test
    public void paidAndRefundedLinesGetTheirProductsCostAndOpenOrdersAreLeftAlone() throws Exception {
        try (Connection c = scratch()) {
            DatabaseConfig.backfillUnitCostOnce(c);
            assertEquals("a paid line", Integer.valueOf(10000), cost(c, 1));
            assertNull("a product with no cost stays unknown", cost(c, 2));
            assertEquals("a refunded line", Integer.valueOf(10000), cost(c, 3));
            assertNull("an open order is not sold yet", cost(c, 4));
            assertNull("a line without a product", cost(c, 5));
        }
    }

    @Test
    public void itRunsOnlyOnceSoALaterUnknownCostIsNeverFilledIn() throws Exception {
        try (Connection c = scratch()) {
            DatabaseConfig.backfillUnitCostOnce(c);
            try (Statement st = c.createStatement()) {
                st.execute("UPDATE products SET cost = 7777 WHERE id = 2"); // a cost entered after the upgrade
                st.execute("INSERT INTO order_items (id, order_id, product_id) VALUES (10, 1, 2)"); // sold later, cost unknown then
            }
            DatabaseConfig.backfillUnitCostOnce(c);
            assertNull("the flag says it ran already", cost(c, 2));
            assertNull(cost(c, 10));
        }
    }
}
