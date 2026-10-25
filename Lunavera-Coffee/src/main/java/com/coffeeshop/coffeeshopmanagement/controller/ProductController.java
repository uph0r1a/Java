package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.Limits;
import java.util.Locale;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.service.ProductService;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.PagedTable;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Backs product management (quanlysanpham.fxml): the product table, the category tabs and list, filters, the add/edit
 * dialog, and CSV import/export. Split out of the old {@code AdminController} (TODO 6.4).
 */
public class ProductController extends AdminScreen {

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final ProductService productService = new ProductService();
    private List<Category> allCategories = List.of();
    // Loaded once per reload via CategoryDAO.countProductsByCategory() (a single grouped
    // query) instead of calling countProductsInCategory(id) per row/per keystroke - see
    // progress.md performance notes.
    private Map<Integer, Integer> categoryProductCounts = Map.of();
    @FXML private Button addProductButton;

    // ---------------------------------------------------------------- Product management
    @FXML private HBox productCategoryTabsBox;
    @FXML private VBox categoryListBox;
    private Integer selectedProductCategoryId = null;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> categoryFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<Product> productTable;
    @FXML private TableColumn<Product, Void> imageColumn;
    @FXML private TableColumn<Product, String> nameColumn;
    @FXML private TableColumn<Product, String> categoryColumn;
    @FXML private TableColumn<Product, String> sellingPriceColumn;
    @FXML private TableColumn<Product, Number> stockColumn;
    @FXML private TableColumn<Product, String> productStatusColumn;
    @FXML private TableColumn<Product, Void> actionColumn;
    @FXML private Label totalProductLabel;
    @FXML private HBox productPagerBox;

    private PagedTable<Product> productPaged;
    private List<Product> allProducts = List.of();
    private List<Category> allActiveCategories = List.of();

    private boolean syncingCategoryFilter = false;

    @FXML
    private void initialize() {
        if (blockNonAdmin(productTable)) {
            return;
        }
        initProductScreen();
    }

    // =================================================================== Order management
    // Moved to a dedicated OrderController - see progress.md, Session 7. quanlydonhang.fxml's
    // fx:controller no longer points here, so these stub handlers are gone rather than left
    // as dead code.

    // =================================================================== Product management (quanlysanpham.fxml)

    /** "Nhập CSV" and "Xuất CSV" next to "Thêm sản phẩm" (TODO 5.8). */
    private void addProductCsvButtons() {
        if (addProductButton == null || !(addProductButton.getParent() instanceof javafx.scene.layout.HBox bar)) return;
        Button importButton = new Button(I18n.t("products.import"));
        importButton.setOnAction(e -> importProductsCsv());
        Button exportButton = new Button(I18n.t("common.exportcsv"));
        exportButton.setOnAction(e -> exportProductsCsv());
        int at = bar.getChildren().indexOf(addProductButton);
        bar.getChildren().addAll(at, List.of(importButton, exportButton));
    }

    private void exportProductsCsv() {
        if (!requireAdmin()) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("products.export.title"));
        chooser.setInitialFileName("san-pham-" + java.time.LocalDate.now() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        java.io.File file = chooser.showSaveDialog(productTable.getScene().getWindow());
        if (file == null) return;
        Runnable pointer = com.coffeeshop.coffeeshopmanagement.util.BusyIndicator.waitCursor(productTable); // TODO 5.16
        Async.run(
                () -> {
                    List<Product> all = productDAO.findAll();
                    com.coffeeshop.coffeeshopmanagement.util.ExportCsv.write(file.toPath(),
                            com.coffeeshop.coffeeshopmanagement.service.ProductCsv.export(all));
                    return all.size();
                },
                count -> {
                    pointer.run();
                    AlertUtil.info(I18n.t("products.export.title2"), I18n.t("products.export.done", count, file.getAbsolutePath()));
                },
                error -> {
                    pointer.run();
                    AlertUtil.error(I18n.t("common.file.savefail"), error.getMessage());
                });
    }

