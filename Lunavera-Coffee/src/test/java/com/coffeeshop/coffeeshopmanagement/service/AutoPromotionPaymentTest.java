package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.PromotionDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.dao.VoucherDAO;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.Voucher;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Kind;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Promotion;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Target;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * TODO 4.55: automatic promotions at the till. The checkout service works them out from the shop's own list and the clock, writes them
 * on the lines, folds their value into the saved discount (so total = subtotal - discount still holds), keeps the cashier's limit on
 * the price before promotions, and refuses a bill whose total ignores them.
 */
public class AutoPromotionPaymentTest {

    private static final LocalDateTime MON_15H = LocalDateTime.of(2026, 11, 2, 15, 0);

    private final CheckoutService checkout = new CheckoutService();
    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final PromotionDAO promotions = new PromotionDAO();
    private final List<Integer> madePromotions = new ArrayList<>();
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
    public void shop() {
        TestSession.admin();
        category = new Category();
        category.setName("test-autopromo-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        AppClock.fix(MON_15H);
    }

    @After
    public void cleanUp() {
        AppClock.reset();
        TestSession.admin();
        for (int id : madePromotions) promotions.delete(id);
        madePromotions.clear();
    }

    private Product newProduct(String price) {
        Product product = new Product();
        product.setName("test-autopromo-product-" + UUID.randomUUID());
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

    private Promotion make(Promotion promotion) {
        Promotion saved = promotions.save(promotion);
        madePromotions.add(saved.id());
        return saved;
    }

    private Promotion happyHour(int percent) {
        return make(new Promotion(0, "test-giờ vàng", Kind.PERCENT, true, percent, 0, 0, 0, Set.of(), LocalTime.of(14, 0), LocalTime.of(16, 0),
                null, null, List.of(Target.category(category.getId()))));
    }

    private static CheckoutService.Payment cash(String discount, String reason, String total, String voucher) {
        return new CheckoutService.Payment(PaymentMethod.CASH, new BigDecimal(discount), reason, new BigDecimal(total), null, null, 0, null, voucher);
    }

    private Order pay(List<OrderItem> items, CheckoutService.Payment payment, Role role) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItem item : items) subtotal = subtotal.add(item.getLineTotal());
        return orderDAO.findById(checkout.pay(null, items, subtotal, null, "", "", null, payment, null, role).order().getId()).orElseThrow();
    }

    private static void assertDong(String expected, BigDecimal actual) {
        assertEquals(expected + " vs " + actual, 0, new BigDecimal(expected).compareTo(actual));
    }

    @Test
    public void aHappyHourTakesItsPercentOffByItselfAndIsRecordedOnTheOrderAndTheLine() {
        Product product = newProduct("40000");
        happyHour(10);
        Order order = pay(List.of(lineFor(product, 1)), cash("0", null, "36000", null), Role.EMPLOYEE);
        assertDong("4000", order.getDiscount());
        assertDong("4000", order.getPromoValue());
        assertDong("40000", order.getSubtotal());
        assertDong("36000", order.getTotal());
        assertEquals(I18n.t("order.discount.reason.promo", "test-giờ vàng"), order.getDiscountReason());
        OrderItem saved = orderDAO.findItemsByOrderId(order.getId()).get(0);
        assertEquals("test-giờ vàng", saved.getPromoName());
        assertDong("4000", saved.getPromoValue());
        assertDong("40000", saved.getLineTotal());
    }

    @Test
    public void outsideTheHoursTheBillIsAtFullPriceAndAScreenThatClaimsADiscountIsRefused() {
        Product product = newProduct("40000");
        happyHour(10);
        AppClock.fix(LocalDateTime.of(2026, 11, 2, 17, 0));
        Order order = pay(List.of(lineFor(product, 1)), cash("0", null, "40000", null), Role.EMPLOYEE);
        assertNull(order.getPromoValue());
        assertDong("0", order.getDiscount());
        assertNull(orderDAO.findItemsByOrderId(order.getId()).get(0).getPromoName());
        try {
            pay(List.of(lineFor(product, 1)), cash("0", null, "36000", null), Role.EMPLOYEE);
            fail("no promotion is on, so 36.000 does not add up");
        } catch (DataAccessException expected) {
            // fine
        }
    }

    @Test
    public void aScreenThatIgnoresTheActivePromotionIsRefusedAndNothingIsSaved() {
        Product product = newProduct("40000");
        happyHour(10);
        try {
            pay(List.of(lineFor(product, 1)), cash("0", null, "40000", null), Role.EMPLOYEE);
            fail("the total must include the promotion");
        } catch (DataAccessException expected) {
            // fine
        }
    }

