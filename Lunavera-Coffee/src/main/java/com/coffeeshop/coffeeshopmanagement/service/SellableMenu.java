package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Which products may be sold at the POS (TODO 2.56): the product must be on sale AND its category must not be hidden.
 * Hiding a category therefore takes all of its products off the menu (a seasonal menu), and unhiding brings them back.
 * A product with no category is always governed by its own flag only.
 */
public final class SellableMenu {
    private SellableMenu() {}

    public static List<Product> filter(List<Product> products, List<Category> categories) {
        Set<Integer> hidden = new HashSet<>();
        for (Category c : categories) {
            if (!c.isActive()) hidden.add(c.getId());
        }
        return products.stream()
                .filter(Product::isActive)
                .filter(p -> p.getCategoryId() == null || !hidden.contains(p.getCategoryId()))
                .toList();
    }
}
