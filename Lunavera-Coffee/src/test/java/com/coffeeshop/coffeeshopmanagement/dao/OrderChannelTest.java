package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderChannel;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.service.OrderService;
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
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.48: the channel of an order is recorded, old orders read by their table, and the report adds revenue up per channel net of refunds. */
public class OrderChannelTest {

    private static final LocalDate D1 = LocalDate.of(2036, 5, 3);
    private static final LocalDate D2 = LocalDate.of(2036, 5, 4);
    private static final LocalDate FIRST = LocalDate.of(2036, 5, 1);
    private static final LocalDate LAST = LocalDate.of(2036, 5, 31);

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

    @Test
    public void aTableIsAlwaysDineInAndNoTableIsTakeAwayUnlessAskedOtherwise() {
        assertEquals(OrderChannel.DINE_IN, OrderChannel.of(null, 3));
        assertEquals(OrderChannel.DINE_IN, OrderChannel.of(OrderChannel.GRAB_FOOD, 3));
        assertEquals(OrderChannel.TAKE_AWAY, OrderChannel.of(null, null));
        assertEquals(OrderChannel.TAKE_AWAY, OrderChannel.of(OrderChannel.DINE_IN, null));
        assertEquals(OrderChannel.GRAB_FOOD, OrderChannel.of(OrderChannel.GRAB_FOOD, null));
        assertEquals(OrderChannel.SHOPEE_FOOD, OrderChannel.of(OrderChannel.SHOPEE_FOOD, null));
    }

    @Test
    public void storedTextReadsBackAndUnknownTextIsIgnored() {
        for (OrderChannel channel : OrderChannel.values()) assertEquals(channel, OrderChannel.fromDb(channel.name()));
        assertNull(OrderChannel.fromDb(null));
        assertNull(OrderChannel.fromDb("DRONE"));
        assertTrue(OrderChannel.GRAB_FOOD.isDeliveryApp());
        assertFalse(OrderChannel.TAKE_AWAY.isDeliveryApp());
    }

    @Test
    public void startingAnOrderRecordsItsChannel() {
        OrderService service = new OrderService();
        Order grab = service.start(null, OrderChannel.GRAB_FOOD, "", "", null).order();
        Order table = service.start(null, "", "", null).order();
        made.add(grab.getId());
        made.add(table.getId());
        OrderDAO dao = new OrderDAO();
        assertEquals(OrderChannel.GRAB_FOOD, dao.findById(grab.getId()).orElseThrow().getChannel());
        assertEquals(OrderChannel.TAKE_AWAY, dao.findById(table.getId()).orElseThrow().getChannel()); // no channel asked = take-away
    }

    private int row(String status, LocalDate day, String channel, Integer table, long total, boolean refunded) throws SQLException {
        String sql = "INSERT INTO orders (order_date, status, subtotal, discount, total, payment_method, paid_at, cancelled_at, table_number, channel) "
                + "VALUES (?,?,?,0,?,'CASH',?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            String paid = DbTime.write(LocalDateTime.of(day, LocalTime.of(10, 0)));
            st.setString(1, paid);
            st.setString(2, status);
            st.setLong(3, total);
            st.setLong(4, total);
            st.setString(5, paid);
            st.setString(6, refunded ? DbTime.write(LocalDateTime.of(D2, LocalTime.of(11, 0))) : null);
            if (table == null) st.setNull(7, java.sql.Types.INTEGER); else st.setInt(7, table);
            st.setString(8, channel);
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                keys.next();
                made.add(keys.getInt(1));
                return keys.getInt(1);
            }
        }
    }

    @Test
    public void reportAddsUpPerChannelNetOfRefundsAndReadsOldOrdersByTheirTable() throws SQLException {
        row("PAID", D1, null, 4, 50_000, false);                 // an old order at a table: dine-in
        row("PAID", D1, null, null, 30_000, false);              // an old order with no table: take-away
        row("PAID", D1, "GRAB_FOOD", null, 100_000, false);
        row("PAID", D1, "GRAB_FOOD", null, 40_000, false);
        row("PAID", D1, "SHOPEE_FOOD", null, 70_000, false);
        row("CANCELLED", D1, "SHOPEE_FOOD", null, 20_000, true); // paid on D1, refunded on D2: counts +1 / +20k on D1, -1 / -20k on D2
        List<ReportDAO.ChannelSales> sales = new ReportDAO().channelSales(FIRST, LAST);
        assertEquals(new BigDecimal("140000"), revenue(sales, OrderChannel.GRAB_FOOD));
        assertEquals(2, orders(sales, OrderChannel.GRAB_FOOD));
        assertEquals(new BigDecimal("70000"), revenue(sales, OrderChannel.SHOPEE_FOOD)); // the refunded sale nets to nothing
        assertEquals(1, orders(sales, OrderChannel.SHOPEE_FOOD));
        assertEquals(new BigDecimal("50000"), revenue(sales, OrderChannel.DINE_IN));
        assertEquals(new BigDecimal("30000"), revenue(sales, OrderChannel.TAKE_AWAY));
        // the channels add up to the revenue of the same days
        BigDecimal sum = BigDecimal.ZERO;
        for (ReportDAO.ChannelSales s : sales) sum = sum.add(s.revenue());
        assertEquals(new ReportDAO().summary(FIRST, LAST).revenue().stripTrailingZeros(), sum.stripTrailingZeros());
        assertEquals(OrderChannel.GRAB_FOOD, sales.get(0).channel()); // biggest first
    }

    private static BigDecimal revenue(List<ReportDAO.ChannelSales> sales, OrderChannel channel) {
        return sales.stream().filter(s -> s.channel() == channel).map(ReportDAO.ChannelSales::revenue).findFirst().orElse(BigDecimal.ZERO);
    }

    private static int orders(List<ReportDAO.ChannelSales> sales, OrderChannel channel) {
        return sales.stream().filter(s -> s.channel() == channel).mapToInt(ReportDAO.ChannelSales::orders).findFirst().orElse(0);
    }
}
