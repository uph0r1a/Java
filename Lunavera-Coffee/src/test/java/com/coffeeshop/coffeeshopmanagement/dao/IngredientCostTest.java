package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditEntry;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Expense;
import com.coffeeshop.coffeeshopmanagement.model.Ingredient;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.service.ExpenseRules;
import com.coffeeshop.coffeeshopmanagement.service.IngredientCostRules;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * TODO 4.51: ingredients have a price per unit, a delivery that says what it cost moves it, and a drink's cost can come from its
 * recipe. Real database, like {@link IngredientTest}; every test makes its own ingredients and products and asserts only about them.
 */
public class IngredientCostTest {

    private final IngredientDAO dao = new IngredientDAO();
    private final ProductDAO productDAO = new ProductDAO();
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
        category = new Category();
        category.setName("test-cost-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
    }

    private Ingredient ingredient(String unit, long stock, Long unitCost) {
        return dao.insert("test-cost-" + UUID.randomUUID(), unit, stock, 0, unitCost);
    }

    private Ingredient reread(Ingredient i) {
        return dao.findAll().stream().filter(x -> x.id() == i.id()).findFirst().orElseThrow();
    }

    private Product product(String cost) {
        Product p = new Product();
        p.setName("test-cost-product-" + UUID.randomUUID());
        p.setCategoryId(category.getId());
        p.setPrice(new BigDecimal("45000"));
        if (cost != null) p.setCost(new BigDecimal(cost));
        p.setStock(0);
        p.setActive(true);
        return productDAO.insert(p);
    }

    private List<AuditEntry> audit(AuditAction action, String label) {
        return new AuditDAO().find(new AuditDAO.Filter(null, null, action, null, label), 20, 0);
    }

    // ------------------------------------------------------------------ the price of an ingredient

    @Test
    public void anIngredientIsAddedWithOrWithoutAPrice() {
        Ingredient priced = ingredient("g", 100, 300L);
        Ingredient unpriced = dao.insert("test-cost-" + UUID.randomUUID(), "g", 100, 0);
        assertEquals(Long.valueOf(300), reread(priced).unitCost());
        assertNull(reread(unpriced).unitCost());
    }

    @Test
    public void thePriceIsChangedAndRemovedAndTheChangeIsOnTheAuditLine() {
        Ingredient beans = ingredient("g", 100, 300L);
        dao.update(beans.id(), beans.name(), "g", 0, 350L);
        assertEquals(Long.valueOf(350), reread(beans).unitCost());
        dao.update(beans.id(), beans.name(), "g", 0, null);
        assertNull(reread(beans).unitCost());
        List<AuditEntry> lines = audit(AuditAction.INGREDIENT_SAVED, beans.name());
        assertTrue(lines.stream().anyMatch(l -> l.detail() != null && l.detail().contains("unit cost 300 -> 350")));
        assertTrue(lines.stream().anyMatch(l -> l.detail() != null && l.detail().contains("unit cost 350 -> none")));
    }

    @Test
    public void theOldFourArgumentUpdateLeavesThePriceAlone() {
        Ingredient beans = ingredient("g", 100, 300L);
        dao.update(beans.id(), beans.name() + "-b", "g", 5);
        Ingredient after = reread(beans);
        assertEquals(Long.valueOf(300), after.unitCost());
        assertEquals(5, after.minStock());
    }

