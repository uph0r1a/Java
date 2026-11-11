package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.service.OrderService;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TODO 3.1: two open orders cannot both hold the last piece. Real database; take-away orders only, so no table is touched, and every
 * test makes its own products and asserts only about them (the shared database may hold other open orders).
 */
public class StockHoldsDbTest {

    private final OrderService orders = new OrderService();
    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final IngredientDAO ingredientDAO = new IngredientDAO();
    private final List<Integer> opened = new ArrayList<>();
    private Category category;

    @BeforeClass
    public static void setUp() {
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
        category = new Category();
        category.setName("test-holds-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
    }

    private Product product(int stock) {
        Product p = new Product();
        p.setName("test-holds-product-" + UUID.randomUUID());
        p.setCategoryId(category.getId());
        p.setPrice(new BigDecimal("40000"));
        p.setStock(stock);
        p.setActive(true);
        return productDAO.insert(p);
    }

    private Order openOrder() {
        Order order = orders.start(null, "", "", null).order();
        opened.add(order.getId());
        return order;
    }

    private static OrderItem line(Product p, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(p.getPrice());
        item.setLineTotal(p.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private Product forSale(Product p, int exceptOrderId) {
        return productDAO.findAllForSale(exceptOrderId).stream().filter(x -> x.getId() == p.getId()).findFirst().orElseThrow();
    }

    @org.junit.After
    public void release() {
        for (int id : opened) orderDAO.cancelOpenTakeAway(id);
        opened.clear();
    }

    @Test
    public void theLastPieceHeldByOneOrderIsNotForSaleToAnother() {
        Product latte = product(1);
        Order a = openOrder();
        Order b = openOrder();
        assertTrue(orderDAO.updateGuarded(a, List.of(line(latte, 1))).saved());
        Product seenByB = forSale(latte, b.getId());
        assertEquals(0, seenByB.getStock());
        assertEquals(1, seenByB.getHeldElsewhere());
        assertEquals("the order that holds it still sees it", 1, forSale(latte, a.getId()).getStock());
        assertEquals("with no order on screen it is held too", 0, forSale(latte, 0).getStock());
    }

    @Test
    public void theSecondAutosaveIsRefusedAndWritesNothing() {
        Product latte = product(1);
        Order a = openOrder();
        Order b = openOrder();
        assertTrue(orderDAO.updateGuarded(a, List.of(line(latte, 1))).saved());
        OrderDAO.SaveResult result = orderDAO.updateGuarded(b, List.of(line(latte, 1)));
        assertTrue("the order is still open", result.stillOpen());
        assertFalse(result.saved());
        assertEquals(1, result.shortages().size());
        assertEquals(latte.getId(), result.shortages().get(0).productId());
        assertEquals("nothing was written", 0, orderDAO.findItemsByOrderId(b.getId()).size());
        assertEquals("the first order is untouched", 1, orderDAO.findItemsByOrderId(a.getId()).size());
    }

    @Test
    public void whatIsFreeCanBeTakenAndTheRestIsRefused() {
        Product latte = product(5);
        Order a = openOrder();
        Order b = openOrder();
        assertTrue(orderDAO.updateGuarded(a, List.of(line(latte, 3))).saved());
        assertTrue(orderDAO.updateGuarded(b, List.of(line(latte, 2))).saved());
        assertFalse(orderDAO.updateGuarded(b, List.of(line(latte, 3))).saved());
        assertEquals(2, orderDAO.findItemsByOrderId(b.getId()).get(0).getQuantity());
    }

    @Test
    public void payingOrCancellingAnOrderFreesItsPieces() {
        Product latte = product(1);
        Order a = openOrder();
        Order b = openOrder();
        orderDAO.updateGuarded(a, List.of(line(latte, 1)));
        assertFalse(orderDAO.updateGuarded(b, List.of(line(latte, 1))).saved());
        orderDAO.cancelOpenTakeAway(a.getId());
        assertEquals(1, forSale(latte, b.getId()).getStock());
        assertTrue(orderDAO.updateGuarded(b, List.of(line(latte, 1))).saved());
    }

    @Test
    public void takingPiecesOutIsAlwaysAllowed() {
        Product latte = product(3);
        Order a = openOrder();
        orderDAO.updateGuarded(a, List.of(line(latte, 3)));
        // the shelf is counted down to 1 while the order holds 3: reducing it must still work
        productDAO.findAll().stream().filter(p -> p.getId() == latte.getId()).findFirst().ifPresent(p -> {
            p.setStock(1);
            productDAO.update(p);
        });
        assertTrue(orderDAO.updateGuarded(a, List.of(line(latte, 2))).saved());
        assertTrue(orderDAO.updateGuarded(a, List.of()).saved());
    }

    @Test
    public void theOldAutosaveStillWritesWithoutTheCheck() {
        Product latte = product(1);
        Order a = openOrder();
        Order b = openOrder();
        orderDAO.updateGuarded(a, List.of(line(latte, 1)));
        assertTrue("the unguarded write is what other code and tests rely on", orderDAO.updateIfStillOpen(b, List.of(line(latte, 1))));
    }

    @Test
    public void anOrderClosedMeanwhileIsReportedAsClosedNotAsAShortage() {
        Product latte = product(1);
        Order a = openOrder();
        orderDAO.cancelOpenTakeAway(a.getId());
        OrderDAO.SaveResult result = orderDAO.updateGuarded(a, List.of(line(latte, 1)));
        assertFalse(result.stillOpen());
        assertTrue(result.shortages().isEmpty());
    }

    @Test
    public void aRecipeProductIsHeldThroughItsIngredients() {
        var milk = ingredientDAO.insert("test-holds-milk-" + UUID.randomUUID(), "ml", 1000, 0);
        Product latte = product(0);
        Product cappuccino = product(0);
        ingredientDAO.saveRecipe(latte.getId(), List.<long[]>of(new long[]{milk.id(), 200}));
        ingredientDAO.saveRecipe(cappuccino.getId(), List.<long[]>of(new long[]{milk.id(), 150}));
        Order a = openOrder();
        Order b = openOrder();
        assertTrue(orderDAO.updateGuarded(a, List.of(line(latte, 3))).saved()); // 600 ml
        assertEquals("5 servings, 3 held", 2, forSale(latte, b.getId()).getStock());
        assertEquals(3, forSale(latte, b.getId()).getHeldElsewhere());
        assertEquals("another drink on the same milk: (1000-600)/150", 2, forSale(cappuccino, b.getId()).getStock());
        assertFalse(orderDAO.updateGuarded(b, List.of(line(cappuccino, 3))).saved());
        assertTrue(orderDAO.updateGuarded(b, List.of(line(cappuccino, 2))).saved());
    }
}
