package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.service.IngredientCostRules.Line;
import com.coffeeshop.coffeeshopmanagement.service.IngredientCostRules.RecipeCost;
import com.coffeeshop.coffeeshopmanagement.service.IngredientCostRules.Typed;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** TODO 4.51: the price of an ingredient, what a delivery does to it, and what a recipe costs. No screen, no database. */
public class IngredientCostRulesTest {

    // ------------------------------------------------------------------ the price of one unit

    @Test
    public void aPriceOfOneUnitIsWholeDongFromOneUpToTheCeiling() {
        assertNotNull(IngredientCostRules.unitCostProblem(0));
        assertNotNull(IngredientCostRules.unitCostProblem(-5));
        assertNull(IngredientCostRules.unitCostProblem(1));
        assertNull(IngredientCostRules.unitCostProblem(300));
        assertNull(IngredientCostRules.unitCostProblem(IngredientCostRules.UNIT_COST_MAX));
        assertNotNull(IngredientCostRules.unitCostProblem(IngredientCostRules.UNIT_COST_MAX + 1));
    }

    @Test
    public void anEmptyPriceBoxMeansNoPrice() {
        assertTrue(IngredientCostRules.parseOptionalUnitCost(null).ok());
        assertNull(IngredientCostRules.parseOptionalUnitCost(null).cost());
        assertTrue(IngredientCostRules.parseOptionalUnitCost("   ").ok());
        assertNull(IngredientCostRules.parseOptionalUnitCost("   ").cost());
    }

    @Test
    public void aTypedPriceIsReadTheWayEveryOtherMoneyBoxIs() {
        Typed plain = IngredientCostRules.parseOptionalUnitCost("300");
        assertTrue(plain.ok());
        assertEquals(Long.valueOf(300), plain.cost());
        assertEquals(Long.valueOf(25_000), IngredientCostRules.parseOptionalUnitCost("25.000đ").cost());
        assertEquals(Long.valueOf(25_000), IngredientCostRules.parseOptionalPaid("25,000").cost());
    }

    @Test
    public void textThatIsNotAnAmountIsNotOk() {
        for (String bad : new String[]{"abc", "12x", "-5", "1e5", "3,5"}) {
            assertFalse(bad + " must not be accepted", IngredientCostRules.parseOptionalUnitCost(bad).ok());
        }
    }

    // ------------------------------------------------------------------ a delivery

    @Test
    public void thePriceOfAUnitIsWhatWasPaidDividedByTheQuantityToTheNearestDong() {
        assertEquals(300, IngredientCostRules.unitCostFromPaid(120_000, 400));
        assertEquals(333, IngredientCostRules.unitCostFromPaid(1_000, 3));
        assertEquals("a half goes up", 501, IngredientCostRules.unitCostFromPaid(1_001, 2));
        assertEquals("less than half a dong is nothing", 0, IngredientCostRules.unitCostFromPaid(1, 3));
        assertEquals(0, IngredientCostRules.unitCostFromPaid(0, 10));
        assertEquals(0, IngredientCostRules.unitCostFromPaid(100, 0));
    }

    @Test
    public void withNothingOnTheShelfTheNewPriceIsTheWholeStory() {
        assertEquals(360, IngredientCostRules.afterDelivery(0, 300L, 500, 360));
        assertEquals("a negative count is nothing on the shelf either", 360, IngredientCostRules.afterDelivery(-10, 300L, 500, 360));
    }

    @Test
    public void anIngredientThatWasNeverPricedTakesTheNewPrice() {
        assertEquals(360, IngredientCostRules.afterDelivery(1_000, null, 500, 360));
    }

    @Test
    public void theShelfAndTheLotAreAveragedByQuantity() {
        // 1000 g at 300 and 500 g at 360: (300.000 + 180.000) / 1500 = 320
        assertEquals(320, IngredientCostRules.afterDelivery(1_000, 300L, 500, 360));
        // the same price again changes nothing
        assertEquals(300, IngredientCostRules.afterDelivery(1_000, 300L, 500, 300));
        // a lot far bigger than the shelf nearly replaces the price
        assertEquals(400, IngredientCostRules.afterDelivery(10, 300L, 10_000, 400)); // 399.9
    }

