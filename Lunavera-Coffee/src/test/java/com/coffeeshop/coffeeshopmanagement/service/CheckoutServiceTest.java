package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The payment step that used to live inside OrderController.completeOrder (TODO 6.5), tested without any screen.
 * Like the DAO tests it shares one database and asserts only about the rows it creates itself.
 */
public class CheckoutServiceTest {

    private final CheckoutService checkout = new CheckoutService();
    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private Category category;

    @BeforeClass
    public static void setUpDatabase() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @AfterClass
    public static void signOut() {
        com.coffeeshop.coffeeshopmanagement.util.TestSession.clear();
    }

    @Before
    public void createCategory() {
        category = new Category();
        category.setName("test-checkout-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
    }

    private Product newProduct(int stock, BigDecimal price) {
        Product product = new Product();
        product.setName("test-checkout-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(price);
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

    private static String newPhone() {
        return "09" + String.format("%08d", new Random().nextInt(100_000_000));
    }

    private int stockOf(Product product) {
        return productDAO.findAll().stream().filter(p -> p.getId() == product.getId()).findFirst().orElseThrow().getStock();
    }

    private static CheckoutService.Payment cash(String total) {
        return new CheckoutService.Payment(PaymentMethod.CASH, BigDecimal.ZERO, null, new BigDecimal(total));
    }

    @Test
    public void paysAnOrderTakenAtTheTillAndCreatesTheCustomerTypedThere() {
        Product product = newProduct(10, new BigDecimal("50000"));
        String phone = newPhone();

        CheckoutService.PaidOrder paid = checkout.pay(6, List.of(lineFor(product, 2)), new BigDecimal("100000"),
                null, "Khách thử", phone, null, cash("100000"), null, Role.ADMIN);

        assertEquals(OrderStatus.PAID, paid.order().getStatus());
        assertEquals(0, new BigDecimal("100000").compareTo(paid.order().getTotal()));
        assertEquals(Integer.valueOf(6), paid.order().getTableNumber());
        assertNotNull("the typed phone makes a customer", paid.customer());
        assertEquals(phone, paid.customer().getPhone());
        assertEquals(Integer.valueOf(paid.customer().getId()), paid.order().getCustomerId());
        assertEquals("stock drops by the quantity sold", 8, stockOf(product));
        assertEquals("points follow the loyalty rule", LoyaltyPolicy.pointsFor(new BigDecimal("100000")),
                customerDAO.findById(paid.customer().getId()).orElseThrow().getLoyaltyPoints());
    }

    @Test
    public void paysTheOrderAlreadySavedForTheTableAndUsesTheCustomerAlreadyAttached() {
        Product product = newProduct(10, new BigDecimal("50000"));
        Customer known = new Customer();
        known.setFullName("test-checkout-customer-" + UUID.randomUUID());
        known.setLoyaltyPoints(0);
        known = customerDAO.insert(known);
        Order open = new Order();
        open.setOrderDate(AppClock.now());
        open.setTableNumber(6);
        open.setStatus(OrderStatus.OPEN);
        open.setSubtotal(BigDecimal.ZERO);
        open.setDiscount(BigDecimal.ZERO);
        open.setTotal(BigDecimal.ZERO);
        open = orderDAO.saveOrUpdateOpenOrder(open, List.of());

        CheckoutService.PaidOrder paid = checkout.pay(6, List.of(lineFor(product, 1)), new BigDecimal("50000"),
                known, "someone else", newPhone(), open, cash("50000"), null, Role.ADMIN);

        assertEquals("the saved order is the one that gets paid", open.getId(), paid.order().getId());
        assertEquals(OrderStatus.PAID, paid.order().getStatus());
        assertEquals("the attached customer wins over the typed text", known.getId(), paid.customer().getId());
        assertEquals(9, stockOf(product));
    }

    @Test
    public void aSaleWithoutAPhoneMakesNoCustomer() {
        Product product = newProduct(5, new BigDecimal("30000"));

        CheckoutService.PaidOrder paid = checkout.pay(6, List.of(lineFor(product, 1)), new BigDecimal("30000"),
                null, "Anh Nam", "", null, cash("30000"), null, Role.ADMIN);

        assertNull(paid.customer());
        assertNull(paid.order().getCustomerId());
        assertEquals(OrderStatus.PAID, paid.order().getStatus());
    }

    @Test
    public void aFailedPaymentLeavesNoCustomerBehindAndNoStockTaken() {
        Product product = newProduct(1, new BigDecimal("20000")); // only 1 in stock
        String phone = newPhone();
        try {
            checkout.pay(6, List.of(lineFor(product, 5)), new BigDecimal("100000"),
                    null, "Khách thử", phone, null, cash("100000"), null, Role.ADMIN);
            fail("selling more than is in stock must fail");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage().contains("không đủ tồn kho"));
        }

        assertTrue("a customer made only for the failed payment is removed again",
                customerDAO.findByPhone(phone).isEmpty());
        assertEquals(1, stockOf(product));
    }

    // ---- TODO 6.45: the discount rule is enforced here, not only by the payment dialog ----

    private static CheckoutService.Payment withDiscount(String discount, String reason, String total) {
        return new CheckoutService.Payment(PaymentMethod.CASH, new BigDecimal(discount), reason, new BigDecimal(total));
    }

    @Test
    public void anEmployeeCannotGiveMoreThanTheStaffLimitEvenIfTheScreenAllowedIt() {
        Product product = newProduct(10, new BigDecimal("100000"));
        String phone = newPhone();
        try {
            // 20% off a 100,000 sale; the limit for staff is 10%
            checkout.pay(6, List.of(lineFor(product, 1)), new BigDecimal("100000"), null, "Khách thử", phone, null,
                    withDiscount("20000", "khách quen", "80000"), null, Role.EMPLOYEE);
            fail("a discount above the staff limit must be refused");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("tối đa"));
        }
        assertTrue("nothing was created for the refused payment", customerDAO.findByPhone(phone).isEmpty());
        assertEquals("no stock was taken", 10, stockOf(product));
    }

    @Test
    public void aDiscountWithinTheStaffLimitAndWithAReasonIsPaidAndTheReasonIsSaved() {
        Product product = newProduct(10, new BigDecimal("100000"));

        CheckoutService.PaidOrder paid = checkout.pay(6, List.of(lineFor(product, 1)), new BigDecimal("100000"), null,
                "", "", null, withDiscount("10000", "  voucher tháng 10 ", "90000"), null, Role.EMPLOYEE);

        assertEquals(OrderStatus.PAID, paid.order().getStatus());
        assertEquals(0, new BigDecimal("10000").compareTo(paid.order().getDiscount()));
        assertEquals("voucher tháng 10", orderDAO.findById(paid.order().getId()).orElseThrow().getDiscountReason());
    }

    @Test
    public void anAdministratorMayGiveALargeDiscountButStillNeedsAReason() {
        Product product = newProduct(10, new BigDecimal("100000"));

        CheckoutService.PaidOrder paid = checkout.pay(6, List.of(lineFor(product, 1)), new BigDecimal("100000"), null,
                "", "", null, withDiscount("50000", "quà sinh nhật", "50000"), null, Role.ADMIN);
        assertEquals(OrderStatus.PAID, paid.order().getStatus());

        Product other = newProduct(10, new BigDecimal("100000"));
        try {
            checkout.pay(6, List.of(lineFor(other, 1)), new BigDecimal("100000"), null, "", "", null,
                    withDiscount("50000", "   ", "50000"), null, Role.ADMIN);
            fail("a discount without a reason must be refused for everyone");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("lý do"));
        }
        assertEquals("the refused sale took no stock", 10, stockOf(other));
    }

