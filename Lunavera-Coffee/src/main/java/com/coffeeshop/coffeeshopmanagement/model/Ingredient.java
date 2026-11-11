package com.coffeeshop.coffeeshopmanagement.model;

/**
 * Something that goes into the drinks (TODO 4.41, rule D12): beans, milk, syrup, cups. The amounts are whole numbers of the
 * ingredient's own unit (grams, millilitres, pieces), so nothing is ever rounded.
 *
 * @param stock    what is left; paying an order takes the recipe amounts off it
 * @param minStock at or below this the ingredient is on the low-stock card
 * @param unitCost the price of one unit in whole dong, or null when it has none yet (TODO 4.51)
 */
public record Ingredient(int id, String name, String unit, long stock, long minStock, Long unitCost) {

    /** An ingredient with no price (what it was before TODO 4.51). */
    public Ingredient(int id, String name, String unit, long stock, long minStock) {
        this(id, name, unit, stock, minStock, null);
    }

    public boolean isLow() {
        return stock <= minStock;
    }
}
