package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 4.16, rule D5: spending loyalty points at the till. The points are taken in the payment transaction and recorded on the order
 * and the customer, their value is part of the discount, and a refund gives them back.
 */
public class PointsRedemptionTest {

    private final CheckoutService checkout = new CheckoutService();
    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
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
    public void createCategory() {
        TestSession.admin();
        category = new Category();
        category.setName("test-points-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
    }

    @After
    public void resetClock() {
        AppClock.reset();
    }

    private Product newProduct(int stock, String price, String cost) {
        Product product = new Product();
        product.setName("test-points-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal(price));
        product.setCost(cost == null ? null : new BigDecimal(cost));
        product.setStock(stock);
        product.setActive(true);
        return productDAO.insert(product);
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

    private Customer newCustomer(int points) {
        Customer customer = new Customer();
        customer.setFullName("Khách điểm " + UUID.randomUUID().toString().substring(0, 8));
        customer.setPhone("09" + String.format(java.util.Locale.ROOT, "%08d", new Random().nextInt(100_000_000)));
        customer.setLoyaltyPoints(points);
        customer.setCreatedAt(LocalDateTime.now());
        return customerDAO.insert(customer);
    }

    private int pointsOf(Customer customer) {
        return customerDAO.findById(customer.getId()).orElseThrow().getLoyaltyPoints();
    }

    private int stockOf(Product product) {
        return productDAO.findAll().stream().filter(p -> p.getId() == product.getId()).findFirst().orElseThrow().getStock();
    }

    /** Sets a balance directly, as another till or an earlier sale would have left it. */
    private static void setPoints(Customer customer, int points) {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("UPDATE customers SET loyalty_points = ? WHERE id = ?")) {
            st.setInt(1, points);
            st.setInt(2, customer.getId());
            st.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static CheckoutService.Payment cash(String discount, String reason, String total, int points) {
        return new CheckoutService.Payment(PaymentMethod.CASH, new BigDecimal(discount), reason, new BigDecimal(total), null, null, points);
    }

    private CheckoutService.PaidOrder pay(Product product, int quantity, Customer customer, CheckoutService.Payment payment, Role role) {
        BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
        return checkout.pay(null, List.of(lineFor(product, quantity)), subtotal, customer, "", "", null, payment, null, role);
    }

    private int ordersOf(Customer customer) {
        return orderDAO.findHistoryPage(new OrderDAO.HistoryFilter(null, null, null, null, customer.getId()), 100, 0).size();
    }

    // ------------------------------------------------------------------ paying

    @Test
    public void pointsPayPartOfTheBillAndAreRecordedOnTheOrderAndTheCustomer() {
        Product product = newProduct(10, "40000", null);
        Customer customer = newCustomer(100);

        CheckoutService.PaidOrder paid = pay(product, 1, customer, cash("0", null, "30000", 10), Role.ADMIN);

        Order order = orderDAO.findById(paid.order().getId()).orElseThrow();
        assertEquals(OrderStatus.PAID, order.getStatus());
        assertEquals(0, new BigDecimal("30000").compareTo(order.getTotal()));
        assertEquals("the value of the points is inside the discount", 0, new BigDecimal("10000").compareTo(order.getDiscount()));
        assertEquals(Integer.valueOf(10), order.getPointsRedeemed());
        assertEquals(0, new BigDecimal("10000").compareTo(order.getPointsValue()));
        assertEquals(I18n.t("order.discount.reason.points", 10), order.getDiscountReason());
        assertEquals("earned on what was paid: 30.000 = 3 points", Integer.valueOf(3), order.getPointsEarned());
        assertEquals("100 - 10 spent + 3 earned", 93, pointsOf(customer));
        assertEquals(9, stockOf(product));
    }

    @Test
    public void aSaleWithoutPointsLeavesTheColumnsEmpty() {
        Product product = newProduct(10, "40000", null);
        Customer customer = newCustomer(100);
        Order order = orderDAO.findById(pay(product, 1, customer, cash("0", null, "40000", 0), Role.ADMIN).order().getId()).orElseThrow();
        assertNull(order.getPointsRedeemed());
        assertNull(order.getPointsValue());
        assertEquals(104, pointsOf(customer));
    }

    @Test
    public void theCashiersDiscountLimitDoesNotApplyToPoints() {
        Product product = newProduct(10, "40000", null);
        Customer customer = newCustomer(100);
        // an ordinary employee may give 10% = 4.000 of their own; the customer's own points are worth 10.000 here
        CheckoutService.PaidOrder paid = pay(product, 1, customer, cash("0", null, "30000", 10), Role.EMPLOYEE);
        assertEquals(Integer.valueOf(10), orderDAO.findById(paid.order().getId()).orElseThrow().getPointsRedeemed());
    }

    @Test
    public void aCashiersOwnDiscountAndPointsAddUpAndBothReasonsAreKept() {
        Product product = newProduct(10, "40000", null);
        Customer customer = newCustomer(100);
        CheckoutService.PaidOrder paid = pay(product, 1, customer, cash("2000", "Khách quen", "28000", 10), Role.ADMIN);
        Order order = orderDAO.findById(paid.order().getId()).orElseThrow();
        assertEquals(0, new BigDecimal("12000").compareTo(order.getDiscount()));
        assertEquals(0, new BigDecimal("28000").compareTo(order.getTotal()));
        assertEquals("Khách quen · " + I18n.t("order.discount.reason.points", 10), order.getDiscountReason());
    }

    @Test
    public void moreThanTheCustomerHasIsRefusedAndNothingIsSaved() {
        Product product = newProduct(10, "40000", null);
        Customer customer = newCustomer(5);
        DataAccessException refused = assertThrows(DataAccessException.class,
                () -> pay(product, 1, customer, cash("0", null, "30000", 10), Role.ADMIN));
        assertEquals(I18n.t("pos.points.notenough", 5), refused.getMessage());
        assertEquals("no stock was taken", 10, stockOf(product));
        assertEquals("no points were taken", 5, pointsOf(customer));
        assertEquals("no order was saved", 0, ordersOf(customer));
    }

    @Test
    public void atMostHalfTheBillMayBePaidWithPoints() {
        Product product = newProduct(10, "40000", null);
        Customer customer = newCustomer(1000);
        DataAccessException refused = assertThrows(DataAccessException.class,
                () -> pay(product, 1, customer, cash("0", null, "19000", 21), Role.ADMIN));
        assertEquals(I18n.t("pos.points.toomany", 20, 50), refused.getMessage());
        assertEquals(1000, pointsOf(customer));
        CheckoutService.PaidOrder paid = pay(product, 1, customer, cash("0", null, "20000", 20), Role.ADMIN);
        assertEquals(Integer.valueOf(20), orderDAO.findById(paid.order().getId()).orElseThrow().getPointsRedeemed());
    }

    @Test
    public void pointsNeedACustomerAndANegativeNumberIsNotAPayment() {
        Product product = newProduct(10, "40000", null);
        DataAccessException none = assertThrows(DataAccessException.class,
                () -> pay(product, 1, null, cash("0", null, "30000", 10), Role.ADMIN));
        assertEquals(I18n.t("pos.points.nocustomer"), none.getMessage());
        Customer customer = newCustomer(100);
        DataAccessException negative = assertThrows(DataAccessException.class,
                () -> pay(product, 1, customer, cash("0", null, "40000", -5), Role.ADMIN));
        assertEquals(I18n.t("pos.points.invalid"), negative.getMessage());
        assertEquals(10, stockOf(product));
        assertEquals(100, pointsOf(customer));
    }

    @Test
    public void theDatabaseRefusesPointsThatAnotherTillSpentFirst() {
        Product product = newProduct(10, "40000", null);
        Customer onScreen = newCustomer(100); // what this till's screen still shows
        setPoints(onScreen, 3);               // what the database holds now
        DataAccessException refused = assertThrows(DataAccessException.class,
                () -> pay(product, 1, onScreen, cash("0", null, "30000", 10), Role.ADMIN));
        assertEquals(I18n.t("order.points.notenough"), refused.getMessage());
        assertEquals("the sale was rolled back: stock", 10, stockOf(product));
        assertEquals("the balance is what it was", 3, pointsOf(onScreen));
        assertEquals("and no order was left behind", 0, ordersOf(onScreen));
    }

    @Test
    public void anOpenOrderCanBePaidWithPointsToo() {
        Product product = newProduct(10, "40000", null);
        Customer customer = newCustomer(50);
        Order open = new Order();
        open.setOrderDate(AppClock.now());
        open.setStatus(OrderStatus.OPEN);
        open.setSubtotal(BigDecimal.ZERO);
        open.setDiscount(BigDecimal.ZERO);
        open.setTotal(BigDecimal.ZERO);
        open = orderDAO.saveOrUpdateOpenOrder(open, List.of());

        CheckoutService.PaidOrder paid = checkout.pay(null, List.of(lineFor(product, 1)), new BigDecimal("40000"), customer, "", "", open,
                cash("0", null, "30000", 10), null, Role.ADMIN);

        Order order = orderDAO.findById(paid.order().getId()).orElseThrow();
        assertEquals(open.getId(), order.getId());
        assertEquals(Integer.valueOf(10), order.getPointsRedeemed());
        assertEquals(0, new BigDecimal("10000").compareTo(order.getDiscount()));
        assertEquals(40 + 3, pointsOf(customer));
    }

    // ------------------------------------------------------------------ refunds (rule D5)

    @Test
    public void aRefundGivesTheSpentPointsBackAndTakesTheEarnedOnesBack() {
        Product product = newProduct(10, "40000", null);
        Customer customer = newCustomer(100);
        int orderId = pay(product, 1, customer, cash("0", null, "30000", 10), Role.ADMIN).order().getId();
        assertEquals(93, pointsOf(customer));

        orderDAO.cancelPaidOrder(orderId, true);

        assertEquals("back to the 100 the customer had", 100, pointsOf(customer));
        assertEquals(10, stockOf(product));
        assertEquals(OrderStatus.CANCELLED, orderDAO.findById(orderId).orElseThrow().getStatus());
    }

    @Test
    public void aRefundNeverTakesTheBalanceBelowZeroButStillGivesTheSpentPointsBack() {
        Product product = newProduct(10, "40000", null);
        Customer customer = newCustomer(100);
        int orderId = pay(product, 1, customer, cash("0", null, "30000", 10), Role.ADMIN).order().getId();
        setPoints(customer, 1); // the customer has spent the rest since (decision 3.7: the balance floors at 0)

        orderDAO.cancelPaidOrder(orderId, false);

        assertEquals("1 - 3 earned floors at 0, then the 10 spent come back", 10, pointsOf(customer));
    }

    @Test
    public void aRefundOfASaleThatSpentNoPointsWorksAsBefore() {
        Product product = newProduct(10, "40000", null);
        Customer customer = newCustomer(100);
        int orderId = pay(product, 1, customer, cash("0", null, "40000", 0), Role.ADMIN).order().getId();
        assertEquals(104, pointsOf(customer));
        orderDAO.cancelPaidOrder(orderId, true);
        assertEquals(100, pointsOf(customer));
    }

    @Test
    public void aRefundDoesNotGivePointsToACustomerWhoWasMadeAnonymousSince() {
        Product product = newProduct(10, "40000", null);
        Customer customer = newCustomer(100);
        int orderId = pay(product, 1, customer, cash("0", null, "30000", 10), Role.ADMIN).order().getId();
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("UPDATE customers SET loyalty_points = 0, anonymised_at = ? WHERE id = ?")) {
            st.setString(1, com.coffeeshop.coffeeshopmanagement.util.DbTime.write(AppClock.now()));
            st.setInt(2, customer.getId());
            st.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        orderDAO.cancelPaidOrder(orderId, true);
        assertEquals("an anonymous record keeps no points", 0, pointsOf(customer));
        assertEquals(OrderStatus.CANCELLED, orderDAO.findById(orderId).orElseThrow().getStatus());
    }

    // ------------------------------------------------------------------ reports

    @Test
    public void theProfitReportTreatsPointsLikeAnyOtherDiscount() {
        AppClock.fix(LocalDateTime.of(2034, 2, 3, 12, 0));
        LocalDate day = LocalDate.of(2034, 2, 3);
        ReportDAO reports = new ReportDAO();
        ReportDAO.Profit before = reports.profit(day, day);
        ReportDAO.Summary summaryBefore = reports.summary(day, day);

        Product product = newProduct(10, "40000", "10000");
        Customer customer = newCustomer(100);
        pay(product, 1, customer, cash("0", null, "30000", 10), Role.ADMIN);

        ReportDAO.Profit after = reports.profit(day, day);
        ReportDAO.Summary summaryAfter = reports.summary(day, day);
        assertEquals("revenue is what was paid", 0, new BigDecimal("30000").compareTo(
                summaryAfter.revenue().subtract(summaryBefore.revenue())));
        assertEquals("profit = what was paid - the cost of the goods", 0, new BigDecimal("20000").compareTo(
                after.grossProfit().subtract(before.grossProfit())));
        assertEquals("the discounts total includes the points", 0, new BigDecimal("10000").compareTo(
                summaryAfter.discounts().subtract(summaryBefore.discounts())));
    }
}
