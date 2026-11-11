package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.ProductOptionDAO;
import com.coffeeshop.coffeeshopmanagement.model.ProductOption;

import java.util.List;
import java.util.Map;

/**
 * What the screens ask for about a product's options, such as size and sugar (TODO 6.21): the options of one product, every active
 * option for the till, saving a product's options and copying them to its category. {@link ProductOptionDAO} holds the permission and
 * the checks. {@code OptionsPane} and {@code CartLineFlow} hold no DAO of their own.
 */
public class ProductOptionService {

    private final ProductOptionDAO options;

    public ProductOptionService() {
        this(new ProductOptionDAO());
    }

    ProductOptionService(ProductOptionDAO options) {
        this.options = options;
    }

    public List<ProductOption> findByProduct(int productId) {
        return options.findByProduct(productId);
    }

    public Map<Integer, List<ProductOption>> findAllActive() {
        return options.findAllActive();
    }

    public void save(int productId, List<ProductOption> toSave) {
        options.save(productId, toSave);
    }

    /** Copies this product's options to the other products of its category; how many were changed. */
    public int copyToCategory(int productId) {
        return options.copyToCategory(productId);
    }
}
