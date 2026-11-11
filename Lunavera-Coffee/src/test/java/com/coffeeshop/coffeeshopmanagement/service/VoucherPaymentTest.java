package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.dao.VoucherDAO;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.Voucher;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 4.11, rule D6: a voucher at the till. It is looked up and checked by the checkout service, its value is part of the discount,
 * its use is counted in the payment transaction (and refused when none is left), and a refund gives the use back.
 */
public class VoucherPaymentTest {

    private final CheckoutService checkout = new CheckoutService();
    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final VoucherDAO vouchers = new VoucherDAO();
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
        category.setName("test-voucher-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
    }

    @After
    public void resetClock() {
        AppClock.reset();
    }

    private static String code() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private Product newProduct(String price) {
        Product product = new Product();
        product.setName("test-voucher-product-" + UUID.randomUUID());
        product.setCategoryId(category.getId());
        product.setPrice(new BigDecimal(price));
        product.setStock(100);
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

    private Voucher percent(long value, Integer maxUses) {
        return vouchers.add(code(), Voucher.PERCENT, value, BigDecimal.ZERO, null, null, maxUses);
    }

    private static CheckoutService.Payment cash(String discount, String reason, String total, int points, String voucher) {
        return new CheckoutService.Payment(PaymentMethod.CASH, new BigDecimal(discount), reason, new BigDecimal(total), null, null, points,
                null, voucher);
    }

    private CheckoutService.PaidOrder pay(Product product, Customer customer, CheckoutService.Payment payment, Role role) {
        return checkout.pay(null, List.of(lineFor(product, 1)), product.getPrice(), customer, "", "", null, payment, null, role);
    }

    private Customer newCustomer(int points) {
        Customer customer = new Customer();
        customer.setFullName("Khách mã " + UUID.randomUUID().toString().substring(0, 8));
        customer.setPhone("09" + String.format(java.util.Locale.ROOT, "%08d", new Random().nextInt(100_000_000)));
        customer.setLoyaltyPoints(points);
        customer.setCreatedAt(LocalDateTime.now());
        return new CustomerDAO().insert(customer);
    }

    private int usedOf(Voucher voucher) {
        return vouchers.findByCode(voucher.code()).orElseThrow().usedCount();
    }

    @Test
    public void aVoucherTakesItsPercentOffAndIsRecordedOnTheOrder() {
        Product product = newProduct("40000");
        Voucher voucher = percent(10, null);
        Order order = orderDAO.findById(pay(product, null, cash("0", null, "36000", 0, voucher.code().toLowerCase()), Role.EMPLOYEE)
                .order().getId()).orElseThrow();
        assertEquals(voucher.code(), order.getVoucherCode());
        assertEquals(0, new BigDecimal("4000").compareTo(order.getVoucherValue()));
        assertEquals("the voucher is inside the discount", 0, new BigDecimal("4000").compareTo(order.getDiscount()));
        assertEquals(I18n.t("order.discount.reason.voucher", voucher.code()), order.getDiscountReason());
        assertEquals(1, usedOf(voucher));
    }

    @Test
    public void theCashiersLimitDoesNotApplyToAVoucher() {
        Product product = newProduct("40000");
        Voucher voucher = percent(50, null); // an employee may give 10% of their own, a voucher is the shop's
        Order order = orderDAO.findById(pay(product, null, cash("0", null, "20000", 0, voucher.code()), Role.EMPLOYEE).order().getId()).orElseThrow();
        assertEquals(0, new BigDecimal("20000").compareTo(order.getTotal()));
    }

    @Test
    public void aVoucherAddsToTheTypedDiscountAndNeverMakesTheTotalNegative() {
        Product product = newProduct("40000");
        Voucher whole = percent(100, null);
        // typed 10.000 (with a reason) + a 100% voucher: the voucher only takes the 30.000 that is left
        Order order = orderDAO.findById(pay(product, null, cash("10000", "khách quen", "0", 0, whole.code()), Role.ADMIN).order().getId()).orElseThrow();
        assertEquals(0, new BigDecimal("30000").compareTo(order.getVoucherValue()));
        assertEquals(0, new BigDecimal("40000").compareTo(order.getDiscount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getTotal()));
        assertTrue(order.getDiscountReason().startsWith("khách quen"));
    }

    @Test
    public void aTotalThatDoesNotAddUpWithTheVoucherIsRefusedAndNothingIsCounted() {
        Product product = newProduct("40000");
        Voucher voucher = percent(10, null);
        try {
            pay(product, null, cash("0", null, "30000", 0, voucher.code()), Role.ADMIN); // the screen claims 10.000 off, the voucher gives 4.000
            fail("the total must add up");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(0, usedOf(voucher));
    }

    @Test
    public void aVoucherThatCannotBeUsedRefusesThePaymentWithTheReason() {
        Product product = newProduct("40000");
        Voucher off = percent(10, null);
        vouchers.setActive(off.id(), false);
        Voucher small = vouchers.add(code(), Voucher.AMOUNT, 5000L, new BigDecimal("100000"), null, null, null);
        Voucher old = vouchers.add(code(), Voucher.AMOUNT, 5000L, BigDecimal.ZERO, null, AppClock.today().minusDays(1), null);
        for (Voucher bad : List.of(off, small, old)) {
            try {
                pay(product, null, cash("0", null, "35000", 0, bad.code()), Role.ADMIN);
                fail("refused: " + bad.code());
            } catch (DataAccessException expected) {
                assertFalse(expected.getMessage(), expected.getMessage().isBlank());
            }
            assertEquals(0, usedOf(bad));
        }
        try {
            pay(product, null, cash("0", null, "40000", 0, "NOSUCHCODE1"), Role.ADMIN);
            fail("unknown code");
        } catch (DataAccessException expected) {
            assertEquals(I18n.t("voucher.problem.notfound"), expected.getMessage());
        }
    }

    @Test
    public void theLastUseCanBeTakenOnlyOnce() {
        Product product = newProduct("40000");
        Voucher once = percent(10, 1);
        pay(product, null, cash("0", null, "36000", 0, once.code()), Role.ADMIN);
        assertEquals(1, usedOf(once));
        try {
            pay(product, null, cash("0", null, "36000", 0, once.code()), Role.ADMIN);
            fail("no use left");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(1, usedOf(once));
    }

    @Test
    public void aSaleThatFailsAfterTheVoucherCheckDoesNotUseTheVoucher() {
        Product product = newProduct("40000");
        Voucher voucher = percent(10, null);
        product.setStock(0);
        productDAO.update(product); // no stock: the payment fails after the discount was worked out
        try {
            pay(product, null, cash("0", null, "36000", 0, voucher.code()), Role.ADMIN);
            fail("no stock");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(0, usedOf(voucher));
    }

    @Test
    public void aRefundGivesTheUseBack() {
        Product product = newProduct("40000");
        Voucher voucher = percent(10, 1);
        int id = pay(product, null, cash("0", null, "36000", 0, voucher.code()), Role.ADMIN).order().getId();
        assertEquals(1, usedOf(voucher));
        orderDAO.cancelPaidOrder(id);
        assertEquals(0, usedOf(voucher));
        pay(product, null, cash("0", null, "36000", 0, voucher.code()), Role.ADMIN); // the single use is free again
        assertEquals(1, usedOf(voucher));
    }

    @Test
    public void aVoucherAndSpentPointsTogetherAddUpAndBothComeBackOnRefund() {
        Product product = newProduct("80000");
        Voucher voucher = percent(10, null); // 8.000
        Customer customer = newCustomer(100);
        // 20 points (the least) = 20.000: 80.000 - 8.000 - 20.000 = 52.000
        CheckoutService.PaidOrder paid = pay(product, customer, cash("0", null, "52000", 20, voucher.code()), Role.ADMIN);
        Order order = orderDAO.findById(paid.order().getId()).orElseThrow();
        assertEquals(0, new BigDecimal("28000").compareTo(order.getDiscount()));
        assertEquals(0, new BigDecimal("8000").compareTo(order.getVoucherValue()));
        assertEquals(Integer.valueOf(20), order.getPointsRedeemed());
        assertEquals(1, usedOf(voucher));
        orderDAO.cancelPaidOrder(order.getId());
        assertEquals(0, usedOf(voucher));
        assertEquals("100 + 20 spent back, earned points taken back", 100, new CustomerDAO().findById(customer.getId()).orElseThrow().getLoyaltyPoints());
    }

    @Test
    public void anOrderDoesNotCarryAVoucherWithoutOne() {
        Product product = newProduct("40000");
        Order order = orderDAO.findById(pay(product, null, cash("0", null, "40000", 0, null), Role.ADMIN).order().getId()).orElseThrow();
        assertNull(order.getVoucherCode());
        assertNull(order.getVoucherValue());
        assertNull(orderDAO.findById(order.getId()).orElseThrow().getVoucherCode());
    }

    @Test
    public void onlyAnAdministratorMakesOrSwitchesAVoucher() {
        Voucher voucher = percent(10, null);
        TestSession.clear(); // nobody signed in: refused like a non-administrator
        try {
            vouchers.add(code(), Voucher.PERCENT, 10L, BigDecimal.ZERO, null, null, null);
            fail("an employee cannot make a voucher");
        } catch (RuntimeException expected) {
            // fine
        }
        try {
            vouchers.setActive(voucher.id(), false);
            fail("an employee cannot switch a voucher");
        } catch (RuntimeException expected) {
            // fine
        }
        TestSession.admin();
        assertTrue(vouchers.findByCode(voucher.code()).orElseThrow().active());
    }

    @Test
    public void aCodeCannotBeMadeTwiceEvenInAnotherCase() {
        Voucher voucher = percent(10, null);
        try {
            vouchers.add(voucher.code().toLowerCase(), Voucher.AMOUNT, 5000L, BigDecimal.ZERO, null, null, null);
            fail("duplicate");
        } catch (DataAccessException expected) {
            assertEquals(I18n.t("voucher.val.exists", voucher.code()), expected.getMessage());
        }
    }

    @Test
    public void aVoucherOnADayInsideItsRangeWorksAndOutsideItDoesNot() {
        Product product = newProduct("40000");
        LocalDate today = AppClock.today();
        Voucher future = vouchers.add(code(), Voucher.PERCENT, 10L, BigDecimal.ZERO, today.plusDays(2), null, null);
        try {
            pay(product, null, cash("0", null, "36000", 0, future.code()), Role.ADMIN);
            fail("not yet");
        } catch (DataAccessException expected) {
            // fine
        }
        Voucher now = vouchers.add(code(), Voucher.PERCENT, 10L, BigDecimal.ZERO, today, today, null);
        pay(product, null, cash("0", null, "36000", 0, now.code()), Role.ADMIN);
        assertEquals(1, usedOf(now));
    }

    // TODO 4.54: a voucher that can be used once per customer.

    private Voucher onceEach(long value, Integer maxUses) {
        return vouchers.add(code(), Voucher.PERCENT, value, BigDecimal.ZERO, null, null, maxUses, true);
    }

    @Test
    public void aOncePerCustomerCodeWorksOnceForEachCustomer() {
        Product product = newProduct("40000");
        Voucher voucher = onceEach(10, null);
        assertTrue(vouchers.findByCode(voucher.code()).orElseThrow().oncePerCustomer());
        Customer an = newCustomer(0);
        Customer binh = newCustomer(0);
        pay(product, an, cash("0", null, "36000", 0, voucher.code()), Role.EMPLOYEE);
        pay(product, binh, cash("0", null, "36000", 0, voucher.code()), Role.EMPLOYEE); // another customer: fine
        assertEquals(2, usedOf(voucher));
        assertTrue(vouchers.usedBy(voucher.code(), an.getId()));
        try {
            pay(product, an, cash("0", null, "36000", 0, voucher.code()), Role.EMPLOYEE);
            fail("the same customer twice");
        } catch (DataAccessException expected) {
            assertEquals(I18n.t("voucher.problem.onceonly"), expected.getMessage());
        }
        assertEquals("the refused bill used nothing", 2, usedOf(voucher));
    }

    @Test
    public void aOncePerCustomerCodeNeedsACustomerOnTheBill() {
        Product product = newProduct("40000");
        Voucher voucher = onceEach(10, null);
        try {
            pay(product, null, cash("0", null, "36000", 0, voucher.code()), Role.EMPLOYEE);
            fail("no customer");
        } catch (DataAccessException expected) {
            assertEquals(I18n.t("voucher.problem.needscustomer"), expected.getMessage());
        }
        assertEquals(0, usedOf(voucher));
    }

    @Test
    public void refundingTheBillGivesTheCustomerTheirUseBack() {
        Product product = newProduct("40000");
        Voucher voucher = onceEach(10, null);
        Customer an = newCustomer(0);
        int first = pay(product, an, cash("0", null, "36000", 0, voucher.code()), Role.EMPLOYEE).order().getId();
        orderDAO.cancelPaidOrder(first, true);
        assertFalse(vouchers.usedBy(voucher.code(), an.getId()));
        pay(product, an, cash("0", null, "36000", 0, voucher.code()), Role.EMPLOYEE); // may use it again
        assertEquals(1, usedOf(voucher));
    }

    @Test
    public void anOrdinaryCodeStillWorksWithoutACustomerAndMoreThanOnce() {
        Product product = newProduct("40000");
        Voucher voucher = percent(10, null);
        assertFalse(voucher.oncePerCustomer());
        Customer an = newCustomer(0);
        pay(product, an, cash("0", null, "36000", 0, voucher.code()), Role.EMPLOYEE);
        pay(product, an, cash("0", null, "36000", 0, voucher.code()), Role.EMPLOYEE);
        pay(product, null, cash("0", null, "36000", 0, voucher.code()), Role.EMPLOYEE);
        assertEquals(3, usedOf(voucher));
    }

    @Test
    public void theRuleItselfNeedsACustomerAndRefusesARepeat() {
        Voucher once = new Voucher(1, "X1", Voucher.PERCENT, 10, BigDecimal.ZERO, null, null, null, 0, true, true);
        assertEquals(VoucherRules.Problem.NEEDS_CUSTOMER, VoucherRules.checkCustomer(once, null, false));
        assertEquals(VoucherRules.Problem.ONCE_ONLY, VoucherRules.checkCustomer(once, 5, true));
        assertEquals(VoucherRules.Problem.OK, VoucherRules.checkCustomer(once, 5, false));
        Voucher plain = new Voucher(1, "X1", Voucher.PERCENT, 10, BigDecimal.ZERO, null, null, null, 0, true);
        assertEquals(VoucherRules.Problem.OK, VoucherRules.checkCustomer(plain, null, true));
        assertEquals(VoucherRules.Problem.OK, VoucherRules.checkCustomer(null, null, false));
        assertFalse(VoucherRules.message(VoucherRules.Problem.NEEDS_CUSTOMER, once).isBlank());
        assertFalse(VoucherRules.message(VoucherRules.Problem.ONCE_ONLY, once).isBlank());
    }
}
