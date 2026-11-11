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
import com.coffeeshop.coffeeshopmanagement.util.RevenueBookText;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
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
 * TODO 4.58: the revenue book. Its lines add up to the report's revenue for the same period, a refund is a negative line on the day it was
 * made, and each month and the whole book end with a total. Rows are made by hand in 2035 (no other test writes there). It shared a year with RefundLinesTest before TODO 6.77.
 */
public class RevenueBookTest {

    private static final LocalDate D3 = LocalDate.of(2035, 3, 3);
    private static final LocalDate D10 = LocalDate.of(2035, 3, 10);
    private static final LocalDate D20 = LocalDate.of(2035, 4, 20);
    private static final LocalDate FIRST = LocalDate.of(2035, 3, 1);
    private static final LocalDate LAST = LocalDate.of(2035, 4, 30);

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

    private List<ReportDAO.BookLine> book() throws SQLException {
        order("CANCELLED", D3, at(D3, 10), at(D10, 16), 100000, 0, 100000, "CASH");
        order("PAID", D10, at(D10, 11), null, 50000, 0, 50000, "CARD");
        order("PAID", D20, at(D20, 9), null, 70000, 0, 70000, "TRANSFER");
        order("OPEN", D20, null, null, 99000, 0, 99000, null);
        return reports.revenueBook(FIRST, LAST);
    }

    @Test
    public void theBookAddsUpToTheReportsRevenue() throws SQLException {
        List<ReportDAO.BookLine> lines = book();
        assertEquals(4, lines.size());
        assertEquals(reports.summary(FIRST, LAST).revenue().longValueExact(), RevenueBookText.total(lines).longValueExact());
        assertEquals(120000, RevenueBookText.total(lines).longValueExact());
    }

    @Test
    public void aRefundIsANegativeLineOnTheDayItWasMade() throws SQLException {
        List<ReportDAO.BookLine> lines = book();
        assertEquals(D3, lines.get(0).day());
        assertEquals(100000, lines.get(0).amount().longValueExact());
        ReportDAO.BookLine refund = lines.stream().filter(ReportDAO.BookLine::refund).findFirst().orElseThrow();
        assertEquals(D10, refund.day());
        assertEquals(-100000, refund.amount().longValueExact());
        assertTrue(RevenueBookText.description(refund).contains(String.valueOf(refund.orderId())));
    }

    @Test
    public void aMonthTotalFollowsEachMonthAndTheGrandTotalEndsTheBook() throws SQLException {
        List<String> text = RevenueBookText.lines(book(), FIRST, LAST, "Shop, \"A\"");
        String month3 = ",," + I18n.t("book.month", "03/2035");
        long monthRows = text.stream().filter(l -> l.startsWith(",,")).count();
        assertEquals("two months and the grand total", 3, monthRows);
        assertTrue(text.stream().anyMatch(l -> l.startsWith(month3) && l.contains(",50000,")));
        assertTrue(text.stream().anyMatch(l -> l.startsWith(",," + I18n.t("book.month", "04/2035")) && l.contains(",70000,")));
        assertTrue(text.get(text.size() - 1).contains(I18n.t("book.total")));
        assertTrue(text.get(text.size() - 1).contains(",120000,"));
        assertTrue("the shop name with a comma and quotes is escaped", text.get(1).startsWith("\""));
        assertTrue(text.stream().anyMatch(l -> l.equals(I18n.t("book.header"))));
    }

    @Test
    public void anEmptyPeriodStillHasAHeaderAndAZeroTotal() throws SQLException {
        List<String> text = RevenueBookText.lines(reports.revenueBook(LocalDate.of(2035, 8, 1), LocalDate.of(2035, 8, 31)),
                LocalDate.of(2035, 8, 1), LocalDate.of(2035, 8, 31), "");
        assertTrue(text.get(text.size() - 1).contains(",0,"));
    }
}
