package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.GiftCard;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.service.ShiftSheet;
import com.coffeeshop.coffeeshopmanagement.service.StaffService;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.Session;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO 4.53: {@link ShiftDAO#sheetExtras}, the detail behind the shift-close sheet: refunds (what they came to, how many, the cash
 * part), discounts, cash taken for gift cards and the takings per method, for the window {@code from <= time < to}. Rows are made by
 * hand, each test on a day of its own in 2038 (no other test writes there, and the database is shared), and removed afterwards. A
 * refund counts in the window in which it was MADE, so one made after a close is not in the closed shift (as the shift totals of 4.6
 * and the ledger of 3.13 already say), and the sheet of an old shift reprints with the figures of the first print.
 */
public class ShiftSheetDatabaseTest {

    private static final AtomicInteger NEXT_DAY = new AtomicInteger(0);

    private final ShiftDAO shifts = new ShiftDAO();
    private final StaffService staff = new StaffService();
    private final List<Integer> orderIds = new ArrayList<>();
    private final List<Integer> cardIds = new ArrayList<>();

    private LocalDate savedToday;
    private LocalDateTime from;   // 08:00 of this test's day: the shift starts here
    private LocalDateTime to;     // 12:00: the shift is closed here

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
    public void setUp() {
        TestSession.admin();
        savedToday = AppClock.today();
        LocalDate day = LocalDate.of(2038, 1, 1).plusDays(NEXT_DAY.incrementAndGet() * 3L);
        from = day.atTime(8, 0);
        to = day.atTime(12, 0);
        AppClock.fix(from.plusHours(1));
    }

    @After
    public void removeWhatWasMade() throws SQLException {
        AppClock.pinDate(savedToday);
        try (Connection c = DatabaseConfig.getConnection(); Statement st = c.createStatement()) {
            for (int id : orderIds) {
                st.executeUpdate("DELETE FROM order_payments WHERE order_id = " + id);
                st.executeUpdate("DELETE FROM order_items WHERE order_id = " + id);
                st.executeUpdate("DELETE FROM orders WHERE id = " + id);
            }
            for (int id : cardIds) {
                st.executeUpdate("DELETE FROM gift_card_moves WHERE card_id = " + id);
                st.executeUpdate("DELETE FROM gift_cards WHERE id = " + id);
            }
        }
        orderIds.clear();
        cardIds.clear();
        TestSession.admin();
    }

    private static String at(LocalDateTime time) {
        return DbTime.write(time);
    }