    @Test
    public void theCashiersLimitStaysOnThePriceBeforePromotions() {
        Product product = newProduct("40000");
        happyHour(10);
        // an employee may give 10% of 40.000 = 4.000 of their own on top of the 4.000 promotion: 32.000 to pay
        Order order = pay(List.of(lineFor(product, 1)), cash("4000", "khách quen", "32000", null), Role.EMPLOYEE);
        assertDong("8000", order.getDiscount());
        assertDong("4000", order.getPromoValue());
        assertTrue(order.getDiscountReason(), order.getDiscountReason().startsWith("khách quen"));
        assertTrue(order.getDiscountReason().endsWith(I18n.t("order.discount.reason.promo", "test-giờ vàng")));
        try {
            pay(List.of(lineFor(product, 1)), cash("4001", "khách quen", "31999", null), Role.EMPLOYEE);
            fail("4.001 is above 10% of the price before promotions");
        } catch (DataAccessException expected) {
            // fine
        }
    }

    @Test
    public void aDiscountCannotTakeMoreThanIsLeftAfterThePromotion() {
        Product product = newProduct("40000");
        happyHour(10);
        try {
            pay(List.of(lineFor(product, 1)), cash("37000", "tặng", "0", null), Role.ADMIN); // 36.000 is left to pay
            fail("more than is left to pay");
        } catch (DataAccessException expected) {
            assertEquals(I18n.t("discount.problem.abovesubtotal"), expected.getMessage());
        }
        Order order = pay(List.of(lineFor(product, 1)), cash("36000", "tặng", "0", null), Role.ADMIN);
        assertDong("40000", order.getDiscount());
        assertDong("0", order.getTotal());
    }

    @Test
    public void aVoucherWorksOnWhatTheCustomerStillHasToPay() {
        Product product = newProduct("40000");
        happyHour(10);
        VoucherDAO vouchers = new VoucherDAO();
        Voucher voucher = vouchers.add("T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(), Voucher.PERCENT, 10L,
                BigDecimal.ZERO, null, null, null);
        // 10% promotion: 36.000 left; the 10% voucher takes 3.600 of that, not 4.000: 32.400 to pay
        Order order = pay(List.of(lineFor(product, 1)), cash("0", null, "32400", voucher.code()), Role.EMPLOYEE);
        assertDong("3600", order.getVoucherValue());
        assertDong("4000", order.getPromoValue());
        assertDong("7600", order.getDiscount());
        assertDong("32400", order.getTotal());
    }

    @Test
    public void buyTwoGetOneFreeGivesTheThirdPieceFree() {
        Product product = newProduct("30000");
        make(new Promotion(0, "test-2+1", Kind.BUY_GET, true, 0, 0, 2, 1, Set.of(), null, null, null, null, List.of(Target.product(product.getId()))));
        Order order = pay(List.of(lineFor(product, 3)), cash("0", null, "60000", null), Role.EMPLOYEE);
        assertDong("30000", order.getPromoValue());
        assertDong("90000", order.getSubtotal());
    }

    @Test
    public void aComboChargesOnePriceForTheSetAndTheLinesShareTheSaving() {
        Product coffee = newProduct("50000");
        Product cake = newProduct("40000");
        make(new Promotion(0, "test-combo", Kind.COMBO, true, 0, 70_000, 0, 0, Set.of(), null, null, null, null,
                List.of(Target.product(coffee.getId()), Target.product(cake.getId()))));
        Order order = pay(List.of(lineFor(coffee, 1), lineFor(cake, 1)), cash("0", null, "70000", null), Role.EMPLOYEE);
        assertDong("20000", order.getPromoValue());
        List<OrderItem> lines = orderDAO.findItemsByOrderId(order.getId());
        assertEquals("test-combo", lines.get(0).getPromoName());
        assertEquals("test-combo", lines.get(1).getPromoName());
        assertDong("20000", lines.get(0).getPromoValue().add(lines.get(1).getPromoValue()));
    }

    @Test
    public void aSwitchedOffOrDeletedPromotionGivesNothing() {
        Product product = newProduct("40000");
        Promotion promotion = happyHour(10);
        promotions.setActive(promotion.id(), false);
        pay(List.of(lineFor(product, 1)), cash("0", null, "40000", null), Role.EMPLOYEE);
        promotions.setActive(promotion.id(), true);
        promotions.delete(promotion.id());
        madePromotions.remove(Integer.valueOf(promotion.id()));
        Order order = pay(List.of(lineFor(product, 1)), cash("0", null, "40000", null), Role.EMPLOYEE);
        assertNull(order.getPromoValue());
    }

