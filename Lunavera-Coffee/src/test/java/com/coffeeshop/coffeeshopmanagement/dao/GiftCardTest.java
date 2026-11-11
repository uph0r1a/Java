package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.GiftCard;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentLine;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.service.GiftCardLookupGuard;
import com.coffeeshop.coffeeshopmanagement.service.GiftCardRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/** TODO 7, gift cards (rule D23): the code, selling, paying part or all of an order with a card, refunds, and the shift's cash. */
public class GiftCardTest {

    private static final AtomicInteger NEXT_DAY = new AtomicInteger(0);

    private final GiftCardDAO cards = new GiftCardDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private LocalDate savedToday;
    private LocalDateTime dayStart;
    private Product product;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void setUp() {
        TestSession.admin();
        savedToday = AppClock.today();
        dayStart = LocalDate.of(2400, 1, 1).plusDays(NEXT_DAY.incrementAndGet() * 3L + (System.nanoTime() % 1000) * 3L).atTime(8, 0);
        AppClock.fix(dayStart.plusHours(4));
        Category category = new Category();
        category.setName("test-gift-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        product = new Product();
        product.setName("test-gift-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal("10000"));
        product.setStock(100000);
        product.setActive(true);
        new ProductDAO().insert(product);
    }

    @After
    public void restoreClock() {
        AppClock.pinDate(savedToday);
    }

    private Order paidWith(String total, PaymentMethod main, List<PaymentLine> lines) {
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal(total));
        item.setLineTotal(new BigDecimal(total));
        Order order = new Order();
        order.setOrderDate(dayStart.plusHours(1));
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(new BigDecimal(total));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal(total));
        order.setPaymentMethod(main);
        order.setPaidAt(dayStart.plusHours(1));
        order.setPaymentLines(lines);
        return orderDAO.insert(order, List.of(item));
    }

    private static PaymentLine card(GiftCard c, String amount) {
        return new PaymentLine(PaymentMethod.GIFT_CARD, new BigDecimal(amount), c.code());
    }

    private static PaymentLine pay(PaymentMethod method, String amount) {
        return new PaymentLine(method, new BigDecimal(amount));
    }

    private long balance(GiftCard c) {
        return cards.findByCode(c.code()).orElseThrow().balance();
    }

    private static int countOrders() {
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement s = c.prepareStatement("SELECT COUNT(*) FROM orders");
             ResultSet rs = s.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Long> rows(int orderId) throws Exception {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement s = c.prepareStatement("SELECT method, amount FROM order_payments WHERE order_id = ?")) {
            s.setInt(1, orderId);
            try (ResultSet rs = s.executeQuery()) {
                Map<String, Long> out = new java.util.TreeMap<>();
                while (rs.next()) out.put(rs.getString(1), rs.getLong(2));
                return out;
            }
        }
    }

    @Test
    public void aCodeLooksRightAndIsTidiedFromWhatWasTyped() {
        String code = GiftCardRules.newCode();
        assertTrue(code.matches("GC-[A-Z2-9]{4}-[A-Z2-9]{4}"));
        assertEquals(code, GiftCardRules.normalise(code.toLowerCase()));
        assertEquals(code, GiftCardRules.normalise(" " + code.replace("-", " ") + " "));
        assertEquals(code, GiftCardRules.normalise(code.replace("-", "")));
        assertNull(GiftCardRules.normalise("GC-0000-0000")); // no 0 in a code
        assertNull(GiftCardRules.normalise("XX-ABCD-EFGH"));
        assertNull(GiftCardRules.normalise(null));
    }

    @Test
    public void anAmountIsWholeThousandsInsideTheLimits() {
        assertTrue(GiftCardRules.validAmount(10_000));
        assertTrue(GiftCardRules.validAmount(5_000_000));
        assertFalse(GiftCardRules.validAmount(9_000));
        assertFalse(GiftCardRules.validAmount(5_001_000));
        assertFalse(GiftCardRules.validAmount(150_500));
    }

