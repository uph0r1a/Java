package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Ingredient;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.CheckoutService;
import com.coffeeshop.coffeeshopmanagement.service.SplitBillRules;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 4.41, rule D12: ingredients, recipes, and a payment that takes the ingredients off in its own transaction. */
public class IngredientTest {

    private final IngredientDAO dao = new IngredientDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private int table;
    private Product latte;
    private Product water;
    private Ingredient beans;
    private Ingredient milk;

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
        table = new TableDAO().findAll().get(3).getTableNumber();
        new TableDAO().setEmpty(table);
        Category category = new Category();
        category.setName("test-ing-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        latte = product(category, "40000", 0);   // the cup number is not used for a recipe product
        water = product(category, "10000", 5);   // no recipe: counts cups
        beans = dao.insert("test-beans-" + UUID.randomUUID(), "g", 200, 50);
        milk = dao.insert("test-milk-" + UUID.randomUUID(), "ml", 3000, 500);
    }

    private Product product(Category category, String price, int stock) {
        Product p = new Product();
        p.setName("test-ing-" + UUID.randomUUID());
        p.setCategoryId(category.getId());
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setActive(true);
        return productDAO.insert(p);
    }

    private Product reread(Product p) {
        return productDAO.findById(p.getId()).orElseThrow();
    }

    private Ingredient reread(Ingredient i) {
        return dao.findAll().stream().filter(x -> x.id() == i.id()).findFirst().orElseThrow();
    }

    private void latteRecipe() {
        dao.saveRecipe(latte.getId(), List.of(new long[]{beans.id(), 18}, new long[]{milk.id(), 150}));
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
        BigDecimal total = SplitBillRules.sum(List.of(lines));
        CheckoutService.Payment payment = new CheckoutService.Payment(PaymentMethod.CASH, BigDecimal.ZERO, null, total, null, null);
        return new CheckoutService().pay(table, List.of(lines), total, null, "", "", null, payment, null, Role.ADMIN).order();
    }

    // ------------------------------------------------------------------ the ingredients

    @Test
    public void anIngredientIsAddedChangedAndDeletedWithAuditLines() {
        Ingredient syrup = dao.insert("  test-syrup-" + UUID.randomUUID() + " ", " ml ", 1000, 100);
        assertEquals("ml", syrup.unit());
        assertFalse(syrup.name().startsWith(" "));
        dao.update(syrup.id(), syrup.name() + "-2", "ml", 300);
        assertEquals(300, reread(syrup).minStock());
        assertEquals(1, new AuditDAO().find(new AuditDAO.Filter(null, null, AuditAction.INGREDIENT_SAVED, null, syrup.name() + "-2"), 10, 0).size());
        dao.delete(syrup.id());
        assertTrue(dao.findAll().stream().noneMatch(i -> i.id() == syrup.id()));
        assertEquals(1, new AuditDAO().find(new AuditDAO.Filter(null, null, AuditAction.INGREDIENT_DELETED, null, syrup.name() + "-2"), 10, 0).size());
    }

    @Test
    public void aBadOrDuplicateIngredientIsRefused() {
        for (Runnable bad : List.<Runnable>of(
                () -> dao.insert("", "g", 0, 0),
                () -> dao.insert("x", "", 0, 0),
                () -> dao.insert("x", "g", -1, 0),
                () -> dao.insert("x", "g", 0, -1),
                () -> dao.insert(beans.name().toUpperCase(), "g", 0, 0),
                () -> dao.update(milk.id(), beans.name(), "ml", 0),
                () -> dao.update(999_999, "nothing", "g", 0))) {
            try {
                bad.run();
                fail("refused");
            } catch (DataAccessException expected) {
                assertFalse(expected.getMessage().isBlank());
            }
        }
    }

