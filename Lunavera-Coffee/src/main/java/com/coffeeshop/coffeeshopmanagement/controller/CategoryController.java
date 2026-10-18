package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.service.CategoryService;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.PagedTable;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Backs category management (quanlydanhmuc.fxml): the table, search, and delete. The add/edit dialog is
 * {@link CategoryDialog}, shared with the product screen's "add category" shortcut. Split out of the old
 * {@code AdminController} (TODO 6.4).
 */
public class CategoryController extends AdminScreen {

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final CategoryService categoryService = new CategoryService();

    // ---------------------------------------------------------------- Category management
    @FXML private Label totalCategoryLabel;
    @FXML private Label activeCategoryLabel;
    @FXML private Label hiddenCategoryLabel;
    @FXML private Label totalCategoryProductLabel;
    @FXML private TextField categorySearchField;
    @FXML private TableView<Category> categoryTable;
    @FXML private TableColumn<Category, Number> categoryIndexColumn;
    @FXML private TableColumn<Category, Void> categoryImageColumn;
    @FXML private TableColumn<Category, String> categoryNameColumn;
    @FXML private TableColumn<Category, String> categoryDescriptionColumn;
    @FXML private TableColumn<Category, Number> categoryProductCountColumn;
    @FXML private TableColumn<Category, String> categoryStatusColumn;
    @FXML private TableColumn<Category, Void> categoryActionColumn;
    @FXML private Label categoryPaginationLabel;
    @FXML private HBox categoryPagerBox;

    private PagedTable<Category> categoryPaged;
    private List<Category> allCategories = List.of();
    // Loaded once per reload via CategoryDAO.countProductsByCategory() (a single grouped
    // query) instead of calling countProductsInCategory(id) per row/per keystroke - see
    // progress.md performance notes.
    private Map<Integer, Integer> categoryProductCounts = Map.of();

    @FXML
    private void initialize() {
        if (blockNonAdmin(categoryTable)) {
            return;
        }
        initCategoryScreen();
    }

    // =================================================================== Category management

    private void initCategoryScreen() {
        categoryImageColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : "☕");
                setGraphic(null);
            }
        });
        categoryPaged = new PagedTable<>(categoryTable, categoryPagerBox, 10)
                .unsortable(categoryImageColumn, categoryIndexColumn, categoryActionColumn);
        categoryIndexColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleIntegerProperty(
                        categoryTable.getItems().indexOf(data.getValue()) + categoryPaged.getFromIndex()));
        categoryNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        categoryDescriptionColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getDescription() == null ? "" : data.getValue().getDescription()));
        categoryProductCountColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleIntegerProperty(
                        categoryProductCounts.getOrDefault(data.getValue().getId(), 0)));
        categoryStatusColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().isActive() ? "Đang hoạt động" : "Đã ẩn"));
        categoryActionColumn.setCellFactory(actionColumnFactory(
                this::openEditCategoryDialog,
                this::deleteCategory
        ));

        if (categorySearchField != null) {
            TextLengthLimiter.limit(categorySearchField, TextLengthLimiter.SEARCH_MAX);
            categorySearchField.textProperty().addListener((obs, old, value) -> applyCategoryFilter());
        }

        reloadCategories();
    }

    private void reloadCategories() {
        Async.run(
                () -> new Object[]{categoryDAO.findAll(), categoryDAO.countProductsByCategory()},
                result -> {
                    allCategories = castList(result[0]);
                    categoryProductCounts = castCountMap(result[1]);
                    applyCategoryFilter();
                },
                error -> AlertUtil.error("Lỗi", error.getMessage())
        );
    }

    private void applyCategoryFilter() {
        String keyword = categorySearchField != null && categorySearchField.getText() != null
                ? com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(categorySearchField.getText()) : "";
        List<Category> filtered = keyword.isEmpty()
                ? allCategories
                : allCategories.stream()
                        .filter(c -> com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(c.getName()).contains(keyword))
                        .collect(Collectors.toList());

        // Sorting + paging both happen inside PagedTable (sort first).
        if (categoryPaged != null) {
            categoryPaged.setItems(filtered);
        }

        long activeCount = allCategories.stream().filter(Category::isActive).count();
        if (totalCategoryLabel != null) totalCategoryLabel.setText(String.valueOf(allCategories.size()));
        if (activeCategoryLabel != null) activeCategoryLabel.setText(String.valueOf(activeCount));
        if (hiddenCategoryLabel != null) hiddenCategoryLabel.setText(String.valueOf(allCategories.size() - activeCount));
        if (totalCategoryProductLabel != null) {
            int totalProducts = categoryProductCounts.values().stream().mapToInt(Integer::intValue).sum();
            totalCategoryProductLabel.setText(String.valueOf(totalProducts));
        }
        if (categoryPaginationLabel != null && categoryPaged != null) {
            categoryPaginationLabel.setText(String.format("Hiển thị %d – %d trong tổng số %d danh mục",
                    categoryPaged.getFromIndex(), categoryPaged.getToIndex(), categoryPaged.getTotalCount()));
        }
    }

    @FXML
    public void handleSearchAction() {
        applyCategoryFilter();
    }

    @FXML
    public void handleAddCategory() {
        CategoryDialog.show(null, this::reloadCategories);
    }

    private void openEditCategoryDialog(Category category) {
        CategoryDialog.show(category, this::reloadCategories);
    }

    /**
     * Both database steps (counting the products, then deleting) run off the UI thread through {@link CategoryService}
     * (TODO 6.14, 6.21); the questions to the person stay here, on the UI thread, between the two.
     */
    private void deleteCategory(Category category) {
        Async.run(
                () -> categoryService.productCount(category.getId()),
                productCount -> {
                    if (productCount > 0) {
                        AlertUtil.warning("Không thể xóa",
                                "Danh mục \"" + category.getName() + "\" đang có " + productCount +
                                        " sản phẩm. Hãy chuyển các sản phẩm sang danh mục khác trước, " +
                                        "hoặc ẩn danh mục thay vì xóa.");
                        return;
                    }
                    boolean confirmed = AlertUtil.confirm("Xác nhận xóa",
                            "Xóa danh mục \"" + category.getName() + "\"? Hành động này không thể hoàn tác.");
                    if (!confirmed) {
                        return;
                    }
                    Async.run(
                            () -> categoryService.delete(category.getId()),
                            deleted -> {
                                if (!deleted) {
                                    AlertUtil.warning("Không thể xóa",
                                            "Danh mục vừa được gán cho một sản phẩm nên chưa bị xóa.");
                                }
                                reloadCategories();
                            },
                            error -> AlertUtil.error("Không thể xóa danh mục", error.getMessage()));
                },
                error -> AlertUtil.error("Lỗi", error.getMessage()));
    }

    @FXML
    public void handleTableClick() {
        // Row selection alone needs no action; edit/delete are reached through the action
        // column's own buttons (see actionColumnFactory).
    }
}
