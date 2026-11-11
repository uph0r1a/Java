package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.41, rule D12: what may be typed for an ingredient and a recipe, and how many servings a stock makes. */
public class IngredientRulesTest {

    @Test
    public void aNameAndUnitAreNeededAndCleaned() {
        assertNull(IngredientRules.problem("  Sữa   tươi ", " ml "));
        assertEquals("Sữa tươi", IngredientRules.clean("  Sữa   tươi "));
        assertNotNull(IngredientRules.problem("", "ml"));
        assertNotNull(IngredientRules.problem("   ", "ml"));
        assertNotNull(IngredientRules.problem("Sữa", " "));
        assertNotNull(IngredientRules.problem("x".repeat(101), "ml"));
        assertNotNull(IngredientRules.problem("Sữa", "x".repeat(21)));
        assertNotNull(IngredientRules.problem(null, null));
    }

    @Test
    public void amountsAreWholeAndNotNegativeOrAbsurd() {
        assertNull(IngredientRules.amountProblem(0));
        assertNull(IngredientRules.amountProblem(50_000));
        assertNotNull(IngredientRules.amountProblem(-1));
        assertNotNull(IngredientRules.amountProblem(IngredientRules.AMOUNT_MAX + 1));
    }

    @Test
    public void aRecipeNeedsPositiveAmountsAndEachIngredientOnce() {
        assertNull(IngredientRules.recipeProblem(List.of()));
        assertNull(IngredientRules.recipeProblem(List.of(new long[]{1, 18}, new long[]{2, 150})));
        assertNotNull(IngredientRules.recipeProblem(List.of(new long[]{1, 0})));
        assertNotNull(IngredientRules.recipeProblem(List.of(new long[]{1, -5})));
        assertNotNull(IngredientRules.recipeProblem(List.of(new long[]{1, 5}, new long[]{1, 6})));
        assertNotNull(IngredientRules.recipeProblem(List.of(new long[]{1, IngredientRules.AMOUNT_MAX + 1})));
    }

    @Test
    public void servingsAreLimitedByTheScarcestIngredient() {
        assertNull("no lines, no recipe", IngredientRules.servings(List.of()));
        assertEquals("200 g of beans at 18 g make 11, 3000 ml of milk at 150 make 20", Integer.valueOf(11),
                IngredientRules.servings(List.of(new long[]{200, 18}, new long[]{3000, 150})));
        assertEquals(Integer.valueOf(0), IngredientRules.servings(List.of(new long[]{5, 18})));
    }
}
