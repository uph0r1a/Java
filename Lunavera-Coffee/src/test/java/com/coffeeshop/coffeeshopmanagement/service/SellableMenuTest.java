package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SellableMenuTest {
    private static Product product(String name, Integer category, boolean active) {
        Product p = new Product();
        p.setName(name);
        p.setCategoryId(category);
        p.setActive(active);
        return p;
    }

    @Test
    public void productsOfAHiddenCategoryAreNotSold() {
        List<Category> cats = List.of(new Category(1, "Quanh năm", "", true), new Category(2, "Mùa hè", "", false));
        List<Product> out = SellableMenu.filter(List.of(
                product("Cà phê", 1, true), product("Trà vải", 2, true),
                product("Đã ngừng", 1, false), product("Không danh mục", null, true)), cats);
        assertEquals(List.of("Cà phê", "Không danh mục"), out.stream().map(Product::getName).toList());
    }

    @Test
    public void unhidingTheCategoryBringsThemBack() {
        List<Category> cats = List.of(new Category(2, "Mùa hè", "", true));
        assertEquals(1, SellableMenu.filter(List.of(product("Trà vải", 2, true)), cats).size());
    }
}