    @Test
    public void aSoldCardHoldsItsAmountAndCanBeFoundByCode() {
        GiftCard sold = cards.sell(200_000);
        assertEquals(200_000, sold.balance());
        assertEquals(200_000, sold.initialAmount());
        assertEquals(sold.code(), cards.findByCode(sold.code().toLowerCase()).orElseThrow().code());
        assertTrue(cards.findByCode("GC-ABCD-EFGH-X").isEmpty());
    }

    @Test(expected = DataAccessException.class)
    public void aBadAmountIsRefused() {
        cards.sell(12_345);
    }

    @Test
    public void partOfAnOrderCanBePaidWithACardAndTheRestWithCash() throws Exception {
        GiftCard c = cards.sell(50_000);
        Order saved = paidWith("80000", PaymentMethod.CASH, List.of(card(c, "30000"), pay(PaymentMethod.CASH, "50000")));
        assertEquals(20_000, balance(c));
        assertEquals(Map.of("CASH", 50000L, "GIFT_CARD", 30000L), rows(saved.getId()));
        assertEquals(PaymentMethod.GIFT_CARD, orderDAO.findById(saved.getId()).orElseThrow().getPaymentMethod());
    }

    @Test
    public void aCardThatHoldsEnoughCanPayTheWholeOrder() throws Exception {
        GiftCard c = cards.sell(100_000);
        Order saved = paidWith("60000", PaymentMethod.GIFT_CARD, List.of(card(c, "60000")));
        assertEquals(40_000, balance(c));
        assertTrue("one method needs no rows", rows(saved.getId()).isEmpty());
        assertEquals(PaymentMethod.GIFT_CARD, orderDAO.findById(saved.getId()).orElseThrow().getPaymentMethod());
    }

    // TODO 3.87: a bill can use more than one gift card.

    @Test
    public void twoCardsCanPayTheWholeOrder() throws Exception {
        GiftCard a = cards.sell(30_000);
        GiftCard b = cards.sell(50_000);
        Order saved = paidWith("60000", PaymentMethod.GIFT_CARD, List.of(card(a, "30000"), card(b, "30000")));
        assertEquals(0, balance(a));
        assertEquals(20_000, balance(b));
        assertTrue("only cards: the method says it, no rows", rows(saved.getId()).isEmpty());
        assertEquals(PaymentMethod.GIFT_CARD, orderDAO.findById(saved.getId()).orElseThrow().getPaymentMethod());
    }

    @Test
    public void twoCardsAndCashAreOneCardRowAndOneCashRow() throws Exception {
        GiftCard a = cards.sell(20_000);
        GiftCard b = cards.sell(20_000);
        Order saved = paidWith("70000", PaymentMethod.CASH, List.of(card(a, "20000"), card(b, "15000"), pay(PaymentMethod.CASH, "35000")));
        assertEquals(Map.of("CASH", 35000L, "GIFT_CARD", 35000L), rows(saved.getId()));
        assertEquals(0, balance(a));
        assertEquals(5_000, balance(b));
    }

