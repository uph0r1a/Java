package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.BusinessDay;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * TODO 2.98: three "one at a time" rules that MariaDB used to leave to the code alone are now rules of the database on both
 * databases: one OPEN order per table, one open clock-in per person, and one take-away number once per shop day. Run on whichever
 * database the test run uses ({@code -Dlunavera.test.db=mariadb} for a real MariaDB).
 */
public class OneAtATimeRulesTest {

    private static final AtomicInteger NEXT_DAY = new AtomicInteger(0);

    private final OrderDAO orderDAO = new OrderDAO();
    private final TableDAO tableDAO = new TableDAO();
    private LocalDate savedToday;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void setUp() {
        savedToday = AppClock.today();
        BusinessDay.resetForTests();
        TestSession.admin();
    }

    @After
    public void restore() {
        AppClock.pinDate(savedToday);
        BusinessDay.resetForTests();
        TestSession.admin();
    }

    private static void rawOrder(Connection c, String status, Integer table) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(
                "INSERT INTO orders (order_date, status, subtotal, discount, total, table_number) VALUES (?, ?, 0, 0, 0, ?)")) {
            st.setString(1, com.coffeeshop.coffeeshopmanagement.util.DbTime.write(LocalDateTime.of(2500, 1, 1, 12, 0)));
            st.setString(2, status);
            if (table == null) st.setNull(3, java.sql.Types.INTEGER); else st.setInt(3, table);
            st.executeUpdate();
        }
    }

    private static void rawClock(Connection c, int userId, boolean open) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(
                "INSERT INTO time_clock (user_id, user_name, clock_in, clock_out) VALUES (?, 'x', ?, ?)")) {
            st.setInt(1, userId);
            st.setString(2, com.coffeeshop.coffeeshopmanagement.util.DbTime.write(LocalDateTime.of(2500, 1, 1, 8, 0)));
            st.setString(3, open ? null : com.coffeeshop.coffeeshopmanagement.util.DbTime.write(LocalDateTime.of(2500, 1, 1, 9, 0)));
            st.executeUpdate();
        }
    }

    private static int uniqueTableNumber() {
        return 700_000 + new Random().nextInt(200_000);
    }

    private static int uniqueUserId() {
        return 4_000_000 + new Random().nextInt(900_000);
    }

    private static void expectUniqueViolation(SQLException_Action action) {
        try {
            action.run();
            fail("the database must refuse this");
        } catch (SQLException e) {
            assertTrue("a unique rule, not another error: " + e.getMessage(), UniqueViolation.isUnique(e));
        }
    }

    private interface SQLException_Action {
        void run() throws SQLException;
    }

    // ---- one OPEN order per table ----

    @Test
    public void theDatabaseRefusesASecondOpenOrderOnATable() throws Exception {
        int table = uniqueTableNumber();
        try (Connection c = DatabaseConfig.getConnection()) {
            rawOrder(c, "OPEN", table);
            expectUniqueViolation(() -> rawOrder(c, "OPEN", table));
        }
    }

    @Test
    public void paidAndCancelledOrdersAndTakeAwayOrdersNeverBlockATable() throws Exception {
        int table = uniqueTableNumber();
        try (Connection c = DatabaseConfig.getConnection()) {
            rawOrder(c, "PAID", table);
            rawOrder(c, "PAID", table);
            rawOrder(c, "CANCELLED", table);
            rawOrder(c, "OPEN", table);            // the one open order the table may have
            rawOrder(c, "OPEN", null);             // take-away orders have no table: any number of them
            rawOrder(c, "OPEN", null);
        }
    }

    @Test
    public void aTableIsFreeAgainOnceItsOrderIsNoLongerOpen() throws Exception {
        int table = uniqueTableNumber();
        try (Connection c = DatabaseConfig.getConnection()) {
            rawOrder(c, "OPEN", table);
            try (PreparedStatement st = c.prepareStatement("UPDATE orders SET status = 'PAID' WHERE table_number = ?")) {
                st.setInt(1, table);
                st.executeUpdate();
            }
            rawOrder(c, "OPEN", table);
        }
    }

    @Test
    public void tillsOpeningTheSameFreeTableAtTheSameMomentGetOneOrderAndTheRestAClearRefusal() throws Exception {
        int table = tableDAO.add("one-at-a-time-" + System.nanoTime(), 4);
        List<Callable<String>> tills = new ArrayList<>();
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < 6; i++) {
            tills.add(() -> {
                go.await();
                Order order = new Order();
                order.setOrderDate(AppClock.now());
                order.setStatus(OrderStatus.OPEN);
                order.setTableNumber(table);
                order.setSubtotal(BigDecimal.ZERO);
                order.setDiscount(BigDecimal.ZERO);
                order.setTotal(BigDecimal.ZERO);
                try {
                    orderDAO.insert(order, List.of());
                    return "saved";
                } catch (DataAccessException e) {
                    return e.getMessage();
                }
            });
        }
        List<String> results = runTogether(tills, go);
        assertEquals("exactly one till opened the table: " + results, 1, results.stream().filter("saved"::equals).count());
        assertEquals("and the table has exactly one open order", 1, count("SELECT COUNT(*) FROM orders WHERE status = 'OPEN' AND table_number = " + table));
        String collision = I18n.t("order.table.collision", table);
        String busy = I18n.t("db.error.busy"); // SQLite may also answer "busy, try again": a clear refusal too, nothing was saved
        for (String r : results) {
            if (!"saved".equals(r)) {
                assertTrue("a clear message, not a database error: " + r,
                        r.equals(collision) || r.contains(busy) || r.contains(String.valueOf(table))); // order.table.hasopen names the table
            }
        }
    }

    // ---- one open clock-in per person ----

    @Test
    public void theDatabaseRefusesASecondOpenClockIn() throws Exception {
        int user = uniqueUserId();
        try (Connection c = DatabaseConfig.getConnection()) {
            rawClock(c, user, true);
            expectUniqueViolation(() -> rawClock(c, user, true));
            rawClock(c, user, false);                 // finished stretches may be many
            rawClock(c, user, false);
            rawClock(c, user + 1, true);              // another person may be clocked in at the same time
        }
    }

    @Test
    public void aPersonCanClockInAgainAfterClockingOut() throws Exception {
        int user = uniqueUserId();
        try (Connection c = DatabaseConfig.getConnection()) {
            rawClock(c, user, true);
            try (PreparedStatement st = c.prepareStatement("UPDATE time_clock SET clock_out = clock_in WHERE user_id = ?")) {
                st.setInt(1, user);
                st.executeUpdate();
            }
            rawClock(c, user, true);
        }
    }

    @Test
    public void clockingTheSamePersonInOnSeveralTillsAtOnceLeavesOneOpenStretch() throws Exception {
        User u = new User();
        u.setId(uniqueUserId());
        u.setUsername("clock-race-" + u.getId());
        u.setRole(Role.EMPLOYEE);
        Session.start(u, null);
        TimeClockDAO clock = new TimeClockDAO();
        CountDownLatch go = new CountDownLatch(1);
        List<Callable<String>> tills = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tills.add(() -> {
                go.await();
                try {
                    clock.clockIn();
                    return "in";
                } catch (DataAccessException e) {
                    return e.getMessage();
                }
            });
        }
        List<String> results = runTogether(tills, go);
        assertEquals("one clock-in won: " + results, 1, results.stream().filter("in"::equals).count());
        assertEquals(1, count("SELECT COUNT(*) FROM time_clock WHERE clock_out IS NULL AND user_id = " + u.getId()));
        for (String r : results) {
            if (!"in".equals(r)) {
                assertTrue("a clear message: " + r, r.equals(I18n.t("time.clock.already.in")) || r.contains(I18n.t("db.error.busy")));
            }
        }
    }

    // ---- one take-away number once per shop day ----

    private Order takeAway(LocalDateTime at) {
        Order order = new Order();
        order.setOrderDate(at);
        order.setStatus(OrderStatus.OPEN); // an open take-away order needs no lines; it gets its number all the same
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        return orderDAO.insert(order, List.of());
    }

    @Test
    public void theDatabaseRefusesTheSameTakeAwayNumberTwiceOnADay() throws Exception {
        LocalDate day = LocalDate.of(2600, 1, 1).plusDays(NEXT_DAY.incrementAndGet());
        try (Connection c = DatabaseConfig.getConnection()) {
            rawQueue(c, day, 1);
            rawQueue(c, day, 2);
            rawQueue(c, day.plusDays(1), 1);          // the number starts again the next day
            expectUniqueViolation(() -> rawQueue(c, day, 2));
        }
    }

    private static void rawQueue(Connection c, LocalDate day, int number) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(
                "INSERT INTO orders (order_date, status, subtotal, discount, total, queue_no, queue_day) VALUES (?, 'PAID', 0, 0, 0, ?, ?)")) {
            st.setString(1, com.coffeeshop.coffeeshopmanagement.util.DbTime.write(day.atTime(12, 0)));
            st.setInt(2, number);
            st.setString(3, day.toString());
            st.executeUpdate();
        }
    }

    @Test
    public void takeAwayOrdersSavedAtTheSameMomentGetDifferentNumbers() throws Exception {
        LocalDate day = LocalDate.of(2700, 1, 1).plusDays(NEXT_DAY.incrementAndGet());
        CountDownLatch go = new CountDownLatch(1);
        List<String> refusals = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Callable<Integer>> tills = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            final int n = i;
            tills.add(() -> {
                go.await();
                try {
                    return takeAway(day.atTime(10, n)).getQueueNo();
                } catch (DataAccessException busy) {
                    refusals.add(String.valueOf(busy.getMessage()) + " <- " + busy.getCause());
                    return null; // SQLite: "busy, try again": nothing saved, no number taken
                }
            });
        }
        List<Integer> given = runTogether(tills, go);
        List<Integer> numbers = new ArrayList<>();
        for (Integer number : given) if (number != null) numbers.add(number);
        assertEquals("all different: " + given, numbers.size(), new HashSet<>(numbers).size());
        assertFalse("at least one till was served", numbers.isEmpty());
        if (!com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.isSqlite()) {
            // MariaDB takes every order: a refused duplicate number is tried again with the newest rows
            assertEquals("every order got a number: " + given + " refused: " + refusals, 8, numbers.size());
            assertEquals("and they are 1 to 8 with no gap", Set.of(1, 2, 3, 4, 5, 6, 7, 8), new HashSet<>(numbers));
        }
    }

    @Test
    public void theNumberIsReadFromTheDayTheOrderWasSavedWith() {
        LocalDate day = LocalDate.of(2800, 1, 1).plusDays(NEXT_DAY.incrementAndGet());
        assertEquals(Integer.valueOf(1), takeAway(day.atTime(23, 0)).getQueueNo());
        assertEquals(Integer.valueOf(2), takeAway(day.atTime(23, 30)).getQueueNo());
        assertEquals("next calendar day: starts again", Integer.valueOf(1), takeAway(day.plusDays(1).atTime(0, 5)).getQueueNo());
    }

    // ---- helpers ----

    private static long count(String sql) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection(); java.sql.Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Runs the tasks on their own threads, released together, and returns what each gave back (an exception fails the test). */
    private static <T> List<T> runTogether(List<Callable<T>> tasks, CountDownLatch go) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) futures.add(pool.submit(task));
            Thread.sleep(200); // let every thread reach the gate
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) results.add(f.get());
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
