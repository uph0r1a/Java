package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.DiscountLine;
import com.coffeeshop.coffeeshopmanagement.service.DiscountReport;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.DiscountText;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 2.37: the discounts of a period per person and reason, net of refunds, adding up to the summary's discount figure. */
public class DiscountReportTest {

    private static final LocalDate DAY = LocalDate.of(2033, 6, 7);
    private static final LocalDate REFUND_DAY = LocalDate.of(2033, 6, 8);
    private static final LocalDate FIRST = LocalDate.of(2033, 6, 1);
    private static final LocalDate LAST = LocalDate.of(2033, 6, 30);

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
            for (int id : made) st.executeUpdate("DELETE FROM orders WHERE id = " + id);
        }
        made.clear();
    }

    private static DiscountLine line(Integer id, String name, String reason, int orders, long amount) {
        return new DiscountLine(id, name, reason, orders, BigDecimal.valueOf(amount));
    }

    @Test
    public void reasonsThatDifferOnlyInCapitalsAndSpacesAreOneReason() {
        List<DiscountLine> merged = DiscountReport.merge(List.of(
                line(1, "An", "Khách quen", 2, 8000),
                line(1, "An", "  khách  QUEN ", 1, 3000),
                line(2, "Bình", "Khách quen", 1, 1000),
                line(1, "An", null, 1, 500),
                line(1, "An", "Lỗi", 0, 0)));
        assertEquals(3, merged.size());
        assertEquals("Khách quen", merged.get(0).reason());
        assertEquals(3, merged.get(0).orders());
        assertEquals(0, new BigDecimal("11000").compareTo(merged.get(0).amount()));
        assertEquals("another person is another line", Integer.valueOf(2), merged.get(1).employeeId());
        assertEquals("", merged.get(2).reason());
        assertEquals(0, new BigDecimal("12500").compareTo(DiscountReport.total(merged)));
    }

    @Test
    public void noReasonAndNoPersonHaveWordsOfTheirOwn() {
        assertFalse(DiscountText.reason(line(null, null, "", 1, 100)).isBlank());
        assertFalse(DiscountText.person(line(null, null, "", 1, 100)).isBlank());
        List<String> csv = DiscountText.csvLines(List.of(line(1, "An, Nguyễn", "giảm \"VIP\"", 2, 8000)));
        assertEquals("", csv.get(0));
        assertEquals(3, csv.size());
        assertTrue(csv.get(2).startsWith("\"An, Nguyễn\","));
        assertTrue(csv.get(2).endsWith(",2,8000"));
    }

    private int order(LocalDate day, long discount, String reason, Integer paidBy, boolean refunded) throws SQLException {
        String sql = "INSERT INTO orders (order_date, status, subtotal, discount, total, payment_method, paid_at, cancelled_at, discount_reason, paid_by) "
                + "VALUES (?,?,?,?,?,'CASH',?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            String paid = DbTime.write(LocalDateTime.of(day, LocalTime.of(10, 0)));
            st.setString(1, paid);
            st.setString(2, refunded ? "CANCELLED" : "PAID");
            st.setLong(3, 50_000);
            st.setLong(4, discount);
            st.setLong(5, 50_000 - discount);
            st.setString(6, paid);
            st.setString(7, refunded ? DbTime.write(LocalDateTime.of(REFUND_DAY, LocalTime.of(11, 0))) : null);
            st.setString(8, reason);
            if (paidBy == null) st.setNull(9, java.sql.Types.INTEGER); else st.setInt(9, paidBy);
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                keys.next();
                made.add(keys.getInt(1));
                return keys.getInt(1);
            }
        }
    }

    private static Integer someEmployee() throws SQLException {
        try (Connection c = DatabaseConfig.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM employees ORDER BY id LIMIT 1")) {
            return rs.next() ? rs.getInt(1) : null;
        }
    }

    @Test
    public void theReportListsDiscountsPerPersonAndReasonNetOfRefunds() throws Exception {
        Integer who = someEmployee();
        order(DAY, 5_000, "Khách quen", who, false);
        order(DAY, 3_000, "khách quen ", who, false);
        order(DAY, 2_000, null, null, false);
        order(DAY, 0, "no discount", who, false);          // nothing given: not in the report
        order(DAY, 4_000, "Lỗi pha chế", who, true);       // refunded in the same period: takes its discount off again
        ReportDAO dao = new ReportDAO();
        List<DiscountLine> lines = dao.discountsByCashier(FIRST, LAST);
        assertEquals(lines.toString(), 2, lines.size());
        DiscountLine regular = lines.get(0);
        assertEquals("Khách quen", regular.reason());
        assertEquals(2, regular.orders());
        assertEquals(0, new BigDecimal("8000").compareTo(regular.amount()));
        DiscountLine nobody = lines.get(1);
        assertEquals("", nobody.reason());
        assertEquals(0, new BigDecimal("2000").compareTo(nobody.amount()));
        assertEquals("the total is the summary's discount figure", 0, ReportDAOAccess.discounts(dao, FIRST, LAST).compareTo(DiscountReport.total(lines)));
    }

    @Test
    public void aRefundInALaterPeriodTakesTheDiscountOffThere() throws Exception {
        order(DAY, 4_000, "Lỗi pha chế", null, true);
        List<DiscountLine> sale = new ReportDAO().discountsByCashier(DAY, DAY);
        assertEquals("on the day of the sale the discount stands", 1, sale.size());
        assertEquals(0, new BigDecimal("4000").compareTo(sale.get(0).amount()));
        List<DiscountLine> refund = new ReportDAO().discountsByCashier(REFUND_DAY, REFUND_DAY);
        assertTrue("on the day of the refund it is taken off", refund.isEmpty() || refund.get(0).amount().signum() < 0);
    }

    /** The summary is a record from {@link ReportDAO#summary}. */
    private static final class ReportDAOAccess {
        static BigDecimal discounts(ReportDAO dao, LocalDate from, LocalDate to) {
            return dao.summary(from, to).discounts();
        }
    }
}
