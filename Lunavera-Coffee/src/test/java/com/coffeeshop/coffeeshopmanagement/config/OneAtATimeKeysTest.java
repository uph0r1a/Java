package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * TODO 2.98: an existing database gets the three "one at a time" rules at start-up, the same way on SQLite (partial unique
 * indexes) and MariaDB (generated column and unique key); while duplicates are in the data the rule is left out (with a warning)
 * instead of stopping the program, and built at a later start once they are gone.
 */
public class OneAtATimeKeysTest {

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    private static boolean indexExists(Connection c, String table, String index) throws SQLException {
        String sql = DatabaseConfig.isSqlite()
                ? "SELECT 1 FROM sqlite_master WHERE type = 'index' AND name = ?"
                : "SELECT 1 FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = '" + table + "' AND index_name = ?";
        try (PreparedStatement st = c.prepareStatement(sql)) {
            st.setString(1, index);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void dropRule(Connection c, String table, String index) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute(DatabaseConfig.isSqlite() ? "DROP INDEX IF EXISTS " + index : "ALTER TABLE " + table + " DROP INDEX " + index);
        }
    }

    /** Builds whatever rules are missing, as the start-up does. */
    private static void startUp(Connection c) throws SQLException {
        if (DatabaseConfig.isSqlite()) {
            SchemaMigrations.ensureOneOpenOrderPerTable(c);
            SchemaMigrations.ensureOneOpenClockIn(c);
            SchemaMigrations.ensureUniqueQueueNumber(c);
        } else {
            SchemaMigrations.ensureMariaOneAtATimeKeys(c);
        }
    }

    private static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) st.setObject(i + 1, args[i]);
            st.executeUpdate();
        }
    }

    private static final String WHEN = "2500-03-04 12:00:00";

    @Test
    public void everyRuleIsThereAfterStartUp() throws Exception {
        try (Connection c = DatabaseConfig.getConnection()) {
            startUp(c);
            assertTrue(indexExists(c, "orders", SchemaMigrations.ONE_OPEN_ORDER_INDEX));
            assertTrue(indexExists(c, "time_clock", SchemaMigrations.ONE_OPEN_CLOCK_INDEX));
            assertTrue(indexExists(c, "orders", SchemaMigrations.QUEUE_NUMBER_INDEX));
        }
    }

    @Test
    public void aRuleIsLeftOutWhileOpenOrdersShareATableAndBuiltOnceTheyDoNot() throws Exception {
        int table = 800_000 + new Random().nextInt(100_000);
        try (Connection c = DatabaseConfig.getConnection()) {
            dropRule(c, "orders", SchemaMigrations.ONE_OPEN_ORDER_INDEX);
            exec(c, "INSERT INTO orders (order_date, status, subtotal, discount, total, table_number) VALUES ('" + WHEN + "', 'OPEN', 0, 0, 0, ?)", table);
            exec(c, "INSERT INTO orders (order_date, status, subtotal, discount, total, table_number) VALUES ('" + WHEN + "', 'OPEN', 0, 0, 0, ?)", table);
            startUp(c); // must not throw
            assertFalse("the duplicates stop the rule from being built", indexExists(c, "orders", SchemaMigrations.ONE_OPEN_ORDER_INDEX));
            exec(c, "UPDATE orders SET status = 'CANCELLED' WHERE table_number = ?", table);
            exec(c, "UPDATE orders SET status = 'OPEN' WHERE table_number = ? AND id = (SELECT id FROM (SELECT MIN(id) AS id FROM orders WHERE table_number = ?) x)", table, table);
            startUp(c);
            assertTrue("built once the duplicates are gone", indexExists(c, "orders", SchemaMigrations.ONE_OPEN_ORDER_INDEX));
        } finally {
            try (Connection c = DatabaseConfig.getConnection()) {
                startUp(c);
            }
        }
    }

    @Test
    public void aRuleIsLeftOutWhileAPersonHasTwoOpenClockIns() throws Exception {
        int user = 5_000_000 + new Random().nextInt(900_000);
        try (Connection c = DatabaseConfig.getConnection()) {
            dropRule(c, "time_clock", SchemaMigrations.ONE_OPEN_CLOCK_INDEX);
            exec(c, "INSERT INTO time_clock (user_id, user_name, clock_in) VALUES (?, 'x', '" + WHEN + "')", user);
            exec(c, "INSERT INTO time_clock (user_id, user_name, clock_in) VALUES (?, 'x', '" + WHEN + "')", user);
            startUp(c);
            assertFalse(indexExists(c, "time_clock", SchemaMigrations.ONE_OPEN_CLOCK_INDEX));
            exec(c, "UPDATE time_clock SET clock_out = clock_in WHERE user_id = ? AND id = (SELECT id FROM (SELECT MIN(id) AS id FROM time_clock WHERE user_id = ?) x)", user, user);
            startUp(c);
            assertTrue(indexExists(c, "time_clock", SchemaMigrations.ONE_OPEN_CLOCK_INDEX));
        } finally {
            try (Connection c = DatabaseConfig.getConnection()) {
                startUp(c);
            }
        }
    }

    @Test
    public void ordersFromBeforeGetTheirCalendarDayAsTheirTakeAwayDayAndDuplicatesBlockTheRule() throws Exception {
        try (Connection c = DatabaseConfig.getConnection()) {
            dropRule(c, "orders", SchemaMigrations.QUEUE_NUMBER_INDEX);
            // two take-away orders of the same old day with the same number (what the old race could produce), no day recorded
            exec(c, "INSERT INTO orders (order_date, status, subtotal, discount, total, queue_no) VALUES ('2501-05-06 23:10:00', 'PAID', 0, 0, 0, 77)");
            exec(c, "INSERT INTO orders (order_date, status, subtotal, discount, total, queue_no) VALUES ('2501-05-06 23:20:00', 'PAID', 0, 0, 0, 77)");
            startUp(c);
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM orders WHERE queue_no = 77 AND CAST(queue_day AS CHAR) LIKE '2501-05-06%'")) {
                rs.next();
                assertEquals("their day was filled in", 2, rs.getInt(1));
            }
            assertFalse("and the duplicate number keeps the rule out for now", indexExists(c, "orders", SchemaMigrations.QUEUE_NUMBER_INDEX));
            exec(c, "UPDATE orders SET queue_no = 78 WHERE queue_no = 77 AND order_date LIKE '%23:20:00%' OR (queue_no = 77 AND order_date LIKE '%23:20%')");
            startUp(c);
            assertTrue(indexExists(c, "orders", SchemaMigrations.QUEUE_NUMBER_INDEX));
        } finally {
            try (Connection c = DatabaseConfig.getConnection()) {
                startUp(c);
            }
        }
    }
}
