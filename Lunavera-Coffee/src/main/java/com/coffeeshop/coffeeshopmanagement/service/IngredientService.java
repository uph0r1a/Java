package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.IngredientDAO;
import com.coffeeshop.coffeeshopmanagement.model.Ingredient;

import java.util.List;

/**
 * What the ingredient and recipe screens ask for (TODO 6.21): the list, adding, changing and deleting an ingredient, a delivery, a
 * stock count, and a product's recipe with its cost. {@link IngredientRules} and {@link IngredientCostRules} hold the rules;
 * {@link IngredientDAO} the permission (administrators only), the stock arithmetic in one transaction and the audit lines.
 * {@code IngredientDialog}, {@code RecipePane} and {@code StockLogDialog} hold no DAO of their own. Meant to be called from a
 * background thread (TODO 6.14).
 */
public class IngredientService {

    private final IngredientDAO ingredients;

    public IngredientService() {
        this(new IngredientDAO());
    }

    IngredientService(IngredientDAO ingredients) {
        this.ingredients = ingredients;
    }

    public List<Ingredient> findAll() {
        return ingredients.findAll();
    }

    public Ingredient insert(String name, String unit, long stock, long minStock, Long unitCost) {
        return ingredients.insert(name, unit, stock, minStock, unitCost);
    }

    public void update(int id, String name, String unit, long minStock, Long unitCost) {
        ingredients.update(id, name, unit, minStock, unitCost);
    }

    public void delete(int id) {
        ingredients.delete(id);
    }

    public IngredientDAO.Receipt receive(int id, long quantity, Long paid, boolean recordExpense, String reason) {
        return ingredients.receive(id, quantity, paid, recordExpense, reason);
    }

    public IngredientDAO.Counted setCounted(int id, long counted, String reason) {
        return ingredients.setCounted(id, counted, reason);
    }

    public List<IngredientDAO.RecipeLine> recipeOf(int productId) {
        return ingredients.recipeOf(productId);
    }

    public IngredientCostRules.RecipeCost recipeCostOf(int productId) {
        return ingredients.recipeCostOf(productId);
    }

    public long useRecipeCostAsProductCost(int productId) {
        return ingredients.useRecipeCostAsProductCost(productId);
    }

    public void saveRecipe(int productId, List<long[]> lines) {
        ingredients.saveRecipe(productId, lines);
    }

    public int countLow() {
        return ingredients.countLow();
    }

    public List<Ingredient> findLow(int limit) {
        return ingredients.findLow(limit);
    }
}
