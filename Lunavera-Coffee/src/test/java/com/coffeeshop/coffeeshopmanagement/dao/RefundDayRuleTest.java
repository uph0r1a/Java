package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.CheckoutService;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 3.13 (rule D1): a sale stays on the day it was paid, a refund is a negative line on the day it was made. The report, the
 * dashboard's revenue and 7-day chart, the payment split and the shift totals all follow it, so cancelling a paid order never changes
 * a day that was already counted. Rows are made by hand with dates in 2031 (no other test writes there) and removed afterwards;
 * the checks on today use before-and-after differences because the database is shared.
 */
public class RefundDayRuleTest {

    private static final LocalDate D3 = LocalDate.of(2031, 3, 3);
    private static final LocalDate D10 = LocalDate.of(2031, 3, 10);
    private static final LocalDate D20 = LocalDate.of(2031, 3, 20);
    private static final LocalDate FIRST = LocalDate.of(2031, 3, 1);
    private static final LocalDate LAST = LocalDate.of(2031, 3, 31);

    private final ReportDAO reports = new ReportDAO();
    private final OrderDAO orders = new OrderDAO();
    private final List<Integer> made = new ArrayList<>();

    @BeforeClass
    public static void setUp() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @AfterClass
    public static void signOut() {
        TestSession.clear();
    }

    @After
    public void removeWhatWasMade() throws SQLException {
        try (Connection c = DatabaseConfig.getConnection(); Statement st = c.createStatement()) {
            for (int id : made) {
                st.executeUpdate("DELETE FROM order_items WHERE order_id = " + id);
                st.executeUpdate("DELETE FROM orders WHERE id = " + id);
            }
        }
        made.clear();
    }

    private static String at(LocalDate day, int hour) {
        return DbTime.write(LocalDateTime.of(day, java.time.LocalTime.of(hour, 0)));
    }

