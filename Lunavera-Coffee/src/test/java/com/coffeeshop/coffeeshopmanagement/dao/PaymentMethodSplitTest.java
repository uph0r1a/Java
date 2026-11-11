package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO.PaymentSplit;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.service.DashboardStatsService;
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
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.30: revenue per payment method for a day range, for a shift window and for today's dashboard card; all from the one ledger. */
public class PaymentMethodSplitTest {

    private static final LocalDate DAY = LocalDate.of(2034, 3, 9);
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
                st.executeUpdate("DELETE FROM order_payments WHERE order_id = " + id);
                st.executeUpdate("DELETE FROM orders WHERE id = " + id);
            }
        }
        made.clear();
    }

    private int sale(LocalDateTime paid, String method, long total, long cashPart) throws SQLException {
        String sql = "INSERT INTO orders (order_date, status, subtotal, discount, total, payment_method, paid_at) VALUES (?,'PAID',?,0,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, DbTime.write(paid));
            st.setLong(2, total);
            st.setLong(3, total);
            st.setString(4, method);
            st.setString(5, DbTime.write(paid));
            st.executeUpdate();
            int id;
            try (ResultSet keys = st.getGeneratedKeys()) {
                keys.next();
                id = keys.getInt(1);
            }
            made.add(id);
            if (cashPart > 0) { // a split: cash and the named method
                try (PreparedStatement p = c.prepareStatement("INSERT INTO order_payments (order_id, method, amount) VALUES (?,?,?)")) {
                    p.setInt(1, id);
                    p.setString(2, "CASH");
                    p.setLong(3, cashPart);
                    p.addBatch();
                    p.setInt(1, id);
                    p.setString(2, method);
                    p.setLong(3, total - cashPart);
                    p.addBatch();
                    p.executeBatch();
                }
            }
            return id;
        }
    }

    private static BigDecimal of(List<PaymentSplit> split, PaymentMethod method) {
        return split.stream().filter(s -> s.method() == method).map(PaymentSplit::revenue).findFirst().orElse(BigDecimal.ZERO);
    }

    private static int ordersOf(List<PaymentSplit> split, PaymentMethod method) {
        return split.stream().filter(s -> s.method() == method).mapToInt(PaymentSplit::orders).findFirst().orElse(0);
    }

    private void threeSales() throws SQLException {
        sale(DAY.atTime(9, 0), "CASH", 40_000, 0);
        sale(DAY.atTime(10, 0), "TRANSFER", 100_000, 0);
        sale(DAY.atTime(11, 0), "TRANSFER", 70_000, 20_000); // 20k cash and 50k transfer
    }

    @Test
    public void aDayHasItsCashAndTransferTotalsAndTheyAddUpToTheRevenue() throws Exception {
        threeSales();
        ReportDAO dao = new ReportDAO();
        List<PaymentSplit> split = dao.paymentSplit(DAY, DAY);
        assertEquals(0, new BigDecimal("60000").compareTo(of(split, PaymentMethod.CASH)));
        assertEquals(0, new BigDecimal("150000").compareTo(of(split, PaymentMethod.TRANSFER)));
        assertEquals(2, ordersOf(split, PaymentMethod.TRANSFER));
        BigDecimal sum = split.stream().map(PaymentSplit::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, dao.summary(DAY, DAY).revenue().compareTo(sum));
    }

    @Test
    public void aShiftWindowHasTheSameSplitAndItsCashMatchesTheDrawer() throws Exception {
        threeSales();
        LocalDateTime from = DAY.atTime(8, 0);
        LocalDateTime to = DAY.atTime(10, 30); // the third sale (11:00) is in the next shift
        List<PaymentSplit> split = new ReportDAO().paymentSplit(from, to);
        assertEquals(0, new BigDecimal("40000").compareTo(of(split, PaymentMethod.CASH)));
        assertEquals(0, new BigDecimal("100000").compareTo(of(split, PaymentMethod.TRANSFER)));
        ShiftDAO.Summary shift = new ShiftDAO().summarize(from, to);
        assertEquals(0, shift.cashSales().compareTo(of(split, PaymentMethod.CASH)));
        BigDecimal sum = split.stream().map(PaymentSplit::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, shift.revenue().compareTo(sum));
        assertTrue("nothing outside the window", new ReportDAO().paymentSplit(DAY.atTime(12, 0), DAY.atTime(13, 0)).isEmpty());
    }

    @Test
    public void todaysCardLinesAddUpToTodaysRevenue() {
        DashboardStatsService.DashboardStats stats = new DashboardStatsService().loadStats();
        BigDecimal sum = stats.todayByMethod().stream().map(PaymentSplit::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, stats.todayRevenue().compareTo(sum));
    }

    @Test
    public void theCardTextHasOneLinePerMethod() {
        assertEquals("", DashboardStatsService.methodsText(null));
        assertEquals("", DashboardStatsService.methodsText(List.of()));
        String text = DashboardStatsService.methodsText(List.of(
                new PaymentSplit(PaymentMethod.CASH, 2, new BigDecimal("60000")),
                new PaymentSplit(PaymentMethod.TRANSFER, 1, new BigDecimal("150000")),
                new PaymentSplit(null, 1, BigDecimal.TEN)));
        String[] lines = text.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].startsWith(PaymentMethod.CASH.label() + ": "));
        assertTrue(lines[1].startsWith(PaymentMethod.TRANSFER.label() + ": "));
    }
}