    /** Adds the products of a CSV file; existing products are never changed. The person sees the outcome before anything is saved. */
    private void importProductsCsv() {
        if (!requireAdmin()) return;
        if (allActiveCategories.isEmpty()) {
            AlertUtil.warning(I18n.t("categories.none.title"), I18n.t("products.import.nocategory"));
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18n.t("products.import.choose"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        java.io.File file = chooser.showOpenDialog(productTable.getScene().getWindow());
        if (file == null) return;
        if (file.length() > 5L * 1024 * 1024) {
            AlertUtil.warning(I18n.t("products.import.toobig.title"), I18n.t("products.import.toobig.message"));
            return;
        }
        com.coffeeshop.coffeeshopmanagement.service.ProductCsv.Result result;
        try {
            String text = com.coffeeshop.coffeeshopmanagement.service.ProductCsv.decode(java.nio.file.Files.readAllBytes(file.toPath()));
            result = com.coffeeshop.coffeeshopmanagement.service.ProductCsv.parse(text, allActiveCategories,
                    (name, categoryId) -> productDAO.existsByNameInCategory(name, categoryId, 0));
        } catch (java.io.IOException | RuntimeException e) {
            AlertUtil.error(I18n.t("products.import.unreadable.title"), I18n.t("products.import.unreadable.message", (e instanceof java.io.IOException ? "" : e.getMessage())));
            return;
        }
        String problems = result.problems().isEmpty() ? ""
                : "\n\n" + I18n.t("products.import.skipped", result.problems().size(), String.join("\n", result.problems().stream().limit(12).toList()), (result.problems().size() > 12 ? "\n" + I18n.t("products.import.more", (result.problems().size() - 12)) : ""));
        String warnings = result.warnings().isEmpty() ? ""
                : "\n\n" + I18n.t("products.import.warned", result.warnings().size(),
                        String.join("\n", result.warnings().stream().limit(8).toList()),
                        (result.warnings().size() > 8 ? "\n" + I18n.t("products.import.more", (result.warnings().size() - 8)) : ""));
        if (result.products().isEmpty()) {
            AlertUtil.warning(I18n.t("products.import.nothing.title"), I18n.t("products.import.nothing.message", problems));
            return;
        }
        if (!AlertUtil.confirm(I18n.t("products.import.title"),
                I18n.t("products.import.confirm", result.products().size(), problems + warnings))) {
            return;
        }
        var toAdd = result.products();
        Async.run(
                () -> {
                    com.coffeeshop.coffeeshopmanagement.util.Permissions.requireAdmin("import products");
                    productDAO.insertAll(toAdd); // one transaction: all rows or none (TODO 2.69)
                    com.coffeeshop.coffeeshopmanagement.util.SecurityLog.event("imported " + toAdd.size() + " products from CSV");
                    return toAdd.size();
                },
                count -> {
                    AlertUtil.info(I18n.t("products.import.title"), I18n.t("products.import.done", count));
                    reloadProducts();
                },
                error -> {
                    AlertUtil.error(I18n.t("products.import.fail"), error.getMessage());
                    reloadProducts();
                },
                productTable);
    }

    private void initProductScreen() {
        addProductCsvButtons();
        // Default 20 rows (was a fixed 8); the page-size box offers 10 / 20 / 50 / 100 / Tất cả.
        productPaged = new PagedTable<>(productTable, productPagerBox, 20)
                .unsortable(imageColumn, actionColumn)
                .sortKey(sellingPriceColumn, Product::getPrice); // by amount, not by "25.000đ" text
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        categoryColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCategoryName() != null ? data.getValue().getCategoryName() : "-"));
        sellingPriceColumn.setCellValueFactory(data -> new SimpleStringProperty(CurrencyUtil.format(data.getValue().getPrice())));
        stockColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().getStock()));
        stockColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Number value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setStyle(null);
                    return;
                }
                setText(value.toString());
                // The product's own reorder level (TODO 4.19), or the shop default: the same rule as the dashboard
                // (StockLevel), one definition of "low".
                Product row = getTableRow() == null ? null : getTableRow().getItem();
                boolean low = row != null ? row.isLowStock()
                        : com.coffeeshop.coffeeshopmanagement.service.StockLevel.isLow(value.intValue(), null);
                setStyle(low ? "-fx-text-fill: #c0392b; -fx-font-weight: bold;" : null);
            }
        });
        productStatusColumn.setCellValueFactory(data ->
                new SimpleStringProperty(!data.getValue().isActive() ? I18n.t("products.status.stopped")
                        : isCategoryHidden(data.getValue()) ? I18n.t("products.status.categoryhidden") : I18n.t("products.status.selling")));
        actionColumn.setCellFactory(actionColumnFactory(this::openEditProductDialog, this::deleteProduct));
        imageColumn.setCellFactory(column -> new TableCell<>() {
            private final javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView();
            {
                imageView.setFitWidth(44);
                imageView.setFitHeight(44);
                imageView.setPreserveRatio(true);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                Product product = (!empty && getTableRow() != null) ? (Product) getTableRow().getItem() : null;
                java.io.File file = product != null
                        ? com.coffeeshop.coffeeshopmanagement.util.ImageStorage.resolve(product.getImagePath())
                        : null;
                if (file == null) {
                    setGraphic(null);
                    return;
                }
                imageView.setImage(com.coffeeshop.coffeeshopmanagement.util.Thumbnails.of(file, 44));
                setGraphic(imageView);
            }
        });

        categoryFilter.getItems().setAll(I18n.t("products.filter.allcategories"));
        categoryFilter.getSelectionModel().selectFirst();
        statusFilter.getItems().setAll(I18n.t("history.status.all"), I18n.t("products.status.selling"), I18n.t("products.status.stopped"),
                I18n.t("products.status.reorder"));
        statusFilter.getSelectionModel().selectFirst();
        TextLengthLimiter.limit(searchField, TextLengthLimiter.SEARCH_MAX);
        searchField.textProperty().addListener((obs, old, value) -> applyProductFilter());

        reloadProducts();
    }

    private boolean isCategoryHidden(Product p) {
        return p.getCategoryId() != null && allCategories.stream()
                .anyMatch(c -> c.getId() == p.getCategoryId() && !c.isActive());
    }

    private void reloadProducts() {
        reloadProducts(null);
    }

    /** Reloads the list; when {@code selectId} is given and on the page shown, that product is selected again (after an arrow). */
    private void reloadProducts(Integer selectId) {
        Async.run(
                () -> {
                    List<Category> categories = categoryDAO.findAll();
                    List<Product> products = productDAO.findAll();
                    Map<Integer, Integer> counts = categoryDAO.countProductsByCategory();
                    return new Object[]{categories, products, counts};
                },
                result -> {
                    allCategories = castList(result[0]);
                    allActiveCategories = allCategories.stream()
                            .filter(Category::isActive).collect(Collectors.toList());
                    allProducts = (List<Product>) result[1];
                    categoryProductCounts = castCountMap(result[2]);

                    if (categoryFilter != null) {
                        syncingCategoryFilter = true;
                        try {
                            categoryFilter.getItems().setAll(I18n.t("products.filter.allcategories"));
                            allActiveCategories.forEach(c -> categoryFilter.getItems().add(c.getName()));
                            if (selectedProductCategoryId != null
                                    && allActiveCategories.stream().noneMatch(c -> c.getId() == selectedProductCategoryId)) {
                                selectedProductCategoryId = null; // selected category was deleted/deactivated
                            }
                            categoryFilter.setValue(I18n.t("products.filter.allcategories"));
                        } finally { syncingCategoryFilter = false; }
                        syncCategoryFilterWithSelection();
                    }

                    renderProductCategoryTabs();
                    renderProductCategoryList();
                    applyProductFilter();
                    if (selectId != null) {
                        for (Product product : productTable.getItems()) {
                            if (product.getId() == selectId) {
                                productTable.getSelectionModel().select(product);
                                break;
                            }
                        }
                    }
                },
                error -> AlertUtil.error(I18n.t("dashboard.load.title"), I18n.t("pos.load.products", error.getMessage()))
        );
    }

    private void renderProductCategoryTabs() {
        if (productCategoryTabsBox == null) return;
        productCategoryTabsBox.getChildren().clear();

        Button allBtn = new Button(I18n.t("products.sidebar.all"));
        allBtn.getStyleClass().add("category-button");
        if (selectedProductCategoryId == null) {
            allBtn.getStyleClass().add("category-button-active");
        }
        allBtn.setOnAction(e -> setProductCategoryTab(null));
        productCategoryTabsBox.getChildren().add(allBtn);

        for (Category cat : allActiveCategories) {
            String icon = getCategoryIcon(cat.getName());
            Button catBtn = new Button(icon + "   " + cat.getName());
            catBtn.getStyleClass().add("category-button");
            if (selectedProductCategoryId != null && selectedProductCategoryId.equals(cat.getId())) {
                catBtn.getStyleClass().add("category-button-active");
            }
            catBtn.setOnAction(e -> setProductCategoryTab(cat.getId()));
            productCategoryTabsBox.getChildren().add(catBtn);
        }
    }

    private void renderProductCategoryList() {
        if (categoryListBox == null) return;
        categoryListBox.getChildren().clear();

        Button allBtn = createCategoryListRow(null, "▦", I18n.t("pos.category.all"), allProducts.size());
        categoryListBox.getChildren().add(allBtn);

        for (Category cat : allActiveCategories) {
            int count = categoryProductCounts.getOrDefault(cat.getId(), 0);
            String icon = getCategoryIcon(cat.getName());
            Button catBtn = createCategoryListRow(cat.getId(), icon, cat.getName(), count);
            categoryListBox.getChildren().add(catBtn);
        }
    }

    private Button createCategoryListRow(Integer categoryId, String icon, String name, int count) {
        Button btn = new Button();
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(36);
        btn.getStyleClass().add("category-list-button");
        boolean isSelected = (selectedProductCategoryId == null && categoryId == null) ||
                             (selectedProductCategoryId != null && selectedProductCategoryId.equals(categoryId));
        if (isSelected) {
            btn.getStyleClass().add("category-list-active");
        }

        Label iconLbl = new Label(icon);
        Label nameLbl = new Label(name);
        nameLbl.setMaxWidth(110);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label countLbl = new Label(String.valueOf(count));
        countLbl.getStyleClass().add("category-count");

        HBox graphic = new HBox(8, iconLbl, nameLbl, spacer, countLbl);
        graphic.setAlignment(Pos.CENTER_LEFT);
        btn.setGraphic(graphic);

        btn.setOnAction(e -> setProductCategoryTab(categoryId));
        return btn;
    }

    private String getCategoryIcon(String name) {
        if (name == null) return "●";
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("phê") || lower.contains("coffee")) return "☕";
        if (lower.contains("bánh") || lower.contains("cake")) return "♨";
        if (lower.contains("trà") || lower.contains("tea")) return "🍵";
        if (lower.contains("nước") || lower.contains("juice") || lower.contains("sinh tố")) return "▣";
        return "●";
    }

    private void setProductCategoryTab(Integer categoryId) {
        selectedProductCategoryId = categoryId;
        if (productPaged != null) productPaged.firstPage();
        syncCategoryFilterWithSelection();
        renderProductCategoryTabs();
        renderProductCategoryList();
        applyProductFilter();
    }

    /** Keep the "Danh mục" combo in step with the sidebar/tab selection ("Tất cả" -> all categories). */
    private void syncCategoryFilterWithSelection() {
        if (categoryFilter == null) return;
        String target = I18n.t("products.filter.allcategories");
        if (selectedProductCategoryId != null) {
            for (Category c : allActiveCategories) {
                if (c.getId() == selectedProductCategoryId) { target = c.getName(); break; }
            }
        }
        if (!target.equals(categoryFilter.getValue())) {
            syncingCategoryFilter = true;
            try { categoryFilter.setValue(target); } finally { syncingCategoryFilter = false; }
        }
    }

    private void applyProductFilter() {
        String keyword = searchField != null && searchField.getText() != null ? com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(searchField.getText()) : "";
        String categoryChoice = categoryFilter != null ? categoryFilter.getValue() : null;
        String statusChoice = statusFilter != null ? statusFilter.getValue() : null;

        List<Product> filtered = allProducts.stream()
                .filter(p -> selectedProductCategoryId == null || (p.getCategoryId() != null && p.getCategoryId().equals(selectedProductCategoryId)))
                .filter(p -> keyword.isEmpty() || com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(p.getName()).contains(keyword))
                .filter(p -> categoryChoice == null || categoryChoice.startsWith(I18n.t("pos.category.all"))
                        || (p.getCategoryName() != null && categoryChoice.equals(p.getCategoryName())))
                .filter(p -> statusChoice == null || statusChoice.startsWith(I18n.t("pos.category.all"))
                        || (statusChoice.equals(I18n.t("products.status.reorder"))
                                ? p.isActive() && p.isLowStock() // "need to reorder": on sale and at or below its level (TODO 4.19)
                                : p.isActive() == statusChoice.equals(I18n.t("products.status.selling"))))
                .collect(Collectors.toList());

        if (productPaged != null) {
            productPaged.setItems(filtered);
        }
        if (totalProductLabel != null) {
            totalProductLabel.setText(I18n.t("products.total", filtered.size()));
        }
    }

    /** Picking a category in the combo also moves the sidebar/tab selection, so they never disagree. */
    @FXML public void handleCategoryFilter() {
        if (syncingCategoryFilter || categoryFilter == null) return;
        String choice = categoryFilter.getValue();
        if (choice == null) return;
        Integer id = null;
        if (!choice.startsWith(I18n.t("pos.category.all"))) {
            for (Category c : allActiveCategories) {
                if (c.getName().equals(choice)) { id = c.getId(); break; }
            }
        }
        if (java.util.Objects.equals(id, selectedProductCategoryId)) {
            applyProductFilter();
            return;
        }
        setProductCategoryTab(id);
    }

    @FXML public void handleStatusFilter() { applyProductFilter(); }

    @FXML
    public void handleAddProduct() {
        if (allActiveCategories.isEmpty()) {
            AlertUtil.warning(I18n.t("categories.none.title"),
                    I18n.t("products.nocategory.message"));
            return;
        }
        openProductDialog(null);
    }

    @FXML
    public void handleMoveProductUp() {
        moveSelectedProduct(-1);
    }

    @FXML
    public void handleMoveProductDown() {
        moveSelectedProduct(1);
    }

    /**
     * The arrows change the place of a product among the others of its category on the sales screen (TODO 5.31). They need a selected
     * row and the stored order shown (no column sort), because only then does a press visibly move the row.
     */
    private void moveSelectedProduct(int delta) {
        Product selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertUtil.info(I18n.t("sort.title"), I18n.t("sort.select.product"));
            return;
        }
        if (!productTable.getSortOrder().isEmpty()) {
            AlertUtil.info(I18n.t("sort.title"), I18n.t("sort.clear"));
            return;
        }
        int id = selected.getId();
        Async.run(
                () -> productDAO.move(id, delta),
                moved -> reloadProducts(id),
                error -> AlertUtil.error(I18n.t("common.error.title"), error.getMessage()));
    }

    private void openEditProductDialog(Product product) {
        openProductDialog(product);
    }

    private void openProductDialog(Product existing) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? I18n.t("products.dialog.add") : I18n.t("products.dialog.edit"));
        AlertUtil.configure(dialog);
        ButtonType saveButtonType = new ButtonType(I18n.t("common.save"), ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        AlertUtil.setDefaultButton(dialog, saveButtonType);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        TextField nameField = new TextField(existing != null ? existing.getName() : "");
        TextLengthLimiter.limit(nameField, TextLengthLimiter.NAME_MAX);
        ComboBox<Category> categoryBox = new ComboBox<>();
        categoryBox.getItems().setAll(allActiveCategories);
        if (existing != null && existing.getCategoryId() != null) {
            allActiveCategories.stream().filter(c -> c.getId() == existing.getCategoryId()).findFirst()
                    .ifPresent(categoryBox::setValue);
        }
        TextField priceField = new TextField(existing != null ? existing.getPrice().toPlainString() : "");
        TextField costField = new TextField(existing != null && existing.getCost() != null ? existing.getCost().toPlainString() : "");
        TextField stockField = new TextField(existing != null ? String.valueOf(existing.getStock()) : "0");
        TextLengthLimiter.limit(priceField, TextLengthLimiter.NUMBER_MAX);
        TextLengthLimiter.limit(costField, TextLengthLimiter.NUMBER_MAX);
        TextLengthLimiter.limit(stockField, 9);
        TextField reorderField = new TextField(existing != null && existing.getReorderLevel() != null
                ? String.valueOf(existing.getReorderLevel()) : "");
        reorderField.setPromptText(I18n.t("products.reorder.prompt", com.coffeeshop.coffeeshopmanagement.service.StockLevel.DEFAULT));
        TextLengthLimiter.limit(reorderField, 7);
        TextArea descriptionField = new TextArea(existing != null ? existing.getDescription() : "");
        TextLengthLimiter.limit(descriptionField, TextLengthLimiter.DESCRIPTION_MAX);
        descriptionField.setPrefRowCount(3);
        CheckBox activeBox = new CheckBox(I18n.t("products.status.selling"));
        activeBox.setSelected(existing == null || existing.isActive());

        Label imagePathLabel = new Label(existing != null && existing.getImagePath() != null
                ? existing.getImagePath() : I18n.t("products.image.none"));
        String[] pickedImagePath = {existing != null ? existing.getImagePath() : null};
        // Pictures are only removed once the product is saved (TODO 2.21): the one it had before, and any copies
        // made while picking. Copies are removed again when the dialog is cancelled.
        final String originalImagePath = existing != null ? existing.getImagePath() : null;
        final java.util.List<String> copiedImages = new java.util.ArrayList<>();
        final boolean[] productSaved = {false};
        Button pickImageButton = new Button(I18n.t("common.pickimage"));
        pickImageButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(I18n.t("products.image.choose"));
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter(I18n.t("products.image.filter"), "*.png", "*.jpg", "*.jpeg", "*.jfif", "*.jpe", "*.gif", "*.bmp"));
            java.io.File picked = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (picked == null) return;
            // Looking at the picture and copying it take time (a big photo, a slow USB stick): not on the screen thread (TODO 3.43).
            pickImageButton.setDisable(true);
            Async.run(
                    () -> {
                        String problem = com.coffeeshop.coffeeshopmanagement.util.ImageStorage.pictureProblem(picked);
                        if (problem != null) return new String[]{problem, null};
                        // Stores a bare file name now, not an absolute path (TODO.md item 10a) - a
                        // database copied to another machine/user still finds its product images.
                        return new String[]{null, com.coffeeshop.coffeeshopmanagement.util.ImageStorage.storeNewFile(picked)};
                    },
                    result -> {
                        pickImageButton.setDisable(false);
                        if (result[0] != null) {
                            AlertUtil.warning(I18n.t("products.image.unusable"), result[0]);
                            return;
                        }
                        if (!dialog.isShowing()) { // the dialog was closed while the picture was being copied
                            com.coffeeshop.coffeeshopmanagement.util.ImageStorage.deleteQuietly(result[1]);
                            return;
                        }
                        pickedImagePath[0] = result[1];
                        copiedImages.add(result[1]);
                        // The stored name is a random string now; show the name of the file that was picked.
                        imagePathLabel.setText(picked.getName());
                    },
                    error -> {
                        pickImageButton.setDisable(false);
                        AlertUtil.error(I18n.t("products.image.savefail"), I18n.t("products.image.copyfail", error.getMessage()));
                    });
        });
        HBox imageRow = new HBox(10, pickImageButton, imagePathLabel);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        int row = 0;
        grid.addRow(row++, new Label(I18n.t("products.label.name")), nameField);
        grid.addRow(row++, new Label(I18n.t("products.label.category")), categoryBox);
        grid.addRow(row++, new Label(I18n.t("products.label.price")), priceField);
        grid.addRow(row++, new Label(I18n.t("products.label.cost")), costField);
        grid.addRow(row++, new Label(I18n.t("products.label.stock")), stockField);
        grid.addRow(row++, new Label(I18n.t("products.label.reorder")), reorderField);
        grid.addRow(row++, new Label(I18n.t("common.label.description")), descriptionField);
        grid.addRow(row++, new Label(I18n.t("products.label.image")), imageRow);
        grid.addRow(row, new Label(""), activeBox);
        dialog.getDialogPane().setContent(grid);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (ValidationUtil.isBlank(nameField.getText())) {
                AlertUtil.warning(I18n.t("common.missing.title"), I18n.t("products.val.name"));
                event.consume();
                return;
            }
            if (categoryBox.getValue() == null) {
                AlertUtil.warning(I18n.t("common.missing.title"), I18n.t("products.val.category"));
                event.consume();
                return;
            }
            BigDecimal price;
            try {
                price = MoneyParser.parseDong(priceField.getText());
                if (price == null || price.signum() <= 0) throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) {
                AlertUtil.warning(I18n.t("products.val.price.title"), I18n.t("products.val.price.message"));
                event.consume();
                return;
            }
            if (Limits.priceTooBig(price)) { // a price with three extra zeros is an easy slip (TODO 3.59)
                AlertUtil.warning(I18n.t("products.val.price.title"), I18n.t("products.val.price.max", Limits.maxPriceText()));
                event.consume();
                return;
            }
            BigDecimal cost = null;
            if (!ValidationUtil.isBlank(costField.getText())) {
                try {
                    cost = MoneyParser.parseDong(costField.getText());
                    if (cost.signum() < 0) throw new IllegalArgumentException("negative");
                } catch (IllegalArgumentException ex) {
                    AlertUtil.warning(I18n.t("products.val.cost.title"), I18n.t("products.val.cost.message"));
                    event.consume();
                    return;
                }
                if (Limits.priceTooBig(cost)) {
                    AlertUtil.warning(I18n.t("products.val.cost.title"), I18n.t("products.val.price.max", Limits.maxPriceText()));
                    event.consume();
                    return;
                }
                if (cost.compareTo(price) > 0
                        && !AlertUtil.confirm(I18n.t("products.val.loss.title"),
                        I18n.t("products.val.loss.message"))) {
                    event.consume();
                    return;
                }
            }
            int stock;
            try {
                stock = Integer.parseInt(stockField.getText().trim());
                if (stock < 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                AlertUtil.warning(I18n.t("products.val.stock.title"), I18n.t("products.val.stock.message"));
                event.consume();
                return;
            }
            if (stock > Limits.MAX_STOCK) {
                AlertUtil.warning(I18n.t("products.val.stock.title"), I18n.t("products.val.stock.max", Limits.MAX_STOCK));
                event.consume();
                return;
            }
            int oldStock = existing != null ? existing.getStock() : 0;
            if (com.coffeeshop.coffeeshopmanagement.service.StockPolicy.looksTooBig(oldStock, stock)
                    && !AlertUtil.confirm(I18n.t("products.stock.jump.title"),
                    I18n.t("products.stock.jump.message", oldStock, stock, stock))) {
                event.consume();
                return;
            }

            try {
                if (productDAO.existsByNameInCategory(nameField.getText(), categoryBox.getValue().getId(),
                        existing != null ? existing.getId() : 0)) {
                    AlertUtil.warning(I18n.t("products.dup.title"), I18n.t("products.dup.message", categoryBox.getValue().getName(), nameField.getText().trim()));
                    event.consume();
                    return;
                }
            } catch (DataAccessException ex) {
                AlertUtil.error(I18n.t("products.dup.checkfail"), ex.getMessage());
                event.consume();
                return;
            }
            String reorderProblem = com.coffeeshop.coffeeshopmanagement.service.StockLevel.problem(reorderField.getText());
            if (reorderProblem != null) {
                AlertUtil.warning(I18n.t("products.val.stock.title"), I18n.t(reorderProblem, Limits.MAX_STOCK));
                event.consume();
                return;
            }
            Product product = existing != null ? existing : new Product();
            product.setName(nameField.getText().trim());
            product.setCategoryId(categoryBox.getValue().getId());
            product.setPrice(price);
            product.setCost(cost);
            product.setStock(stock);
            product.setReorderLevel(com.coffeeshop.coffeeshopmanagement.service.StockLevel.parse(reorderField.getText()));
            product.setDescription(descriptionField.getText());
            product.setImagePath(pickedImagePath[0]);
            product.setActive(activeBox.isSelected());

            try {
                if (existing == null) {
                    productDAO.insert(product);
                } else {
                    productDAO.update(product);
                }
                productSaved[0] = true;
                // Saved: now the replaced picture and any picture picked and then replaced again can go.
                for (String copy : copiedImages) {
                    if (!copy.equals(pickedImagePath[0])) {
                        com.coffeeshop.coffeeshopmanagement.util.ImageStorage.deleteQuietly(copy);
                    }
                }
                if (originalImagePath != null && !originalImagePath.equals(pickedImagePath[0])
                        && productDAO.countUsingImage(originalImagePath, product.getId()) == 0) {
                    com.coffeeshop.coffeeshopmanagement.util.ImageStorage.deleteQuietly(originalImagePath);
                }
            } catch (DataAccessException ex) {
                AlertUtil.error(I18n.t("products.save.fail"), ex.getMessage());
                event.consume();
            }
        });

        AlertUtil.confirmBeforeDiscarding(dialog, ButtonType.CANCEL);
        dialog.showAndWait();
        if (!productSaved[0]) {
            // Cancelled (or never saved): the copies made while picking are of no use.
            for (String copy : copiedImages) {
                com.coffeeshop.coffeeshopmanagement.util.ImageStorage.deleteQuietly(copy);
            }
        }
        reloadProducts();
    }

    /**
     * The database steps (looking for orders, stopping or deleting, removing the picture) run off the UI thread through
     * {@link ProductService} (TODO 6.14, 6.21); the questions to the person stay here, on the UI thread, between them.
     */
    private void deleteProduct(Product product) {
        Async.run(
                () -> productService.orderReferences(product.getId()),
                orderRefs -> {
                    if (orderRefs > 0) {
                        boolean confirmed = AlertUtil.confirm(I18n.t("common.delete.fail"),
                                I18n.t("products.delete.inorders", product.getName(), orderRefs));
                        if (confirmed) {
                            Async.run(
                                    () -> {
                                        productService.deactivate(product.getId());
                                        return Boolean.TRUE;
                                    },
                                    done -> reloadProducts(),
                                    error -> AlertUtil.error(I18n.t("products.stop.fail"), error.getMessage()),
                                    productTable);
                        }
                        return;
                    }
                    boolean confirmed = AlertUtil.confirm(I18n.t("common.delete.confirm.title"),
                            I18n.t("products.delete.confirm", product.getName()));
                    if (!confirmed) {
                        return;
                    }
                    Async.run(
                            () -> productService.delete(product),
                            deleted -> {
                                if (!deleted) {
                                    AlertUtil.warning(I18n.t("common.delete.fail"),
                                            I18n.t("products.delete.race"));
                                }
                                reloadProducts();
                            },
                            error -> AlertUtil.error(I18n.t("products.delete.fail"), error.getMessage()),
                            productTable);
                },
                error -> AlertUtil.error(I18n.t("common.error.title"), error.getMessage()),
                productTable); // no second click on a delete button while this runs (TODO 2.75)
    }

    /**
     * The "+ Thêm danh mục" shortcut on this screen. The dialog is the one the category screen uses; when a category was
     * saved the product screen reloads itself, because its tabs and category list come from the categories.
     */
    @FXML
    public void handleAddCategory() {
        CategoryDialog.show(null, this::reloadProducts);
    }
}
