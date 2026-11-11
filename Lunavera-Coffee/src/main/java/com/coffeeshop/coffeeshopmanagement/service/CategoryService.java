package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.util.Messages;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;
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

    /**
     * Adds a category ({@code existing == null}) or changes one. Only an administrator may. A blank name is refused. The change is
     * made on a copy: {@code existing} (which a table on screen may be showing) is touched only after the database accepted it
     * (TODO 2.76). Returns the saved category.
     *
     * @throws Permissions.PermissionDeniedException if the signed-in person is not an administrator
     * @throws DataAccessException                   for a blank name, or when the database refuses the save
     */
    public Category save(Category existing, String name, String description, boolean active) {
        Permissions.requireAdmin("save a category");
        if (ValidationUtil.isBlank(name)) {
            throw new DataAccessException(Messages.get("category.dialog.missingName.text"));
        }
        Category category = existing != null
                ? new Category(existing.getId(), existing.getName(), existing.getDescription(), existing.isActive())
                : new Category();
        category.setName(name.trim());
        category.setDescription(description);
        category.setActive(active);
        if (existing == null) {
            categoryDAO.insert(category);
            return category;
        }
        categoryDAO.update(category);
        existing.setName(category.getName());
        existing.setDescription(category.getDescription());
        existing.setActive(category.isActive());
        return existing;
    }

    public java.util.List<com.coffeeshop.coffeeshopmanagement.model.Category> findAll() {
        return categoryDAO.findAll();
    }

    public java.util.Map<Integer, Integer> countProductsByCategory() {
        return categoryDAO.countProductsByCategory();
    }

    public boolean move(int categoryId, int delta) {
        return categoryDAO.move(categoryId, delta);
    }
}
