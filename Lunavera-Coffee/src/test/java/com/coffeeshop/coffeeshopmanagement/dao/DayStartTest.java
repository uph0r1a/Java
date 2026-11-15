package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.TimeClockEntry;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.BusinessDay;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * TODO 4.50: a shop that is open after midnight starts its day at a set hour. Orders are paid at 23:50, 00:40, 03:59, 04:00 and
 * 04:01 on a day of this test's own far in the future, and the dashboard, the history, the reports, the take-away number and the
 * staff hours must all agree where the day ends, with a day start of 04:00 and with none.
 */
public class DayStartTest {

    private static final AtomicInteger NEXT = new AtomicInteger(0);
    /** The MariaDB test database keeps its orders between runs, so each run starts in a block of days of its own (SQLite starts empty every time). */
    private static final long RUN_BLOCK = java.util.concurrent.ThreadLocalRandom.current().nextLong(0, 3000) * 100L;

    private final OrderDAO orderDAO = new OrderDAO();
    private final ReportDAO reports = new ReportDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();

    private LocalDate savedToday;
    private LocalDate d;          // the evening, 23:50 of this day
    private Product product;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void setUp() {
        TestSession.admin();
        BusinessDay.resetForTests();
        savedToday = AppClock.today();
        d = LocalDate.of(2400, 1, 1).plusDays(RUN_BLOCK + NEXT.incrementAndGet() * 5L);
        AppClock.fix(d.plusDays(1).atTime(12, 0));
        Category category = new Category();
        category.setName("test-daystart-category-" + UUID.randomUUID());
        category.setActive(true);
        categoryDAO.insert(category);
        product = new Product();
        product.setName("test-daystart-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("10000"));
        product.setStock(100000);
        product.setActive(true);
        productDAO.insert(product);
    }

    @After
    public void restore() {
        BusinessDay.resetForTests();
        AppClock.pinDate(savedToday);
        TestSession.admin();
    }

    /** A paid take-away order of {@code total} at {@code paidAt}. */
    private Order sale(String total, LocalDateTime paidAt) {
        Order order = new Order();
        order.setOrderDate(paidAt);
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(new BigDecimal(total));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal(total));
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaidAt(paidAt);
        OrderItem line = new OrderItem();
        line.setProductId(product.getId());
        line.setProductName(product.getName());
        line.setQuantity(1);
        line.setUnitPrice(new BigDecimal(total));
        line.setLineTotal(new BigDecimal(total));
        orderDAO.insert(order, List.of(line));
        return order;
    }

    /** One evening: 23:50 (10,000), 00:40 (20,000), 03:59 (30,000), 04:00 exactly (40,000), 04:01 (50,000). */
    private void theEvening() {
        sale("10000", d.atTime(23, 50));
        sale("20000", d.plusDays(1).atTime(0, 40));
        sale("30000", d.plusDays(1).atTime(3, 59));
        sale("40000", d.plusDays(1).atTime(4, 0));
        sale("50000", d.plusDays(1).atTime(4, 1));
    }

    private static long revenue(ReportDAO.Summary s) {
        return s.revenue().longValue();
    }

    // ---- the reports ----

    @Test
    public void withNoDayStartTheReportsSplitAtMidnight() {
        theEvening();
        assertEquals(1, reports.summary(d, d).paidOrders());
        assertEquals(10000, revenue(reports.summary(d, d)));
        assertEquals(4, reports.summary(d.plusDays(1), d.plusDays(1)).paidOrders());
        assertEquals(140000, revenue(reports.summary(d.plusDays(1), d.plusDays(1))));
    }

    @Test
    public void withADayStartOfFourTheEveningIsOneDay() {
        theEvening();
        BusinessDay.setStartMinutes(4 * 60);
        ReportDAO.Summary first = reports.summary(d, d);
        assertEquals("23:50, 00:40 and 03:59", 3, first.paidOrders());
        assertEquals(60000, revenue(first));
        ReportDAO.Summary second = reports.summary(d.plusDays(1), d.plusDays(1));
        assertEquals("04:00 sharp and 04:01", 2, second.paidOrders());
        assertEquals(90000, revenue(second));
        assertEquals("both days together are everything", 150000, revenue(reports.summary(d, d.plusDays(1))));
    }

