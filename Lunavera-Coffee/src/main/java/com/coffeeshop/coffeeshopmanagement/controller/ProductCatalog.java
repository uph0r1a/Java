package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.config.SeedNames;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.service.PosService;
import com.coffeeshop.coffeeshopmanagement.service.SellableMenu;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * The menu side of the till (TODO 6.54): the category chips, the product tiles, the search box with its barcode-scanner Enter (TODO 4.42),
 * the "best sellers first" switch (TODO 5.10) and the list of products and categories they are drawn from. Moved out of
 * {@code OrderController} word for word. It knows nothing about the cart or the table: a tap on a tile goes to {@code onPick}, and the
 * three questions it needs from the till (is a payment being saved, which order is on screen) come in as suppliers.
 */
final class ProductCatalog {

    private static final Logger LOGGER = Logger.getLogger(ProductCatalog.class.getName());

    private final PosService posService;
    private final TextField productSearchField;
    private final HBox categoryTabBox;
    private final FlowPane productCatalogFlow;
    private final Consumer<Product> onPick;
    private final BooleanSupplier paymentInFlight;
    private final IntSupplier orderOnScreenId;

    private List<Product> allProducts = List.of();
    private List<Category> allCategories = List.of();
    private Integer selectedCategoryId = null;
    /** Units sold in the last 30 days per product, for "best sellers first" (TODO 5.10); empty until loaded. */
    private java.util.Map<Integer, Integer> recentSales = java.util.Map.of();
    private javafx.scene.control.ToggleButton bestSellersToggle;

    /**
     * @param onPick          called with the product when a tile is tapped or a scanned code matches it
     * @param paymentInFlight true while a payment is being saved (TODO 2.79): a scan is ignored then
     * @param orderOnScreenId the id of the order on screen, or 0 when there is none: its own lines are not "held" against it (TODO 3.1)
     */
    ProductCatalog(PosService posService, TextField productSearchField, HBox categoryTabBox, FlowPane productCatalogFlow,
                   Consumer<Product> onPick, BooleanSupplier paymentInFlight, IntSupplier orderOnScreenId) {
        this.posService = posService;
        this.productSearchField = productSearchField;
        this.categoryTabBox = categoryTabBox;
        this.productCatalogFlow = productCatalogFlow;
        this.onPick = onPick;
        this.paymentInFlight = paymentInFlight;
        this.orderOnScreenId = orderOnScreenId;
    }

    /** Hooks up the search box and adds the best-sellers switch; call once, when the screen is built. */
    void setUp() {
        if (productSearchField != null) {
            TextLengthLimiter.limit(productSearchField, TextLengthLimiter.SEARCH_MAX);
            productSearchField.textProperty().addListener((obs, o, n) -> render());
            productSearchField.setOnAction(e -> handleSearchEnter()); // a barcode scanner types the code and Enter (TODO 4.42)
        }
        setUpBestSellersToggle();
    }

    /** The products as last read, on sale or not. */
    List<Product> products() {
        return allProducts;
    }

    /** The product with this id among {@link #products()}, or null. */
    Product findById(Integer id) {
        if (id == null) {
            return null;
        }
        return allProducts.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
    }

    /** The products that can be sold now: active, in an active category (TODO 5.20). */
    List<Product> onSale() {
        return SellableMenu.filter(allProducts, allCategories);
    }

    void reload() {
        reload(false);
    }

    void reload(boolean quiet) {
        final int onScreen = orderOnScreenId.getAsInt(); // the order whose own lines are not "held" against it (TODO 3.1)
        Async.run(
                () -> {
                    PosService.Menu menu = posService.menu(onScreen);
                    CartLineFlow.reload();
                    return menu;
                },
                menu -> {
                    allCategories = menu.categories();
                    allProducts = menu.products();
                    renderCategoryChips();
                    render();
                },
                err -> {
                    if (quiet) {
                        LOGGER.log(Level.FINE, "Product refresh failed", err);
                    } else {
                        AlertUtil.error(I18n.t("common.error.title"), I18n.t("pos.load.menu", err.getMessage()));
                    }
                }
        );
    }

    private void renderCategoryChips() {
        if (categoryTabBox == null) return;
        categoryTabBox.getChildren().clear();

        Button allBtn = new Button(I18n.t("pos.category.all"));
        allBtn.getStyleClass().add("category-chip");
        if (selectedCategoryId == null) allBtn.getStyleClass().add("category-chip-active");
        allBtn.setOnAction(e -> {
            selectedCategoryId = null;
            renderCategoryChips();
            render();
        });
        categoryTabBox.getChildren().add(allBtn);

        for (Category cat : allCategories) {
            if (!cat.isActive()) continue;
            Button catBtn = new Button(SeedNames.category(cat.getName()));
            boolean isSelected = selectedCategoryId != null && selectedCategoryId == cat.getId();
            catBtn.getStyleClass().add("category-chip");
            if (isSelected) catBtn.getStyleClass().add("category-chip-active");
            catBtn.setOnAction(e -> {
                selectedCategoryId = cat.getId();
                renderCategoryChips();
                render();
            });
            categoryTabBox.getChildren().add(catBtn);
        }
    }

    private void setUpBestSellersToggle() {
        if (productSearchField == null || !(productSearchField.getParent() instanceof HBox bar) || bestSellersToggle != null) return;
        bestSellersToggle = new javafx.scene.control.ToggleButton(I18n.t("pos.bestsellers"));
        bestSellersToggle.setPrefHeight(32);
        bestSellersToggle.setSelected(com.coffeeshop.coffeeshopmanagement.util.UiSettings.getBoolean("pos.bestSellersFirst", true));
        bestSellersToggle.selectedProperty().addListener((o, was, now) -> {
            com.coffeeshop.coffeeshopmanagement.util.UiSettings.putBoolean("pos.bestSellersFirst", now);
            render();
        });
        bar.getChildren().add(bar.getChildren().indexOf(productSearchField), bestSellersToggle);
    }

