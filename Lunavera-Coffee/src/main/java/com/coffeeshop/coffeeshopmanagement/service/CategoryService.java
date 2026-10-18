package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;

/**
 * The rules about categories that do not belong on a screen (TODO 6.21): only an administrator may remove one, and a category
 * that products still use is never removed (the database layer checks again at the moment of the delete, so a product added a
 * second ago is not orphaned). It touches no screen and is meant to be called from a background thread (TODO 6.14).
 */
public class CategoryService {

    private final CategoryDAO categoryDAO;

    public CategoryService() {
        this(new CategoryDAO());
    }

    CategoryService(CategoryDAO categoryDAO) {
        this.categoryDAO = categoryDAO;
    }

    /** How many products use this category (shown to the cashier before they confirm). */
    public int productCount(int categoryId) {
        return categoryDAO.countProductsInCategory(categoryId);
    }

    /**
     * Removes the category if no product uses it.
     *
     * @return true if it was removed; false if a product uses it (nothing was removed)
     * @throws com.coffeeshop.coffeeshopmanagement.util.Permissions.PermissionDeniedException if the signed-in person is not an administrator
     */
    public boolean delete(int categoryId) {
        Permissions.requireAdmin("delete a category");
        return categoryDAO.deleteIfUnused(categoryId);
    }
}