    @Test
    public void theDailyTableGroupsByTheShopDay() {
        theEvening();
        BusinessDay.setStartMinutes(4 * 60);
        List<ReportDAO.DailySales> days = reports.dailySales(d, d.plusDays(1));
        assertEquals(2, days.size());
        assertEquals(d, days.get(0).date());
        assertEquals(3, days.get(0).orders());
        assertEquals(60000, days.get(0).revenue().longValue());
        assertEquals(d.plusDays(1), days.get(1).date());
        assertEquals(2, days.get(1).orders());
        assertEquals(90000, days.get(1).revenue().longValue());
    }

    @Test
    public void theDailyTableAtMidnightStillSplitsTheEvening() {
        theEvening();
        List<ReportDAO.DailySales> days = reports.dailySales(d, d.plusDays(1));
        assertEquals(2, days.size());
        assertEquals(10000, days.get(0).revenue().longValue());
        assertEquals(140000, days.get(1).revenue().longValue());
    }

    @Test
    public void aDayStartOfFourThirtyMovesTheEdgeToo() {
        theEvening();
        BusinessDay.setStartMinutes(4 * 60 + 30);
        assertEquals("04:00 and 04:01 are now before the start", 5, reports.summary(d, d).paidOrders());
        assertEquals(0, reports.summary(d.plusDays(1), d.plusDays(1)).paidOrders());
    }

    // ---- the history ----

    private Set<Long> historyTotals(LocalDate from, LocalDate to) {
        return orderDAO.findHistoryPage(new OrderDAO.HistoryFilter(null, null, from, to), 100, 0).stream()
                .map(s -> s.order().getTotal().longValue()).collect(Collectors.toSet());
    }

    @Test
    public void theHistoryFilterFollowsTheShopDay() {
        theEvening();
        assertEquals(Set.of(10000L), historyTotals(d, d));
        BusinessDay.setStartMinutes(4 * 60);
        assertEquals(Set.of(10000L, 20000L, 30000L), historyTotals(d, d));
        assertEquals(Set.of(40000L, 50000L), historyTotals(d.plusDays(1), d.plusDays(1)));
    }

    // ---- the dashboard ----

    @Test
    public void todayOnTheDashboardIsTheShopDay() {
        theEvening();
        BusinessDay.setStartMinutes(4 * 60);
        AppClock.fix(d.plusDays(1).atTime(0, 30)); // half past midnight: still the evening of d
        assertEquals(60000, orderDAO.sumRevenueToday().longValue());
        assertEquals(Set.of(10000L, 20000L, 30000L),
                orderDAO.findTodayOrders().stream().map(o -> o.getTotal().longValue()).collect(Collectors.toSet()));
        AppClock.fix(d.plusDays(1).atTime(4, 30));
        assertEquals(90000, orderDAO.sumRevenueToday().longValue());
    }

    @Test
    public void atHalfPastMidnightWithNoDayStartTodayIsAlreadyTheNextDay() {
        theEvening();
        AppClock.fix(d.plusDays(1).atTime(0, 30));
        assertEquals(140000, orderDAO.sumRevenueToday().longValue());
    }

    @Test
    public void theSevenDayChartAgreesWithTheReport() {
        theEvening();
        BusinessDay.setStartMinutes(4 * 60);
        AppClock.fix(d.plusDays(1).atTime(0, 30));
        assertEquals(60000, orderDAO.revenueForLast7Days().get(d).longValue());
    }

    // ---- the take-away number ----

    @Test
    public void theTakeAwayNumberStartsAgainAtTheDayStart() {
        BusinessDay.setStartMinutes(4 * 60);
        assertEquals(Integer.valueOf(1), sale("1000", d.atTime(23, 50)).getQueueNo());
        assertEquals(Integer.valueOf(2), sale("1000", d.plusDays(1).atTime(0, 40)).getQueueNo());
        assertEquals(Integer.valueOf(3), sale("1000", d.plusDays(1).atTime(3, 59)).getQueueNo());
        assertEquals("a new shop day: back to 1", Integer.valueOf(1), sale("1000", d.plusDays(1).atTime(4, 1)).getQueueNo());
        assertEquals(Integer.valueOf(2), sale("1000", d.plusDays(1).atTime(9, 0)).getQueueNo());
    }

