package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
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
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 4.37: refunding some lines of a paid order. The order of the examples: A 2 x 20,000 and B 1 x 30,000, subtotal 70,000,
 * discount 7,000, paid 63,000 in cash; refunding one A gives back 18,000 (its share of what was paid). Rows are made by hand with
 * dates in 2032 (no other test writes there). RevenueBookTest and OrderChannelTest wrote there too until TODO 6.77 moved them. and removed afterwards, refunds first.
 */
public class RefundLinesTest {

    private static final LocalDate SALE_DAY = LocalDate.of(2032, 3, 3);
    private static final LocalDate REFUND_DAY = LocalDate.of(2032, 3, 10);
    private static final LocalDate FIRST = LocalDate.of(2032, 3, 1);
    private static final LocalDate LAST = LocalDate.of(2032, 3, 31);

    private final RefundDAO refunds = new RefundDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final OrderDAO orders = new OrderDAO();
    private final ReportDAO reports = new ReportDAO();
    private final List<Integer> made = new ArrayList<>();
    private Category category;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @AfterClass
    public static void signOut() {
        TestSession.clear();
    }

    @Before
    public void signInAndFixTheClock() {
        TestSession.admin();
        AppClock.fix(LocalDateTime.of(REFUND_DAY, LocalTime.of(11, 0)));
        category = new Category();
        category.setName("test-refund-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
    }

    @After
    public void removeWhatWasMade() throws SQLException {
        AppClock.reset();
        TestSession.admin();
        try (Connection c = DatabaseConfig.getConnection(); Statement st = c.createStatement()) {
            for (int id : made) {
                st.executeUpdate("DELETE FROM refunds WHERE order_id = " + id);
                st.executeUpdate("DELETE FROM order_payments WHERE order_id = " + id);
                st.executeUpdate("DELETE FROM order_items WHERE order_id = " + id);
                st.executeUpdate("DELETE FROM orders WHERE id = " + id);
            }
        }
        made.clear();
    }

    private static long dong(BigDecimal money) {
        return money.longValueExact();
    }

    private static String at(LocalDate day, int hour) {
        return DbTime.write(LocalDateTime.of(day, LocalTime.of(hour, 0)));
    }

    private Product newProduct(int stock) {
        Product product = new Product();
        product.setName("test-refund-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("20000"));
        product.setStock(stock);
        product.setActive(true);
        return productDAO.insert(product);
    }

    private Customer newCustomer(int points) {
        Customer customer = new Customer();
        customer.setFullName("test-refund-customer-" + UUID.randomUUID());
        customer.setPhone("09" + String.format(java.util.Locale.ROOT, "%08d", new Random().nextInt(100_000_000)));
        customer.setLoyaltyPoints(points);
        customer.setCreatedAt(LocalDateTime.now());
        return customerDAO.insert(customer);
    }

    private int pointsOf(Customer customer) {
        return customerDAO.findById(customer.getId()).orElseThrow().getLoyaltyPoints();
    }

    private int stockOf(Product product) {
        return productDAO.findById(product.getId()).orElseThrow().getStock();
    }

    /** One order row by hand (paid in cash on the sale day unless told otherwise). */
    private int order(String status, Integer customerId, long subtotal, long discount, long total, Integer pointsEarned, int pointsSpent)
            throws SQLException {
        String sql = "INSERT INTO orders (order_date, customer_id, status, subtotal, discount, total, payment_method, paid_at, paid_by, "
                + "points_earned, points_redeemed) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, at(SALE_DAY, 9));
            if (customerId == null) st.setNull(2, java.sql.Types.INTEGER); else st.setInt(2, customerId);
            st.setString(3, status);
            st.setLong(4, subtotal);
            st.setLong(5, discount);
            st.setLong(6, total);
            st.setString(7, "CASH");
            st.setString(8, at(SALE_DAY, 10));
            st.setNull(9, java.sql.Types.INTEGER);
            if (pointsEarned == null) st.setNull(10, java.sql.Types.INTEGER); else st.setInt(10, pointsEarned);
            st.setInt(11, pointsSpent);
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                keys.next();
                int id = keys.getInt(1);
                made.add(id);
                return id;
            }
        }
    }