    @Test
    public void theAverageIsRoundedToTheNearestDong() {
        // 1 at 100 and 2 at 101: 302 / 3 = 100.67 -> 101
        assertEquals(101, IngredientCostRules.afterDelivery(1, 100L, 2, 101));
        // 2 at 100 and 1 at 101: 301 / 3 = 100.33 -> 100
        assertEquals(100, IngredientCostRules.afterDelivery(2, 100L, 1, 101));
    }

    @Test
    public void theLargestAllowedStockAndPriceDoNotOverflow() {
        long stock = IngredientRules.AMOUNT_MAX;
        long price = IngredientCostRules.UNIT_COST_MAX;
        assertEquals(price, IngredientCostRules.afterDelivery(stock, price, 1, price));
    }

    @Test
    public void aDeliveryNeedsAQuantityAndAPriceThatMakesSense() {
        assertNotNull("zero arrived", IngredientCostRules.deliveryProblem(0, null));
        assertNotNull("negative", IngredientCostRules.deliveryProblem(-3, null));
        assertNotNull("more than any stock", IngredientCostRules.deliveryProblem(IngredientRules.AMOUNT_MAX + 1, null));
        assertNull("no price known is fine", IngredientCostRules.deliveryProblem(500, null));
        assertNull(IngredientCostRules.deliveryProblem(500, 150_000L));
        assertNotNull("paid nothing", IngredientCostRules.deliveryProblem(500, 0L));
        assertNotNull("paid a negative amount", IngredientCostRules.deliveryProblem(500, -1L));
        assertNotNull("a typing slip in the total", IngredientCostRules.deliveryProblem(500, IngredientCostRules.PAID_MAX + 1));
        assertEquals("under half a dong a unit", I18n.t("ingredient.val.pricelow"), IngredientCostRules.deliveryProblem(1_000, 400L));
        assertNotNull("above the price ceiling", IngredientCostRules.deliveryProblem(1, IngredientCostRules.UNIT_COST_MAX + 1));
    }

    // ------------------------------------------------------------------ the cost of a recipe

    @Test
    public void aRecipeCostsTheSumOfAmountTimesPrice() {
        // a latte: 18 g of beans at 300, 200 ml of milk at 25, one cup at 1.500
        RecipeCost cost = IngredientCostRules.recipeCost(List.of(
                new Line("beans", 18, 300L), new Line("milk", 200, 25L), new Line("cup", 1, 1_500L)));
        assertEquals(5_400 + 5_000 + 1_500, cost.total());
        assertTrue(cost.complete());
        assertTrue(cost.missing().isEmpty());
        assertEquals(3, cost.lines());
    }

    @Test
    public void anIngredientWithoutAPriceMakesTheCostIncompleteAndIsNamed() {
        RecipeCost cost = IngredientCostRules.recipeCost(List.of(
                new Line("beans", 18, 300L), new Line("syrup", 20, null), new Line("ice", 100, null)));
        assertEquals("only what is priced is added", 5_400, cost.total());
        assertFalse(cost.complete());
        assertEquals(List.of("syrup", "ice"), cost.missing());
    }

    @Test
    public void aProductWithNoRecipeHasNoCost() {
        RecipeCost cost = IngredientCostRules.recipeCost(List.of());
        assertEquals(0, cost.total());
        assertEquals(0, cost.lines());
        assertFalse("nothing is not a cost of nothing", cost.complete());
    }

    @Test
    public void aLineWithNoPriceHasNoCostAndAPricedOneIsExact() {
        assertNull(new Line("x", 5, null).cost());
        assertEquals(Long.valueOf(1_500), new Line("x", 5, 300L).cost());
    }
}
