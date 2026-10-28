package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.*;

/**
 * TODO 3.17 (cash received and change), 3.25 (points earned), 4.31 (bank reference): what a payment leaves on the order, through
 * both ways an order gets paid (saved as paid at once, and an open order paid later). Each test makes its own rows.
 */
public class OrderPaymentDetailsTest {

    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private Category category;

    @BeforeClass
    public static void setUpDatabase() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void createCategory() {
        category = new Category();
        category.setName("test-pay-category-" + UUID.randomUUID());
        category.setActive(true);
        categoryDAO.insert(category);
    }

    private Product newProduct(int stock, String price) {
        Product product = new Product();
        product.setName("test-pay-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setActive(true);
        return productDAO.insert(product);
    }

    private Customer newCustomer(int points) {
        Customer customer = new Customer();
        customer.setFullName("test-pay-customer-" + UUID.randomUUID());
        customer.setLoyaltyPoints(points);
        return customerDAO.insert(customer);
    }

    private OrderItem lineFor(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private Order paidOrder(String total, Integer customerId, PaymentMethod method) {
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(new BigDecimal(total));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal(total));
        order.setPaymentMethod(method);
        order.setPaidAt(AppClock.now());
        return order;
    }

    private Order openOrder() {
        // OrderDAO refuses an order on a table that does not exist (TODO 4.18), so the test makes its own table first.
        int tableNumber = ThreadLocalRandom.current().nextInt(1000, 9000); // a table nobody else uses in this run
        try {
            sql("INSERT INTO dining_tables (table_number, name, status, capacity) VALUES (?, ?, "
                    + com.coffeeshop.coffeeshopmanagement.config.SqlStatus.EMPTY + ", 4)", tableNumber, "test-pay-table-" + tableNumber);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setTableNumber(tableNumber);
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        return orderDAO.saveOrUpdateOpenOrder(order, List.of());
    }

    private void sql(String sql, Object... params) throws Exception {
        try (Connection connection = DatabaseConfig.getConnection(); PreparedStatement st = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) st.setObject(i + 1, params[i]);
            st.executeUpdate();
        }
    }

    private int pointsOf(Customer customer) {
        return customerDAO.findById(customer.getId()).orElseThrow().getLoyaltyPoints();
    }

    // ------------------------------------------------------------------ cash (3.17)

    @Test
    public void aCashSaleKeepsWhatWasHandedOverAndTheChange() {
        Product product = newProduct(10, "40000");
        Order order = paidOrder("80000", null, PaymentMethod.CASH);
        order.setCashReceived(new BigDecimal("100000"));
        Order saved = orderDAO.insert(order, List.of(lineFor(product, 2)));

        Order reloaded = orderDAO.findById(saved.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100000").compareTo(reloaded.getCashReceived()));
        assertEquals(0, new BigDecimal("20000").compareTo(reloaded.getChangeGiven()));
    }

    @Test
    public void payingExactlyLeavesZeroChangeNotNull() {
        Product product = newProduct(10, "40000");
        Order order = paidOrder("40000", null, PaymentMethod.CASH);
        order.setCashReceived(new BigDecimal("40000"));
        Order reloaded = orderDAO.findById(orderDAO.insert(order, List.of(lineFor(product, 1))).getId()).orElseThrow();
        assertEquals(0, reloaded.getChangeGiven().signum());
    }

    @Test
    public void lessCashThanTheTotalIsRefusedAndNothingIsSold() {
        Product product = newProduct(10, "40000");
        Order order = paidOrder("40000", null, PaymentMethod.CASH);
        order.setCashReceived(new BigDecimal("39000"));
        assertThrows(DataAccessException.class, () -> orderDAO.insert(order, List.of(lineFor(product, 1))));
        assertEquals("the stock must be untouched", 10,
                productDAO.findAll().stream().filter(p -> p.getId() == product.getId()).findFirst().orElseThrow().getStock());
    }

    @Test
    public void aCashSaleWithoutAFigureStaysValidAndKeepsNone() {
        Product product = newProduct(10, "40000");
        Order reloaded = orderDAO.findById(orderDAO.insert(paidOrder("40000", null, PaymentMethod.CASH),
                List.of(lineFor(product, 1))).getId()).orElseThrow();
        assertNull(reloaded.getCashReceived());
        assertNull(reloaded.getChangeGiven());
    }

    @Test
    public void aCardOrTransferSaleNeverKeepsACashFigure() {
        Product product = newProduct(10, "40000");
        Order card = paidOrder("40000", null, PaymentMethod.CARD);
        card.setCashReceived(new BigDecimal("50000"));
        Order reloaded = orderDAO.findById(orderDAO.insert(card, List.of(lineFor(product, 1))).getId()).orElseThrow();
        assertNull(reloaded.getCashReceived());
        assertNull(reloaded.getChangeGiven());
    }

    @Test
    public void anOpenOrderPaidLaterKeepsTheCashFiguresToo() {
        Product product = newProduct(10, "40000");
        Order open = openOrder();
        Order paid = orderDAO.payExistingOrder(open.getId(), null, PaymentMethod.CASH, BigDecimal.ZERO, null,
                new BigDecimal("80000"), List.of(lineFor(product, 2)), null, new BigDecimal("100000"), null);
        assertEquals(0, new BigDecimal("100000").compareTo(paid.getCashReceived()));
        assertEquals(0, new BigDecimal("20000").compareTo(paid.getChangeGiven()));
        assertThrows(DataAccessException.class, () -> orderDAO.payExistingOrder(openOrder().getId(), null, PaymentMethod.CASH,
                BigDecimal.ZERO, null, new BigDecimal("40000"), List.of(lineFor(product, 1)), null, new BigDecimal("1000"), null));
    }

    // ------------------------------------------------------------------ transfer reference (4.31)

    @Test
    public void aTransferKeepsItsCleanedReferenceAndOtherMethodsKeepNone() {
        Product product = newProduct(10, "40000");
        Order transfer = paidOrder("40000", null, PaymentMethod.TRANSFER);
        transfer.setTransferRef("  FT26298   123456 ");
        Order a = orderDAO.findById(orderDAO.insert(transfer, List.of(lineFor(product, 1))).getId()).orElseThrow();
        assertEquals("FT26298 123456", a.getTransferRef());

        Order cash = paidOrder("40000", null, PaymentMethod.CASH);
        cash.setTransferRef("SHOULD-NOT-STAY");
        Order b = orderDAO.findById(orderDAO.insert(cash, List.of(lineFor(product, 1))).getId()).orElseThrow();
        assertNull(b.getTransferRef());
    }

    @Test
    public void anOpenOrderPaidByTransferKeepsTheReference() {
        Product product = newProduct(10, "40000");
        Order paid = orderDAO.payExistingOrder(openOrder().getId(), null, PaymentMethod.TRANSFER, BigDecimal.ZERO,
                null, new BigDecimal("40000"), List.of(lineFor(product, 1)), null, null, "FT99");
        assertEquals("FT99", paid.getTransferRef());
    }

    @Test
    public void theHistorySearchFindsATransferByItsReferenceInAnyCase() {
        Product product = newProduct(10, "40000");
        String reference = "REF" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Order transfer = paidOrder("40000", null, PaymentMethod.TRANSFER);
        transfer.setTransferRef(reference);
        Order saved = orderDAO.insert(transfer, List.of(lineFor(product, 1)));

        for (String typed : List.of(reference, reference.toLowerCase(), reference.substring(2, 11))) {
            assertTrue(typed, orderDAO.findHistoryPage(new OrderDAO.HistoryFilter(typed, null, null, null), 50, 0).stream()
                    .anyMatch(s -> s.order().getId() == saved.getId()));
        }
        assertTrue(orderDAO.findHistoryPage(new OrderDAO.HistoryFilter(reference + "ZZ", null, null, null), 50, 0).isEmpty());
    }

    // ------------------------------------------------------------------ points (3.25)

    @Test
    public void thePointsAnOrderGaveAreSavedWithIt() {
        Product product = newProduct(10, "50000");
        Customer customer = newCustomer(0);
        Order saved = orderDAO.insert(paidOrder("100000", customer.getId(), PaymentMethod.CASH), List.of(lineFor(product, 2)));
        int expected = LoyaltyPolicy.pointsFor(new BigDecimal("100000"));
        assertTrue(expected > 0);
        assertEquals(Integer.valueOf(expected), orderDAO.findById(saved.getId()).orElseThrow().getPointsEarned());
        assertEquals(expected, pointsOf(customer));
    }

    @Test
    public void aSaleWithoutACustomerRecordsZeroPoints() {
        Product product = newProduct(10, "50000");
        Order saved = orderDAO.insert(paidOrder("100000", null, PaymentMethod.CASH), List.of(lineFor(product, 2)));
        assertEquals(Integer.valueOf(0), orderDAO.findById(saved.getId()).orElseThrow().getPointsEarned());
    }

    @Test
    public void anOpenOrderPaidLaterRecordsItsPointsToo() {
        Product product = newProduct(10, "50000");
        Customer customer = newCustomer(0);
        Order paid = orderDAO.payExistingOrder(openOrder().getId(), customer.getId(), PaymentMethod.CASH, BigDecimal.ZERO,
                new BigDecimal("100000"), List.of(lineFor(product, 2)));
        assertEquals(Integer.valueOf(LoyaltyPolicy.pointsFor(new BigDecimal("100000"))),
                orderDAO.findById(paid.getId()).orElseThrow().getPointsEarned());
    }

    @Test
    public void anOpenOrderIsSavedWithNoPointsRecorded() {
        assertNull(orderDAO.findById(openOrder().getId()).orElseThrow().getPointsEarned());
    }

    @Test
    public void aRefundTakesBackExactlyThePointsRecordedNotWhatTodaysRuleWouldGive() throws Exception {
        Product product = newProduct(10, "50000");
        Customer customer = newCustomer(0);
        Order saved = orderDAO.insert(paidOrder("100000", customer.getId(), PaymentMethod.CASH), List.of(lineFor(product, 2)));
        int byRule = LoyaltyPolicy.pointsFor(new BigDecimal("100000"));
        assertNotEquals("this test needs a recorded number different from the rule's", 3, byRule);
        sql("UPDATE orders SET points_earned = 3 WHERE id = ?", saved.getId());   // as if the rule had been different that day
        sql("UPDATE customers SET loyalty_points = 50 WHERE id = ?", customer.getId());

        orderDAO.cancelPaidOrder(saved.getId());

        assertEquals(47, pointsOf(customer));
    }

    @Test
    public void aRefundOfAnOrderPaidBeforePointsWereRecordedFallsBackToTheRule() throws Exception {
        Product product = newProduct(10, "50000");
        Customer customer = newCustomer(0);
        Order saved = orderDAO.insert(paidOrder("100000", customer.getId(), PaymentMethod.CASH), List.of(lineFor(product, 2)));
        sql("UPDATE orders SET points_earned = NULL WHERE id = ?", saved.getId());   // an old order has no record
        sql("UPDATE customers SET loyalty_points = 500 WHERE id = ?", customer.getId());

        orderDAO.cancelPaidOrder(saved.getId());

        assertEquals(500 - LoyaltyPolicy.pointsFor(new BigDecimal("100000")), pointsOf(customer));
    }

    // ------------------------------------------------------------------ e-wallets (4.34)

    @Test
    public void eachWalletIsSavedAndReadBackAsItselfWithItsCleanedCode() {
        Product product = newProduct(10, "40000");
        for (PaymentMethod wallet : PaymentMethod.eWallets()) {
            Order sale = paidOrder("40000", null, wallet);
            sale.setTransferRef("  " + wallet.name() + "   77 ");
            sale.setCashReceived(new BigDecimal("100000")); // never kept for a wallet
            Order read = orderDAO.findById(orderDAO.insert(sale, List.of(lineFor(product, 1))).getId()).orElseThrow();
            assertEquals(wallet, read.getPaymentMethod());
            assertEquals(wallet.name() + " 77", read.getTransferRef());
            assertNull(read.getCashReceived());
            assertNull(read.getChangeGiven());
        }
    }

    @Test
    public void anOpenOrderPaidWithAWalletKeepsTheMethodAndTheCode() {
        Product product = newProduct(10, "40000");
        Order paid = orderDAO.payExistingOrder(openOrder().getId(), null, PaymentMethod.ZALOPAY, BigDecimal.ZERO,
                null, new BigDecimal("40000"), List.of(lineFor(product, 1)), null, null, "ZP-1");
        assertEquals(PaymentMethod.ZALOPAY, orderDAO.findById(paid.getId()).orElseThrow().getPaymentMethod());
        assertEquals("ZP-1", paid.getTransferRef());
    }
}