    private int line(int orderId, Integer productId, String name, int quantity, long lineTotal, Long unitCost) throws SQLException {
        String sql = "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, line_total, unit_cost) VALUES (?,?,?,?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setInt(1, orderId);
            if (productId == null) st.setNull(2, java.sql.Types.INTEGER); else st.setInt(2, productId);
            st.setString(3, name);
            st.setInt(4, quantity);
            st.setLong(5, lineTotal / quantity);
            st.setLong(6, lineTotal);
            if (unitCost == null) st.setNull(7, java.sql.Types.BIGINT); else st.setLong(7, unitCost);
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** The example order: A 2 x 20,000 (id in [0]) and B 1 x 30,000 (id in [1]), paid 63,000. Returns {orderId, lineA, lineB}. */
    private int[] exampleOrder(Integer productA, Integer customerId, Integer earned, int spent) throws SQLException {
        int id = order("PAID", customerId, 70000, 7000, 63000, earned, spent);
        int a = line(id, productA, "A", 2, 40000, 8000L);
        int b = line(id, null, "B", 1, 30000, 10000L);
        return new int[]{id, a, b};
    }

    private int countRefundRows(int orderId) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM refunds WHERE order_id = ?")) {
            st.setInt(1, orderId);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void assertRefused(Runnable action) {
        try {
            action.run();
            fail("expected the action to be refused");
        } catch (DataAccessException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    // ---------------------------------------------------------------- the refund itself

    @Test
    public void refundingLinesIsWrittenToTheAuditLogWithTheAmountAndTheReason() throws SQLException {
        int[] o = exampleOrder(null, null, 0, 0);
        refunds.refund(o[0], Map.of(o[1], 1), false, PaymentMethod.CASH, "spilled");
        java.util.List<com.coffeeshop.coffeeshopmanagement.model.AuditEntry> entries = new AuditDAO().find(new AuditDAO.Filter(null, null,
                com.coffeeshop.coffeeshopmanagement.model.AuditAction.ORDER_LINES_REFUNDED, null, "#" + o[0]), 10, 0);
        assertEquals(1, entries.size());
        assertEquals("18000", entries.get(0).newValue());
        assertTrue(entries.get(0).detail(), entries.get(0).detail().contains("NOT returned"));
        assertTrue(entries.get(0).detail().contains("spilled"));
    }

    @Test
    public void oneLineIsRefundedForItsShareAndTheOrderStaysPaid() throws SQLException {
        int[] o = exampleOrder(null, null, 0, 0);

        RefundDAO.Result result = refunds.refund(o[0], Map.of(o[1], 1), true, PaymentMethod.CASH, "  spilled   on the table ");

        assertEquals(18000, dong(result.amount()));
        assertEquals(1, result.lines());
        assertEquals(1, countRefundRows(o[0]));
        List<RefundDAO.RefundRow> rows = refunds.forOrder(o[0]);
        assertEquals(1, rows.size());
        assertEquals("A", rows.get(0).productName());
        assertEquals(1, rows.get(0).quantity());
        assertEquals(18000, dong(rows.get(0).amount()));
        assertEquals("CASH", rows.get(0).method());
        assertEquals("spilled on the table", rows.get(0).reason());
        assertNotNull(rows.get(0).refundedAt());
        assertEquals("the order is still a paid order", com.coffeeshop.coffeeshopmanagement.model.OrderStatus.PAID,
                orders.findById(o[0]).orElseThrow().getStatus());
        RefundDAO.State state = refunds.load(o[0]);
        assertEquals(18000, dong(state.refundedSoFar()));
        assertEquals(1, state.lines().get(0).refundedQuantity());
        assertEquals(1, state.lines().get(0).remaining());
    }

    @Test
    public void goodsGoBackToStockForTheRefundedPiecesOnlyAndOnlyWhenAsked() throws SQLException {
        Product product = newProduct(10);
        int[] o = exampleOrder(product.getId(), null, 0, 0);

        refunds.refund(o[0], Map.of(o[1], 1), true, PaymentMethod.CASH, null);
        assertEquals("one piece back, not the two of the line", 11, stockOf(product));

        refunds.refund(o[0], Map.of(o[1], 1), false, PaymentMethod.CASH, null);
        assertEquals("a drink already made does not go back", 11, stockOf(product));
    }

    @Test
    public void aRequestThatDoesNotFitIsRefusedAndNothingIsWritten() throws SQLException {
        Product product = newProduct(10);
        int[] o = exampleOrder(product.getId(), null, 0, 0);

        assertRefused(() -> refunds.refund(o[0], Map.of(o[1], 3), true, PaymentMethod.CASH, null)); // only 2 were sold
        assertRefused(() -> refunds.refund(o[0], Map.of(), true, PaymentMethod.CASH, null));
        assertRefused(() -> refunds.refund(o[0], Map.of(2_000_000_000, 1), true, PaymentMethod.CASH, null));

        assertEquals(0, countRefundRows(o[0]));
        assertEquals(10, stockOf(product));
    }

    @Test
    public void refundingInTwoStepsAndThenTheRestAddsUpToTheWholeOrder() throws SQLException {
        int[] o = exampleOrder(null, null, 0, 0);

        assertEquals(18000, dong(refunds.refund(o[0], Map.of(o[1], 1), true, PaymentMethod.CASH, null).amount()));
        assertEquals(18000, dong(refunds.refund(o[0], Map.of(o[1], 1), true, PaymentMethod.CASH, null).amount()));
        assertEquals(27000, dong(refunds.refund(o[0], Map.of(o[2], 1), true, PaymentMethod.CASH, null).amount()));

        assertEquals("all together exactly what was paid", 63000, dong(refunds.load(o[0]).refundedSoFar()));
        assertRefused(() -> refunds.refund(o[0], Map.of(o[1], 1), true, PaymentMethod.CASH, null)); // nothing is left
    }

    @Test
    public void aWholeRefundIsRefusedOnceLinesWereRefundedAndALineRefundOnACancelledOrder() throws SQLException {
        int[] o = exampleOrder(null, null, 0, 0);
        refunds.refund(o[0], Map.of(o[1], 1), true, PaymentMethod.CASH, null);
        assertRefused(() -> orders.cancelPaidOrder(o[0]));
        assertEquals(1, countRefundRows(o[0]));

        int[] other = exampleOrder(null, null, 0, 0);
        orders.cancelPaidOrder(other[0]);
        assertRefused(() -> refunds.refund(other[0], Map.of(other[1], 1), true, PaymentMethod.CASH, null));
        assertEquals(0, countRefundRows(other[0]));
    }

    @Test
    public void onlyAnAdministratorMayRefundLines() throws SQLException {
        int[] o = exampleOrder(null, null, 0, 0);
        TestSession.clear();
        assertRefused(() -> refunds.refund(o[0], Map.of(o[1], 1), true, PaymentMethod.CASH, null));
        TestSession.admin();
        assertEquals(0, countRefundRows(o[0]));
    }

    @Test
    public void loyaltyPointsMoveInProportionAndEndAtWhatAWholeRefundMoves() throws SQLException {
        Customer customer = newCustomer(100);
        int[] o = exampleOrder(null, customer.getId(), 63, 40); // earned 63, spent 40

        RefundDAO.Result first = refunds.refund(o[0], Map.of(o[1], 1), true, PaymentMethod.CASH, null); // 18000 of 63000
        assertEquals(18, first.earnedBack());
        assertEquals(11, first.spentBack());
        assertEquals("100 - 18 + 11", 93, pointsOf(customer));

        refunds.refund(o[0], Map.of(o[1], 1, o[2], 1), true, PaymentMethod.CASH, null); // the rest
        assertEquals("100 - 63 + 40, as a whole refund would leave it", 77, pointsOf(customer));
    }

    @Test
    public void anAnonymousCustomerIsNotGivenPointsBack() throws SQLException {
        Customer customer = newCustomer(100);
        int[] o = exampleOrder(null, customer.getId(), 63, 40);
        TestSession.admin();
        new com.coffeeshop.coffeeshopmanagement.service.CustomerMergeService().anonymise(customer.getId());

        refunds.refund(o[0], Map.of(o[1], 2, o[2], 1), true, PaymentMethod.CASH, null);

        assertEquals("anonymised: no balance to give points to, and none to take from", 0, pointsOf(customer));
    }

    // ---------------------------------------------------------------- what the reports see (TODO 3.13 and 4.37)

    @Test
    public void theSaleStaysOnItsDayAndTheRefundIsNegativeMoneyOnItsOwn() throws SQLException {
        int[] o = exampleOrder(null, null, 0, 0);
        refunds.refund(o[0], Map.of(o[1], 1), true, PaymentMethod.CASH, null); // made on REFUND_DAY by the fixed clock

        ReportDAO.Summary saleDay = reports.summary(SALE_DAY, SALE_DAY);
        assertEquals(1, saleDay.paidOrders());
        assertEquals("the day that was counted does not change", 63000, dong(saleDay.revenue()));
        assertEquals(7000, dong(saleDay.discounts()));

        ReportDAO.Summary refundDay = reports.summary(REFUND_DAY, REFUND_DAY);
        assertEquals("a refunded line does not change the number of orders", 0, refundDay.paidOrders());
        assertEquals(-18000, dong(refundDay.revenue()));
        assertEquals("nor the discounts", 0, dong(refundDay.discounts()));
        assertEquals("it is not a cancelled order", 0, refundDay.cancelledOrders());

        ReportDAO.Summary month = reports.summary(FIRST, LAST);
        assertEquals(1, month.paidOrders());
        assertEquals(45000, dong(month.revenue()));
    }

    @Test
    public void productsAndProfitCountOnlyThePiecesThatStand() throws SQLException {
        int[] o = exampleOrder(null, null, 0, 0);
        refunds.refund(o[0], Map.of(o[1], 1), true, PaymentMethod.CASH, null);

        List<ReportDAO.ProductSales> products = reports.topProducts(FIRST, LAST, 10);
        ReportDAO.ProductSales a = products.stream().filter(p -> p.productName().equals("A")).findFirst().orElseThrow();
        assertEquals("sold 2, 1 refunded", 1, a.quantity());
        assertEquals("40,000 - 20,000", 20000, dong(a.revenue()));
        ReportDAO.Profit profit = reports.profit(FIRST, LAST);
        // revenue 45,000 minus what the pieces that stand cost (1 x 8,000 + 10,000) = 27,000
        assertEquals(27000, dong(profit.grossProfit()));
        assertEquals("lines are not taken away by refunding some of their pieces", 2, profit.linesWithCost());
    }

    @Test
    public void aCashRefundTakesCashOutOfTheShiftAndATransferRefundDoesNot() throws SQLException {
        int[] o = exampleOrder(null, null, 0, 0);
        refunds.refund(o[0], Map.of(o[1], 1), true, PaymentMethod.CASH, null);
        refunds.refund(o[0], Map.of(o[2], 1), true, PaymentMethod.TRANSFER, null);

        LocalDateTime from = LocalDateTime.of(REFUND_DAY, LocalTime.of(8, 0));
        LocalDateTime to = LocalDateTime.of(REFUND_DAY, LocalTime.of(23, 0));
        ShiftDAO.Summary shift = new ShiftDAO().summarize(from, to);
        assertEquals(-45000, dong(shift.revenue())); // 18,000 + 27,000 given back
        assertEquals("only the cash refund comes out of the drawer", -18000, dong(shift.cashSales()));
        assertEquals(0, shift.paidOrders());
    }

    @Test
    public void thePaymentSplitTakesTheRefundOffTheMethodItWasGivenBackBy() throws SQLException {
        int[] o = exampleOrder(null, null, 0, 0);
        refunds.refund(o[0], Map.of(o[1], 1), true, PaymentMethod.TRANSFER, null);

        List<ReportDAO.PaymentSplit> split = reports.paymentSplit(FIRST, LAST);
        ReportDAO.PaymentSplit cash = split.stream().filter(s -> s.method() == PaymentMethod.CASH).findFirst().orElseThrow();
        ReportDAO.PaymentSplit transfer = split.stream().filter(s -> s.method() == PaymentMethod.TRANSFER).findFirst().orElseThrow();
        assertEquals(63000, dong(cash.revenue()));
        assertEquals(1, cash.orders());
        assertEquals(-18000, dong(transfer.revenue()));
        assertEquals("no order is counted under the method of the refund", 0, transfer.orders());
    }

    @Test
    public void theDashboardRevenueOfTheDayItWasMadeIsReducedAndTheDaysBeforeAreNot() throws SQLException {
        int[] o = exampleOrder(null, null, 0, 0);
        BigDecimal before = orders.sumRevenueToday(); // today is the refund day: the clock is fixed
        refunds.refund(o[0], Map.of(o[1], 1), true, PaymentMethod.CASH, null);
        assertEquals(-18000, dong(orders.sumRevenueToday().subtract(before)));
    }

    /** TODO 4.37 / 6.90 (a): the history marks an order that had lines refunded, and only that one. */
    @Test
    public void theHistoryMarksAnOrderThatHadLinesRefunded() throws SQLException {
        int[] refunded = exampleOrder(null, null, 0, 0);
        int[] untouched = exampleOrder(null, null, 0, 0);
        refunds.refund(refunded[0], Map.of(refunded[1], 1), true, PaymentMethod.CASH, null);
        List<OrderDAO.OrderSummary> page = orders.findHistoryPage(new OrderDAO.HistoryFilter(null, null, SALE_DAY, SALE_DAY), 50, 0);
        OrderDAO.OrderSummary first = page.stream().filter(r -> r.order().getId() == refunded[0]).findFirst().orElseThrow();
        OrderDAO.OrderSummary second = page.stream().filter(r -> r.order().getId() == untouched[0]).findFirst().orElseThrow();
        assertTrue("some lines were given back", first.linesRefunded());
        assertFalse("nothing was given back", second.linesRefunded());
    }
}