    @Test
    public void thePaidOrderKeepsThePromotionsNameWhenThePromotionIsDeletedLater() {
        Product product = newProduct("40000");
        Promotion promotion = happyHour(10);
        Order order = pay(List.of(lineFor(product, 1)), cash("0", null, "36000", null), Role.EMPLOYEE);
        promotions.delete(promotion.id());
        madePromotions.remove(Integer.valueOf(promotion.id()));
        assertEquals("test-giờ vàng", orderDAO.findItemsByOrderId(order.getId()).get(0).getPromoName());
        assertDong("4000", orderDAO.findById(order.getId()).orElseThrow().getPromoValue());
    }

    @Test
    public void aLineThatIsNotInThePromotionPaysFullPrice() {
        Product drink = newProduct("40000");
        Category other = new Category();
        other.setName("test-autopromo-other-" + UUID.randomUUID());
        other.setActive(true);
        new CategoryDAO().insert(other);
        Product cake = new Product();
        cake.setName("test-autopromo-cake-" + UUID.randomUUID());
        cake.setCategoryId(other.getId());
        cake.setPrice(new BigDecimal("40000"));
        cake.setStock(100);
        cake.setActive(true);
        cake = productDAO.insert(cake);
        happyHour(10); // for the drinks category only
        Order order = pay(List.of(lineFor(drink, 1), lineFor(cake, 1)), cash("0", null, "76000", null), Role.EMPLOYEE);
        assertDong("4000", order.getPromoValue());
        List<OrderItem> lines = orderDAO.findItemsByOrderId(order.getId());
        assertEquals("test-giờ vàng", lines.get(0).getPromoName());
        assertNull(lines.get(1).getPromoName());
    }

    @Test
    public void theBillIsPricedAtTheMomentThePaymentWindowWasOpenedEvenIfThePromotionEndedSince() {
        Product product = newProduct("40000");
        happyHour(10);
        LocalDateTime opened = LocalDateTime.of(2026, 11, 2, 15, 59);
        AppClock.fix(LocalDateTime.of(2026, 11, 2, 16, 3)); // the promotion ended at 16:00
        Order order = pay(List.of(lineFor(product, 1)), cash("0", null, "36000", null).withPricedAt(opened), Role.EMPLOYEE);
        assertDong("4000", order.getPromoValue());
        assertDong("36000", order.getTotal());
    }

    @Test
    public void withoutAMomentFromTheWindowTheBillIsPricedNow() {
        Product product = newProduct("40000");
        happyHour(10);
        AppClock.fix(LocalDateTime.of(2026, 11, 2, 16, 3));
        try {
            pay(List.of(lineFor(product, 1)), cash("0", null, "36000", null), Role.EMPLOYEE); // no longer a happy hour
            fail("priced now there is no promotion, so 36.000 does not add up");
        } catch (DataAccessException expected) {
            // fine
        }
    }

    @Test
    public void aWindowOpenedTooLongAgoIsRefusedAndNothingIsSaved() {
        Product product = newProduct("40000");
        happyHour(10);
        AppClock.fix(LocalDateTime.of(2026, 11, 2, 16, 30));
        try {
            pay(List.of(lineFor(product, 1)), cash("0", null, "36000", null).withPricedAt(LocalDateTime.of(2026, 11, 2, 15, 59)), Role.EMPLOYEE);
            fail("31 minutes old");
        } catch (DataAccessException expected) {
            assertEquals(I18n.t("order.promo.stale", 15), expected.getMessage());
        }
    }

    @Test
    public void aMomentInTheFutureCannotBeUsedToReachAPromotionThatIsNotOnYet() {
        Product product = newProduct("40000");
        happyHour(10);
        AppClock.fix(LocalDateTime.of(2026, 11, 2, 13, 0)); // not on until 14:00
        try {
            pay(List.of(lineFor(product, 1)), cash("0", null, "36000", null).withPricedAt(LocalDateTime.of(2026, 11, 2, 15, 0)), Role.EMPLOYEE);
            fail("a time two hours ahead is refused");
        } catch (DataAccessException expected) {
            assertEquals(I18n.t("order.promo.stale", 15), expected.getMessage());
        }
    }

    @Test
    public void aMomentFromBeforeThePromotionStartedCannotBeUsedToAvoidItEither() {
        Product product = newProduct("40000");
        happyHour(10);
        // the window opened at 13:58 (not on), the payment is at 14:05: priced at 13:58 there is no promotion, so full price is right
        AppClock.fix(LocalDateTime.of(2026, 11, 2, 14, 5));
        Order order = pay(List.of(lineFor(product, 1)), cash("0", null, "40000", null).withPricedAt(LocalDateTime.of(2026, 11, 2, 13, 58)), Role.EMPLOYEE);
        assertNull(order.getPromoValue());
    }
}
