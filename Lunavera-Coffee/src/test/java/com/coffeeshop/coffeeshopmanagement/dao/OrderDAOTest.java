package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.model.*;
import com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Covers OrderDAO.insert()'s and cancelPaidOrder()'s transactional logic - the highest-value
 * gap in test coverage, since it's where stock, loyalty points, and order/item rows all have to
 * move together correctly or not at all. See TestDatabaseSupport for why every DAO test shares
 * one database and asserts only about the rows it created itself.
 */
public class OrderDAOTest {

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
        category.setName("test-order-category-" + UUID.randomUUID());
        category.setActive(true);
        categoryDAO.insert(category);
    }

    private Product newProduct(int stock, BigDecimal price) {
        Product product = new Product();
        product.setName("test-order-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(price);
        product.setStock(stock);
        product.setActive(true);
        return productDAO.insert(product);
    }

    private Customer newCustomer() {
        Customer customer = new Customer();
        customer.setFullName("test-order-customer-" + UUID.randomUUID());
        customer.setLoyaltyPoints(0);
        return customerDAO.insert(customer);
    }

    /** One line: quantity x unitPrice, matching what the POS itself computes. */
    private OrderItem lineFor(Product product, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(product.getPrice());
        item.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private Order paidOrderTotaling(BigDecimal total, Integer customerId) {
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(total);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(total);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaidAt(AppClock.now());
        return order;
    }

    @Test
    public void insertPaidOrderDecrementsStockAndAwardsPoints() {
        Product product = newProduct(10, new BigDecimal("50000"));
        Customer customer = newCustomer();
        OrderItem line = lineFor(product, 2); // 100.000đ
        Order order = paidOrderTotaling(new BigDecimal("100000"), customer.getId());

        Order saved = orderDAO.insert(order, List.of(line));
        assertTrue(saved.getId() > 0);

        Product reloadedProduct = productDAO.findAll().stream()
                .filter(p -> p.getId() == product.getId()).findFirst().orElseThrow();
        assertEquals("stock should drop by the quantity sold", 8, reloadedProduct.getStock());

        Customer reloadedCustomer = customerDAO.findById(customer.getId()).orElseThrow();
        assertEquals("points earned should follow LoyaltyPolicy for a 100.000đ order",
                LoyaltyPolicy.pointsFor(new BigDecimal("100000")), reloadedCustomer.getLoyaltyPoints());
    }

    @Test
    public void insertFailsAndRollsBackEverythingWhenStockIsInsufficient() {
        Product product = newProduct(1, new BigDecimal("20000")); // only 1 in stock
        OrderItem line = lineFor(product, 5); // asking for 5
        Order order = paidOrderTotaling(new BigDecimal("100000"), null);

        int paidOrdersBefore = orderDAO.countByStatus(OrderStatus.PAID);
        try {
            orderDAO.insert(order, List.of(line));
            fail("selling more than is in stock should have thrown");
        } catch (DataAccessException e) {
            assertTrue(e.getMessage().contains("không đủ tồn kho"));
        }

        Product reloadedProduct = productDAO.findAll().stream()
                .filter(p -> p.getId() == product.getId()).findFirst().orElseThrow();
        assertEquals("a failed sale must never touch stock", 1, reloadedProduct.getStock());
        assertEquals("a failed sale must never leave a PAID order behind",
                paidOrdersBefore, orderDAO.countByStatus(OrderStatus.PAID));
    }

    @Test
    public void insertWithNoCustomerAttachedSucceedsWithoutAwardingAnyPoints() {
        Product product = newProduct(5, new BigDecimal("30000"));
        OrderItem line = lineFor(product, 1);
        Order order = paidOrderTotaling(new BigDecimal("30000"), null); // walk-in, no customer

        Order saved = orderDAO.insert(order, List.of(line));
        assertTrue(saved.getId() > 0);
        // Nothing to assert about points - there is no customer id for any to land on; the real
        // assertion is simply that insert() doesn't throw when customerId is null.
    }

    @Test
    public void findByIdAndFindItemsByOrderIdReturnWhatWasInserted() {
        Product product = newProduct(5, new BigDecimal("15000"));
        OrderItem line = lineFor(product, 2);
        Order order = paidOrderTotaling(new BigDecimal("30000"), null);
        Order saved = orderDAO.insert(order, List.of(line));

        Order reloaded = orderDAO.findById(saved.getId()).orElseThrow();
        assertEquals(OrderStatus.PAID, reloaded.getStatus());
        assertEquals(0, new BigDecimal("30000").compareTo(reloaded.getTotal()));

        List<OrderItem> items = orderDAO.findItemsByOrderId(saved.getId());
        assertEquals(1, items.size());
        assertEquals(product.getName(), items.get(0).getProductName());
        assertEquals(2, items.get(0).getQuantity());
    }

    @Test
    public void cancelPaidOrderRestoresStockSubtractsPointsAndMarksCancelled() {
        Product product = newProduct(10, new BigDecimal("20000"));
        Customer customer = newCustomer();
        OrderItem line = lineFor(product, 3); // 60.000đ
        Order order = paidOrderTotaling(new BigDecimal("60000"), customer.getId());
        Order saved = orderDAO.insert(order, List.of(line));

        int pointsAfterSale = customerDAO.findById(customer.getId()).orElseThrow().getLoyaltyPoints();
        assertEquals(LoyaltyPolicy.pointsFor(new BigDecimal("60000")), pointsAfterSale);

        orderDAO.cancelPaidOrder(saved.getId());

        assertEquals(OrderStatus.CANCELLED, orderDAO.findById(saved.getId()).orElseThrow().getStatus());
        Product reloadedProduct = productDAO.findAll().stream()
                .filter(p -> p.getId() == product.getId()).findFirst().orElseThrow();
        assertEquals("cancelling should put every sold unit back", 10, reloadedProduct.getStock());
        assertEquals("cancelling should take back exactly the points that sale earned",
                0, customerDAO.findById(customer.getId()).orElseThrow().getLoyaltyPoints());
    }

    @Test
    public void aRefundCanLeaveTheStockAloneButStillTakesBackThePoints() {
        // TODO 2.66: a drink that was made and thrown away must not raise the stock
        Product product = newProduct(10, new BigDecimal("20000"));
        Customer customer = newCustomer();
        Order saved = orderDAO.insert(paidOrderTotaling(new BigDecimal("60000"), customer.getId()), List.of(lineFor(product, 3)));
        orderDAO.cancelPaidOrder(saved.getId(), false);
        assertEquals(OrderStatus.CANCELLED, orderDAO.findById(saved.getId()).orElseThrow().getStatus());
        Product reloaded = productDAO.findAll().stream().filter(p -> p.getId() == product.getId()).findFirst().orElseThrow();
        assertEquals("the 3 units were sold and not put back", 7, reloaded.getStock());
        assertEquals(0, customerDAO.findById(customer.getId()).orElseThrow().getLoyaltyPoints());
    }

    @Test
    public void cancelPaidOrderRefusesAnOrderThatIsNotPaid() {
        Product product = newProduct(5, new BigDecimal("10000"));
        OrderItem line = lineFor(product, 1);
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setStatus(OrderStatus.OPEN); // never paid, so insert() never touches stock/points
        order.setSubtotal(new BigDecimal("10000"));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("10000"));
        Order saved = orderDAO.insert(order, List.of(line));

        try {
            orderDAO.cancelPaidOrder(saved.getId());
            fail("cancelling a non-PAID order should have thrown");
        } catch (DataAccessException e) {
            assertTrue(e.getMessage().contains("đã thanh toán"));
        }
    }

    @Test
    public void historyPageAndCountAgreeAndRespectPageSize() {
        Product product = newProduct(20, new BigDecimal("10000"));
        for (int i = 0; i < 5; i++) {
            orderDAO.insert(paidOrderTotaling(new BigDecimal("10000"), null), List.of(lineFor(product, 1)));
        }

        OrderDAO.HistoryFilter noFilter = OrderDAO.HistoryFilter.NONE;
        int totalBefore = orderDAO.countHistory(noFilter);
        assertTrue("count should include at least the 5 orders just created", totalBefore >= 5);

        List<OrderDAO.OrderSummary> firstPageOfTwo = orderDAO.findHistoryPage(noFilter, 2, 0);
        assertEquals(2, firstPageOfTwo.size());
        // Newest first: the two most recently inserted ids should lead the very first page.
        assertTrue(firstPageOfTwo.get(0).order().getId() > firstPageOfTwo.get(1).order().getId());
    }

    @Test
    public void historyFilterByStatusOnlyMatchesThatStatus() {
        Product product = newProduct(20, new BigDecimal("10000"));
        Order paid = orderDAO.insert(paidOrderTotaling(new BigDecimal("10000"), null), List.of(lineFor(product, 1)));
        orderDAO.cancelPaidOrder(paid.getId());

        OrderDAO.HistoryFilter cancelledOnly = new OrderDAO.HistoryFilter(null, OrderStatus.CANCELLED, null, null);
        boolean found = orderDAO.findHistoryPage(cancelledOnly, 500, 0).stream()
                .anyMatch(s -> s.order().getId() == paid.getId());
        assertTrue("the just-cancelled order should show up under the CANCELLED filter", found);

        OrderDAO.HistoryFilter paidOnly = new OrderDAO.HistoryFilter(null, OrderStatus.PAID, null, null);
        boolean wronglyStillPaid = orderDAO.findHistoryPage(paidOnly, 500, 0).stream()
                .anyMatch(s -> s.order().getId() == paid.getId());
        assertFalse("a cancelled order must not show up under the PAID filter", wronglyStillPaid);
    }

    @Test
    public void historyFilterByKeywordMatchesOrderIdOrCustomerName() {
        Product product = newProduct(20, new BigDecimal("10000"));
        Customer customer = newCustomer();
        Order order = orderDAO.insert(paidOrderTotaling(new BigDecimal("10000"), customer.getId()), List.of(lineFor(product, 1)));

        OrderDAO.HistoryFilter byId = new OrderDAO.HistoryFilter(String.valueOf(order.getId()), null, null, null);
        assertTrue(orderDAO.findHistoryPage(byId, 500, 0).stream().anyMatch(s -> s.order().getId() == order.getId()));

        OrderDAO.HistoryFilter byCustomerName = new OrderDAO.HistoryFilter(customer.getFullName(), null, null, null);
        assertTrue(orderDAO.findHistoryPage(byCustomerName, 500, 0).stream().anyMatch(s -> s.order().getId() == order.getId()));

        OrderDAO.HistoryFilter noMatch = new OrderDAO.HistoryFilter("no-such-keyword-" + UUID.randomUUID(), null, null, null);
        assertTrue(orderDAO.findHistoryPage(noMatch, 500, 0).stream().noneMatch(s -> s.order().getId() == order.getId()));
    }

    @Test
    public void historyFilterByDateRangeExcludesOrdersOutsideIt() {
        Product product = newProduct(20, new BigDecimal("10000"));
        Order order = orderDAO.insert(paidOrderTotaling(new BigDecimal("10000"), null), List.of(lineFor(product, 1)));
        java.time.LocalDate today = AppClock.today();

        OrderDAO.HistoryFilter includesToday = new OrderDAO.HistoryFilter(null, null, today, today);
        assertTrue(orderDAO.findHistoryPage(includesToday, 500, 0).stream().anyMatch(s -> s.order().getId() == order.getId()));

        OrderDAO.HistoryFilter onlyYesterday = new OrderDAO.HistoryFilter(null, null, today.minusDays(1), today.minusDays(1));
        assertTrue("an order from today must not match a yesterday-only range",
                orderDAO.findHistoryPage(onlyYesterday, 500, 0).stream().noneMatch(s -> s.order().getId() == order.getId()));
    }

    @Test
    public void historySortsTheWholeResultBeforePaging() {
        Product product = newProduct(30, new BigDecimal("10000"));
        Customer customer = newCustomer();
        for (String total : new String[]{"30000", "10000", "20000"}) {
            orderDAO.insert(paidOrderTotaling(new BigDecimal(total), customer.getId()), List.of(lineFor(product, Integer.parseInt(total) / 10000)));
        }
        OrderDAO.HistoryFilter mine = new OrderDAO.HistoryFilter(customer.getFullName(), null, null, null);

        OrderDAO.HistorySort ascending = new OrderDAO.HistorySort(OrderDAO.HistorySortKey.TOTAL, false);
        OrderDAO.HistorySort descending = new OrderDAO.HistorySort(OrderDAO.HistorySortKey.TOTAL, true);

        // Page 1 of 2-per-page must hold the two smallest totals overall - not "the two newest, then sorted".
        List<OrderDAO.OrderSummary> ascPage1 = orderDAO.findHistoryPage(mine, ascending, 2, 0);
        assertEquals(new BigDecimal("10000"), ascPage1.get(0).order().getTotal());
        assertEquals(new BigDecimal("20000"), ascPage1.get(1).order().getTotal());

        List<OrderDAO.OrderSummary> descPage1 = orderDAO.findHistoryPage(mine, descending, 2, 0);
        assertEquals(new BigDecimal("30000"), descPage1.get(0).order().getTotal());
        List<OrderDAO.OrderSummary> descPage2 = orderDAO.findHistoryPage(mine, descending, 2, 2);
        assertEquals(1, descPage2.size());
        assertEquals(new BigDecimal("10000"), descPage2.get(0).order().getTotal());
    }

    // ---- step 3: paying an order that was opened on a table -----------------------------------

    private Order openOrderOnTable(int tableNumber, Integer customerId) {
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setTableNumber(tableNumber);
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(BigDecimal.ZERO);
        return orderDAO.saveOrUpdateOpenOrder(order, List.of());
    }

    @Test
    public void payExistingOrderLinksACustomerGivenAtPaymentAndAwardsThemPoints() {
        Product product = newProduct(10, new BigDecimal("50000"));
        Customer customer = newCustomer(); // created at the till, after the order was opened
        Order open = openOrderOnTable(5, null);

        Order paid = orderDAO.payExistingOrder(open.getId(), customer.getId(), PaymentMethod.CASH,
                BigDecimal.ZERO, new BigDecimal("100000"), List.of(lineFor(product, 2)));

        assertEquals(OrderStatus.PAID, paid.getStatus());
        assertEquals(Integer.valueOf(customer.getId()), paid.getCustomerId());
        assertEquals(LoyaltyPolicy.pointsFor(new BigDecimal("100000")),
                customerDAO.findById(customer.getId()).orElseThrow().getLoyaltyPoints());
    }

    @Test
    public void payExistingOrderKeepsTheOrdersCustomerWhenNoneIsGiven() {
        Product product = newProduct(10, new BigDecimal("50000"));
        Customer customer = newCustomer();
        Order open = openOrderOnTable(5, customer.getId());

        Order paid = orderDAO.payExistingOrder(open.getId(), null, PaymentMethod.CASH,
                BigDecimal.ZERO, new BigDecimal("100000"), List.of(lineFor(product, 2)));

        assertEquals(Integer.valueOf(customer.getId()), paid.getCustomerId());
        assertEquals(LoyaltyPolicy.pointsFor(new BigDecimal("100000")),
                customerDAO.findById(customer.getId()).orElseThrow().getLoyaltyPoints());
    }

    @Test
    public void payExistingOrderFreesTheTableInTheSameTransaction() {
        Product product = newProduct(10, new BigDecimal("50000"));
        Order open = openOrderOnTable(6, null);
        assertTrue(new TableDAO().findAll().stream().anyMatch(t -> t.getTableNumber() == 6 && t.isOccupied()));

        orderDAO.payExistingOrder(open.getId(), null, PaymentMethod.CASH,
                BigDecimal.ZERO, new BigDecimal("50000"), List.of(lineFor(product, 1)));

        assertTrue(new TableDAO().findAll().stream().anyMatch(t -> t.getTableNumber() == 6 && !t.isOccupied()));
    }

    @Test
    public void payingTheSameOrderTwiceFailsAndChangesNothing() {
        Product product = newProduct(10, new BigDecimal("50000"));
        Customer customer = newCustomer();
        Order open = openOrderOnTable(7, customer.getId());
        orderDAO.payExistingOrder(open.getId(), null, PaymentMethod.CASH,
                BigDecimal.ZERO, new BigDecimal("50000"), List.of(lineFor(product, 1)));

        try {
            orderDAO.payExistingOrder(open.getId(), null, PaymentMethod.CASH,
                    BigDecimal.ZERO, new BigDecimal("50000"), List.of(lineFor(product, 1)));
            fail("a second payment of the same order must be refused");
        } catch (DataAccessException expected) {
            // expected
        }
        assertEquals("stock must only drop once", 9, productDAO.findAll().stream()
                .filter(p -> p.getId() == product.getId()).findFirst().orElseThrow().getStock());
        assertEquals("points must only be awarded once", LoyaltyPolicy.pointsFor(new BigDecimal("50000")),
                customerDAO.findById(customer.getId()).orElseThrow().getLoyaltyPoints());
    }

    @Test
    public void aCancelledOrderCannotBePaid() {
        Product product = newProduct(10, new BigDecimal("50000"));
        Order open = openOrderOnTable(8, null);
        new TableDAO().setEmpty(8); // cancels the OPEN order

        try {
            orderDAO.payExistingOrder(open.getId(), null, PaymentMethod.CASH,
                    BigDecimal.ZERO, new BigDecimal("50000"), List.of(lineFor(product, 1)));
            fail("a cancelled order must not be payable");
        } catch (DataAccessException expected) {
            // expected
        }
        assertEquals(OrderStatus.CANCELLED, orderDAO.findById(open.getId()).orElseThrow().getStatus());
    }

    @Test
    public void aTableCannotHoldTwoOpenOrders() { // TODO 3.22
        Order first = openOrderOnTable(4, null);
        try {
            openOrderOnTable(4, null);
            fail("a second OPEN order for the same table must be refused");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("#" + first.getId()));
        }
        assertEquals("the first order is still the table's order", first.getId(),
                orderDAO.findOpenOrderByTable(4).orElseThrow().getId());

        new TableDAO().setEmpty(4); // closes the first order
        Order again = openOrderOnTable(4, null); // the table is free again
        assertTrue(again.getId() > first.getId());
        new TableDAO().setEmpty(4);
    }

    @Test
    public void aLateAutosaveCannotTouchAnOrderThatWasAlreadyPaid() {
        Product product = newProduct(10, new BigDecimal("50000"));
        Order open = openOrderOnTable(9, null);
        orderDAO.payExistingOrder(open.getId(), null, PaymentMethod.CASH,
                BigDecimal.ZERO, new BigDecimal("50000"), List.of(lineFor(product, 1)));

        open.setTotal(BigDecimal.ONE);
        orderDAO.saveOrUpdateOpenOrder(open, List.of()); // the stale autosave arriving after payment

        Order reloaded = orderDAO.findById(open.getId()).orElseThrow();
        assertEquals(OrderStatus.PAID, reloaded.getStatus());
        assertEquals("the paid total must survive", 0, new BigDecimal("50000").compareTo(reloaded.getTotal()));
        assertEquals("the paid items must survive", 1, orderDAO.findItemsByOrderId(open.getId()).size());
    }

    @Test
    public void historySearchTreatsPercentAndUnderscoreAsPlainCharacters() {
        Product product = newProduct(20, new BigDecimal("10000"));
        Customer plain = newCustomer(); // name has neither % nor _
        Order plainOrder = orderDAO.insert(paidOrderTotaling(new BigDecimal("10000"), plain.getId()), List.of(lineFor(product, 1)));

        Customer odd = new Customer();
        odd.setFullName("50%off_" + UUID.randomUUID());
        odd.setLoyaltyPoints(0);
        odd = customerDAO.insert(odd);
        Order oddOrder = orderDAO.insert(paidOrderTotaling(new BigDecimal("10000"), odd.getId()), List.of(lineFor(product, 1)));

        for (String wildcard : new String[]{"%", "_"}) {
            List<OrderDAO.OrderSummary> found = orderDAO.findHistoryPage(
                    new OrderDAO.HistoryFilter(wildcard, null, null, null), 5000, 0);
            assertTrue("\"" + wildcard + "\" must not match an order whose names do not contain it",
                    found.stream().noneMatch(s -> s.order().getId() == plainOrder.getId()));
            assertTrue("\"" + wildcard + "\" still finds a name that really contains it",
                    found.stream().anyMatch(s -> s.order().getId() == oddOrder.getId()));
        }
        List<OrderDAO.OrderSummary> exact = orderDAO.findHistoryPage(
                new OrderDAO.HistoryFilter(odd.getFullName(), null, null, null), 100, 0);
        assertTrue(exact.stream().anyMatch(s -> s.order().getId() == oddOrder.getId()));
    }

    @Test
    public void escapeLikeEscapesTheThreeSpecialCharacters() {
        assertEquals("a!%b!_c!!d", OrderDAO.escapeLike("a%b_c!d"));
        assertEquals("a backslash is an ordinary character now", "a\\b", OrderDAO.escapeLike("a\\b"));
    }

    // ---- bank transfer ("Chuyển khoản") -------------------------------------------------------

    private Order paidByTransfer(BigDecimal total, Integer customerId) {
        Order order = paidOrderTotaling(total, customerId);
        order.setPaymentMethod(PaymentMethod.TRANSFER);
        return order;
    }

    @Test
    public void aBankTransferOrderIsSavedAndReadBackAsTransfer() {
        Product product = newProduct(10, new BigDecimal("40000"));
        Order saved = orderDAO.insert(paidByTransfer(new BigDecimal("80000"), null), List.of(lineFor(product, 2)));

        Order reloaded = orderDAO.findById(saved.getId()).orElseThrow();
        assertEquals(PaymentMethod.TRANSFER, reloaded.getPaymentMethod());
        assertEquals(OrderStatus.PAID, reloaded.getStatus());
        Product afterSale = productDAO.findAll().stream().filter(p -> p.getId() == product.getId()).findFirst().orElseThrow();
        assertEquals("a transfer sale takes stock like any other sale", 8, afterSale.getStock());
    }

    @Test
    public void payingAnOpenOrderByTransferStoresTheMethod() {
        Product product = newProduct(10, new BigDecimal("25000"));
        Order open = new Order();
        open.setOrderDate(AppClock.now());
        open.setStatus(OrderStatus.OPEN);
        open.setSubtotal(BigDecimal.ZERO);
        open.setDiscount(BigDecimal.ZERO);
        open.setTotal(BigDecimal.ZERO);
        Order saved = orderDAO.insert(open, List.of());

        Order paid = orderDAO.payExistingOrder(saved.getId(), null, PaymentMethod.TRANSFER,
                BigDecimal.ZERO, new BigDecimal("50000"), List.of(lineFor(product, 2)));

        assertEquals(PaymentMethod.TRANSFER, paid.getPaymentMethod());
        assertEquals(OrderStatus.PAID, paid.getStatus());
        assertEquals(PaymentMethod.TRANSFER, orderDAO.findById(saved.getId()).orElseThrow().getPaymentMethod());
    }

    @Test
    public void cancellingABankTransferOrderPutsStockAndPointsBack() {
        Product product = newProduct(10, new BigDecimal("20000"));
        Customer customer = newCustomer();
        Order saved = orderDAO.insert(paidByTransfer(new BigDecimal("60000"), customer.getId()), List.of(lineFor(product, 3)));
        assertEquals(LoyaltyPolicy.pointsFor(new BigDecimal("60000")),
                customerDAO.findById(customer.getId()).orElseThrow().getLoyaltyPoints());

        orderDAO.cancelPaidOrder(saved.getId());

        assertEquals(OrderStatus.CANCELLED, orderDAO.findById(saved.getId()).orElseThrow().getStatus());
        Product reloaded = productDAO.findAll().stream().filter(p -> p.getId() == product.getId()).findFirst().orElseThrow();
        assertEquals(10, reloaded.getStock());
        assertEquals(0, customerDAO.findById(customer.getId()).orElseThrow().getLoyaltyPoints());
    }

    @Test
    public void bankTransferSalesCountInTheReportTotals() {
        ReportDAO reports = new ReportDAO();
        java.time.LocalDate today = AppClock.today();
        ReportDAO.Summary before = reports.summary(today, today);
        Product product = newProduct(10, new BigDecimal("30000"));

        orderDAO.insert(paidByTransfer(new BigDecimal("30000"), null), List.of(lineFor(product, 1)));

        ReportDAO.Summary after = reports.summary(today, today);
        assertEquals(before.paidOrders() + 1, after.paidOrders());
        assertEquals(before.revenue().add(new BigDecimal("30000")), after.revenue());
    }

    /** TODO 6.37: an autosave writes only what changed; the lines that did not change keep their database row. */
    @Test
    public void autosaveChangesOnlyTheLinesThatDiffer() {
        Product a = newProduct(50, new BigDecimal("10000"));
        Product b = newProduct(50, new BigDecimal("20000"));
        Product c = newProduct(50, new BigDecimal("30000"));
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setStatus(OrderStatus.OPEN);
        order.setSubtotal(new BigDecimal("50000"));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("50000"));
        Order saved = orderDAO.insert(order, List.of(lineFor(a, 1), lineFor(b, 2)));
        List<OrderItem> first = orderDAO.findItemsByOrderId(saved.getId());
        assertEquals(2, first.size());
        int rowA = first.get(0).getId();
        int rowB = first.get(1).getId();

        // B goes from 2 to 3, C is added, A is untouched
        assertTrue(orderDAO.updateIfStillOpen(saved, List.of(lineFor(a, 1), lineFor(b, 3), lineFor(c, 1))));
        List<OrderItem> second = orderDAO.findItemsByOrderId(saved.getId());
        assertEquals(3, second.size());
        assertEquals("an unchanged line must keep its row", rowA, second.get(0).getId());
        assertEquals("a changed quantity is an update in place", rowB, second.get(1).getId());
        assertEquals(3, second.get(1).getQuantity());
        assertEquals(0, new BigDecimal("60000").compareTo(second.get(1).getLineTotal()));
        assertEquals(c.getId(), second.get(2).getProductId().intValue());

        // saving the same thing again changes no row
        assertTrue(orderDAO.updateIfStillOpen(saved, List.of(lineFor(a, 1), lineFor(b, 3), lineFor(c, 1))));
        List<OrderItem> third = orderDAO.findItemsByOrderId(saved.getId());
        assertEquals(second.get(0).getId(), third.get(0).getId());
        assertEquals(second.get(1).getId(), third.get(1).getId());
        assertEquals(second.get(2).getId(), third.get(2).getId());

        // removing A leaves B and C where they were
        assertTrue(orderDAO.updateIfStillOpen(saved, List.of(lineFor(b, 3), lineFor(c, 1))));
        List<OrderItem> fourth = orderDAO.findItemsByOrderId(saved.getId());
        assertEquals(2, fourth.size());
        assertEquals(rowB, fourth.get(0).getId());

        // emptying the order removes every line
        assertTrue(orderDAO.updateIfStillOpen(saved, List.of()));
        assertEquals(0, orderDAO.findItemsByOrderId(saved.getId()).size());
    }

    @org.junit.AfterClass
    public static void signOut() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.clear();
    }
}
