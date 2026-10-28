package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * TODO 3.36: the date filters are ranges on the bare columns, so the indexes can be used. Three things are checked: the shape of
 * the SQL (no function around a column, one {@code ?} per bound), that it finds exactly the same orders as the old
 * {@code DATE(COALESCE(paid_at, order_date))} rule on awkward data, and that SQLite really plans it with an index.
 */
public class EffectiveDayTest {

    @BeforeClass
    public static void setUp() {
        TestDatabaseSupport.ensureReady();
    }

    private static int count(String text, char c) {
        return (int) text.chars().filter(ch -> ch == c).count();
    }

    // ---- the shape of the SQL ----

    @Test
    public void aClosedRangeIsFourPlaceholdersAndNoFunctionOnTheColumns() {
        String sql = EffectiveDay.between("");
        assertEquals(4, count(sql, '?'));
        assertFalse(sql, sql.toUpperCase(java.util.Locale.ROOT).contains("DATE("));
        assertFalse(sql, sql.toUpperCase(java.util.Locale.ROOT).contains("COALESCE"));
        assertTrue(sql, sql.contains("paid_at IS NULL"));
    }

    @Test
    public void thePrefixGoesInFrontOfEveryColumn() {
        String sql = EffectiveDay.between("o.");
        assertFalse(sql, sql.replace("o.paid_at", "").replace("o.order_date", "").contains("paid_at"));
        assertFalse(sql, sql.replace("o.paid_at", "").replace("o.order_date", "").contains("order_date"));
    }

    @Test
    public void everyCombinationHasAsManyPlaceholdersAsValues() {
        LocalDate d = LocalDate.of(2026, 10, 10);
        for (boolean from : new boolean[]{true, false}) {
            for (boolean to : new boolean[]{true, false}) {
                if (!from && !to) continue;
                String sql = EffectiveDay.condition("", from, to);
                List<String> values = EffectiveDay.params(from ? d : null, to ? d : null);
                assertEquals(from + "/" + to + ": " + sql, count(sql, '?'), values.size());
            }
        }
    }