    @Test
    public void stockChangesAreGuardedAndLogged() {
        assertEquals(700, dao.changeStock(beans.id(), 500, "delivery"));
        assertEquals(600, dao.changeStock(beans.id(), -100, "spilled"));
        try {
            dao.changeStock(beans.id(), -601, null);
            fail("below zero");
        } catch (DataAccessException expected) {
            // fine
        }
        try {
            dao.changeStock(beans.id(), 0, null);
            fail("no change");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(600, reread(beans).stock());
        var entries = new AuditDAO().find(new AuditDAO.Filter(null, null, AuditAction.INGREDIENT_STOCK, null, beans.name()), 10, 0);
        assertEquals(2, entries.size());
    }

    @Test
    public void aCountIsSavedAsTheCountedNumberEvenWhenStockMovedMeanwhile() {
        dao.changeStock(beans.id(), -100, "sold while counting"); // the window still shows the old stock (the beans start at 200: stock may not go below zero)
        var done = dao.setCounted(beans.id(), beans.stock(), "count"); // typed: what the screen showed, which is what is on the shelf
        assertEquals(beans.stock() - 100, done.before());
        assertEquals(beans.stock(), done.after());
        assertEquals(beans.stock(), reread(beans).stock());
        assertEquals("counting the same number again changes nothing", beans.stock(), dao.setCounted(beans.id(), beans.stock(), "count").after());
        assertEquals(2, new AuditDAO().find(new AuditDAO.Filter(null, null, AuditAction.INGREDIENT_STOCK, null, beans.name()), 10, 0).size());
        try {
            dao.setCounted(beans.id(), -1, "count");
            fail("below zero");
        } catch (DataAccessException expected) {
            // fine
        }
    }

    @Test
    public void nobodyButAnAdministratorChangesAnything() {
        latteRecipe();
        TestSession.clear();
        for (Runnable bad : List.<Runnable>of(
                () -> dao.insert("nope-" + UUID.randomUUID(), "g", 0, 0),
                () -> dao.update(beans.id(), "nope", "g", 0),
                () -> dao.changeStock(beans.id(), 5, null),
                () -> dao.delete(milk.id()),
                () -> dao.saveRecipe(latte.getId(), List.of()))) {
            try {
                bad.run();
                fail("refused");
            } catch (RuntimeException expected) {
                // fine
            }
        }
        TestSession.admin();
        assertEquals(200, reread(beans).stock());
        assertEquals(2, dao.recipeOf(latte.getId()).size());
    }

    // ------------------------------------------------------------------ recipes

    @Test
    public void aRecipeIsSavedReplacedAndRemovedAndAnIngredientInUseCannotBeDeleted() {
        latteRecipe();
        assertEquals(2, dao.recipeOf(latte.getId()).size());
        try {
            dao.delete(beans.id());
            fail("in a recipe");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage().contains(beans.name()));
        }
        dao.saveRecipe(latte.getId(), List.of(new long[]{milk.id(), 200}));
        assertEquals(1, dao.recipeOf(latte.getId()).size());
        assertEquals(200, dao.recipeOf(latte.getId()).get(0).amount());
        assertTrue(reread(latte).isRecipeBased());
        dao.saveRecipe(latte.getId(), List.of());
        assertTrue(dao.recipeOf(latte.getId()).isEmpty());
        assertFalse(reread(latte).isRecipeBased());
        dao.delete(beans.id());
        assertEquals(3, new AuditDAO().find(new AuditDAO.Filter(null, null, AuditAction.RECIPE_CHANGED, null, latte.getName()), 10, 0).size());
    }

    @Test
    public void aBadRecipeIsRefusedAndKeepsTheOldOne() {
        latteRecipe();
        for (List<long[]> bad : List.of(List.of(new long[]{beans.id(), 0}), List.of(new long[]{beans.id(), 5}, new long[]{beans.id(), 6}),
                List.of(new long[]{999_999, 5}))) {
            try {
                dao.saveRecipe(latte.getId(), bad);
                fail("refused");
            } catch (DataAccessException expected) {
                // fine
            }
        }
        assertEquals(2, dao.recipeOf(latte.getId()).size());
        try {
            dao.saveRecipe(999_999, List.of(new long[]{beans.id(), 5}));
            fail("no such product");
        } catch (DataAccessException expected) {
            // fine
        }
    }

    @Test
    public void aRecipeProductShowsTheServingsItsIngredientsAllow() {
        latteRecipe(); // 200 g / 18 = 11 cups, 3000 ml / 150 = 20 cups
        assertEquals(11, reread(latte).getStock());
        dao.changeStock(beans.id(), -190, null); // 10 g left: no cup
        assertEquals(0, reread(latte).getStock());
        assertEquals("a product without a recipe keeps its own number", 5, reread(water).getStock());
    }

    // ------------------------------------------------------------------ payment, refund, cancel

