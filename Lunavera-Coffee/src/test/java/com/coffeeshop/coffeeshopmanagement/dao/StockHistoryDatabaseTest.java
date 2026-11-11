package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Ingredient;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.CheckoutService;
import com.coffeeshop.coffeeshopmanagement.service.SplitBillRules;
import com.coffeeshop.coffeeshopmanagement.service.StockHistory;
import com.coffeeshop.coffeeshopmanagement.service.StockHistory.Kind;
import com.coffeeshop.coffeeshopmanagement.service.StockHistory.Move;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 4.59: the stock history lists sales and refunds as well as the stock log, and its balance ends at today's stock. */
public class StockHistoryDatabaseTest {

    private final StockHistoryDAO history = new StockHistoryDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final IngredientDAO ingredientDAO = new IngredientDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private int table;
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
    public void fresh() {
        TestSession.admin();
        table = new TableDAO().findAll().get(4).getTableNumber();
        new TableDAO().setEmpty(table);
        category = new Category();
        category.setName("test-hist-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
    }

    private Product product(int stock) {
        Product p = new Product();
        p.setName("test-hist-" + UUID.randomUUID());
        p.setCategoryId(category.getId());
        p.setPrice(new BigDecimal("20000"));
        p.setStock(stock);
        p.setActive(true);
        return productDAO.insert(p);
    }

    private OrderItem line(Product p, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(p.getPrice());
        item.setLineTotal(p.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private Order pay(OrderItem... lines) {
        new TableDAO().setEmpty(table);
        BigDecimal total = SplitBillRules.sum(List.of(lines));
        CheckoutService.Payment payment = new CheckoutService.Payment(PaymentMethod.CASH, BigDecimal.ZERO, null, total, null, null);
        return new CheckoutService().pay(table, List.of(lines), total, null, "", "", null, payment, null, Role.ADMIN).order();
    }

    private long sum(StockHistory.Page page, Kind kind) {
        return page.moves().stream().filter(m -> m.kind() == kind).mapToLong(Move::quantity).sum();
    }

    @Test
    public void aCupProductListsReceiptsWasteSalesAndRefundsAndEndsAtTodaysStock() {
        Product cup = product(10);                                    // 10 to start: the stock log has an ADJUST 0 -> 10
        new StockLogDAO().receive(cup.getId(), 20, null, "Acme", null);   // 30
        new StockLogDAO().writeOff(cup.getId(), 2, "dropped");            // 28
        Order sold = pay(line(cup, 5));                                    // 23
        Order other = pay(line(cup, 3));                                   // 20
        int itemId = orderDAO.findItemsByOrderId(sold.getId()).get(0).getId();
        new RefundDAO().refund(sold.getId(), Map.of(itemId, 2), true, PaymentMethod.CASH, "cold");   // 22 (two put back)
        orderDAO.cancelPaidOrder(other.getId(), true, "mistake");                                   // 25 (three put back)
        int now = productDAO.findById(cup.getId()).orElseThrow().getStock();
        assertEquals(25, now);

        StockHistory.Page page = history.ofProduct(cup.getId(), 100);
        assertEquals(25, page.now());
        assertEquals(25, page.moves().get(0).balance());             // the newest line left today's stock
        assertEquals(-8, sum(page, Kind.SALE));
        assertEquals(5, sum(page, Kind.REFUND));
        assertEquals(20, sum(page, Kind.RECEIPT));
        assertEquals(-2, sum(page, Kind.WASTE));
        assertEquals(10, sum(page, Kind.ADJUST));
        assertEquals(0, page.before());                               // the stock log starts at 0: every change is in the list
        assertFalse(page.cut());
        // a sale names its order; the refund line carries its reason
        assertTrue(page.moves().stream().anyMatch(m -> m.kind() == Kind.SALE && m.orderId() == sold.getId() && m.quantity() == -5));
        assertTrue(page.moves().stream().anyMatch(m -> m.kind() == Kind.REFUND && "cold".equals(m.note()) && m.quantity() == 2));
        assertTrue(page.moves().stream().anyMatch(m -> m.kind() == Kind.REFUND && m.orderId() == other.getId() && m.quantity() == 3));
    }

    @Test
    public void goodsThatDidNotGoBackOnTheShelfAreNotListed() {
        Product cup = product(10);
        Order a = pay(line(cup, 4));                                   // 6
        Order b = pay(line(cup, 2));                                   // 4
        int itemId = orderDAO.findItemsByOrderId(a.getId()).get(0).getId();
        new RefundDAO().refund(a.getId(), Map.of(itemId, 1), false, PaymentMethod.CASH, "spilled");   // not put back
        orderDAO.cancelPaidOrder(b.getId(), false, "thrown away");                                  // not put back
        assertEquals(4, productDAO.findById(cup.getId()).orElseThrow().getStock());
        StockHistory.Page page = history.ofProduct(cup.getId(), 100);
        assertEquals(0, sum(page, Kind.REFUND));
        assertEquals(-6, sum(page, Kind.SALE));
        assertEquals(4, page.moves().get(0).balance());
        assertEquals(0, page.before());                                 // the product's own 0 -> 10 stock log line is the first line
    }

    @Test
    public void anIngredientListsItsSalesRefundsAndDeliveries() {
        Product latte = product(0);
        Ingredient beans = ingredientDAO.insert("test-hist-beans-" + UUID.randomUUID(), "g", 1000, 50);
        ingredientDAO.saveRecipe(latte.getId(), List.<long[]>of(new long[]{beans.id(), 18}));
        ingredientDAO.changeStock(beans.id(), 500, "delivery");        // 1500
        Order sold = pay(line(latte, 3));                              // -54 -> 1446
        int itemId = orderDAO.findItemsByOrderId(sold.getId()).get(0).getId();
        new RefundDAO().refund(sold.getId(), Map.of(itemId, 1), true, PaymentMethod.CASH, "wrong drink");   // +18 -> 1464
        StockHistory.Page page = history.ofIngredient(beans.id(), 100);
        assertEquals(1464, page.now());
        assertEquals(1464, page.moves().get(0).balance());
        assertEquals(-54, sum(page, Kind.SALE));
        assertEquals(18, sum(page, Kind.REFUND));
        assertEquals(500, sum(page, Kind.CHANGE));
        assertEquals(1000, page.before());                             // the starting stock typed on creation is not a movement
        // the recipe product itself counts no cups
        assertTrue(history.ofProduct(latte.getId(), 100).moves().stream().noneMatch(m -> m.kind() == Kind.SALE));
    }

    @Test
    public void onlyTheNewestLinesAreShownAndTheirBalancesStayRight() {
        Product cup = product(50);
        for (int i = 0; i < 5; i++) pay(line(cup, 1));                  // 45
        StockHistory.Page page = history.ofProduct(cup.getId(), 3);
        assertEquals(3, page.moves().size());
        assertTrue(page.cut());
        assertEquals(45, page.moves().get(0).balance());
        assertEquals(47, page.moves().get(2).balance());
        assertEquals(48, page.before());                                // before the oldest line SHOWN
    }

    @Test
    public void onlyAnAdministratorMayReadItAndAMissingItemIsReported() {
        Product cup = product(1);
        TestSession.clear();
        try {
            history.ofProduct(cup.getId(), 10);
            fail("admin only");
        } catch (SecurityException | DataAccessException expected) {
            // fine
        } finally {
            TestSession.admin();
        }
        try {
            history.ofProduct(999_999, 10);
            fail("no such product");
        } catch (DataAccessException expected) {
            assertFalse(expected.getMessage().isBlank());
        }
    }
}