    @Test
    public void theValuesAreTheFirstDayAndTheDayAfterTheLastForEachColumn() {
        LocalDate from = LocalDate.of(2026, 10, 10);
        LocalDate to = LocalDate.of(2026, 10, 10);
        assertEquals(List.of("2026-10-10", "2026-10-11", "2026-10-10", "2026-10-11"), EffectiveDay.params(from, to));
        assertEquals(List.of("2026-10-10", "2026-10-10"), EffectiveDay.params(from, null));
        assertEquals(List.of("2027-01-01", "2027-01-01"), EffectiveDay.params(null, LocalDate.of(2026, 12, 31)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aRangeWithNoEndIsRefused() {
        EffectiveDay.condition("", false, false);
    }

    // ---- the same orders as the old rule ----

    private static Connection memory() throws Exception {
        Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, order_date TEXT NOT NULL, paid_at TEXT)");
        }
        return c;
    }

    private static void insert(Connection c, int id, String orderDate, String paidAt) throws Exception {
        try (PreparedStatement st = c.prepareStatement("INSERT INTO orders (id, order_date, paid_at) VALUES (?, ?, ?)")) {
            st.setInt(1, id);
            st.setString(2, orderDate);
            st.setString(3, paidAt);
            st.executeUpdate();
        }
    }

    private static List<Integer> ids(Connection c, String where, List<String> params) throws Exception {
        List<Integer> result = new ArrayList<>();
        try (PreparedStatement st = c.prepareStatement("SELECT id FROM orders WHERE " + where + " ORDER BY id")) {
            for (int i = 0; i < params.size(); i++) st.setString(i + 1, params.get(i));
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) result.add(rs.getInt(1));
            }
        }
        return result;
    }

    private static final String OLD = OrderDAO.EFFECTIVE_DAY;

    /** The old filter for a closed or open range, as the code used to write it. */
    private static List<Integer> oldRule(Connection c, LocalDate from, LocalDate to) throws Exception {
        List<String> where = new ArrayList<>();
        List<String> params = new ArrayList<>();
        if (from != null) {
            where.add(OLD + " >= ?");
            params.add(from.toString());
        }
        if (to != null) {
            where.add(OLD + " <= ?");
            params.add(to.toString());
        }
        return ids(c, String.join(" AND ", where), params);
    }

    private static List<Integer> newRule(Connection c, LocalDate from, LocalDate to) throws Exception {
        return ids(c, EffectiveDay.condition("", from != null, to != null), EffectiveDay.params(from, to));
    }

    @Test
    public void theEdgesOfADayAreWhereTheOldRulePutThem() throws Exception {
        try (Connection c = memory()) {
            insert(c, 1, "2026-10-10T23:50", "2026-10-11T00:10");        // opened one day, paid the next: the paid day counts
            insert(c, 2, "2026-10-11T00:00", null);                       // never paid, opened at midnight sharp (seconds left out)
            insert(c, 3, "2026-10-10T08:00:00", "2026-10-10T23:59:59.999999999");
            insert(c, 4, "2026-10-10T09:00", "2026-10-11T00:00");         // paid at the first instant of the next day
            insert(c, 5, "2026-10-10", null);                             // a date with no time of day
            insert(c, 6, "2026-10-10 08:00:00", "2026-10-10 08:05:00");   // text written the MariaDB way (an imported file)
            insert(c, 7, "2026-10-09T10:00", null);                       // an old order with no paid time
            LocalDate day = LocalDate.of(2026, 10, 10);
            assertEquals(List.of(3, 5, 6), newRule(c, day, day));
            assertEquals(oldRule(c, day, day), newRule(c, day, day));
            LocalDate next = day.plusDays(1);
            assertEquals(List.of(1, 2, 4), newRule(c, next, next));
            assertEquals(oldRule(c, next, next), newRule(c, next, next));
        }
    }

    @Test
    public void theNewRuleFindsExactlyTheOrdersTheOldRuleFoundOnRandomData() throws Exception {
        Random random = new Random(36);
        LocalDateTime start = LocalDateTime.of(2026, 9, 25, 0, 0);
        try (Connection c = memory()) {
            for (int id = 1; id <= 3000; id++) {
                LocalDateTime opened = start.plusMinutes(random.nextInt(25 * 24 * 60));
                LocalDateTime paid = random.nextInt(5) == 0 ? null : opened.plusMinutes(random.nextInt(90));
                insert(c, id, stored(opened, random), paid == null ? null : stored(paid, random));
            }
            for (int i = 0; i < 300; i++) {
                LocalDate a = LocalDate.of(2026, 9, 22).plusDays(random.nextInt(30));
                LocalDate b = a.plusDays(random.nextInt(8));
                assertEquals("closed " + a + ".." + b, oldRule(c, a, b), newRule(c, a, b));
                assertEquals("from " + a, oldRule(c, a, null), newRule(c, a, null));
                assertEquals("to " + b, oldRule(c, null, b), newRule(c, null, b));
            }
        }
    }

    /** A time the way the databases write it: SQLite's ISO text (seconds left out when zero), or the MariaDB text with a space. */
    private static String stored(LocalDateTime time, Random random) {
        return random.nextBoolean() ? time.toString() : time.withSecond(random.nextInt(60)).toString().replace('T', ' ');
    }

    // ---- the index is used ----

    @Test
    public void sqliteUsesAnIndexForTheDayFilters() throws Exception {
        TestDatabaseSupport.requireSqlite();
        LocalDate day = LocalDate.of(2026, 10, 10);
        String where = EffectiveDay.between("");
        String sql = "SELECT * FROM orders WHERE " + where;
        for (String value : EffectiveDay.params(day, day)) {
            sql = sql.replaceFirst("\\?", "'" + value + "'");
        }
        StringBuilder plan = new StringBuilder();
        try (Connection c = DatabaseConfig.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("EXPLAIN QUERY PLAN " + sql)) {
            while (rs.next()) plan.append(rs.getString("detail")).append(' ');
        }
        assertTrue("the day filter should use idx_orders_paid_at but the plan is: " + plan, plan.toString().contains("idx_orders_paid_at"));
        assertFalse("it must not read the whole table: " + plan, plan.toString().contains("SCAN orders"));
    }
}