    /** One order row by hand; {@code paidAt} and {@code cancelledAt} may be null. */
    private int order(String status, LocalDate openedOn, String paidAt, String cancelledAt, long subtotal, long discount, long total,
                      String method) throws SQLException {
        String sql = "INSERT INTO orders (order_date, status, subtotal, discount, total, payment_method, paid_at, cancelled_at) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, at(openedOn, 9));
            st.setString(2, status);
            st.setLong(3, subtotal);
            st.setLong(4, discount);
            st.setLong(5, total);
            st.setString(6, method);
            st.setString(7, paidAt);
            st.setString(8, cancelledAt);
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                keys.next();
                int id = keys.getInt(1);
                made.add(id);
                return id;
            }
        }
    }

    private void line(int orderId, String name, int quantity, long total, Long unitCost) throws SQLException {
        String sql = "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, line_total, unit_cost) VALUES (?,NULL,?,?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement(sql)) {
            st.setInt(1, orderId);
            st.setString(2, name);
            st.setInt(3, quantity);
            st.setLong(4, total / quantity);
            st.setLong(5, total);
            if (unitCost == null) st.setNull(6, java.sql.Types.BIGINT); else st.setLong(6, unitCost);
            st.executeUpdate();
        }
    }

    /** Sale of 100,000 (discount 10,000) paid on the 3rd and refunded on the 10th, a card sale of 50,000 on the 10th. */
    private int refundedSale() throws SQLException {
        int id = order("CANCELLED", D3, at(D3, 10), at(D10, 16), 110000, 10000, 100000, "CASH");
        line(id, "ledger-test-drink", 2, 110000, 20000L);
        return id;
    }

    private static long dong(BigDecimal money) {
        return money.longValueExact();
    }

    @Test
    public void aSaleStaysOnItsOwnDayAfterItIsRefundedLater() throws SQLException {
        refundedSale();
        ReportDAO.Summary paidDay = reports.summary(D3, D3);
        assertEquals("the sale is still there on the day it was paid", 1, paidDay.paidOrders());
        assertEquals(100000, dong(paidDay.revenue()));
        assertEquals(10000, dong(paidDay.discounts()));
        assertEquals("the refund is not on that day", 0, paidDay.cancelledOrders());
    }

    @Test
    public void theRefundIsANegativeLineOnTheDayItWasMade() throws SQLException {
        refundedSale();
        order("PAID", D10, at(D10, 11), null, 50000, 0, 50000, "CARD");
        ReportDAO.Summary refundDay = reports.summary(D10, D10);
        assertEquals("one card sale minus one refund", 0, refundDay.paidOrders());
        assertEquals(-50000, dong(refundDay.revenue()));
        assertEquals(-10000, dong(refundDay.discounts()));
        assertEquals(1, refundDay.cancelledOrders());
        assertEquals("no division by a zero or negative count", 0, dong(refundDay.averageOrderValue()));
    }

    @Test
    public void aRangeWithBothDaysNetsTheSaleAndItsRefundToNothing() throws SQLException {
        refundedSale();
        order("PAID", D10, at(D10, 11), null, 50000, 0, 50000, "CARD");
        ReportDAO.Summary month = reports.summary(FIRST, LAST);
        assertEquals(1, month.paidOrders());
        assertEquals(50000, dong(month.revenue()));
        assertEquals(0, dong(month.discounts()));
        assertEquals(1, month.cancelledOrders());
    }

    @Test
    public void theDailyTableShowsTheSaleOnItsDayAndTheRefundOnItsOwn() throws SQLException {
        refundedSale();
        order("PAID", D10, at(D10, 11), null, 50000, 0, 50000, "CARD");
        List<ReportDAO.DailySales> days = reports.dailySales(FIRST, LAST);
        assertEquals(2, days.size());
        assertEquals(D3, days.get(0).date());
        assertEquals(1, days.get(0).orders());
        assertEquals(100000, dong(days.get(0).revenue()));
        assertEquals(D10, days.get(1).date());
        assertEquals(0, days.get(1).orders());
        assertEquals(-50000, dong(days.get(1).revenue()));
        assertEquals("profit of the sale: 110,000 - 2 x 20,000 - 10,000 discount", 60000, dong(days.get(0).profit()));
        assertEquals("the refund takes it back on its own day", -60000, dong(days.get(1).profit()));
    }

    @Test
    public void paymentMethodsAreNetOfRefundsMadeInTheRange() throws SQLException {
        refundedSale();
        order("PAID", D10, at(D10, 11), null, 50000, 0, 50000, "CARD");
        Map<PaymentMethodKey, long[]> byMethod = new java.util.HashMap<>();
        for (ReportDAO.PaymentSplit split : reports.paymentSplit(FIRST, LAST)) {
            byMethod.put(new PaymentMethodKey(split.method()), new long[]{split.orders(), dong(split.revenue())});
        }
        assertArrayEquals(new long[]{0, 0}, byMethod.get(new PaymentMethodKey(PaymentMethod.CASH)));
        assertArrayEquals(new long[]{1, 50000}, byMethod.get(new PaymentMethodKey(PaymentMethod.CARD)));
    }

    private record PaymentMethodKey(PaymentMethod method) { }

    private static void assertArrayEquals(long[] expected, long[] actual) {
        assertNotNull(actual);
        assertEquals(expected[0], actual[0]);
        assertEquals(expected[1], actual[1]);
    }

    @Test
    public void productsProfitAndLinesFollowTheSameRule() throws SQLException {
        refundedSale();
        ReportDAO.ProductSales sold = reports.allProducts(D3, D3).stream()
                .filter(p -> "ledger-test-drink".equals(p.productName())).findFirst().orElseThrow();
        assertEquals(2, sold.quantity());
        assertEquals(110000, dong(sold.revenue()));

        ReportDAO.ProductSales back = reports.allProducts(D10, D10).stream()
                .filter(p -> "ledger-test-drink".equals(p.productName())).findFirst().orElseThrow();
        assertEquals(-2, back.quantity());
        assertEquals(-110000, dong(back.revenue()));

        assertTrue("sold and refunded in one range: not listed",
                reports.allProducts(FIRST, LAST).stream().noneMatch(p -> "ledger-test-drink".equals(p.productName())));

        ReportDAO.Profit month = reports.profit(FIRST, LAST);
        assertEquals(0, dong(month.grossProfit()));
        assertEquals("one line sold and the same line refunded", 0, month.linesWithCost());
        assertEquals(60000, dong(reports.profit(D3, D3).grossProfit()));
        assertEquals(-60000, dong(reports.profit(D10, D10).grossProfit()));
    }

    @Test
    public void anOrderRefundedBeforeTheRefundTimeWasRecordedKeepsTheOldBehaviour() throws SQLException {
        order("CANCELLED", D20, at(D20, 10), null, 70000, 0, 70000, "CASH");
        ReportDAO.Summary day = reports.summary(D20, D20);
        assertEquals("not counted as a sale, as before", 0, day.paidOrders());
        assertEquals(0, dong(day.revenue()));
        assertEquals("but still counted as a refund of that day's sale", 1, day.cancelledOrders());
        assertTrue(reports.dailySales(D20, D20).isEmpty());
    }

    @Test
    public void anOrderCancelledWhileStillOpenIsNeverMoney() throws SQLException {
        order("CANCELLED", D20, null, at(D20, 12), 30000, 0, 30000, null);
        ReportDAO.Summary day = reports.summary(D20, D20);
        assertEquals(0, day.paidOrders());
        assertEquals(0, dong(day.revenue()));
        assertEquals(0, day.cancelledOrders());
    }

    @Test
    public void aRealRefundWritesTheTimeAndMovesTodayNotTheDayOfTheSale() throws SQLException {
        LocalDate today = AppClock.today();
        LocalDate saleDay = today.minusDays(3);
        BigDecimal revenueBefore = orders.sumRevenueToday();
        Map<LocalDate, BigDecimal> chartBefore = orders.revenueForLast7Days();

        Category category = new Category();
        category.setName("test-refund-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        Product product = new Product();
        product.setName("test-refund-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("40000"));
        product.setStock(5);
        product.setActive(true);
        product = new ProductDAO().insert(product);
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(1);
        item.setUnitPrice(product.getPrice());
        item.setLineTotal(product.getPrice());
        Order open = new Order();
        open.setOrderDate(AppClock.now());
        open.setStatus(com.coffeeshop.coffeeshopmanagement.model.OrderStatus.OPEN);
        open.setSubtotal(BigDecimal.ZERO);
        open.setDiscount(BigDecimal.ZERO);
        open.setTotal(BigDecimal.ZERO);
        open = orders.insert(open, List.of());
        CheckoutService.PaidOrder paid = new CheckoutService().pay(null, List.of(item), new BigDecimal("40000"), null, "", "", open,
                new CheckoutService.Payment(PaymentMethod.CASH, BigDecimal.ZERO, null, new BigDecimal("40000")), null, Role.ADMIN);
        made.add(paid.order().getId());
        assertEquals("paid today: today's revenue is up", 40000, dong(orders.sumRevenueToday().subtract(revenueBefore)));

        // The sale is moved three days back, as if it had been paid then, and refunded now.
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement("UPDATE orders SET paid_at = ? WHERE id = ?")) {
            st.setString(1, at(saleDay, 10));
            st.setInt(2, paid.order().getId());
            st.executeUpdate();
        }
        assertEquals("moved away from today", 0, dong(orders.sumRevenueToday().subtract(revenueBefore)));

        orders.cancelPaidOrder(paid.order().getId(), false);

        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement("SELECT cancelled_at FROM orders WHERE id = ?")) {
            st.setInt(1, paid.order().getId());
            try (ResultSet rs = st.executeQuery()) {
                assertTrue(rs.next());
                assertNotNull("the refund time is written with the refund", rs.getString(1));
            }
        }
        assertEquals("today shows the refund as a negative amount", -40000, dong(orders.sumRevenueToday().subtract(revenueBefore)));
        Map<LocalDate, BigDecimal> chartAfter = orders.revenueForLast7Days();
        assertEquals("the day of the sale keeps its sale", 40000, dong(chartAfter.get(saleDay).subtract(chartBefore.get(saleDay))));
        assertEquals("and the refund is on today's bar", -40000, dong(chartAfter.get(today).subtract(chartBefore.get(today))));
    }

    @Test
    public void aShiftTakesARefundMadeAfterItsCloseIntoTheNextShift() throws SQLException {
        LocalDate day = LocalDate.of(2031, 4, 1);
        int id = order("CANCELLED", day, at(day, 10), at(day, 16), 100000, 0, 100000, "CASH");
        made.size();
        ShiftDAO shifts = new ShiftDAO();
        ShiftDAO.Summary morning = shifts.summarize(LocalDateTime.of(day, java.time.LocalTime.of(9, 0)), LocalDateTime.of(day, java.time.LocalTime.of(12, 0)));
        assertEquals(100000, dong(morning.revenue()));
        assertEquals(100000, dong(morning.cashSales()));
        assertEquals(1, morning.paidOrders());

        ShiftDAO.Summary afternoon = shifts.summarize(LocalDateTime.of(day, java.time.LocalTime.of(15, 0)), LocalDateTime.of(day, java.time.LocalTime.of(18, 0)));
        assertEquals("the refund of a sale from an earlier shift", -100000, dong(afternoon.revenue()));
        assertEquals(-100000, dong(afternoon.cashSales()));
        assertEquals(-1, afternoon.paidOrders());

        ShiftDAO.Summary whole = shifts.summarize(LocalDateTime.of(day, java.time.LocalTime.of(9, 0)), LocalDateTime.of(day, java.time.LocalTime.of(18, 0)));
        assertEquals("sale and refund in one window cancel out", 0, dong(whole.revenue()));
        assertEquals(0, whole.paidOrders());
        assertTrue(id > 0);
    }
}