    @Test
    public void aPriceThatIsNotAllowedIsRefusedAndNothingIsSaved() {
        for (long bad : new long[]{0, -1, IngredientCostRules.UNIT_COST_MAX + 1}) {
            try {
                dao.insert("test-cost-" + UUID.randomUUID(), "g", 0, 0, bad);
                fail("refused: " + bad);
            } catch (DataAccessException expected) {
                assertFalse(expected.getMessage().isBlank());
            }
        }
        Ingredient beans = ingredient("g", 100, 300L);
        try {
            dao.update(beans.id(), beans.name(), "g", 0, 0L);
            fail("refused");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(Long.valueOf(300), reread(beans).unitCost());
    }

    // ------------------------------------------------------------------ deliveries

    @Test
    public void aDeliveryWithNoPriceAddsStockAndLeavesThePrice() {
        Ingredient beans = ingredient("g", 100, 300L);
        IngredientDAO.Receipt receipt = dao.receive(beans.id(), 400, null, false, "Delivery");
        assertEquals(500, receipt.stockAfter());
        assertEquals(Long.valueOf(300), receipt.unitCostAfter());
        assertEquals(500, reread(beans).stock());
        assertEquals(Long.valueOf(300), reread(beans).unitCost());
    }

    @Test
    public void aPricedDeliveryOfAnUnpricedIngredientSetsThePriceFromWhatWasPaid() {
        Ingredient milk = ingredient("ml", 200, null);
        IngredientDAO.Receipt receipt = dao.receive(milk.id(), 1_000, 25_000L, false, "Delivery");
        assertEquals(1_200, receipt.stockAfter());
        assertEquals("25.000 for 1.000 ml is 25 per ml, whatever the old unpriced stock was", Long.valueOf(25), receipt.unitCostAfter());
        assertEquals(Long.valueOf(25), reread(milk).unitCost());
    }

    @Test
    public void aPricedDeliveryAveragesWithWhatIsOnTheShelf() {
        Ingredient beans = ingredient("g", 1_000, 300L);
        IngredientDAO.Receipt receipt = dao.receive(beans.id(), 500, 180_000L, false, "Delivery"); // 500 g at 360
        assertEquals(Long.valueOf(320), receipt.unitCostAfter());
        assertEquals(1_500, reread(beans).stock());
        assertEquals(Long.valueOf(320), reread(beans).unitCost());
    }

    @Test
    public void anEmptyShelfTakesThePriceOfTheNewLot() {
        Ingredient beans = ingredient("g", 0, 300L);
        assertEquals(Long.valueOf(360), dao.receive(beans.id(), 500, 180_000L, false, "Delivery").unitCostAfter());
    }

    @Test
    public void theAuditLineOfADeliveryHasTheStockAndThePriceChange() {
        Ingredient beans = ingredient("g", 1_000, 300L);
        dao.receive(beans.id(), 500, 180_000L, false, "Delivery");
        AuditEntry line = audit(AuditAction.INGREDIENT_STOCK, beans.name()).get(0);
        assertEquals("1000", line.oldValue());
        assertEquals("1500", line.newValue());
        assertTrue(line.detail().contains("paid 180000"));
        assertTrue(line.detail().contains("unit cost 300 -> 320"));
    }

    @Test
    public void aDeliveryCanAlsoBeWrittenDownAsASuppliesExpenseInTheSameStroke() {
        Ingredient beans = ingredient("g", 0, null);
        dao.receive(beans.id(), 500, 180_000L, true, "Delivery");
        List<Expense> today = new ExpenseDAO().find(AppClock.today(), AppClock.today(), 500);
        Expense written = today.stream().filter(e -> e.note() != null && e.note().contains(beans.name())).findFirst().orElseThrow();
        assertEquals(ExpenseRules.SUPPLIES, written.category());
        assertEquals(0, new BigDecimal("180000").compareTo(written.amount()));
        assertFalse("not cash from the drawer", written.fromDrawer());
    }

    @Test
    public void noExpenseIsWrittenUnlessAskedFor() {
        Ingredient beans = ingredient("g", 0, null);
        dao.receive(beans.id(), 500, 180_000L, false, "Delivery");
        List<Expense> today = new ExpenseDAO().find(AppClock.today(), AppClock.today(), 500);
        assertTrue(today.stream().noneMatch(e -> e.note() != null && e.note().contains(beans.name())));
    }

    @Test
    public void aRefusedDeliveryChangesNothingAndWritesNoExpense() {
        Ingredient beans = ingredient("g", 100, 300L);
        long[][] bad = {{0, 1_000}, {-5, 1_000}, {1_000, 400}, {1_000, 0}};
        for (long[] attempt : bad) {
            try {
                dao.receive(beans.id(), attempt[0], attempt[1], true, "Delivery");
                fail("refused: " + attempt[0] + " / " + attempt[1]);
            } catch (DataAccessException expected) {
                assertFalse(expected.getMessage().isBlank());
            }
        }
        Ingredient after = reread(beans);
        assertEquals(100, after.stock());
        assertEquals(Long.valueOf(300), after.unitCost());
        assertTrue(new ExpenseDAO().find(AppClock.today(), AppClock.today(), 500).stream()
                .noneMatch(e -> e.note() != null && e.note().contains(beans.name())));
    }

    @Test
    public void aDeliveryThatWouldPassTheStockCeilingIsRefused() {
        Ingredient beans = ingredient("g", 100, 300L);
        try {
            dao.receive(beans.id(), 100_000_000L, null, false, "Delivery");
            fail("refused");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(100, reread(beans).stock());
    }

    @Test
    public void nobodyButAnAdministratorReceivesOrSetsAPrice() {
        Ingredient beans = ingredient("g", 100, 300L);
        Product latte = product(null);
        TestSession.clear();
        for (Runnable bad : List.<Runnable>of(
                () -> dao.receive(beans.id(), 10, 3_000L, false, "x"),
                () -> dao.update(beans.id(), "nope", "g", 0, 1L),
                () -> dao.insert("nope-" + UUID.randomUUID(), "g", 0, 0, 5L),
                () -> dao.useRecipeCostAsProductCost(latte.getId()))) {
            try {
                bad.run();
                fail("refused");
            } catch (RuntimeException expected) {
                // fine
            }
        }
        TestSession.admin();
        assertEquals(100, reread(beans).stock());
        assertEquals(Long.valueOf(300), reread(beans).unitCost());
    }

    // ------------------------------------------------------------------ the cost of a recipe

    private Product latteWith(Ingredient beans, long beansAmount, Ingredient milk, long milkAmount, String typedCost) {
        Product latte = product(typedCost);
        dao.saveRecipe(latte.getId(), List.of(new long[]{beans.id(), beansAmount}, new long[]{milk.id(), milkAmount}));
        return latte;
    }

    @Test
    public void aRecipeLineCarriesThePriceOfItsIngredient() {
        Ingredient beans = ingredient("g", 100, 300L);
        Ingredient milk = ingredient("ml", 100, null);
        Product latte = latteWith(beans, 18, milk, 200, null);
        List<IngredientDAO.RecipeLine> lines = dao.recipeOf(latte.getId());
        assertEquals(2, lines.size());
        assertEquals(Long.valueOf(300), lines.stream().filter(l -> l.ingredientId() == beans.id()).findFirst().orElseThrow().unitCost());
        assertNull(lines.stream().filter(l -> l.ingredientId() == milk.id()).findFirst().orElseThrow().unitCost());
    }

    @Test
    public void theCostOfARecipeFollowsThePriceOfItsIngredients() {
        Ingredient beans = ingredient("g", 100, 300L);
        Ingredient milk = ingredient("ml", 100, 25L);
        Product latte = latteWith(beans, 18, milk, 200, null);
        IngredientCostRules.RecipeCost cost = dao.recipeCostOf(latte.getId());
        assertTrue(cost.complete());
        assertEquals(5_400 + 5_000, cost.total());
        // the price of beans goes up: the recipe's cost follows, nothing else has to be edited
        dao.update(beans.id(), beans.name(), "g", 0, 400L);
        assertEquals(7_200 + 5_000, dao.recipeCostOf(latte.getId()).total());
    }

    @Test
    public void aRecipeWithAnUnpricedIngredientIsIncompleteAndCannotBecomeTheProductCost() {
        Ingredient beans = ingredient("g", 100, 300L);
        Ingredient milk = ingredient("ml", 100, null);
        Product latte = latteWith(beans, 18, milk, 200, "9999");
        IngredientCostRules.RecipeCost cost = dao.recipeCostOf(latte.getId());
        assertFalse(cost.complete());
        assertEquals(List.of(milk.name()), cost.missing());
        try {
            dao.useRecipeCostAsProductCost(latte.getId());
            fail("refused");
        } catch (DataAccessException expected) {
            assertTrue("the sentence names what is missing", expected.getMessage().contains(milk.name()));
        }
        assertEquals(0, new BigDecimal("9999").compareTo(productDAO.findById(latte.getId()).orElseThrow().getCost()));
    }

    @Test
    public void aProductWithNoRecipeCannotTakeACostFromOne() {
        Product plain = product("1234");
        try {
            dao.useRecipeCostAsProductCost(plain.getId());
            fail("refused");
        } catch (DataAccessException expected) {
            assertFalse(expected.getMessage().isBlank());
        }
        assertEquals(0, new BigDecimal("1234").compareTo(productDAO.findById(plain.getId()).orElseThrow().getCost()));
    }

    @Test
    public void usingTheRecipeCostSetsTheProductCostAndLeavesTheAuditLine() {
        Ingredient beans = ingredient("g", 100, 300L);
        Ingredient milk = ingredient("ml", 100, 25L);
        Product latte = latteWith(beans, 18, milk, 200, "8000");
        assertEquals(10_400, dao.useRecipeCostAsProductCost(latte.getId()));
        assertEquals(0, new BigDecimal("10400").compareTo(productDAO.findById(latte.getId()).orElseThrow().getCost()));
        AuditEntry line = audit(AuditAction.PRODUCT_COST, latte.getName()).get(0);
        assertEquals("8000", line.oldValue());
        assertEquals("10400", line.newValue());
    }

    @Test
    public void aProductThatHadNoCostGetsOneAndUsingItAgainWritesNoSecondLine() {
        Ingredient beans = ingredient("g", 100, 300L);
        Ingredient milk = ingredient("ml", 100, 25L);
        Product latte = latteWith(beans, 18, milk, 200, null);
        dao.useRecipeCostAsProductCost(latte.getId());
        assertEquals(0, new BigDecimal("10400").compareTo(productDAO.findById(latte.getId()).orElseThrow().getCost()));
        dao.useRecipeCostAsProductCost(latte.getId());
        assertEquals("the second time changes nothing", 1, audit(AuditAction.PRODUCT_COST, latte.getName()).size());
        assertNull(audit(AuditAction.PRODUCT_COST, latte.getName()).get(0).oldValue());
    }
}
