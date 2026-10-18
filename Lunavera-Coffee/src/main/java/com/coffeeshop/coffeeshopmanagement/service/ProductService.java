package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.ImageStorage;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;

/**
 * The rules about removing a product that do not belong on a screen (TODO 6.21): only an administrator may remove or stop
 * selling one; a product that appears in an order is never deleted (the history keeps pointing at it), it can only be
 * stopped; and a picture file is deleted with its product only when no other product uses it. {@code ProductDAO.delete} says
 * "callers must first confirm no order references this product": this class is that caller, so the rule is no longer only
 * a comment. It touches no screen and is meant to be called from a background thread (TODO 6.14).
 */
public class ProductService {

    private final ProductDAO productDAO;

    public ProductService() {
        this(new ProductDAO());
    }

    ProductService(ProductDAO productDAO) {
        this.productDAO = productDAO;
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
}