    void loadRecentSales() {
        Async.run(
                posService::recentUnitsSold,
                sales -> {
                    recentSales = sales;
                    render();
                },
                err -> LOGGER.log(Level.FINE, "Could not load best sellers", err));
    }

    void render() {
        if (productCatalogFlow == null) return;
        productCatalogFlow.getChildren().clear();

        String keyword = productSearchField != null && productSearchField.getText() != null
                ? com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(productSearchField.getText()) : "";

        List<Product> filtered = com.coffeeshop.coffeeshopmanagement.service.SellableMenu.filter(allProducts, allCategories).stream()
                .filter(p -> selectedCategoryId == null || java.util.Objects.equals(p.getCategoryId(), selectedCategoryId))
                .filter(p -> keyword.isEmpty() || com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(p.getName()).contains(keyword)
                        || com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(SeedNames.product(p.getName())).contains(keyword)
                        || (p.getCode() != null && com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(p.getCode()).contains(keyword)))
                .collect(Collectors.toList());
        if (bestSellersToggle != null && bestSellersToggle.isSelected()) {
            // Most sold first; products never sold (or out of stock) keep their usual order after them. The sort is stable.
            filtered = filtered.stream()
                    .sorted(java.util.Comparator.comparingInt((Product p) -> p.getStock() <= 0 ? 1 : 0)
                            .thenComparing(java.util.Comparator.comparingInt((Product p) -> -recentSales.getOrDefault(p.getId(), 0))))
                    .collect(Collectors.toList());
        }

        for (Product product : filtered) {
            VBox card = new VBox(4);
            card.getStyleClass().add("product-card-item");

            Label nameLabel = new Label(SeedNames.product(product.getName()));
            nameLabel.getStyleClass().add("product-card-name");
            nameLabel.setMaxWidth(130);
            nameLabel.setWrapText(true);

            Label priceLabel = new Label(CurrencyUtil.format(product.getPrice()));
            priceLabel.getStyleClass().add("product-card-price");

            card.getChildren().addAll(nameLabel, priceLabel);

            if (product.getStock() <= 0) {
                Label outOfStock = new Label(I18n.t("stock.badge.out"));
                outOfStock.setStyle("-fx-text-fill: #b33939; " + com.coffeeshop.coffeeshopmanagement.util.TextSize.size(10) + " -fx-font-weight: bold;");
                card.getChildren().add(outOfStock);
                card.setOpacity(0.55);
            } else {
                if (product.isLowStock()) {
                    // Low-stock badge (TODO 5.6): the cashier sees it before promising the last cups.
                    Label low = new Label(I18n.t("pos.stock.low", product.getStock()));
                    low.setStyle("-fx-text-fill: #b26a00; " + com.coffeeshop.coffeeshopmanagement.util.TextSize.size(10) + " -fx-font-weight: bold;");
                    card.getChildren().add(low);
                }
                card.setOnMouseClicked(e -> onPick.accept(product));
            }

            productCatalogFlow.getChildren().add(card);
        }
    }

    /**
     * Enter in the menu search (TODO 4.42). A USB barcode scanner types the digits and Enter like a keyboard, so this is the whole
     * scanner support: when the typed text is exactly the code of a product on sale, that product goes into the order at once and the
     * box is emptied for the next scan. A code that no product has is said so (the cashier would otherwise see an empty menu and not
     * know whether the scan failed); a code of a product that is stopped says that instead. Text that is only part of a name, which
     * still shows products, is left alone: that person is searching, not scanning.
     */
    private void handleSearchEnter() {
        if (paymentInFlight.getAsBoolean() || productSearchField == null) return;
        String typed = productSearchField.getText();
        if (!com.coffeeshop.coffeeshopmanagement.service.ProductCode.looksLikeCode(typed)) return;
        String code = com.coffeeshop.coffeeshopmanagement.service.ProductCode.clean(typed);
        Product onSale = com.coffeeshop.coffeeshopmanagement.service.SellableMenu.filter(allProducts, allCategories).stream()
                .filter(p -> code.equals(p.getCode())).findFirst().orElse(null);
        if (onSale != null) {
            productSearchField.clear();
            onPick.accept(onSale);
            productSearchField.requestFocus();
            return;
        }
        boolean stopped = allProducts.stream().anyMatch(p -> code.equals(p.getCode()));
        if (stopped) {
            AlertUtil.warning(I18n.t("pos.code.title"), I18n.t("pos.code.stopped", code));
        } else if (productCatalogFlow != null && productCatalogFlow.getChildren().isEmpty()) {
            AlertUtil.warning(I18n.t("pos.code.title"), I18n.t("pos.code.unknown", code));
        } else {
            return;
        }
        com.coffeeshop.coffeeshopmanagement.util.AlertPlayer.play(com.coffeeshop.coffeeshopmanagement.util.AlertSounds.Cue.CODE_NOT_FOUND); // TODO 5.47
        productSearchField.selectAll(); // the next scan replaces it
    }

    /** Reads the products for sale again (after a payment or a change of order); the categories stay as they are. */
    void refreshProducts() {
        final int onScreen = orderOnScreenId.getAsInt();
        Async.run(() -> {
            List<Product> products = posService.productsForSale(onScreen);
            CartLineFlow.reload();
            return products;
        }, products -> allProducts = products,
                error -> AlertUtil.error(I18n.t("common.error.title"), I18n.t("pos.load.products", error.getMessage()))); // TODO 6.14
    }
}