    @Test
    public void withNoDayStartTheTakeAwayNumberStartsAtMidnight() {
        assertEquals(Integer.valueOf(1), sale("1000", d.atTime(23, 50)).getQueueNo());
        assertEquals("after midnight: back to 1", Integer.valueOf(1), sale("1000", d.plusDays(1).atTime(0, 40)).getQueueNo());
        assertEquals(Integer.valueOf(2), sale("1000", d.plusDays(1).atTime(3, 59)).getQueueNo());
    }

    // ---- the staff hours ----

    @Test
    public void anEveningShiftIsOneDayOfHours() {
        User u = new User();
        u.setId(3_000_000 + new Random().nextInt(900_000));
        u.setUsername("daystart-" + u.getId());
        u.setRole(Role.EMPLOYEE);
        Session.start(u, null);
        TimeClockDAO clock = new TimeClockDAO();
        AppClock.fix(d.atTime(20, 0));
        clock.clockIn();
        AppClock.fix(d.plusDays(1).atTime(1, 0));
        TimeClockEntry out = clock.clockOut();
        assertFalse(out.isOpen());
        TestSession.admin();

        assertEquals("calendar day: only 20:00 to midnight is on the 10th", 240, minutesOf(clock, u.getId(), d));
        BusinessDay.setStartMinutes(4 * 60);
        assertEquals("shop day: the whole 5 hours", 300, minutesOf(clock, u.getId(), d));
        assertEquals(0, minutesOf(clock, u.getId(), d.plusDays(1)));
    }

    private static long minutesOf(TimeClockDAO clock, int userId, LocalDate day) {
        return clock.hours(day, day).stream().filter(h -> h.userId() == userId).mapToLong(TimeClockDAO.Hours::minutes).sum();
    }

    // ---- the SQL a MariaDB would be given ----

    @Test
    public void theShownDayIsWrittenForEachDatabase() {
        assertEquals("no day start: the plain date, as before", "DATE(x)", EffectiveDay.shown("x", com.coffeeshop.coffeeshopmanagement.config.SqlDialect.SQLITE));
        assertEquals("DATE(x)", EffectiveDay.shown("x", com.coffeeshop.coffeeshopmanagement.config.SqlDialect.MARIADB));
        BusinessDay.setStartMinutes(4 * 60);
        assertEquals("DATE(x, '-240 minutes')", EffectiveDay.shown("x", com.coffeeshop.coffeeshopmanagement.config.SqlDialect.SQLITE));
        assertEquals("DATE(x - INTERVAL 240 MINUTE)", EffectiveDay.shown("x", com.coffeeshop.coffeeshopmanagement.config.SqlDialect.MARIADB));
        boolean sqlite = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.isSqlite();
        assertEquals("the ledger uses it for the database in use",
                sqlite ? "DATE(o.event_time, '-240 minutes')" : "DATE(o.event_time - INTERVAL 240 MINUTE)", SalesLedger.day());
        assertEquals(sqlite ? "DATE(oi.event_time, '-240 minutes')" : "DATE(oi.event_time - INTERVAL 240 MINUTE)", SalesLedger.lineDay());
    }

    @Test
    public void theBoundsOfARangeAreTheStartOfTheFirstDayAndTheStartOfTheDayAfterTheLast() {
        BusinessDay.setStartMinutes(4 * 60);
        List<String> values = EffectiveDay.params(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 10));
        if (com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.isSqlite()) {
            assertEquals(List.of("2026-10-10T04:00", "2026-10-11T04:00", "2026-10-10T04:00", "2026-10-11T04:00"), values);
        } else {
            assertEquals(List.of("2026-10-10 04:00:00", "2026-10-11 04:00:00", "2026-10-10 04:00:00", "2026-10-11 04:00:00"), values);
        }
    }
}