    @Test
    public void payingTakesTheIngredientsOffAndNotTheCupCount() {
        latteRecipe();
        pay(line(latte, 3));
        assertEquals(200 - 54, reread(beans).stock());
        assertEquals(3000 - 450, reread(milk).stock());
        assertEquals("the product's own number is not touched", 0, productRaw(latte));
    }

    private int productRaw(Product p) {
        try (var c = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection();
             var st = c.prepareStatement("SELECT stock FROM products WHERE id = ?")) {
            st.setInt(1, p.getId());
            try (var rs = st.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    public void aProductWithoutARecipeStillCountsCups() {
        pay(line(water, 2));
        assertEquals(3, reread(water).getStock());
        assertEquals(200, reread(beans).stock());
    }

    @Test
    public void aShortIngredientRefusesTheWholePaymentAndTakesNothing() {
        latteRecipe();
        try {
            pay(line(water, 1), line(latte, 12)); // 12 x 18 = 216 g of beans, only 200
            fail("not enough beans");
        } catch (DataAccessException expected) {
            assertTrue(expected.getMessage().contains(latte.getName()));
        }
        assertEquals(200, reread(beans).stock());
        assertEquals(3000, reread(milk).stock());
        assertEquals("the cup of water was rolled back too", 5, reread(water).getStock());
    }

    @Test
    public void aLineOfTheSameProductOnTwoLinesIsAddedUp() {
        latteRecipe();
        pay(line(latte, 5), line(latte, 6)); // 11 x 18 = 198 g
        assertEquals(2, reread(beans).stock());
        try {
            pay(line(latte, 1));
            fail("2 g left");
        } catch (DataAccessException expected) {
            // fine
        }
    }

    @Test
    public void cancellingAPaidOrderPutsTheIngredientsBack() {
        latteRecipe();
        Order paid = pay(line(latte, 2), line(water, 1));
        assertEquals(164, reread(beans).stock());
        orderDAO.cancelPaidOrder(paid.getId(), true);
        assertEquals(200, reread(beans).stock());
        assertEquals(3000, reread(milk).stock());
        assertEquals(5, reread(water).getStock());
        assertEquals(OrderStatus.CANCELLED, orderDAO.findById(paid.getId()).orElseThrow().getStatus());
    }

    @Test
    public void cancellingWithoutReturningToStockLeavesTheIngredientsUsed() {
        latteRecipe();
        Order paid = pay(line(latte, 2));
        orderDAO.cancelPaidOrder(paid.getId(), false);
        assertEquals(164, reread(beans).stock());
    }

    @Test
    public void refundingOneLinePutsOnlyItsIngredientsBackWhenAsked() {
        latteRecipe();
        Order paid = pay(line(latte, 3));
        int itemId = orderDAO.findItemsByOrderId(paid.getId()).get(0).getId();
        new RefundDAO().refund(paid.getId(), java.util.Map.of(itemId, 1), true, PaymentMethod.CASH, null);
        assertEquals(200 - 54 + 18, reread(beans).stock());
        assertEquals(3000 - 450 + 150, reread(milk).stock());
        new RefundDAO().refund(paid.getId(), java.util.Map.of(itemId, 1), false, PaymentMethod.CASH, null);
        assertEquals("not returned: stays used", 200 - 54 + 18, reread(beans).stock());
    }

    // ------------------------------------------------------------------ refunds use what the sale took (TODO 2.96)

    @Test
    public void aRefundPutsBackWhatTheSaleTookEvenIfTheRecipeChangedSince() {
        latteRecipe(); // beans 18, milk 150 per cup
        Order paid = pay(line(latte, 3));
        assertEquals(200 - 54, reread(beans).stock());
        dao.saveRecipe(latte.getId(), List.of(new long[]{beans.id(), 30})); // the shop changed the recipe: more beans, no milk
        int itemId = orderDAO.findItemsByOrderId(paid.getId()).get(0).getId();
        new RefundDAO().refund(paid.getId(), java.util.Map.of(itemId, 1), true, PaymentMethod.CASH, null);
        assertEquals("18 beans, what one cup took, not today's 30", 200 - 54 + 18, reread(beans).stock());
        assertEquals("the milk of that cup comes back although the recipe has no milk now", 3000 - 450 + 150, reread(milk).stock());
    }

    @Test
    public void cancellingAPaidOrderPutsBackWhatItTookAfterTheRecipeWasRemoved() {
        latteRecipe();
        Order paid = pay(line(latte, 2));
        dao.saveRecipe(latte.getId(), List.of()); // back to counting cups
        orderDAO.cancelPaidOrder(paid.getId(), true);
        assertEquals(200, reread(beans).stock());
        assertEquals(3000, reread(milk).stock());
        assertEquals("the cup number is not touched", 0, reread(latte).getStock());
    }

    @Test
    public void aCupSaleComesBackAsCupsEvenIfARecipeWasAddedSince() {
        Order paid = pay(line(water, 2));
        assertEquals(3, reread(water).getStock());
        dao.saveRecipe(water.getId(), List.of(new long[]{beans.id(), 18}));
        int itemId = orderDAO.findItemsByOrderId(paid.getId()).get(0).getId();
        new RefundDAO().refund(paid.getId(), java.util.Map.of(itemId, 2), true, PaymentMethod.CASH, null);
        assertEquals("no beans appear from nowhere", 200, reread(beans).stock());
        dao.saveRecipe(water.getId(), List.of()); // count cups again to read the cup number
        assertEquals(5, reread(water).getStock());
    }

    @Test
    public void aSaleWithNoRecordFallsBackToTheRecipeTheProductHasNow() throws Exception {
        latteRecipe();
        Order paid = pay(line(latte, 1));
        int itemId = orderDAO.findItemsByOrderId(paid.getId()).get(0).getId();
        try (java.sql.Connection connection = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection();
             java.sql.PreparedStatement st = connection.prepareStatement("DELETE FROM stock_takes WHERE order_item_id = ?")) {
            st.setInt(1, itemId); // as a sale paid before the record existed
            st.executeUpdate();
        }
        dao.saveRecipe(latte.getId(), List.of(new long[]{beans.id(), 30}));
        new RefundDAO().refund(paid.getId(), java.util.Map.of(itemId, 1), true, PaymentMethod.CASH, null);
        assertEquals(200 - 18 + 30, reread(beans).stock());
    }

    @Test
    public void everyPaidLineWritesDownWhatItTook() throws Exception {
        latteRecipe();
        Order paid = pay(line(latte, 2), line(water, 1));
        int rows = 0;
        try (java.sql.Connection connection = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection();
             java.sql.PreparedStatement st = connection.prepareStatement(
                     "SELECT COUNT(*) FROM stock_takes t JOIN order_items i ON i.id = t.order_item_id WHERE i.order_id = ?")) {
            st.setInt(1, paid.getId());
            try (java.sql.ResultSet rs = st.executeQuery()) {
                rs.next();
                rows = rs.getInt(1);
            }
        }
        assertEquals("two ingredients for the latte line, one cup row for the water line", 3, rows);
    }

    // ------------------------------------------------------------------ the low-stock card

    @Test
    public void theLowStockCardListsIngredientsAtOrBelowTheirMinimum() {
        int before = dao.countLow();
        dao.changeStock(beans.id(), -150, null); // 50 = the minimum
        assertEquals(before + 1, dao.countLow());
        assertTrue(dao.findLow(100).stream().anyMatch(i -> i.id() == beans.id()));
        assertTrue(dao.findLow(100).stream().noneMatch(i -> i.id() == milk.id()));
        dao.changeStock(beans.id(), 500, null);
        assertEquals(before, dao.countLow());
    }

    @Test
    public void aRecipeProductIsNotOnTheCupCountLowStockCard() {
        dao.saveRecipe(latte.getId(), List.of(new long[]{milk.id(), 150}));
        assertTrue(productDAO.findLowStock(1_000_000, 10_000).stream().noneMatch(p -> p.getId() == latte.getId()));
        dao.saveRecipe(latte.getId(), List.of());
        assertTrue("without a recipe its cup number (0) is low", productDAO.findLowStock(1_000_000, 10_000).stream().anyMatch(p -> p.getId() == latte.getId()));
    }

    @Test
    public void deletingAProductDeletesItsRecipe() {
        latteRecipe();
        int usedBefore = dao.recipeOf(latte.getId()).size();
        assertEquals(2, usedBefore);
        productDAO.delete(latte.getId());
        assertTrue(dao.recipeOf(latte.getId()).isEmpty());
        dao.delete(beans.id());
    }

}