    @Test
    public void theSameCardTwiceIsRefused() {
        GiftCard a = cards.sell(100_000);
        int before = countOrders();
        try {
            paidWith("60000", PaymentMethod.GIFT_CARD, List.of(card(a, "30000"), card(a, "30000")));
            fail("one card is one line");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(100_000, balance(a));
        assertEquals(before, countOrders());
    }

    @Test
    public void ifTheSecondCardIsTooSmallTheFirstIsNotSpent() {
        GiftCard a = cards.sell(50_000);
        GiftCard b = cards.sell(10_000);
        try {
            paidWith("60000", PaymentMethod.GIFT_CARD, List.of(card(a, "30000"), card(b, "30000")));
            fail("the second card holds only 10000");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(50_000, balance(a));
        assertEquals(10_000, balance(b));
    }

    @Test
    public void refundingAnOrderWithTwoCardsReturnsBoth() {
        GiftCard a = cards.sell(30_000);
        GiftCard b = cards.sell(30_000);
        Order saved = paidWith("50000", PaymentMethod.GIFT_CARD, List.of(card(a, "30000"), card(b, "20000")));
        assertEquals(0, balance(a));
        assertEquals(10_000, balance(b));
        orderDAO.cancelPaidOrder(saved.getId(), true);
        assertEquals(30_000, balance(a));
        assertEquals(30_000, balance(b));
    }

    // TODO 4.45: the list, the money still owed on cards, and voiding a card sold by mistake.

    private GiftCardDAO.Row rowOf(GiftCard c) {
        return cards.findAll(100_000).stream().filter(r -> r.card().id() == c.id()).findFirst().orElseThrow();
    }

    private static void signInAsCashier() {
        com.coffeeshop.coffeeshopmanagement.model.User u = new com.coffeeshop.coffeeshopmanagement.model.User();
        u.setId(2);
        u.setUsername("test-cashier");
        u.setRole(com.coffeeshop.coffeeshopmanagement.model.Role.EMPLOYEE);
        com.coffeeshop.coffeeshopmanagement.util.Session.start(u, null);
    }

    @Test
    public void theListShowsTheCardItsStateAndWhenItWasLastUsed() {
        GiftCard c = cards.sell(50_000);
        GiftCardDAO.Row fresh = rowOf(c);
        assertEquals(50_000, fresh.card().initialAmount());
        assertNull("never used", fresh.lastUse());
        assertFalse(fresh.voided());
        paidWith("20000", PaymentMethod.GIFT_CARD, List.of(card(c, "20000")));
        GiftCardDAO.Row used = rowOf(c);
        assertEquals(30_000, used.card().balance());
        assertNotNull(used.lastUse());
    }

    @Test
    public void theOpenBalanceIsWhatIsStillOnTheCards() {
        GiftCardDAO.OpenBalance before = cards.openBalance();
        GiftCard c = cards.sell(100_000);
        GiftCard spent = cards.sell(10_000);
        paidWith("10000", PaymentMethod.GIFT_CARD, List.of(card(spent, "10000"))); // used up: holds nothing
        paidWith("30000", PaymentMethod.GIFT_CARD, List.of(card(c, "30000")));
        GiftCardDAO.OpenBalance after = cards.openBalance();
        assertEquals(before.cards() + 1, after.cards());
        assertEquals(before.amount() + 70_000, after.amount());
    }

    @Test
    public void aCardNothingWasSpentFromCanBeVoidedAndTheCashGoesBackOutOfTheShift() {
        ShiftDAO shifts = new ShiftDAO();
        LocalDateTime from = dayStart;
        LocalDateTime to = dayStart.plusHours(10);
        GiftCard c = cards.sell(300_000);
        BigDecimal cashAfterSale = shifts.summarize(from, to).cashSales();
        GiftCard voided = cards.voidCard(c.code().toLowerCase());
        assertEquals(0, voided.balance());
        assertTrue(rowOf(c).voided());
        assertEquals("the cash given back is taken off the drawer", 0,
                cashAfterSale.subtract(new BigDecimal("300000")).compareTo(shifts.summarize(from, to).cashSales()));
        try {
            cards.voidCard(c.code());
            fail("already voided");
        } catch (DataAccessException expected) {
            assertEquals(com.coffeeshop.coffeeshopmanagement.util.I18n.t("giftcard.void.refused"), expected.getMessage());
        }
        assertEquals("a voided card cannot pay", 0, balance(c));
        try {
            paidWith("10000", PaymentMethod.GIFT_CARD, List.of(card(c, "10000")));
            fail("voided card");
        } catch (DataAccessException expected) {
            // fine
        }
    }

    @Test
    public void aCardThatWasSpentFromCannotBeVoidedEvenAfterAFullRefund() {
        GiftCard c = cards.sell(100_000);
        Order saved = paidWith("40000", PaymentMethod.GIFT_CARD, List.of(card(c, "40000")));
        try {
            cards.voidCard(c.code());
            fail("40000 was spent");
        } catch (DataAccessException expected) {
            // fine
        }
        orderDAO.cancelPaidOrder(saved.getId(), true);
        assertEquals(100_000, balance(c));
        try {
            cards.voidCard(c.code());
            fail("it was used once: the history says so");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(100_000, balance(c));
    }

    @Test
    public void onlyAnAdministratorListsOrVoidsAndAnUnknownCodeIsRefused() {
        GiftCard c = cards.sell(20_000);
        signInAsCashier();
        try {
            cards.findAll(10);
            fail("admin only");
        } catch (DataAccessException expected) {
            // fine
        }
        try {
            cards.voidCard(c.code());
            fail("admin only");
        } catch (DataAccessException expected) {
            // fine
        }
        assertTrue("any signed-in person may see the total", cards.openBalance().cards() >= 1);
        TestSession.admin();
        assertEquals(20_000, balance(c));
        try {
            cards.voidCard("GC-NOPE-NOPE");
            fail("unknown");
        } catch (DataAccessException expected) {
            // fine
        }
    }

    @Test
    public void aCardWithTooLittleStopsThePaymentAndSavesNothing() {
        GiftCard c = cards.sell(20_000);
        int before = countOrders();
        try {
            paidWith("60000", PaymentMethod.GIFT_CARD, List.of(card(c, "60000")));
            fail("the card holds only 20000");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(20_000, balance(c));
        assertEquals(before, countOrders());
    }

    @Test
    public void anUnknownCardStopsThePayment() {
        GiftCard c = cards.sell(20_000);
        PaymentLine nobody = new PaymentLine(PaymentMethod.GIFT_CARD, new BigDecimal("10000"), "GC-AAAA-AAAA");
        try {
            paidWith("30000", PaymentMethod.CASH, List.of(nobody, pay(PaymentMethod.CASH, "20000")));
            fail("no such card");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(20_000, balance(c));
    }

    @Test
    public void refundingTheWholeOrderPutsTheMoneyBackOnTheCardOnlyOnce() {
        GiftCard c = cards.sell(100_000);
        Order saved = paidWith("70000", PaymentMethod.CASH, List.of(card(c, "40000"), pay(PaymentMethod.CASH, "30000")));
        assertEquals(60_000, balance(c));
        orderDAO.cancelPaidOrder(saved.getId(), true);
        assertEquals(100_000, balance(c));
        try {
            orderDAO.cancelPaidOrder(saved.getId(), true);
        } catch (DataAccessException expected) {
            // already refunded
        }
        assertEquals("never restored twice", 100_000, balance(c));
    }

    @Test
    public void cardCashTakenInAShiftCountsAsCashInTheDrawerButNotAsRevenue() {
        ShiftDAO shifts = new ShiftDAO();
        LocalDateTime from = dayStart;
        LocalDateTime to = dayStart.plusHours(10);
        ShiftDAO.Summary before = shifts.summarize(from, to);
        cards.sell(300_000);
        ShiftDAO.Summary after = shifts.summarize(from, to);
        assertEquals(0, before.cashSales().add(new BigDecimal("300000")).compareTo(after.cashSales()));
        assertEquals(0, before.revenue().compareTo(after.revenue()));
    }

    // ---- TODO 2.93: a refund of some LINES of an order paid with a card goes back onto the card first, never into the drawer ----

    /** An order of one line per amount in {@code totals} (one piece each), paid as told. */
    private Order paidLines(PaymentMethod main, List<PaymentLine> lines, String... totals) {
        List<OrderItem> items = new java.util.ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (String t : totals) {
            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setQuantity(1);
            item.setUnitPrice(new BigDecimal(t));
            item.setLineTotal(new BigDecimal(t));
            items.add(item);
            sum = sum.add(new BigDecimal(t));
        }
        Order order = new Order();
        order.setOrderDate(dayStart.plusHours(1));
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(sum);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(sum);
        order.setPaymentMethod(main);
        order.setPaidAt(dayStart.plusHours(1));
        order.setPaymentLines(lines);
        return orderDAO.insert(order, items);
    }

    private List<Integer> lineIds(int orderId) {
        return orderDAO.findItemsByOrderId(orderId).stream().map(OrderItem::getId).toList();
    }

    private BigDecimal drawerCash() {
        return new ShiftDAO().summarize(dayStart, dayStart.plusHours(10)).cashSales();
    }

    private static long dong(BigDecimal money) {
        return money.longValueExact();
    }

    private RefundDAO.Result refundLine(Order order, int lineIndex, PaymentMethod method) {
        return new RefundDAO().refund(order.getId(), Map.of(lineIds(order.getId()).get(lineIndex), 1), false, method, null);
    }

    @Test
    public void aLineRefundOfACardPaymentGoesBackOntoTheCardAndNotIntoCash() {
        GiftCard c = cards.sell(100_000);
        Order saved = paidLines(PaymentMethod.GIFT_CARD, List.of(card(c, "60000")), "40000", "20000");
        assertEquals(40_000, balance(c));
        BigDecimal cashBefore = drawerCash();

        RefundDAO.Result result = refundLine(saved, 0, PaymentMethod.CASH); // the cashier chose cash: the card gets it anyway

        assertEquals(40_000, dong(result.amount()));
        assertEquals(40_000, dong(result.toCard()));
        assertEquals("the card has its money back", 80_000, balance(c));
        assertEquals("no cash left the drawer", 0, cashBefore.compareTo(drawerCash()));
        List<RefundDAO.RefundRow> rows = new RefundDAO().forOrder(saved.getId());
        assertEquals(1, rows.size());
        assertEquals("GIFT_CARD", rows.get(0).method());
        assertEquals(1, rows.get(0).quantity());
    }

    @Test
    public void theReportByPaymentMethodTakesTheRefundOffTheCard() {
        GiftCard c = cards.sell(100_000);
        Order saved = paidLines(PaymentMethod.GIFT_CARD, List.of(card(c, "60000")), "40000", "20000");
        refundLine(saved, 0, PaymentMethod.CASH);
        LocalDate day = dayStart.toLocalDate();
        java.util.Map<PaymentMethod, BigDecimal> byMethod = new java.util.EnumMap<>(PaymentMethod.class);
        new ReportDAO().paymentSplit(day, day).forEach(p -> byMethod.put(p.method(), p.revenue()));
        assertEquals(20_000, dong(byMethod.get(PaymentMethod.GIFT_CARD)));
        assertNull("nothing was paid back in cash", byMethod.get(PaymentMethod.CASH));
    }

    @Test
    public void anOrderPaidPartlyByCardGivesTheCardItsPartFirstAndTheRestInTheMethodChosen() {
        GiftCard c = cards.sell(50_000);
        Order saved = paidLines(PaymentMethod.CASH, List.of(card(c, "30000"), pay(PaymentMethod.CASH, "50000")), "50000", "30000");
        assertEquals(20_000, balance(c));
        BigDecimal cashBefore = drawerCash();

        RefundDAO.Result result = refundLine(saved, 0, PaymentMethod.CASH); // 50.000: 30.000 to the card, 20.000 in cash

        assertEquals(50_000, dong(result.amount()));
        assertEquals(30_000, dong(result.toCard()));
        assertEquals(50_000, balance(c));
        assertEquals("only the 20.000 of cash left the drawer", 0, cashBefore.subtract(new BigDecimal("20000")).compareTo(drawerCash()));
        List<RefundDAO.RefundRow> rows = new RefundDAO().forOrder(saved.getId());
        assertEquals("a line split in two is two rows", 2, rows.size());
        assertEquals("GIFT_CARD", rows.get(0).method());
        assertEquals(30_000, dong(rows.get(0).amount()));
        assertEquals(1, rows.get(0).quantity());
        assertEquals("CASH", rows.get(1).method());
        assertEquals(20_000, dong(rows.get(1).amount()));
        assertEquals("the pieces are counted once", 0, rows.get(1).quantity());
        RefundDAO.State state = new RefundDAO().load(saved.getId());
        assertEquals("the line counts as refunded once", 1, state.lines().get(0).refundedQuantity());
        assertEquals(50_000, dong(state.refundedSoFar()));
        assertEquals("nothing is left on the card to give back", 0, state.cardLeft());
    }

    @Test
    public void severalLineRefundsNeverGiveTheCardMoreThanTheOrderSpent() {
        GiftCard c = cards.sell(100_000);
        Order saved = paidLines(PaymentMethod.CASH, List.of(card(c, "30000"), pay(PaymentMethod.CASH, "50000")), "50000", "30000");
        assertEquals(70_000, balance(c));
        refundLine(saved, 1, PaymentMethod.CASH); // 30.000: all of it onto the card
        assertEquals(100_000, balance(c));
        RefundDAO.Result second = refundLine(saved, 0, PaymentMethod.CASH); // 50.000: the card has nothing left to take
        assertEquals(0, dong(second.toCard()));
        assertEquals("never more than the 30.000 the order spent", 100_000, balance(c));
        try {
            orderDAO.cancelPaidOrder(saved.getId(), true);
            fail("an order with refunded lines cannot be cancelled as a whole");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals("and the refused whole refund gave nothing more", 100_000, balance(c));
    }

    @Test
    public void aCardPaymentRefundedInTwoStepsEndsWithTheCardWhole() {
        GiftCard c = cards.sell(100_000);
        Order saved = paidLines(PaymentMethod.GIFT_CARD, List.of(card(c, "60000")), "40000", "20000");
        refundLine(saved, 0, PaymentMethod.CASH);
        assertEquals(80_000, balance(c));
        refundLine(saved, 1, PaymentMethod.CASH);
        assertEquals(100_000, balance(c));
        assertEquals(0, new RefundDAO().load(saved.getId()).cardLeft());
    }

    @Test
    public void aGiftCardCannotBeChosenToPayBackMoreThanItPaid() {
        GiftCard c = cards.sell(50_000);
        Order saved = paidLines(PaymentMethod.CASH, List.of(card(c, "30000"), pay(PaymentMethod.CASH, "50000")), "50000", "30000");
        try {
            refundLine(saved, 0, PaymentMethod.GIFT_CARD); // 50.000, the card paid 30.000
            fail("the card cannot take 50.000");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals("nothing changed", 20_000, balance(c));
        assertTrue(new RefundDAO().forOrder(saved.getId()).isEmpty());
        refundLine(saved, 1, PaymentMethod.GIFT_CARD); // 30.000: exactly what the card paid
        assertEquals(50_000, balance(c));
    }

    @Test
    public void whatAnOrderSpentOnTwoCardsComesBackOnEachInProportion() throws Exception {
        // The payment screen pays one order with one card, so the second spend is made here on the same connection the way a refund would see it.
        GiftCard a = cards.sell(100_000);
        GiftCard b = cards.sell(50_000);
        Order saved = paidLines(PaymentMethod.GIFT_CARD, List.of(card(a, "60000")), "60000");
        try (Connection c = DatabaseConfig.getConnection()) {
            c.setAutoCommit(false);
            GiftCardDAO.redeem(c, b.code(), new BigDecimal("30000"), saved.getId());
            assertEquals(90_000, GiftCardDAO.spentNet(c, saved.getId()));
            assertEquals("30.000 over 60.000 and 30.000 spent: 20.000 and 10.000", 30_000, GiftCardDAO.restoreUpTo(c, saved.getId(), 30_000));
            assertEquals(60_000, GiftCardDAO.spentNet(c, saved.getId()));
            assertEquals("asking for more gives only what is left", 60_000, GiftCardDAO.restoreUpTo(c, saved.getId(), 999_999));
            assertEquals(0, GiftCardDAO.spentNet(c, saved.getId()));
            assertEquals("and nothing a second time", 0, GiftCardDAO.restoreUpTo(c, saved.getId(), 999_999));
            c.commit();
        }
        assertEquals(100_000, balance(a));
        assertEquals(50_000, balance(b));
    }

    @Test
    public void aRefundWithNothingToSelectChangesNoCard() {
        GiftCard c = cards.sell(100_000);
        Order saved = paidLines(PaymentMethod.GIFT_CARD, List.of(card(c, "60000")), "40000", "20000");
        try {
            new RefundDAO().refund(saved.getId(), Map.of(), false, PaymentMethod.CASH, null);
            fail("nothing was chosen");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(40_000, balance(c));
    }

    @Test
    public void anOrderThatPaidNoCardRefundsExactlyAsBefore() {
        Order saved = paidLines(PaymentMethod.CASH, List.of(), "40000", "20000");
        RefundDAO.Result result = refundLine(saved, 0, PaymentMethod.CASH);
        assertEquals(0, dong(result.toCard()));
        List<RefundDAO.RefundRow> rows = new RefundDAO().forOrder(saved.getId());
        assertEquals(1, rows.size());
        assertEquals("CASH", rows.get(0).method());
        assertEquals(0, new RefundDAO().load(saved.getId()).cardLeft());
    }

    @Test
    public void theReceiptKnowsWhichCardPaidAndWhatWasLeftThen() { // TODO 5.51
        GiftCard c = cards.sell(100_000);
        Order first = paidWith("30000", PaymentMethod.GIFT_CARD, List.of(card(c, "30000")));
        Order second = paidWith("20000", PaymentMethod.GIFT_CARD, List.of(card(c, "20000")));
        var one = cards.usesForOrder(first.getId());
        var two = cards.usesForOrder(second.getId());
        assertEquals(1, one.size());
        assertEquals(c.code().substring(c.code().length() - 4), one.get(0).codeTail());
        assertEquals(0, new BigDecimal("30000").compareTo(one.get(0).paid()));
        assertEquals("what was left right after the first payment, not today's balance", 0, new BigDecimal("70000").compareTo(one.get(0).left()));
        assertEquals(0, new BigDecimal("50000").compareTo(two.get(0).left()));
        assertEquals(50_000, balance(c));
    }

    @Test
    public void anOrderThatPaidNoCardHasNoGiftCardUse() {
        Order cash = paidWith("10000", PaymentMethod.CASH, List.of());
        assertTrue(cards.usesForOrder(cash.getId()).isEmpty());
    }

    // ------------------------------------------------------------------ TODO 2A.45

    private static String unknownCode(int n) {
        return "GC-AAA" + "ABCDEFGHJK".charAt(n % 10) + "-ZZZZ"; // well-formed, never sold
    }

    @Test
    public void sixUnknownCodesInARowMakeTheSixthLookupWaitAndLeaveOneAuditLine() {
        AtomicLong clock = new AtomicLong(1_000);
        GiftCardDAO guarded = new GiftCardDAO(new GiftCardLookupGuard(clock::get));
        GiftCard sold = cards.sell(100_000);
        int linesBefore = new AuditDAO().count(new AuditDAO.Filter(null, null, AuditAction.GIFT_CARD_GUESSING, null, null));
        for (int i = 0; i < GiftCardLookupGuard.MAX_WRONG; i++) {
            assertTrue(guarded.findByCode(unknownCode(i)).isEmpty());
            clock.addAndGet(1_000);
        }
        try {
            guarded.findByCode(unknownCode(99));
            fail("the sixth lookup must wait");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(String.valueOf(GiftCardLookupGuard.PAUSE_SECONDS - 1)));
        }
        try {
            guarded.findByCode(sold.code());
            fail("even a right code waits during the pause");
        } catch (DataAccessException expected) { /* fine */ }
        assertEquals("one audit line when the pause began, none for the lookups that waited", linesBefore + 1,
                new AuditDAO().count(new AuditDAO.Filter(null, null, AuditAction.GIFT_CARD_GUESSING, null, null)));
        clock.addAndGet(GiftCardLookupGuard.PAUSE_SECONDS * 1000L);
        assertEquals(sold.code(), guarded.findByCode(sold.code()).orElseThrow().code());
    }

    @Test
    public void rightCodesAndTextsThatCannotBeACodeAreNotCounted() {
        GiftCardLookupGuard guard = new GiftCardLookupGuard();
        GiftCardDAO guarded = new GiftCardDAO(guard);
        GiftCard sold = cards.sell(100_000);
        for (int i = 0; i < 20; i++) {
            assertTrue(guarded.findByCode(sold.code()).isPresent());
            assertTrue(guarded.findByCode("not a code").isEmpty());
        }
        assertEquals(0, guard.wrongInWindow());
        assertEquals(0, guard.secondsToWait());
    }
}
