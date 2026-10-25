package com.coffeeshop.coffeeshopmanagement.config;

import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.util.HashMap;
import java.util.Map;

/**
 * English names for the starter menu (TODO 1.7). The database keeps the Vietnamese name the starter menu was seeded with
 * (see {@link MenuSeedData}); this class gives the English name to show instead while the language in use is English.
 * Only a name that is still exactly a starter name is translated: a product the shop added or renamed is shown as typed.
 * It is display only: nothing here is ever written to the database, searched in SQL or used to compare names.
 *
 * <p>Used by the POS, the dashboard, the report and the receipt. The product and category management screens keep showing the
 * stored names, because they are where the stored names are edited.
 */
public final class SeedNames {

    private static final Map<String, String> PRODUCTS = new HashMap<>();
    private static final Map<String, String> CATEGORIES = new HashMap<>();

    static {
        PRODUCTS.put("Berry Cloud — Cheesecake dâu", "Berry Cloud — Strawberry cheesecake");
        PRODUCTS.put("Brownie", "Brownie");
        PRODUCTS.put("Choco Croissant", "Choco Croissant");
        PRODUCTS.put("Chocolate Dream — Chocolate mousse", "Chocolate Dream — Chocolate mousse");
        PRODUCTS.put("Croissant", "Croissant");
        PRODUCTS.put("Golden Honey Cake — Bánh mật ong & hạnh nhân", "Golden Honey Cake — Honey & almond cake");
        PRODUCTS.put("Lunavera Tiramisu — Tiramisu cà phê", "Lunavera Tiramisu — Coffee tiramisu");
        PRODUCTS.put("Matcha Bliss — Matcha cheesecake", "Matcha Bliss — Matcha cheesecake");
        PRODUCTS.put("Matcha Croissant", "Matcha Croissant");
        PRODUCTS.put("Red Velvet Cake", "Red Velvet Cake");
        PRODUCTS.put("Bạc sỉu", "Bac siu (white coffee)");
        PRODUCTS.put("Cafe dừa", "Coconut coffee");
        PRODUCTS.put("Cafe kem phô mai", "Cheese cream coffee");
        PRODUCTS.put("Cafe nâu", "Brown coffee");
        PRODUCTS.put("Cafe sữa", "Coffee with milk");
        PRODUCTS.put("Cafe đen", "Black coffee");
        PRODUCTS.put("Lunavera Fresh - Táo & Dứa & Chanh", "Lunavera Fresh - Apple & Pineapple & Lemon");
        PRODUCTS.put("Nước ép cam", "Orange juice");
        PRODUCTS.put("Nước ép chanh dây", "Passion fruit juice");
        PRODUCTS.put("Nước ép dứa", "Pineapple juice");
        PRODUCTS.put("Nước ép táo", "Apple juice");
        PRODUCTS.put("Ruby Fresh - Dưa hấu & Dâu", "Ruby Fresh - Watermelon & Strawberry");
        PRODUCTS.put("Sunrise Orange - Cam & Dứa", "Sunrise Orange - Orange & Pineapple");
        PRODUCTS.put("Tropical Glow - Dứa & Chanh dây", "Tropical Glow - Pineapple & Passion fruit");
        PRODUCTS.put("Berry Sunset — Trà dâu hibiscus", "Berry Sunset — Strawberry hibiscus tea");
        PRODUCTS.put("Lunavera Tea - Trà nhài cam vàng", "Lunavera Tea - Jasmine golden orange tea");
        PRODUCTS.put("Lychee Garden — Trà vải hoa hồng", "Lychee Garden — Lychee rose tea");
        PRODUCTS.put("Peach Blossom — Trà đào cam sả", "Peach Blossom — Peach orange lemongrass tea");
        PRODUCTS.put("Tropical Breeze — Trà dứa chanh dây", "Tropical Breeze — Pineapple passion fruit tea");
        PRODUCTS.put("Trà chanh", "Lemon tea");
        PRODUCTS.put("Trà dâu", "Strawberry tea");
        PRODUCTS.put("Trà vải", "Lychee tea");
        PRODUCTS.put("Trà đào", "Peach tea");

        CATEGORIES.put("Bánh ngọt", "Cakes & pastries");
        CATEGORIES.put("Cà phê", "Coffee");
        CATEGORIES.put("Nước ép & Sinh tố", "Juices & Smoothies");
        CATEGORIES.put("Trà", "Tea");
    }

    private SeedNames() {
    }

    /** The product name to show in the language in use. */
    public static String product(String storedName) {
        return product(I18n.current(), storedName);
    }

    /** The product name to show in the given language ({@code vi} shows the stored name as it is). */
    public static String product(String language, String storedName) {
        return translate(PRODUCTS, language, storedName);
    }

    /** The category name to show in the language in use. */
    public static String category(String storedName) {
        return category(I18n.current(), storedName);
    }

    public static String category(String language, String storedName) {
        return translate(CATEGORIES, language, storedName);
    }

    private static String translate(Map<String, String> names, String language, String storedName) {
        if (storedName == null || !I18n.EN.equals(language)) {
            return storedName;
        }
        String english = names.get(storedName.trim());
        return english != null ? english : storedName;
    }

    /** For the test that every starter name has an English one. */
    static Map<String, String> products() {
        return PRODUCTS;
    }

    static Map<String, String> categories() {
        return CATEGORIES;
    }
}
