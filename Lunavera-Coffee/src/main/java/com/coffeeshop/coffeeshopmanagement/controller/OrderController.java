package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.TableStatus;
import com.coffeeshop.coffeeshopmanagement.service.Cart;
import com.coffeeshop.coffeeshopmanagement.service.CheckoutService;
import com.coffeeshop.coffeeshopmanagement.service.CustomerService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.DataPaths;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.TransferQr;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.DiscountPolicy;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SessionGuard;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Backs the POS/order-creation screen (quanlydonhang.fxml) - a dine-in table grid + a live
 * cart + checkout.
 */
public class OrderController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    private final ProductDAO productDAO = new ProductDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final TableDAO tableDAO = new TableDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();

    @FXML private Label adminNameLabel;
    @FXML private Label adminRoleLabel;

    @FXML private GridPane tableGrid;
    @FXML private Label selectedTableLabel;
    @FXML private Label selectedTableStatusLabel;
    @FXML private Label selectedTableCapacityLabel;

    @FXML private Label orderStatusLabel;
    @FXML private Label orderIdLabel;
    @FXML private Label orderTableLabel;
    @FXML private Label orderTimeLabel;

    @FXML private TextField customerNameField;
    @FXML private TextField customerPhoneField;
    @FXML private Label customerInfoLabel;
    @FXML private Label cartItemCountLabel;

    @FXML private TableView<OrderItem> orderDetailTable;
    @FXML private TableColumn<OrderItem, Number> detailIndexColumn;
    @FXML private TableColumn<OrderItem, String> productNameColumn;
    @FXML private TableColumn<OrderItem, Void> quantityColumn;
    @FXML private TableColumn<OrderItem, String> unitPriceColumn;
    @FXML private TableColumn<OrderItem, String> subtotalColumn;

    @FXML private TextField productSearchField;
    @FXML private HBox categoryTabBox;
    @FXML private FlowPane productCatalogFlow;

    @FXML private Label totalAmountLabel;
    @FXML private Button paymentButton;

    @FXML private Button productMenuButton;
    @FXML private javafx.scene.control.Separator categorySeparator;
    @FXML private javafx.scene.layout.VBox adminMenuBox;

    private final ObservableList<OrderItem> cart = FXCollections.observableArrayList();
    /** The cart rules (TODO 6.5): they work on {@link #cart}, so the table keeps showing what they change. */
    private final Cart cartRules = new Cart(cart);
    private final Map<Integer, Button> tableButtonByNumber = new LinkedHashMap<>();
    private final Map<Integer, DiningTable> tableDataByNumber = new LinkedHashMap<>();
    private List<Product> allProducts = List.of();
    private List<Category> allCategories = List.of();
    private Integer selectedCategoryId = null;
    private Integer selectedTableNumber;
    private boolean orderInProgress;
    private Customer attachedCustomer;
    private Timeline realTimeClock;
    private Order activeOpenOrder;
    private Order lastCompletedOrder;
    private List<OrderItem> lastCompletedItems = List.of();
    /** Name of the customer on the last paid order, kept for "In lại hóa đơn" (TODO 2.46). */
    private String lastCompletedCustomerName;

    private record TableOrderData(Order order, List<OrderItem> items, Customer customer) {
    }

    private record PaymentResult(PaymentMethod method, BigDecimal discount, String discountReason, BigDecimal total) {
    }

    // =================================================================== initialize

    @FXML
    private void initialize() {
        javafx.application.Platform.runLater(this::installCloseGuard);
        if (adminNameLabel != null) {
            adminNameLabel.setText(Session.getDisplayName());
        }
        if (adminRoleLabel != null) {
            adminRoleLabel.setText(Session.isAdmin() ? "Quản trị viên" : "Nhân viên");
        }

        if (!Session.isAdmin()) {
            if (productMenuButton != null) {
                productMenuButton.setVisible(false);
                productMenuButton.setManaged(false);
            }
            if (categorySeparator != null) {
                categorySeparator.setVisible(false);
                categorySeparator.setManaged(false);
            }
            if (adminMenuBox != null) {
                adminMenuBox.setVisible(false);
                adminMenuBox.setManaged(false);
            }
        }

        if (orderStatusLabel != null) {
            orderStatusLabel.setText("Không hoạt động");
            orderStatusLabel.getStyleClass().setAll("order-status-badge-empty");
        }
        if (orderIdLabel != null) orderIdLabel.setText("Chưa có đơn");
        if (orderTableLabel != null) orderTableLabel.setText("-");
        if (totalAmountLabel != null) totalAmountLabel.setText("0 đ");

        initRealTimeClock();
        initCustomerInputs();
        loadTablesFromDb();

        detailIndexColumn.setCellValueFactory(data -> new SimpleIntegerProperty(
                orderDetailTable.getItems().indexOf(data.getValue()) + 1));
        productNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getProductName()));
        quantityColumn.setCellFactory(col -> quantityCell());
        unitPriceColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyUtil.format(data.getValue().getUnitPrice())));
        subtotalColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyUtil.format(data.getValue().getLineTotal())));

        if (productSearchField != null) {
            TextLengthLimiter.limit(productSearchField, TextLengthLimiter.SEARCH_MAX);
            productSearchField.textProperty().addListener((obs, o, n) -> renderProductCatalog());
        }

        setUpBestSellersToggle();
        installShortcuts();
        loadCategoriesAndProducts();
        loadRecentSales();
        refreshCart();
    }

    // =================================================================== Real-Time Clock
    private void initRealTimeClock() {
        realTimeClock = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClockDisplay()));
        realTimeClock.setCycleCount(Animation.INDEFINITE);
        realTimeClock.play();
        updateClockDisplay();
        // Stop the timer when this screen is replaced (TODO 2.19). SceneNavigator swaps the root of the
        // same Scene, so the old screen's label loses its scene (the window stays); stop on that.
        if (orderTimeLabel != null) {
            orderTimeLabel.sceneProperty().addListener((o, oldScene, scene) -> {
                if (realTimeClock == null) return;
                if (scene == null) realTimeClock.stop();
                else if (realTimeClock.getStatus() != Animation.Status.RUNNING) realTimeClock.play();
            });
        }
    }

    private void updateClockDisplay() {
        if (orderTimeLabel != null) {
            String nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"));
            if (activeOpenOrder != null && activeOpenOrder.getOrderDate() != null) {
                orderTimeLabel.setText(nowStr + " (Đơn: " + activeOpenOrder.getOrderDate().format(DateTimeFormatter.ofPattern("HH:mm")) + ")");
            } else {
                orderTimeLabel.setText(nowStr);
            }
        }
    }

    // =================================================================== Customer Auto-Link
    private void initCustomerInputs() {
        TextLengthLimiter.limit(customerNameField, TextLengthLimiter.NAME_MAX);
        TextLengthLimiter.limit(customerPhoneField, 15);
        if (customerPhoneField != null) {
            customerPhoneField.textProperty().addListener((obs, oldVal, newVal) -> {
                String typed = newVal == null ? "" : newVal.trim();
                // Editing the phone away from the attached customer's number detaches them, so the
                // order can never be paid under the previous person's loyalty account.
                if (attachedCustomer != null && attachedCustomer.getPhone() != null
                        && !attachedCustomer.getPhone().equals(ValidationUtil.normalizePhone(typed))) {
                    attachedCustomer = null;
                    syncOpenOrderToDb();
                }
                if (typed.length() >= 9) {
                    lookupCustomerByPhone(typed);
                } else {
                    showCustomerInfo(null, false);
                }
            });
        }
        if (customerNameField != null) {
            customerNameField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (!isNowFocused) {
                    handleCustomerInfoChanged();
                }
            });
        }
    }

    private void lookupCustomerByPhone(String phone) {
        Async.run(
                () -> customerDAO.findByPhone(phone),
                optCust -> {
                    // Lookups finish in any order: ignore one for a number that is no longer typed.
                    if (customerPhoneField == null || !phone.equals(customerPhoneField.getText() == null ? "" : customerPhoneField.getText().trim())) {
                        return;
                    }
                    if (optCust.isPresent()) {
                        attachedCustomer = optCust.get();
                        // The stored name is the source of truth: the POS never renames a customer.
                        if (customerNameField != null && !attachedCustomer.getFullName().equals(customerNameField.getText())) {
                            customerNameField.setText(attachedCustomer.getFullName());
                        }
                        showCustomerInfo(attachedCustomer, false);
                        syncOpenOrderToDb();
                    } else {
                        showCustomerInfo(null, ValidationUtil.isValidPhone(phone));
                    }
                },
                err -> LOGGER.log(Level.WARNING, "Customer lookup by phone failed", err)
        );
    }

    /** The line under the phone field: who the typed number belongs to, so the cashier sees the match. */
    private void showCustomerInfo(Customer customer, boolean newCustomer) {
        if (customerInfoLabel == null) return;
        String text = customer != null
                ? "Khách cũ: " + customer.getFullName() + " · " + customer.getLoyaltyPoints() + " điểm"
                : newCustomer ? "Khách mới: sẽ được lưu khi gọi món hoặc thanh toán" : "";
        customerInfoLabel.setText(text);
        customerInfoLabel.setVisible(!text.isEmpty());
        customerInfoLabel.setManaged(!text.isEmpty());
    }

    private void handleCustomerInfoChanged() {
        String name = customerNameField != null && customerNameField.getText() != null ? customerNameField.getText().trim() : "";
        String phone = customerPhoneField != null && customerPhoneField.getText() != null ? customerPhoneField.getText().trim() : "";
        if (name.isEmpty() && phone.isEmpty()) return;
        // Only LOOK for an existing customer here. A new one is created when the order is started or paid, so pressing
        // Tab after a mistyped number no longer leaves a permanent customer behind (TODO 3.29).
        if (ValidationUtil.isValidPhone(phone)) {
            lookupCustomerByPhone(phone);
        } else if (phone.isEmpty() && customerInfoLabel != null) {
            // A name without a phone is a walk-in: say so, so staff know no points are given (TODO 3.9).
            customerInfoLabel.setText("Chưa có số điện thoại: khách sẽ không tích điểm và không tìm lại được sau này.");
            customerInfoLabel.setVisible(true);
            customerInfoLabel.setManaged(true);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(OrderController.class.getName());
    private boolean autosaveWarningShown;
    private boolean startingOrder;

    private final CustomerService customerService = new CustomerService();
    private final CheckoutService checkoutService = new CheckoutService(orderDAO, tableDAO, customerService);

    private Customer ensureCustomerInDb(String name, String phone) {
        return customerService.findOrCreateByPhone(name, phone);
    }

    // =================================================================== Category & Product Catalog
    private void loadCategoriesAndProducts() {
        Async.run(
                () -> {
                    List<Category> categories = categoryDAO.findAll();
                    List<Product> products = productDAO.findAll();
                    return Map.entry(categories, products);
                },
                entry -> {
                    allCategories = entry.getKey();
                    allProducts = entry.getValue();
                    renderCategoryChips();
                    renderProductCatalog();
                },
                err -> AlertUtil.error("Lỗi", "Không thể tải danh mục và sản phẩm: " + err.getMessage())
        );
    }

    private void renderCategoryChips() {
        if (categoryTabBox == null) return;
        categoryTabBox.getChildren().clear();

        Button allBtn = new Button("Tất cả");
        allBtn.getStyleClass().add("category-chip");
        if (selectedCategoryId == null) allBtn.getStyleClass().add("category-chip-active");
        allBtn.setOnAction(e -> {
            selectedCategoryId = null;
            renderCategoryChips();
            renderProductCatalog();
        });
        categoryTabBox.getChildren().add(allBtn);

        for (Category cat : allCategories) {
            if (!cat.isActive()) continue;
            Button catBtn = new Button(cat.getName());
            boolean isSelected = selectedCategoryId != null && selectedCategoryId == cat.getId();
            catBtn.getStyleClass().add("category-chip");
            if (isSelected) catBtn.getStyleClass().add("category-chip-active");
            catBtn.setOnAction(e -> {
                selectedCategoryId = cat.getId();
                renderCategoryChips();
                renderProductCatalog();
            });
            categoryTabBox.getChildren().add(catBtn);
        }
    }

    /** Units sold in the last 30 days per product, for "best sellers first" (TODO 5.10); empty until loaded. */
    private java.util.Map<Integer, Integer> recentSales = java.util.Map.of();
    private javafx.scene.control.ToggleButton bestSellersToggle;

    private void setUpBestSellersToggle() {
        if (productSearchField == null || !(productSearchField.getParent() instanceof HBox bar) || bestSellersToggle != null) return;
        bestSellersToggle = new javafx.scene.control.ToggleButton("Bán chạy trước");
        bestSellersToggle.setPrefHeight(32);
        bestSellersToggle.setSelected(com.coffeeshop.coffeeshopmanagement.util.UiSettings.getBoolean("pos.bestSellersFirst", true));
        bestSellersToggle.selectedProperty().addListener((o, was, now) -> {
            com.coffeeshop.coffeeshopmanagement.util.UiSettings.putBoolean("pos.bestSellersFirst", now);
            renderProductCatalog();
        });
        bar.getChildren().add(bar.getChildren().indexOf(productSearchField), bestSellersToggle);
    }

    /**
     * Ctrl+F jumps to the menu search, Ctrl+Enter starts the payment (TODO 5.7). The scene is shared by all screens, so the
     * keys are taken off again as soon as this screen is replaced; otherwise Ctrl+Enter would pay on a screen that is gone.
     */
    private void installShortcuts() {
        if (productSearchField == null) return;
        javafx.scene.input.KeyCombination find = new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.F, javafx.scene.input.KeyCombination.CONTROL_DOWN);
        javafx.scene.input.KeyCombination pay = new javafx.scene.input.KeyCodeCombination(
                javafx.scene.input.KeyCode.ENTER, javafx.scene.input.KeyCombination.CONTROL_DOWN);
        Runnable focusSearch = () -> { productSearchField.requestFocus(); productSearchField.selectAll(); };
        Runnable startPayment = () -> { if (paymentButton != null && !paymentButton.isDisabled()) handlePayment(); };
        productSearchField.sceneProperty().addListener((o, old, scene) -> {
            if (old != null) {
                old.getAccelerators().remove(find);
                old.getAccelerators().remove(pay);
            }
            if (scene != null) {
                scene.getAccelerators().put(find, focusSearch);
                scene.getAccelerators().put(pay, startPayment);
            }
        });
        productSearchField.setTooltip(new javafx.scene.control.Tooltip("Ctrl+F: tìm món nhanh"));
        if (paymentButton != null) paymentButton.setTooltip(new javafx.scene.control.Tooltip("Ctrl+Enter: thanh toán"));
    }

    private void loadRecentSales() {
        Async.run(
                () -> new com.coffeeshop.coffeeshopmanagement.dao.ReportDAO()
                        .unitsSold(java.time.LocalDate.now().minusDays(30), java.time.LocalDate.now()),
                sales -> {
                    recentSales = sales;
                    renderProductCatalog();
                },
                err -> LOGGER.log(Level.FINE, "Could not load best sellers", err));
    }

    private void renderProductCatalog() {
        if (productCatalogFlow == null) return;
        productCatalogFlow.getChildren().clear();

        String keyword = productSearchField != null && productSearchField.getText() != null
                ? com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(productSearchField.getText()) : "";

        List<Product> filtered = com.coffeeshop.coffeeshopmanagement.service.SellableMenu.filter(allProducts, allCategories).stream()
                .filter(p -> selectedCategoryId == null || java.util.Objects.equals(p.getCategoryId(), selectedCategoryId))
                .filter(p -> keyword.isEmpty() || com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(p.getName()).contains(keyword))
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

            Label nameLabel = new Label(product.getName());
            nameLabel.getStyleClass().add("product-card-name");
            nameLabel.setMaxWidth(130);
            nameLabel.setWrapText(true);

            Label priceLabel = new Label(CurrencyUtil.format(product.getPrice()));
            priceLabel.getStyleClass().add("product-card-price");

            card.getChildren().addAll(nameLabel, priceLabel);

            if (product.getStock() <= 0) {
                Label outOfStock = new Label("Hết hàng");
                outOfStock.setStyle("-fx-text-fill: #b33939; -fx-font-size: 10px; -fx-font-weight: bold;");
                card.getChildren().add(outOfStock);
                card.setOpacity(0.55);
            } else {
                if (product.getStock() <= com.coffeeshop.coffeeshopmanagement.dao.ProductDAO.LOW_STOCK_THRESHOLD) {
                    // Low-stock badge (TODO 5.6): the cashier sees it before promising the last cups.
                    Label low = new Label("Sắp hết (còn " + product.getStock() + ")");
                    low.setStyle("-fx-text-fill: #b26a00; -fx-font-size: 10px; -fx-font-weight: bold;");
                    card.getChildren().add(low);
                }
                card.setOnMouseClicked(e -> handleQuickAddProduct(product));
            }

            productCatalogFlow.getChildren().add(card);
        }
    }

    private void handleQuickAddProduct(Product product) {
        if (selectedTableNumber == null) {
            AlertUtil.warning("Chưa chọn bàn", "Vui lòng chọn một bàn ở sơ đồ bên trái trước khi gọi món.");
            return;
        }

        if (!orderInProgress || activeOpenOrder == null) {
            startOrderForTable(selectedTableNumber, () -> addToCart(product));
        } else {
            addToCart(product);
        }
    }

    private void loadProducts() {
        try {
            allProducts = productDAO.findAll();
        } catch (DataAccessException e) {
            AlertUtil.error("Lỗi", "Không thể tải danh sách sản phẩm: " + e.getMessage());
        }
    }

    /** Renders "[-]  qty  [+]" inline in the quantity column */
    private TableCell<OrderItem, Void> quantityCell() {
        return new TableCell<>() {
            private final Button minus = new Button("−");
            private final Button plus = new Button("+");
            private final Label qtyLabel = new Label();
            private final HBox box = new HBox(4, minus, qtyLabel, plus);

            {
                box.setAlignment(Pos.CENTER);
                minus.setOnAction(e -> adjustQuantity(getTableView().getItems().get(getIndex()), -1));
                plus.setOnAction(e -> adjustQuantity(getTableView().getItems().get(getIndex()), 1));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    qtyLabel.setText(String.valueOf(getTableView().getItems().get(getIndex()).getQuantity()));
                    setGraphic(box);
                }
            }
        };
    }

    // =================================================================== Table selection & management (3 CỘT x 4 DÒNG)

    public void loadTablesFromDb() {
        if (tableGrid == null) return;
        Async.run(
                tableDAO::findAll,
                tables -> {
                    tableGrid.getChildren().clear();
                    tableButtonByNumber.clear();
                    tableDataByNumber.clear();
                    for (DiningTable table : tables) {
                        int num = table.getTableNumber();
                        tableDataByNumber.put(num, table);
                        int col = (num - 1) % 3;
                        int row = (num - 1) / 3;

                        String statusText = table.isOccupied() ? "Đang sử dụng" : "Không hoạt động";
                        Button btn = new Button("☕  " + table.getName() + "\n" + statusText);
                        btn.setMaxWidth(Double.MAX_VALUE);
                        btn.setMaxHeight(Double.MAX_VALUE);
                        btn.getStyleClass().add(table.isOccupied() ? "table-button-occupied" : "table-button-empty");
                        if (selectedTableNumber != null && selectedTableNumber == num) {
                            btn.getStyleClass().add("table-button-selected");
                        }
                        GridPane.setHgrow(btn, Priority.ALWAYS);
                        GridPane.setVgrow(btn, Priority.ALWAYS);
                        btn.setOnAction(e -> selectTable(num));
                        tableGrid.add(btn, col, row);
                        tableButtonByNumber.put(num, btn);
                    }
                    if (selectedTableNumber == null && !tables.isEmpty()) {
                        selectTable(tables.get(0).getTableNumber());
                    } else if (selectedTableNumber != null) {
                        updateSelectedTableInfo(selectedTableNumber);
                    }
                },
                error -> AlertUtil.error("Lỗi", "Không thể tải danh sách bàn: " + error.getMessage())
        );
    }

    public void selectTable(int number) {
        selectedTableNumber = number;
        highlightSelectedTableButton(number);
        updateSelectedTableInfo(number);
        loadTableOrderFromDb(number);
    }

    private void updateSelectedTableInfo(int number) {
        if (selectedTableLabel != null) {
            selectedTableLabel.setText("Bàn " + number);
        }
        DiningTable table = tableDataByNumber.get(number);
        boolean occupied = table != null && table.isOccupied();
        if (selectedTableStatusLabel != null) {
            selectedTableStatusLabel.setText(occupied ? "Đang sử dụng" : "Không hoạt động");
            selectedTableStatusLabel.getStyleClass().setAll(occupied ? "occupied-badge" : "empty-badge");
        }
        if (selectedTableCapacityLabel != null && table != null) {
            selectedTableCapacityLabel.setText("Sức chứa: " + table.getCapacity() + " người");
        }
    }

    private void loadTableOrderFromDb(int number) {
        Async.run(
                () -> {
                    Optional<Order> openOrder = orderDAO.findOpenOrderByTable(number);
                    if (openOrder.isPresent()) {
                        List<OrderItem> items = orderDAO.findItemsByOrderId(openOrder.get().getId());
                        Customer cust = null;
                        if (openOrder.get().getCustomerId() != null) {
                            cust = customerDAO.findById(openOrder.get().getCustomerId()).orElse(null);
                        }
                        return new TableOrderData(openOrder.get(), items, cust);
                    }
                    return new TableOrderData(null, List.of(), null);
                },
                data -> {
                    // An answer for a table that is no longer the selected one must not overwrite the screen (TODO 2.55).
                    if (!java.util.Objects.equals(selectedTableNumber, number)) return;
                    DiningTable table = tableDataByNumber.get(number);
                    boolean occupied = table != null && table.isOccupied();
                    if (data.order() != null) {
                        activeOpenOrder = data.order();
                        attachedCustomer = data.customer();
                        cart.setAll(data.items());
                        orderInProgress = true;
                        if (orderStatusLabel != null) {
                            orderStatusLabel.setText("Đang phục vụ");
                            orderStatusLabel.getStyleClass().setAll("order-status-badge");
                        }
                        if (orderIdLabel != null) {
                            orderIdLabel.setText(com.coffeeshop.coffeeshopmanagement.util.OrderNumber.format(data.order().getId()));
                        }
                        if (orderTableLabel != null) {
                            orderTableLabel.setText("Bàn " + number);
                        }
                        updateClockDisplay();
                        if (customerNameField != null) {
                            customerNameField.setText(data.customer() != null ? data.customer().getFullName() : "Khách vãng lai");
                        }
                        if (customerPhoneField != null) {
                            customerPhoneField.setText(data.customer() != null && data.customer().getPhone() != null ? data.customer().getPhone() : "");
                        }
                        showCustomerInfo(data.customer(), false);
                        orderDetailTable.getItems().setAll(cart);
                        totalAmountLabel.setText(CurrencyUtil.format(cartSubtotal()));
                    } else if (occupied) {
                        activeOpenOrder = null;
                        attachedCustomer = null;
                        cart.clear();
                        orderInProgress = true;
                        if (orderStatusLabel != null) {
                            orderStatusLabel.setText("Đang phục vụ");
                            orderStatusLabel.getStyleClass().setAll("order-status-badge");
                        }
                        if (orderIdLabel != null) {
                            orderIdLabel.setText("Đang phục vụ");
                        }
                        if (orderTableLabel != null) {
                            orderTableLabel.setText("Bàn " + number);
                        }
                        updateClockDisplay();
                        if (customerNameField != null) {
                            customerNameField.setText("Khách vãng lai");
                        }
                        if (customerPhoneField != null) {
                            customerPhoneField.setText("");
                        }
                        orderDetailTable.getItems().setAll(cart);
                        totalAmountLabel.setText("0 đ");
                    } else {
                        activeOpenOrder = null;
                        attachedCustomer = null;
                        cart.clear();
                        orderInProgress = false;
                        if (orderStatusLabel != null) {
                            orderStatusLabel.setText("Không hoạt động");
                            orderStatusLabel.getStyleClass().setAll("order-status-badge-empty");
                        }
                        if (orderIdLabel != null) {
                            orderIdLabel.setText("Chưa có đơn");
                        }
                        if (orderTableLabel != null) {
                            orderTableLabel.setText("Bàn " + number);
                        }
                        updateClockDisplay();
                        if (customerNameField != null) {
                            customerNameField.setText("");
                        }
                        if (customerPhoneField != null) {
                            customerPhoneField.setText("");
                        }
                        orderDetailTable.getItems().setAll(cart);
                        totalAmountLabel.setText("0 đ");
                    }
                    if (cartItemCountLabel != null) {
                        int totalQty = cartRules.totalQuantity();
                        cartItemCountLabel.setText(cart.size() + " món (" + totalQty + " phần)");
                    }
                },
                error -> {
                    LOGGER.log(Level.WARNING, "Could not load the order of table " + number, error);
                    AlertUtil.error("Lỗi", "Không thể tải đơn của bàn " + number + ": " + error.getMessage());
                }
        );
    }

    private void highlightSelectedTableButton(int number) {
        for (Map.Entry<Integer, Button> entry : tableButtonByNumber.entrySet()) {
            entry.getValue().getStyleClass().remove("table-button-selected");
            if (entry.getKey() == number) {
                entry.getValue().getStyleClass().add("table-button-selected");
            }
        }
    }

    @FXML
    public void handleToggleTableStatus() {
        if (selectedTableNumber == null) {
            AlertUtil.info("Chưa chọn bàn", "Vui lòng chọn một bàn để đổi trạng thái.");
            return;
        }
        DiningTable table = tableDataByNumber.get(selectedTableNumber);
        if (table == null) return;

        boolean newOccupied = !table.isOccupied();
        final int tNum = selectedTableNumber;
        // Emptying a table cancels its unpaid order: only the cashier who opened it, or an admin, may (TODO 3.8).
        if (!newOccupied && activeOpenOrder != null
                && !com.coffeeshop.coffeeshopmanagement.service.TablePolicy.canEmptyTableWithOpenOrder(
                Session.isAdmin(),
                Session.getCurrentEmployee() != null ? Session.getCurrentEmployee().getId() : null,
                activeOpenOrder.getEmployeeId())) {
            AlertUtil.warning("Không đủ quyền",
                    com.coffeeshop.coffeeshopmanagement.service.TablePolicy.REFUSED_MESSAGE);
            return;
        }
        // setEmpty() also cancels the table's OPEN order, so never do that silently.
        if (!newOccupied && activeOpenOrder != null && !cart.isEmpty()
                && !AlertUtil.confirm("Hủy đơn đang phục vụ",
                "Bàn " + tNum + " đang có đơn chưa thanh toán. Đổi sang không hoạt động sẽ hủy đơn này. Tiếp tục?")) {
            return;
        }
        Async.run(
                () -> {
                    if (newOccupied) {
                        tableDAO.setOccupied(tNum, null);
                    } else {
                        tableDAO.setEmpty(tNum);
                    }
                    return null;
                },
                result -> {
                    loadTablesFromDb();
                    loadTableOrderFromDb(tNum);
                    AlertUtil.info("Thành công",
                            "Bàn " + tNum + " đã đổi sang: " + (newOccupied ? "Đang sử dụng" : "Không hoạt động"));
                },
                error -> AlertUtil.error("Lỗi", "Không thể cập nhật trạng thái bàn: " + error.getMessage())
        );
    }

    @FXML
    public void handleTableDetail() {
        if (selectedTableNumber == null) {
            AlertUtil.info("Chưa chọn bàn", "Vui lòng chọn một bàn để xem chi tiết.");
            return;
        }
        DiningTable table = tableDataByNumber.get(selectedTableNumber);
        String status = (table != null && table.isOccupied()) ? "Đang sử dụng" : "Không hoạt động";
        AlertUtil.info("Chi tiết " + selectedTableLabel.getText(),
                (table != null ? "Sức chứa: " + table.getCapacity() + " người\n" : "") +
                "Trạng thái: " + status);
    }

    @FXML
    public void handleStartOrder() {
        if (selectedTableNumber == null) {
            AlertUtil.warning("Chưa chọn bàn", "Vui lòng chọn một bàn trước khi bắt đầu đặt đơn.");
            return;
        }
        startOrderForTable(selectedTableNumber, null);
    }

    private record StartedOrder(Order order, Customer customer) {}

    /**
     * Starts a new OPEN order for the table (the one place that does this): resolves the customer
     * typed into the fields, saves the order and marks the table occupied, then updates the screen.
     * {@code afterStart} runs on the FX thread once the order is on screen (may be null).
     */
    private void startOrderForTable(int tNum, Runnable afterStart) {
        if (startingOrder) return;
        startingOrder = true;
        final String name = customerNameField != null && customerNameField.getText() != null ? customerNameField.getText().trim() : "";
        final String phone = customerPhoneField != null && customerPhoneField.getText() != null ? customerPhoneField.getText().trim() : "";
        Async.runOrdered(
                () -> {
                    Customer cust = ensureCustomerInDb(name, phone);
                    Order order = new Order();
                    order.setOrderDate(LocalDateTime.now());
                    order.setEmployeeId(Session.getCurrentEmployee() != null ? Session.getCurrentEmployee().getId() : null);
                    order.setCustomerId(cust != null ? cust.getId() : null);
                    order.setStatus(OrderStatus.OPEN);
                    order.setSubtotal(BigDecimal.ZERO);
                    order.setDiscount(BigDecimal.ZERO);
                    order.setTotal(BigDecimal.ZERO);
                    order.setTableNumber(tNum);
                    Order saved = orderDAO.insert(order, List.of());
                    return new StartedOrder(saved, cust);
                },
                started -> {
                    startingOrder = false;
                    activeOpenOrder = started.order();
                    attachedCustomer = started.customer();
                    showCustomerInfo(started.customer(), false);
                    orderInProgress = true;
                    cart.clear();
                    if (orderStatusLabel != null) {
                        orderStatusLabel.setText("Đang phục vụ");
                        orderStatusLabel.getStyleClass().setAll("order-status-badge");
                    }
                    if (orderIdLabel != null) {
                        orderIdLabel.setText(com.coffeeshop.coffeeshopmanagement.util.OrderNumber.format(started.order().getId()));
                    }
                    if (orderTableLabel != null) {
                        orderTableLabel.setText("Bàn " + tNum);
                    }
                    updateClockDisplay();
                    orderDetailTable.getItems().setAll(cart);
                    totalAmountLabel.setText("0 đ");
                    loadTablesFromDb();
                    if (afterStart != null) afterStart.run();
                },
                error -> {
                    startingOrder = false;
                    LOGGER.log(Level.WARNING, "Could not start order for table " + tNum, error);
                    AlertUtil.error("Lỗi", "Không thể bắt đầu đặt đơn cho bàn " + tNum + ": " + error.getMessage());
                }
        );
    }

    // =================================================================== Cart

    private void addToCart(Product product) {
        Cart.Result result = cartRules.add(product);
        if (result.outcome() == Cart.Outcome.NOT_ENOUGH_STOCK) {
            AlertUtil.warning("Không đủ hàng",
                    "\"" + product.getName() + "\" chỉ còn " + result.stock() + " trong kho.");
            return;
        }
        refreshCart();
    }

    private void adjustQuantity(OrderItem line, int delta) {
        int newQty = line.getQuantity() + delta;
        if (delta > 0) {
            Product product = findProductById(line.getProductId());
            if (Cart.exceedsStock(product, newQty)) {
                AlertUtil.warning("Không đủ hàng",
                        "\"" + line.getProductName() + "\" chỉ còn " + product.getStock() + " trong kho.");
                return;
            }
        }
        // Taking the last cup off a line with "−" removes the line: ask first, one mis-tap should not lose it (TODO 5.5).
        if (delta < 0 && newQty <= 0 && !AlertUtil.confirm("Bỏ món khỏi đơn",
                "Bỏ \"" + line.getProductName() + "\" khỏi đơn hàng?")) {
            return;
        }
        setLineQuantity(line, newQty);
    }

    private void setLineQuantity(OrderItem item, int newQty) {
        cartRules.setQuantity(item, newQty);
        refreshCart();
    }

    private Product findProductById(Integer id) {
        if (id == null) {
            return null;
        }
        return allProducts.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
    }

    private BigDecimal cartSubtotal() {
        return cartRules.subtotal();
    }

    private void refreshCart() {
        orderDetailTable.getItems().setAll(cart);
        totalAmountLabel.setText(CurrencyUtil.format(cartSubtotal()));
        if (cartItemCountLabel != null) {
            int totalQty = cartRules.totalQuantity();
            cartItemCountLabel.setText(cart.size() + " món (" + totalQty + " phần)");
        }
        syncOpenOrderToDb();
    }

    private void syncOpenOrderToDb() {
        if (activeOpenOrder != null && activeOpenOrder.getId() > 0) {
            activeOpenOrder.setSubtotal(cartSubtotal());
            activeOpenOrder.setTotal(cartSubtotal());
            activeOpenOrder.setCustomerId(attachedCustomer != null ? attachedCustomer.getId() : null);
            final Order orderToSave = activeOpenOrder;
            final List<OrderItem> itemsToSave = new ArrayList<>(cart);
            // Ordered: saves (and the payment that follows) run in the order they were made, so an
            // older cart can never overwrite a newer one or land after the order was paid.
            Async.runOrdered(
                    () -> orderDAO.updateIfStillOpen(orderToSave, itemsToSave),
                    stillOpen -> {
                        autosaveWarningShown = false;
                        if (!stillOpen) {
                            // Paid or cancelled elsewhere: say so now, not at the payment (TODO 3.28).
                            AlertUtil.warning("Đơn đã bị đóng",
                                    "Đơn này đã bị đóng ở nơi khác (đã thanh toán, hoặc bàn đã được làm trống). "
                                            + "Các thay đổi vừa rồi không được lưu. Màn hình sẽ tải lại.");
                            loadTablesFromDb();
                            if (selectedTableNumber != null) loadTableOrderFromDb(selectedTableNumber);
                        }
                    },
                    err -> {
                        LOGGER.log(Level.WARNING, "Autosave of the open order failed", err);
                        if (!autosaveWarningShown) {
                            autosaveWarningShown = true;
                            AlertUtil.warning("Chưa lưu được đơn",
                                    "Không thể tự động lưu đơn đang phục vụ: " + err.getMessage()
                                            + "\nĐơn vẫn hiển thị trên màn hình; hãy kiểm tra lại trước khi thanh toán.");
                        }
                    }
            );
        }
    }

    @FXML
    public void handleEditOrder() {
        if (!orderInProgress) {
            AlertUtil.warning("Chưa có đơn hàng", "Vui lòng bắt đầu đặt đơn trước.");
            return;
        }
        List<Customer> customers;
        try {
            customers = customerDAO.findAll();
        } catch (DataAccessException e) {
            AlertUtil.error("Lỗi", e.getMessage());
            return;
        }
        if (customers.isEmpty()) {
            AlertUtil.info("Chưa có khách hàng", "Chưa có khách hàng nào được đăng ký trong hệ thống.");
            return;
        }
        ChoiceDialog<Customer> dialog = new ChoiceDialog<>(attachedCustomer, customers);
        dialog.setTitle("Gắn khách hàng vào đơn");
        dialog.setHeaderText(null);
        dialog.setContentText("Chọn khách hàng cho đơn này (Cancel để giữ Khách vãng lai):");
        AlertUtil.configure(dialog);
        dialog.showAndWait().ifPresent(customer -> {
            attachedCustomer = customer;
            if (customerNameField != null) customerNameField.setText(customer.getFullName());
            if (customerPhoneField != null) customerPhoneField.setText(customer.getPhone() != null ? customer.getPhone() : "");
            syncOpenOrderToDb();
        });
    }

    // =================================================================== Payment / checkout

    @FXML
    public void handlePayment() {
        if (!orderInProgress) {
            AlertUtil.warning("Chưa có đơn hàng", "Vui lòng bắt đầu đặt đơn trước khi thanh toán.");
            return;
        }
        if (cart.isEmpty()) {
            AlertUtil.warning("Đơn hàng trống", "Vui lòng thêm ít nhất một món trước khi thanh toán.");
            return;
        }
        // An account locked mid-shift must not be able to complete a sale.
        if (!SessionGuard.validateNow()) {
            cart.clear();
            SessionGuard.forceLogout();
            return;
        }
        openPaymentDialog();
    }

    private void openPaymentDialog() {
        BigDecimal subtotal = cartSubtotal();

        Dialog<PaymentResult> dialog = new Dialog<>();
        dialog.setTitle("Thanh toán");
        AlertUtil.configure(dialog);
        ButtonType confirmButtonType = new ButtonType("Xác nhận thanh toán", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        ToggleGroup methodGroup = new ToggleGroup();
        RadioButton cashRadio = new RadioButton(PaymentMethod.CASH.label());
        cashRadio.setToggleGroup(methodGroup);
        cashRadio.setSelected(true);
        RadioButton cardRadio = new RadioButton(PaymentMethod.CARD.label());
        cardRadio.setToggleGroup(methodGroup);
        RadioButton transferRadio = new RadioButton(PaymentMethod.TRANSFER.label());
        transferRadio.setToggleGroup(methodGroup);

        TextField discountField = new TextField("0");
        TextField discountReasonField = new TextField();
        discountReasonField.setPromptText("Bắt buộc khi có giảm giá");
        TextLengthLimiter.limit(discountReasonField, DiscountPolicy.REASON_MAX_LENGTH);
        // Only worth asking for a reason when there is a discount.
        discountReasonField.setDisable(true);
        discountField.textProperty().addListener((obs, o, n) -> {
            BigDecimal typed = parseNonNegative(n);
            discountReasonField.setDisable(typed == null || typed.signum() == 0);
        });
        Role currentRole = Session.getCurrentRole();
        BigDecimal maxDiscount = DiscountPolicy.maxDiscount(currentRole, subtotal);
        Label discountHint = new Label(currentRole == Role.ADMIN ? "Quản trị viên: không giới hạn."
                : "Nhân viên được giảm tối đa " + DiscountPolicy.STAFF_MAX_PERCENT + "% (" + CurrencyUtil.format(maxDiscount) + ").");
        discountHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #5b7e45;");
        TextField cashReceivedField = new TextField();
        TextLengthLimiter.limit(discountField, TextLengthLimiter.NUMBER_MAX);
        TextLengthLimiter.limit(cashReceivedField, TextLengthLimiter.NUMBER_MAX);
        cashReceivedField.disableProperty().bind(cashRadio.selectedProperty().not()); // only cash has "tiền khách đưa"
        Label totalPreviewLabel = new Label(CurrencyUtil.format(subtotal));
        Label changePreviewLabel = new Label(CurrencyUtil.format(BigDecimal.ZERO));

        // Quick cash buttons (TODO 5.1): "Đủ tiền" fills the exact total, the others a common note; a note below the total is disabled.
        BigDecimal[] currentTotal = {subtotal};
        Button exactButton = new Button("Đủ tiền");
        HBox quickCash = new HBox(6, exactButton);
        java.util.Map<Button, BigDecimal> noteButtons = new java.util.LinkedHashMap<>();
        for (long note : new long[]{50_000, 100_000, 200_000, 500_000}) {
            Button b = new Button(CurrencyUtil.format(BigDecimal.valueOf(note)));
            noteButtons.put(b, BigDecimal.valueOf(note));
            b.setOnAction(e -> cashReceivedField.setText(String.valueOf(note)));
            quickCash.getChildren().add(b);
        }
        exactButton.setOnAction(e -> cashReceivedField.setText(currentTotal[0].toBigInteger().toString()));
        quickCash.disableProperty().bind(cashRadio.selectedProperty().not());

        Runnable updatePreview = () -> {
            BigDecimal discount = parseNonNegative(discountField.getText());
            if (discount == null || discount.compareTo(subtotal) > 0) {
                discount = BigDecimal.ZERO;
            }
            BigDecimal total = subtotal.subtract(discount);
            currentTotal[0] = total;
            noteButtons.forEach((button, value) -> button.setDisable(value.compareTo(total) < 0));
            totalPreviewLabel.setText(CurrencyUtil.format(total));
            BigDecimal received = parseNonNegative(cashReceivedField.getText());
            BigDecimal change = received != null ? received.subtract(total) : null;
            changePreviewLabel.setText(change != null && change.compareTo(BigDecimal.ZERO) >= 0
                    ? CurrencyUtil.format(change) : "-");
        };
        discountField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        cashReceivedField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        cardRadio.selectedProperty().addListener((obs, o, n) -> updatePreview.run());
        transferRadio.selectedProperty().addListener((obs, o, n) -> updatePreview.run());
        updatePreview.run();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label("Tạm tính:"), new Label(CurrencyUtil.format(subtotal)));
        grid.addRow(1, new Label("Giảm giá:"), new VBox(2, discountField, discountHint));
        grid.addRow(2, new Label("Lý do giảm giá:"), discountReasonField);
        grid.addRow(3, new Label("Thành tiền:"), totalPreviewLabel);
        grid.addRow(4, new Label("Phương thức:"), new HBox(14, cashRadio, cardRadio, transferRadio));
        grid.addRow(5, new Label("Tiền khách đưa:"), new VBox(6, cashReceivedField, quickCash));
        grid.addRow(6, new Label("Tiền thối lại:"), changePreviewLabel);

        // Bank transfer: the shop's own QR picture and the amount, shown only for this method.
        // The cashier must tick that the money arrived before the order can be paid.
        CheckBox transferReceivedBox = new CheckBox("Tôi đã nhận đủ tiền trong tài khoản");
        VBox transferBox = buildTransferBox(totalPreviewLabel, transferReceivedBox);
        transferBox.visibleProperty().bind(transferRadio.selectedProperty());
        transferBox.managedProperty().bind(transferRadio.selectedProperty());
        grid.add(transferBox, 0, 7, 2, 1);
        dialog.getDialogPane().setContent(grid);

        PaymentResult[] pendingResult = {null};
        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(confirmButtonType);
        transferRadio.selectedProperty().addListener((obs, was, isNow) -> {
            confirmButton.setText(isNow ? "Đã nhận tiền - hoàn tất" : "Xác nhận thanh toán");
            // The dialog was sized for the short layout; grow or shrink it when the QR appears or goes.
            javafx.application.Platform.runLater(() -> {
                if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                    dialog.getDialogPane().getScene().getWindow().sizeToScene();
                }
            });
        });
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            BigDecimal discount = parseNonNegative(discountField.getText());
            if (discount == null) {
                AlertUtil.warning("Giảm giá không hợp lệ", "Vui lòng nhập một số hợp lệ.");
                event.consume();
                return;
            }
            DiscountPolicy.Result discountResult = DiscountPolicy.check(currentRole, subtotal, discount, discountReasonField.getText());
            String discountProblem = discountResult == DiscountPolicy.Result.OK ? null
                    : DiscountPolicy.messageFor(discountResult, currentRole, subtotal);
            if (discountProblem != null) {
                AlertUtil.warning("Giảm giá không hợp lệ", discountProblem);
                event.consume();
                return;
            }
            BigDecimal total = subtotal.subtract(discount);
            PaymentMethod method = cashRadio.isSelected() ? PaymentMethod.CASH
                    : transferRadio.isSelected() ? PaymentMethod.TRANSFER : PaymentMethod.CARD;
            if (method == PaymentMethod.TRANSFER && !transferReceivedBox.isSelected()) {
                AlertUtil.warning("Chưa xác nhận", "Vui lòng tick xác nhận đã nhận đủ tiền chuyển khoản trước khi hoàn tất.");
                event.consume();
                return;
            }
            if (method == PaymentMethod.CASH) {
                BigDecimal received = parseNonNegative(cashReceivedField.getText());
                if (received == null) {
                    AlertUtil.warning("Số tiền không hợp lệ", "Vui lòng nhập số tiền khách đưa.");
                    event.consume();
                    return;
                }
                if (received.compareTo(total) < 0) {
                    AlertUtil.warning("Chưa đủ tiền", "Số tiền khách đưa nhỏ hơn tổng tiền cần thanh toán.");
                    event.consume();
                    return;
                }
            }
            pendingResult[0] = new PaymentResult(method, discount,
                    DiscountPolicy.reasonToStore(discount, discountReasonField.getText()), total);
        });

        dialog.showAndWait();
        if (pendingResult[0] != null) {
            completeOrder(pendingResult[0]);
        }
    }

    /**
     * The block shown for "Chuyển khoản": the amount to transfer, the shop's QR picture (or a
     * clear note when the picture has not been added - never a made-up code), the order code to
     * use as the transfer note, and the "money received" tick box.
     */
    private VBox buildTransferBox(Label totalPreviewLabel, CheckBox transferReceivedBox) {
        Label title = new Label("Số tiền khách cần chuyển:");
        Label amount = new Label();
        amount.textProperty().bind(totalPreviewLabel.textProperty());
        amount.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        javafx.scene.Node picture;
        Optional<String> imageUrl = TransferQr.findImageUrl();
        javafx.scene.image.Image qrImage = imageUrl.map(u -> new javafx.scene.image.Image(u, 240, 240, true, true)).orElse(null);
        if (qrImage != null && !qrImage.isError()) {
            javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(qrImage);
            view.setFitWidth(240);
            view.setFitHeight(240);
            view.setPreserveRatio(true);
            picture = view;
        } else {
            Label missing = new Label((qrImage != null ? "Ảnh QR chuyển khoản bị lỗi hoặc không đọc được. " : "Chưa có ảnh QR chuyển khoản. ")
                    + "Hãy đặt ảnh tên \"qr-transfer.png\" vào thư mục:\n"
                    + DataPaths.dataDir() + "\nrồi mở lại cửa sổ thanh toán.");
            missing.setWrapText(true);
            missing.setMaxWidth(320);
            picture = missing;
        }

        VBox box = new VBox(8, title, amount, picture);
        // Who the money goes to, and a warning when the picture is not the one an administrator accepted (TODO 2A.17).
        java.util.Optional<java.nio.file.Path> pictureFile = com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.dataPicture();
        if (pictureFile.isPresent() && qrImage != null && !qrImage.isError()) {
            var state = com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.inspect(pictureFile.get());
            String holder = com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.accountInfo(pictureFile.get());
            if (!holder.isEmpty()) {
                Label who = new Label("Tài khoản nhận: " + holder);
                who.setWrapText(true);
                who.setMaxWidth(320);
                box.getChildren().add(who);
            }
            if (state == com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.State.CHANGED) {
                Label warn = new Label("⚠ Ảnh QR đã bị thay đổi so với ảnh đã được xác nhận. Đừng nhận tiền chuyển khoản "
                        + "cho tới khi quản trị viên kiểm tra ảnh này đúng là tài khoản của quán.");
                warn.setWrapText(true);
                warn.setMaxWidth(320);
                warn.setStyle("-fx-text-fill: #b33939; -fx-font-weight: bold;");
                box.getChildren().add(warn);
                if (Session.isAdmin()) {
                    Button accept = new Button("Ảnh này đúng, xác nhận");
                    accept.setOnAction(e -> {
                        try {
                            com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard.accept(pictureFile.get());
                            box.getChildren().remove(warn);
                            accept.setVisible(false);
                            accept.setManaged(false);
                        } catch (java.io.IOException ex) {
                            AlertUtil.error("Không xác nhận được", "Không ghi được dấu xác nhận cho ảnh QR.");
                        }
                    });
                    box.getChildren().add(accept);
                }
            }
        }
        if (activeOpenOrder != null && activeOpenOrder.getId() > 0) {
            Label note = new Label("Nội dung chuyển khoản: DH" + String.format("%06d", activeOpenOrder.getId()));
            note.setStyle("-fx-font-weight: bold;");
            box.getChildren().add(note);
        }
        box.getChildren().add(transferReceivedBox);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(6, 0, 0, 0));
        return box;
    }

    private BigDecimal parseNonNegative(String text) {
        return MoneyParser.parseOptionalDong(text);
    }

    private void completeOrder(PaymentResult payment) {
        final int tNum = selectedTableNumber;
        final List<OrderItem> items = new ArrayList<>(cart);
        final Customer knownCustomer = attachedCustomer;
        final String name = customerNameField != null && customerNameField.getText() != null ? customerNameField.getText().trim() : "";
        final String phone = customerPhoneField != null && customerPhoneField.getText() != null ? customerPhoneField.getText().trim() : "";
        final Order openOrder = activeOpenOrder;
        final BigDecimal subtotal = cartSubtotal();
        final Integer employeeId = Session.getCurrentEmployee() != null ? Session.getCurrentEmployee().getId() : null;
        final Role actingRole = Session.getCurrentRole(); // read here, on the screen thread; the service enforces the discount rule (TODO 6.45)
        paymentButton.setDisable(true);
        // Ordered so it runs after any autosave still in flight.
        Async.runOrdered(
                () -> checkoutService.pay(tNum, items, subtotal, knownCustomer, name, phone, openOrder,
                        new CheckoutService.Payment(payment.method(), payment.discount(), payment.discountReason(), payment.total()),
                        employeeId, actingRole),
                paid -> {
                    Order savedOrder = paid.order();
                    paymentButton.setDisable(false);
                    lastCompletedOrder = savedOrder;
                    lastCompletedItems = items;
                    lastCompletedCustomerName = paid.customer() != null ? paid.customer().getFullName() : null;
                    activeOpenOrder = null;
                    orderInProgress = false;
                    attachedCustomer = paid.customer();
                    String customerName = paid.customer() != null ? paid.customer().getFullName() : null;
                    // No "success" box any more: the receipt window opens with a green "ĐÃ THANH TOÁN" line (TODO 5.21).
                    openInvoiceWindow(savedOrder, items, customerName);
                    loadProducts();
                    cart.clear();
                    orderDetailTable.getItems().clear();
                    totalAmountLabel.setText("0 đ");
                    resetOrderAfterPayment();
                    loadTablesFromDb();
                },
                error -> {
                    paymentButton.setDisable(false);
                    LOGGER.log(Level.WARNING, "Payment failed", error);
                    AlertUtil.error("Lỗi thanh toán", "Không thể lưu đơn hàng: " + error.getMessage());
                    loadProducts();
                    loadTablesFromDb();
                }
        );
    }

    private boolean confirmLeaveOrder() {
        removeCloseGuard();
        // Let the last autosave reach the database before the window goes away (TODO 3.24).
        Async.flushOrdered(3000);
        return true;
    }

    private void installCloseGuard() {
        javafx.stage.Window window = paymentButton != null && paymentButton.getScene() != null
                ? paymentButton.getScene().getWindow() : null;
        if (window != null) {
            window.setOnCloseRequest(e -> {
                if (!confirmLeaveOrder()) e.consume();
            });
        }
    }

    private void removeCloseGuard() {
        javafx.stage.Window window = paymentButton != null && paymentButton.getScene() != null
                ? paymentButton.getScene().getWindow() : null;
        if (window != null) {
            window.setOnCloseRequest(null);
        }
    }

    private void resetOrderAfterPayment() {
        orderInProgress = false;
        attachedCustomer = null;
        cart.clear();
        refreshCart();
        orderStatusLabel.setText("Chưa có đơn");
        orderIdLabel.setText("-");
        if (customerNameField != null) customerNameField.clear();
        if (customerPhoneField != null) customerPhoneField.clear();
        showCustomerInfo(null, false);
    }

    private void openInvoiceWindow(Order order, List<OrderItem> items, String customerName) {
        HoaDonController controller = SceneNavigator.loadAndShowNewWindow(
                "/fxml/donthanhtoan.fxml", "Hóa đơn #" + order.getId());
        if (controller != null) {
            controller.setInvoiceData(order, items, Session.getDisplayName(), customerName);
        }
    }

    @FXML
    public void handleShowOrderHistory() {
        OrderHistoryWindow.show();
    }

    @FXML
    public void handlePrintInvoice() {
        if (lastCompletedOrder == null) {
            AlertUtil.warning("Chưa có hóa đơn",
                    "Đơn hàng hiện tại chưa được thanh toán nên chưa có hóa đơn để in.");
            return;
        }
        openInvoiceWindow(lastCompletedOrder, lastCompletedItems, lastCompletedCustomerName);
    }

    // =================================================================== Navigation
    // Duplicated from AdminScreen rather than shared - see the class javadoc.

    @FXML
    public void openDashboard(ActionEvent event) {
        if (!confirmLeaveOrder()) return;
        String target = Session.isAdmin() ? "/fxml/admin-trangchu.fxml" : "/fxml/employee-trangchu.fxml";
        SceneNavigator.switchScene(event, target);
    }

    @FXML
    public void openProductManagement(ActionEvent event) {
        if (!Session.isAdmin()) {
            AlertUtil.warning("Không đủ quyền", "Chức năng này chỉ dành cho Quản trị viên.");
            return;
        }
        if (!confirmLeaveOrder()) return;
        SceneNavigator.switchScene(event, "/fxml/quanlysanpham.fxml");
    }

    @FXML
    public void openCustomerManagement(ActionEvent event) {
        if (!confirmLeaveOrder()) return;
        SceneNavigator.switchScene(event, "/fxml/quanlykhachhang.fxml");
    }

    @FXML
    public void openCategoryManagement(ActionEvent event) {
        if (!Session.isAdmin()) {
            AlertUtil.warning("Không đủ quyền", "Chức năng này chỉ dành cho Quản trị viên.");
            return;
        }
        if (!confirmLeaveOrder()) return;
        SceneNavigator.switchScene(event, "/fxml/quanlydanhmuc.fxml");
    }

    @FXML
    public void openAccountManagement(ActionEvent event) {
        if (!Session.isAdmin()) {
            AlertUtil.warning("Không đủ quyền", "Chức năng này chỉ dành cho Quản trị viên.");
            return;
        }
        if (!confirmLeaveOrder()) return;
        SceneNavigator.switchScene(event, "/fxml/quanlytaikhoan.fxml");
    }

    @FXML
    public void logout(ActionEvent event) {
        if (!confirmLeaveOrder()) return;
        Session.clear();
        SceneNavigator.switchScene(event, "/fxml/dangnhap.fxml");
    }
}