    @Test
    public void nobodyIdentifiedIsTreatedAsAnOrdinaryEmployee() {
        Product product = newProduct(10, new BigDecimal("100000"));
        try {
            checkout.pay(6, List.of(lineFor(product, 1)), new BigDecimal("100000"), null, "", "", null,
                    withDiscount("50000", "không rõ ai", "50000"), null, null);
            fail("with no role the staff limit applies");
        } catch (DataAccessException expected) {
            assertEquals(10, stockOf(product));
        }
    }

    @Test
    public void theSubtotalTheScreenSendsIsNotTrusted() {
        Product product = newProduct(10, new BigDecimal("100000"));
        try {
            // The screen claims a 1,000,000 subtotal (so 100,000 would look like 10%), but the lines add up to 100,000.
            checkout.pay(6, List.of(lineFor(product, 1)), new BigDecimal("1000000"), null, "", "", null,
                    withDiscount("100000", "thử", "0"), null, Role.EMPLOYEE);
            fail("the rule must use the sum of the lines");
        } catch (DataAccessException expected) {
            assertEquals(10, stockOf(product));
        }
    }

    @Test
    public void aRefusedDiscountLeavesTheOpenOrderOpenForALegitimateRetry() {
        Product product = newProduct(10, new BigDecimal("100000"));
        Order open = new Order();
        open.setOrderDate(AppClock.now());
        open.setTableNumber(6);
        open.setStatus(OrderStatus.OPEN);
        open.setSubtotal(BigDecimal.ZERO);
        open.setDiscount(BigDecimal.ZERO);
        open.setTotal(BigDecimal.ZERO);
        Order saved = orderDAO.saveOrUpdateOpenOrder(open, List.of());

        try {
            checkout.pay(6, List.of(lineFor(product, 1)), new BigDecimal("100000"), null, "", "", saved,
                    withDiscount("30000", "thử", "70000"), null, Role.EMPLOYEE);
            fail("30% is above the staff limit");
        } catch (DataAccessException expected) {
            // refused
        }
        assertEquals(OrderStatus.OPEN, orderDAO.findById(saved.getId()).orElseThrow().getStatus());

        CheckoutService.PaidOrder retry = checkout.pay(6, List.of(lineFor(product, 1)), new BigDecimal("100000"), null,
                "", "", saved, withDiscount("10000", "thử lại", "90000"), null, Role.EMPLOYEE);
        assertEquals(OrderStatus.PAID, retry.order().getStatus());
        assertEquals("the same order is paid on the retry", saved.getId(), retry.order().getId());
    }
}
