package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.ImageStorage;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;

/**
 * The rules about removing a product that do not belong on a screen (TODO 6.21): only an administrator may remove or stop
 * selling one; a product that appears in an order is never deleted (the history keeps pointing at it), it can only be
 * stopped; and a picture file is deleted with its product only when no other product uses it. {@code ProductDAO.delete} says
 * "callers must first confirm no order references this product": this class is that caller, so the rule is no longer only
 * a comment. It also saves a product from the add/edit box ({@link #save}, checks in {@link ProductFormRules}). It touches no screen
 * and is meant to be called from a background thread (TODO 6.14).
 */
public class ProductService {

    private final ProductDAO productDAO;

    public ProductService() {
        this(new ProductDAO());
    }

    ProductService(ProductDAO productDAO) {
        this.productDAO = productDAO;
    }

    public java.util.List<Product> findAll() {
        return productDAO.findAll();
    }

    public java.util.Optional<Product> findById(int id) {
        return productDAO.findById(id);
    }

    /** The database says no to what was typed (the same name in the category, a code another product has): the box's own title and sentence. */
    public static final class Refused extends DataAccessException {
        private static final long serialVersionUID = 1L;
        private final String title;

        Refused(String title, String message) {
            super(message);
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    /**
     * Saves a checked box ({@link ProductFormRules#check}): a new product when {@code existing} is null, else that product changed.
     * Only an administrator; the same name in the category and a code another product has are refused here, in the database, not by a
     * screen that may be older than the other tills (TODO 6.21). Returns the product as saved.
     *
     * @throws Refused when the name or the code is taken
     */
    public Product save(Product existing, ProductFormRules.Values values) {
        Permissions.requireAdmin("save a product");
        int id = existing != null ? existing.getId() : 0;
        if (productDAO.existsByNameInCategory(values.name(), values.category().getId(), id)) {
            throw new Refused(I18n.t("products.dup.title"), I18n.t("products.dup.message", values.category().getName(), values.name()));
        }
        if (productDAO.codeTaken(values.code(), id)) {
            throw new Refused(I18n.t("products.val.code.title"), I18n.t("products.code.taken"));
        }
        Product product = existing != null ? existing : new Product();
        product.setName(values.name());
        product.setCategoryId(values.category().getId());
        product.setPrice(values.price());
        product.setCost(values.cost());
        product.setStock(values.stock());
        product.setReorderLevel(values.reorderLevel());
        product.setCode(values.code());
        product.setDescription(values.description());
        product.setImagePath(values.imagePath());
        product.setActive(values.active());
        if (existing == null) {
            productDAO.insert(product);
        } else {
            productDAO.update(product);
        }
        return product;
    }

    /** In how many order lines this product appears (above 0 means it cannot be deleted, only stopped). */
    public int orderReferences(int productId) {
        return productDAO.countOrderReferences(productId);
    }

    /** Stops selling the product (it stays in the history and can be switched on again). */
    public void deactivate(int productId) {
        Permissions.requireAdmin("stop selling a product");
        productDAO.setActive(productId, false);
    }

    /**
     * Deletes the product and, when no other product uses its picture, the picture file too.
     *
     * @return true if it was deleted; false if an order uses it (nothing was deleted, stop selling it instead)
     * @throws com.coffeeshop.coffeeshopmanagement.util.Permissions.PermissionDeniedException if the signed-in person is not an administrator
     */
    public boolean delete(Product product) {
        Permissions.requireAdmin("delete a product");
        // Checked here again, right before the delete: an order may have been paid since the screen asked.
        if (productDAO.countOrderReferences(product.getId()) > 0) {
            return false;
        }
        productDAO.delete(product.getId());
        if (productDAO.countUsingImage(product.getImagePath(), product.getId()) == 0) {
            ImageStorage.deleteQuietly(product.getImagePath());
        }
        return true;
    }

    public boolean existsByNameInCategory(String name, Integer categoryId, int excludeId) {
        return productDAO.existsByNameInCategory(name, categoryId, excludeId);
    }

    /** One transaction: all rows or none (TODO 2.69). */
    public void insertAll(java.util.List<Product> products) {
        productDAO.insertAll(products);
    }

    public boolean move(int productId, int delta) {
        return productDAO.move(productId, delta);
    }

    public int countUsingImage(String imagePath, int excludeId) {
        return productDAO.countUsingImage(imagePath, excludeId);
    }
}