    /** One order row by hand: {@code cancelledAt} is null for an order that was not refunded; {@code paidBy} is the cashier or null. */
    private int order(String status, LocalDateTime paidAt, LocalDateTime cancelledAt, long subtotal, long discount, long total,
                      String method, Integer paidBy) throws SQLException {
        String sql = "INSERT INTO orders (order_date, status, subtotal, discount, total, payment_method, paid_at, cancelled_at, paid_by) VALUES (?,?,?,?,?,?,?,?,?)";
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, at(paidAt));
            st.setString(2, status);
            st.setLong(3, subtotal);
            st.setLong(4, discount);
            st.setLong(5, total);
            st.setString(6, method);
            st.setString(7, at(paidAt));
            if (cancelledAt == null) st.setNull(8, java.sql.Types.VARCHAR); else st.setString(8, at(cancelledAt));
            if (paidBy == null) st.setNull(9, java.sql.Types.INTEGER); else st.setInt(9, paidBy);
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                keys.next();
                orderIds.add(keys.getInt(1));
                return keys.getInt(1);
            }
        }
    }

    /** A paid order that stands. */
    private int sale(LocalDateTime paidAt, long total, long discount, String method) throws SQLException {
        return order("PAID", paidAt, null, total + discount, discount, total, method, null);
    }

    private int sale(LocalDateTime paidAt, long total, long discount, String method, Employee cashier) throws SQLException {
        return order("PAID", paidAt, null, total + discount, discount, total, method, cashier.getId());
    }

    /** A paid order that was refunded whole at {@code refundedAt}. */
    private int refundedSale(LocalDateTime paidAt, LocalDateTime refundedAt, long total, long discount, String method) throws SQLException {
        return order("CANCELLED", paidAt, refundedAt, total + discount, discount, total, method, null);
    }

    private int refundedSale(LocalDateTime paidAt, LocalDateTime refundedAt, long total, long discount, String method, Employee cashier)
            throws SQLException {
        return order("CANCELLED", paidAt, refundedAt, total + discount, discount, total, method, cashier.getId());
    }

    /** One part of an order paid with two methods (what {@code order_payments} keeps; the order's own method is the main non-cash one). */
    private void pay(int orderId, String method, long amount) throws SQLException {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("INSERT INTO order_payments (order_id, method, amount) VALUES (?, ?, ?)")) {
            st.setInt(1, orderId);
            st.setString(2, method);
            st.setLong(3, amount);
            st.executeUpdate();
        }
    }

    private Employee person(String name) {
        Employee e = new Employee();
        e.setFullName(name + " " + UUID.randomUUID().toString().substring(0, 6));
        e.setPhone("09" + String.format(java.util.Locale.ROOT, "%08d", new java.util.Random().nextInt(100_000_000)));
        return staff.save(e);
    }

    private GiftCard sellCardAt(LocalDateTime time, long amount) {
        AppClock.fix(time);
        TestSession.admin();
        GiftCard card = new GiftCardDAO().sell(amount);
        cardIds.add(card.id());
        return card;
    }

    private static long dong(BigDecimal money) {
        return money.longValueExact();
    }

    private static ShiftSheet.Method method(ShiftSheet.Extras extras, PaymentMethod method) {
        return extras.byMethod().stream().filter(m -> m.method() == method).findFirst().orElse(null);
    }

    // ---- refunds

    @Test
    public void aRefundMadeAfterTheCloseIsNotInTheClosedShiftButInTheNextOne() throws SQLException {
        sale(from.plusHours(1), 20_000, 0, "CARD");
        refundedSale(from.plusHours(2), to.plusHours(1), 30_000, 0, "CASH"); // paid at 10:00, given back at 13:00

        ShiftSheet.Extras closed = shifts.sheetExtras(from, to);
        assertEquals("the refund of 13:00 is not in the shift closed at 12:00", 0, closed.refundCount());
        assertEquals(0, dong(closed.refunds()));
        assertEquals(0, dong(closed.refundsInCash()));

        ShiftSheet.Extras next = shifts.sheetExtras(to, to.plusHours(4));
        assertEquals("it is a negative line of the next shift", 1, next.refundCount());
        assertEquals(30_000, dong(next.refunds()));
        assertEquals(30_000, dong(next.refundsInCash()));
    }

    @Test
    public void theSaleOfARefundedOrderStaysInTheShiftThatTookTheMoney() throws SQLException {
        refundedSale(from.plusHours(2), to.plusHours(1), 30_000, 0, "CASH");
        ShiftSheet.Extras closed = shifts.sheetExtras(from, to);
        ShiftSheet.Method cash = method(closed, PaymentMethod.CASH);
        assertNotNull("the sale is still counted in its own shift", cash);
        assertEquals(1, cash.orders());
        assertEquals(30_000, dong(cash.revenue()));
    }

    @Test
    public void aCashRefundIsCountedAsCashAndACardRefundIsNot() throws SQLException {
        refundedSale(from.plusHours(1), from.plusHours(3), 40_000, 0, "CASH");
        refundedSale(from.plusHours(1), from.plusHours(3).plusMinutes(30), 80_000, 0, "CARD");
        sale(from.plusHours(2), 50_000, 0, "CASH");

        ShiftSheet.Extras extras = shifts.sheetExtras(from, to);
        assertEquals(2, extras.refundCount());
        assertEquals("both refunds are in the sum", 120_000, dong(extras.refunds()));
        assertEquals("only the cash one left the drawer", 40_000, dong(extras.refundsInCash()));
    }

    @Test
    public void aRefundOfAnEarlierShiftsSaleFallsInTheShiftThatGaveTheMoneyBack() throws SQLException {
        refundedSale(from.minusMinutes(1), from.plusHours(1), 25_000, 0, "CASH"); // paid at 07:59, given back at 09:00

        ShiftSheet.Extras extras = shifts.sheetExtras(from, to);
        assertEquals(1, extras.refundCount());
        assertEquals(25_000, dong(extras.refunds()));
        assertEquals(25_000, dong(extras.refundsInCash()));
        assertTrue("the sale itself is not in this shift", extras.byMethod().stream().noneMatch(m -> m.revenue().signum() > 0));
    }

    // ---- discounts

    @Test
    public void discountsAreThoseOfTheSalesOfTheWindowLessThoseOfOrdersRefundedInIt() throws SQLException {
        sale(from.plusHours(1), 100_000, 10_000, "CASH");                                      // +10,000
        refundedSale(from.plusHours(1), from.plusHours(3), 40_000, 5_000, "CASH");              // +5,000 sold, -5,000 given back
        refundedSale(from.plusHours(2), to.plusHours(1), 30_000, 3_000, "CASH");                // refunded after the close: stays, +3,000
        sale(from.minusHours(1), 70_000, 7_000, "CASH");                                        // another shift
        sale(to, 60_000, 6_000, "CASH");                                                        // exactly at the end: the next shift

        assertEquals(13_000, dong(shifts.sheetExtras(from, to).discounts()));
    }

    @Test
    public void aSaleWithoutADiscountGivesNone() throws SQLException {
        sale(from.plusHours(1), 100_000, 0, "CARD");
        assertEquals(0, dong(shifts.sheetExtras(from, to).discounts()));
    }

    // ---- gift cards

    @Test
    public void giftCardsSoldInTheWindowAreCountedLessThoseVoided() {
        sellCardAt(from.minusHours(1), 40_000);                          // before the shift
        sellCardAt(from.plusHours(1), 200_000);                          // in
        GiftCard mistake = sellCardAt(from.plusHours(2), 100_000);       // in, then taken back
        AppClock.fix(from.plusHours(2).plusMinutes(30));
        TestSession.admin();
        new GiftCardDAO().voidCard(mistake.code());
        sellCardAt(to.plusHours(1), 50_000);                             // after the close

        ShiftSheet.Extras extras = shifts.sheetExtras(from, to);
        assertEquals("200,000 sold, 100,000 sold and given back", 200_000, dong(extras.giftCardsSold()));
        assertEquals("a card is no sale", 0, extras.byMethod().size());
        assertEquals(50_000, dong(shifts.sheetExtras(to, to.plusHours(4)).giftCardsSold()));
    }

    // ---- the takings per method and the shift's own totals

    @Test
    public void theTakingsPerMethodAreNetOfRefundsAndAddUpToTheShiftsRevenue() throws SQLException {
        sale(from.plusHours(1), 100_000, 10_000, "CASH");
        sale(from.plusHours(2), 60_000, 0, "CARD");
        refundedSale(from.plusHours(1), from.plusHours(3), 40_000, 5_000, "CASH");
        refundedSale(from.plusHours(1), from.plusHours(3), 80_000, 8_000, "CARD");
        refundedSale(from.plusHours(2), to.plusHours(1), 30_000, 3_000, "CASH");   // refunded after the close

        ShiftSheet.Extras extras = shifts.sheetExtras(from, to);
        ShiftSheet.Method cash = method(extras, PaymentMethod.CASH);
        ShiftSheet.Method card = method(extras, PaymentMethod.CARD);
        assertNotNull(cash);
        assertNotNull(card);
        assertEquals("100,000 + (40,000 - 40,000) + 30,000", 130_000, dong(cash.revenue()));
        assertEquals("a sale and its refund in one shift cancel out, the order too", 2, cash.orders());
        assertEquals("60,000 + (80,000 - 80,000)", 60_000, dong(card.revenue()));
        assertEquals(1, card.orders());

        // what the sheet shows per method is what the close stores as the shift's revenue and cash
        ShiftDAO.Summary summary = shifts.summarize(from, to);
        long byMethodTotal = extras.byMethod().stream().mapToLong(m -> dong(m.revenue())).sum();
        assertEquals(dong(summary.revenue()), byMethodTotal);
        assertEquals(dong(summary.cashSales()), dong(cash.revenue()));
    }

    // ---- an order paid with two methods

    @Test
    public void anOrderPaidWithTwoMethodsIsSplitBetweenThemAndItsCashIsOnlyTheCashPart() throws SQLException {
        int id = sale(from.plusHours(1), 100_000, 0, "CARD"); // 60,000 in cash and 40,000 by card
        pay(id, "CASH", 60_000);
        pay(id, "CARD", 40_000);

        ShiftSheet.Extras extras = shifts.sheetExtras(from, to);
        ShiftSheet.Method cash = method(extras, PaymentMethod.CASH);
        ShiftSheet.Method card = method(extras, PaymentMethod.CARD);
        assertNotNull(cash);
        assertNotNull(card);
        assertEquals(60_000, dong(cash.revenue()));
        assertEquals("the one order counts once for each method that paid it", 1, cash.orders());
        assertEquals(40_000, dong(card.revenue()));
        assertEquals(1, card.orders());

        ShiftDAO.Summary summary = shifts.summarize(from, to);
        assertEquals("the methods add up to the shift's revenue", 100_000, dong(summary.revenue()));
        assertEquals("and the cash is the cash part only", 60_000, dong(summary.cashSales()));
        assertEquals(dong(summary.cashSales()), dong(cash.revenue()));
    }

    @Test
    public void aRefundOfASplitOrderTakesBackItsCashPartFromTheDrawerAndTheRestFromTheCard() throws SQLException {
        int id = refundedSale(from.plusHours(1), from.plusHours(3), 100_000, 0, "CARD");
        pay(id, "CASH", 60_000);
        pay(id, "CARD", 40_000);

        ShiftSheet.Extras extras = shifts.sheetExtras(from, to);
        assertEquals(1, extras.refundCount());
        assertEquals(100_000, dong(extras.refunds()));
        assertEquals("only the cash part left the drawer", 60_000, dong(extras.refundsInCash()));
        for (ShiftSheet.Method part : extras.byMethod()) {
            assertEquals("sold and given back in one shift: nothing is left of " + part.method(), 0, dong(part.revenue()));
            assertEquals(0, part.orders());
        }
    }

    // ---- who gave the discounts

    @Test
    public void discountsAreSplitPerCashierAndAddUpToTheTotal() throws SQLException {
        Employee an = person("An");
        Employee binh = person("Binh");
        sale(from.plusHours(1), 100_000, 10_000, "CASH", an);
        sale(from.plusHours(2), 50_000, 0, "CASH", an);                                    // no discount: not one of An's discounted orders
        refundedSale(from.plusHours(2), to.plusHours(1), 30_000, 3_000, "CASH", an);       // refunded after the close: stays
        sale(from.plusHours(1), 60_000, 4_000, "CARD", binh);
        refundedSale(from.plusHours(1), from.plusHours(3), 40_000, 5_000, "CASH", binh);   // sold and refunded in the shift: cancels out
        sale(from.minusHours(1), 70_000, 7_000, "CASH", binh);                              // another shift

        ShiftSheet.Extras extras = shifts.sheetExtras(from, to);
        List<ShiftSheet.DiscountShare> people = extras.discountsByPerson();
        assertEquals(2, people.size());
        assertEquals("the biggest first", an.getFullName(), people.get(0).name());
        assertEquals(2, people.get(0).orders());
        assertEquals(13_000, dong(people.get(0).amount()));
        assertEquals(binh.getFullName(), people.get(1).name());
        assertEquals("the refunded order is one sold and one given back", 1, people.get(1).orders());
        assertEquals(4_000, dong(people.get(1).amount()));
        assertEquals("the people add up to the discounts of the sheet", dong(extras.discounts()),
                people.stream().mapToLong(p -> dong(p.amount())).sum());
    }

    @Test
    public void aCashierWhoseDiscountsAllWentBackIsLeftOut() throws SQLException {
        Employee chi = person("Chi");
        refundedSale(from.plusHours(1), from.plusHours(2), 40_000, 5_000, "CASH", chi);
        assertTrue(shifts.sheetExtras(from, to).discountsByPerson().isEmpty());
    }

    @Test
    public void anOrderWithoutACashierGetsALineWithoutAName() throws SQLException {
        sale(from.plusHours(1), 90_000, 2_000, "CASH");
        List<ShiftSheet.DiscountShare> people = shifts.sheetExtras(from, to).discountsByPerson();
        assertEquals(1, people.size());
        assertEquals(2_000, dong(people.get(0).amount()));
        assertTrue("no name to show: the sheet says Unknown", people.get(0).name() == null);
    }

    // ---- who may read it, and a shift with nothing in it

    @Test
    public void aQuietWindowGivesZerosAndNoMethods() {
        ShiftSheet.Extras extras = shifts.sheetExtras(from, to);
        assertEquals(0, extras.refundCount());
        assertEquals(0, dong(extras.refunds()));
        assertEquals(0, dong(extras.refundsInCash()));
        assertEquals(0, dong(extras.discounts()));
        assertEquals(0, dong(extras.giftCardsSold()));
        assertTrue(extras.byMethod().isEmpty());
        assertTrue(extras.discountsByPerson().isEmpty());
    }

    @Test
    public void aCashierMayReadItBecauseTheCashierPrintsTheSheetRightAfterClosing() throws SQLException {
        sale(from.plusHours(1), 100_000, 10_000, "CASH");
        refundedSale(from.plusHours(1), from.plusHours(2), 40_000, 0, "CASH");
        ShiftSheet.Extras asAdmin = shifts.sheetExtras(from, to);

        User cashier = new User();
        cashier.setId(2);
        cashier.setUsername("cashier");
        cashier.setRole(Role.EMPLOYEE);
        Session.start(cashier, null);
        ShiftSheet.Extras asCashier = shifts.sheetExtras(from, to);

        assertEquals(asAdmin, asCashier);
        assertEquals(1, asCashier.refundCount());
    }

    @Test
    public void readingItTwiceGivesTheSameFiguresSoAReprintMatchesTheFirstPrint() throws SQLException {
        sale(from.plusHours(1), 100_000, 10_000, "CASH");
        refundedSale(from.plusHours(1), to.plusHours(2), 40_000, 0, "CARD"); // a refund made long after
        sale(from.plusHours(2), 10_000, 0, "CASH"); // a sale that stands for now ...
        int standing = orderIds.get(orderIds.size() - 1);
        ShiftSheet.Extras first = shifts.sheetExtras(from, to);
        // ... and is refunded later still, after the shift was closed and its sheet printed: the same sale, now cancelled.
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement(
                "UPDATE orders SET status = 'CANCELLED', cancelled_at = ? WHERE id = ?")) {
            st.setString(1, at(to.plusHours(3)));
            st.setInt(2, standing);
            st.executeUpdate();
        }
        assertEquals("a later refund does not change a closed shift", first, shifts.sheetExtras(from, to));
    }
}
