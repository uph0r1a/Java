package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.config.SeedNames;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptText;
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
import com.coffeeshop.coffeeshopmanagement.service.AttachedCustomerRule;
import com.coffeeshop.coffeeshopmanagement.service.Cart;
import com.coffeeshop.coffeeshopmanagement.service.QuantityInput;
import com.coffeeshop.coffeeshopmanagement.service.Limits;
import com.coffeeshop.coffeeshopmanagement.service.CheckoutService;
import com.coffeeshop.coffeeshopmanagement.service.CustomerService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.AutoRefresh;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.DiscountPolicy;
import com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy;
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
    /** A take-away order (no table) is on screen, or a new one is about to start (TODO 4.25); {@code takeAwayOrderId} is its saved order, once there is one. */
    private boolean takeAway;
    private Integer takeAwayOrderId;
    private boolean orderInProgress;
    private static final int REFRESH_SECONDS = 20;
    private Timeline sharedDataRefresh;
    @FXML
    private Label tableRefreshLabel;
    /**
     * True from the moment "Thanh toán" is confirmed until the save has finished (TODO 2.79). While it is true the whole screen
     * is disabled and the handlers below ignore keyboard shortcuts too, so nothing the cashier does can be wiped or mixed up
     * by the callback of a payment that is still being saved (a slow MariaDB link makes that seconds).
     */
    private boolean paymentInFlight;
    private Customer attachedCustomer;
    private Timeline realTimeClock;
    private Order activeOpenOrder;
    private Order lastCompletedOrder;
    private List<OrderItem> lastCompletedItems = List.of();
    /** Name of the customer on the last paid order, kept for "In lại hóa đơn" (TODO 2.46). */
    private String lastCompletedCustomerName;

    private record TableOrderData(Order order, List<OrderItem> items, Customer customer) {
    }

    /** {@code cashReceived} (cash only) and {@code transferRef} (transfer only) are saved with the order (TODO 3.17, 4.31). */
    private record PaymentResult(PaymentMethod method, BigDecimal discount, String discountReason, BigDecimal total,
                                 BigDecimal cashReceived, String transferRef, int pointsRedeemed, BigDecimal cashPart) {
    }

    // =================================================================== initialize

    @FXML
    private void initialize() {
        javafx.application.Platform.runLater(this::installCloseGuard);
        if (adminNameLabel != null) {
            adminNameLabel.setText(Session.getDisplayName());
        }
        if (adminRoleLabel != null) {
            adminRoleLabel.setText(I18n.t(Session.isAdmin() ? "role.admin" : "role.employee"));
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
            orderStatusLabel.setText(I18n.t("tables.status.free"));
            orderStatusLabel.getStyleClass().setAll("order-status-badge-empty");
        }
        if (orderIdLabel != null) orderIdLabel.setText(I18n.t("pos.order.none"));
        if (orderTableLabel != null) orderTableLabel.setText("-");
        if (totalAmountLabel != null) totalAmountLabel.setText(I18n.t("pos.money.zero"));

        initRealTimeClock();
        initCustomerInputs();
        loadTablesFromDb();
        startSharedDataRefresh();

        detailIndexColumn.setCellValueFactory(data -> new SimpleIntegerProperty(
                orderDetailTable.getItems().indexOf(data.getValue()) + 1));
        productNameColumn.setCellValueFactory(data -> new SimpleStringProperty(SeedNames.product(data.getValue().getProductName())));
        productNameColumn.setCellFactory(col -> nameWithNoteCell()); // the note under the name, and a button for it (TODO 4.9)
        quantityColumn.setCellFactory(col -> quantityCell());
        unitPriceColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyUtil.format(data.getValue().getUnitPrice())));
        subtotalColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyUtil.format(data.getValue().getLineTotal())));

        if (productSearchField != null) {
            TextLengthLimiter.limit(productSearchField, TextLengthLimiter.SEARCH_MAX);
            productSearchField.textProperty().addListener((obs, o, n) -> renderProductCatalog());
            productSearchField.setOnAction(e -> handleSearchEnter()); // a barcode scanner types the code and Enter (TODO 4.42)
        }

        setUpBestSellersToggle();
        installShortcuts();
        loadCategoriesAndProducts();
        loadRecentSales();
        refreshCart();
    }

    // =================================================================== Real-Time Clock
    /**
     * With two or more tills on one server (TODO 5.37) this screen would otherwise show the tables and the stock as they were
     * when it opened: a table another till just opened looks free, a dish that sold out there looks available. Every 20 seconds
     * the table map and the product list are read again. The selected table, the cart, the category and the search are kept;
     * nothing is reloaded while a payment is being saved; a failed refresh is only logged.
     */
    private void startSharedDataRefresh() {
        if (tableGrid != null) {
            sharedDataRefresh = AutoRefresh.every(tableGrid, Duration.seconds(REFRESH_SECONDS), this::refreshSharedData);
        }
    }

    private void refreshSharedData() {
        if (paymentInFlight || startingOrder) {
            return;
        }
        loadTablesFromDb(true);
        loadCategoriesAndProducts(true);
    }

    private void stampRefresh() {
        if (tableRefreshLabel != null) {
            tableRefreshLabel.setText(I18n.t("pos.updatedat", AppClock.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))));
            tableRefreshLabel.setVisible(true);
            tableRefreshLabel.setManaged(true);
        }
    }

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
            String nowStr = AppClock.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"));
            if (activeOpenOrder != null && activeOpenOrder.getOrderDate() != null) {
                orderTimeLabel.setText(I18n.t("pos.time.withorder", nowStr, activeOpenOrder.getOrderDate().format(DateTimeFormatter.ofPattern("HH:mm"))));
            } else {
                orderTimeLabel.setText(nowStr);
            }
        }
    }

    // =================================================================== Customer Auto-Link
    /** How long the phone box must be quiet before the number is looked up (TODO 5.29). */
    private static final double PHONE_LOOKUP_DELAY_MS = 250;

    private final javafx.animation.PauseTransition phoneLookupDelay =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(PHONE_LOOKUP_DELAY_MS));

    private void initCustomerInputs() {
        TextLengthLimiter.limit(customerNameField, TextLengthLimiter.NAME_MAX);
        TextLengthLimiter.limit(customerPhoneField, 15);
        if (customerPhoneField != null) {
            customerPhoneField.textProperty().addListener((obs, oldVal, newVal) -> {
                String typed = newVal == null ? "" : newVal.trim();
                // Editing the phone away from the attached customer detaches them, so the order can never be paid under the
                // previous person's loyalty account. Also for a customer saved without a phone (TODO 2.70).
                if (attachedCustomer != null && !AttachedCustomerRule.stillMatches(attachedCustomer,
                        customerNameField != null ? customerNameField.getText() : "", typed)) {
                    attachedCustomer = null;
                    syncOpenOrderToDb();
                }
                // Look up only a complete, valid number and only once the typing has paused (TODO 5.29): "+84 901 234 567" typed
                // or pasted used to start up to seven lookups, each its own thread and, on a MariaDB server, its own round trip.
                phoneLookupDelay.stop();
                if (attachedCustomer == null) {
                    showCustomerInfo(null, false);
                }
                if (ValidationUtil.isValidPhone(typed)) {
                    phoneLookupDelay.setOnFinished(e -> {
                        String current = customerPhoneField.getText() == null ? "" : customerPhoneField.getText().trim();
                        if (ValidationUtil.isValidPhone(current)) {
                            lookupCustomerByPhone(current);
                        }
                    });
                    phoneLookupDelay.playFromStart();
                }
            });
        }
        if (customerNameField != null) {
            customerNameField.textProperty().addListener((obs, oldVal, newVal) -> {
                // Editing the name away from the attached customer detaches them as well (TODO 2.70): the till never renames a
                // customer, so a different name means a different person. The phone, if it is a known number, finds them again.
                if (attachedCustomer != null && !AttachedCustomerRule.stillMatches(attachedCustomer, newVal,
                        customerPhoneField != null ? customerPhoneField.getText() : "")) {
                    attachedCustomer = null;
                    syncOpenOrderToDb();
                    String phone = customerPhoneField != null && customerPhoneField.getText() != null ? customerPhoneField.getText().trim() : "";
                    if (ValidationUtil.isValidPhone(phone)) {
                        lookupCustomerByPhone(phone);
                    } else {
                        showCustomerInfo(null, false);
                    }
                }
            });
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
                ? I18n.t("pos.customer.known", customer.getFullName(), customer.getLoyaltyPoints())
                : newCustomer ? I18n.t("pos.customer.new") : "";
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
            customerInfoLabel.setText(I18n.t("pos.customer.nophone"));
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
        loadCategoriesAndProducts(false);
    }

    private void loadCategoriesAndProducts(boolean quiet) {
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
            renderProductCatalog();
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
        bestSellersToggle = new javafx.scene.control.ToggleButton(I18n.t("pos.bestsellers"));
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
        productSearchField.setTooltip(new javafx.scene.control.Tooltip(I18n.t("pos.tip.search")));
        if (paymentButton != null) paymentButton.setTooltip(new javafx.scene.control.Tooltip(I18n.t("pos.tip.pay")));
    }

    private void loadRecentSales() {
        Async.run(
                () -> new com.coffeeshop.coffeeshopmanagement.dao.ReportDAO()
                        .unitsSold(AppClock.today().minusDays(30), AppClock.today()),
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
                outOfStock.setStyle("-fx-text-fill: #b33939; -fx-font-size: 10px; -fx-font-weight: bold;");
                card.getChildren().add(outOfStock);
                card.setOpacity(0.55);
            } else {
                if (product.isLowStock()) {
                    // Low-stock badge (TODO 5.6): the cashier sees it before promising the last cups.
                    Label low = new Label(I18n.t("pos.stock.low", product.getStock()));
                    low.setStyle("-fx-text-fill: #b26a00; -fx-font-size: 10px; -fx-font-weight: bold;");
                    card.getChildren().add(low);
                }
                card.setOnMouseClicked(e -> handleQuickAddProduct(product));
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
        if (paymentInFlight || productSearchField == null) return;
        String typed = productSearchField.getText();
        if (!com.coffeeshop.coffeeshopmanagement.service.ProductCode.looksLikeCode(typed)) return;
        String code = com.coffeeshop.coffeeshopmanagement.service.ProductCode.clean(typed);
        Product onSale = com.coffeeshop.coffeeshopmanagement.service.SellableMenu.filter(allProducts, allCategories).stream()
                .filter(p -> code.equals(p.getCode())).findFirst().orElse(null);
        if (onSale != null) {
            productSearchField.clear();
            handleQuickAddProduct(onSale);
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
        productSearchField.selectAll(); // the next scan replaces it
    }

    private void handleQuickAddProduct(Product product) {
        if (paymentInFlight) {
            return; // a payment is being saved (TODO 2.79)
        }
        if (selectedTableNumber == null && !takeAway) {
            AlertUtil.warning(I18n.t("pos.notable.title"), I18n.t("pos.notable.order"));
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
            AlertUtil.error(I18n.t("common.error.title"), I18n.t("pos.load.products", e.getMessage()));
        }
    }

    /**
     * The name column of the cart (TODO 4.9): the product name, under it the line's note in a smaller italic when it has one, and a
     * pencil button that opens the note box ({@link OrderNoteDialog}). The note is part of the line, so it is saved with the order by
     * the same autosave as the quantity.
     */
    private TableCell<OrderItem, String> nameWithNoteCell() {
        return new TableCell<>() {
            private final Label nameLabel = new Label();
            private final Label noteLabel = new Label();
            private final Button editButton = new Button("\u270E");
            private final VBox texts = new VBox(1, nameLabel, noteLabel);
            private final HBox box = new HBox(6, texts, editButton);

            {
                nameLabel.setWrapText(true);
                noteLabel.setWrapText(true);
                noteLabel.setStyle("-fx-text-fill: #8a5a00; -fx-font-size: 11px; -fx-font-style: italic;");
                texts.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(texts, Priority.ALWAYS);
                editButton.setFocusTraversable(false);
                editButton.setTooltip(new javafx.scene.control.Tooltip(I18n.t("pos.note.tip")));
                editButton.setOnAction(e -> {
                    if (getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                        editNote(getTableView().getItems().get(getIndex()));
                    }
                });
                box.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String note = getTableView().getItems().get(getIndex()).getNote();
                nameLabel.setText(name);
                noteLabel.setText(note == null ? "" : "\u21B3 " + note);
                noteLabel.setVisible(note != null);
                noteLabel.setManaged(note != null);
                setText(null);
                setGraphic(box);
            }
        };
    }

    /** Asks for a new note for one cart line and puts it on the line (TODO 4.9); the cart is saved and drawn again. */
    private void editNote(OrderItem line) {
        if (paymentInFlight || line == null) {
            return; // a payment is being saved (TODO 2.79)
        }
        java.util.Optional<String> typed = OrderNoteDialog.show(SeedNames.product(line.getProductName()), line.getNote());
        if (typed.isEmpty()) {
            return;
        }
        cartRules.setNote(line, typed.get());
        refreshCart();
    }

    /** Renders "[-]  qty  [+]" inline in the quantity column; a click on the number turns it into a small box to type a quantity (TODO 5.27). */
    private TableCell<OrderItem, Void> quantityCell() {
        return new TableCell<>() {
            private final Button minus = new Button("−");
            private final Button plus = new Button("+");
            private final Label qtyLabel = new Label();
            private final TextField qtyField = new TextField();
            private final HBox box = new HBox(4, minus, qtyLabel, plus);
            private boolean typing;

            {
                box.setAlignment(Pos.CENTER);
                minus.setOnAction(e -> adjustQuantity(getTableView().getItems().get(getIndex()), -1));
                plus.setOnAction(e -> adjustQuantity(getTableView().getItems().get(getIndex()), 1));
                qtyLabel.setCursor(javafx.scene.Cursor.HAND);
                qtyLabel.setTooltip(new javafx.scene.control.Tooltip(I18n.t("pos.qty.type.hint")));
                qtyLabel.setOnMouseClicked(e -> startTyping());
                qtyField.setPrefColumnCount(3);
                qtyField.setMaxWidth(54);
                // digits only, at most three (QuantityInput); the stock limit is applied by changeQuantity, as for "+"
                qtyField.setTextFormatter(new javafx.scene.control.TextFormatter<String>(change ->
                        QuantityInput.sanitize(change.getControlNewText()).equals(change.getControlNewText()) ? change : null));
                qtyField.setOnAction(e -> finishTyping(true));
                qtyField.setOnKeyPressed(e -> {
                    if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                        finishTyping(false);
                    }
                });
                qtyField.focusedProperty().addListener((o, was, now) -> {
                    if (!now) {
                        finishTyping(true);
                    }
                });
            }

            private void startTyping() {
                if (typing || isEmpty() || paymentInFlight) {
                    return;
                }
                typing = true;
                qtyField.setText(qtyLabel.getText());
                box.getChildren().set(1, qtyField);
                qtyField.requestFocus();
                qtyField.selectAll();
            }

            /** {@code apply}: use what was typed; otherwise (Esc) leave the quantity as it was. */
            private void finishTyping(boolean apply) {
                if (!typing) {
                    return;
                }
                typing = false; // first, so the focus change below cannot run this twice
                box.getChildren().set(1, qtyLabel);
                if (apply && getIndex() >= 0 && getIndex() < getTableView().getItems().size()) {
                    QuantityInput.parse(qtyField.getText()).ifPresent(
                            n -> changeQuantity(getTableView().getItems().get(getIndex()), n));
                }
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    typing = false;
                    setGraphic(null);
                } else {
                    if (typing) {
                        typing = false;
                        box.getChildren().set(1, qtyLabel);
                    }
                    qtyLabel.setText(String.valueOf(getTableView().getItems().get(getIndex()).getQuantity()));
                    setGraphic(box);
                }
            }
        };
    }

    // =================================================================== Table selection & management (3 CỘT x 4 DÒNG)

    public void loadTablesFromDb() {
        loadTablesFromDb(false);
    }

    /** {@code quiet}: a background refresh (TODO 5.37) logs a failure instead of opening an error box every few seconds. */
    private void loadTablesFromDb(boolean quiet) {
        if (tableGrid == null) return;
        Async.run(
                () -> Map.entry(tableDAO.findAll(), orderDAO.findOpenTakeAway()),
                loaded -> {
                    List<DiningTable> tables = loaded.getKey();
                    tableGrid.getChildren().clear();
                    tableButtonByNumber.clear();
                    tableDataByNumber.clear();
                    if (tables.isEmpty()) { // no table at all: say what to do instead of showing an empty square
                        tableGrid.add(com.coffeeshop.coffeeshopmanagement.util.DashboardWidgets.emptyTablesNote(),
                                0, 0, com.coffeeshop.coffeeshopmanagement.service.TableLayout.COLUMNS, 1);
                    }
                    int index = 0;
                    for (DiningTable table : tables) {
                        int num = table.getTableNumber();
                        tableDataByNumber.put(num, table);
                        // the place comes from the position in the list, not from the number (TODO 3.48)
                        com.coffeeshop.coffeeshopmanagement.service.TableLayout.Cell cell =
                                com.coffeeshop.coffeeshopmanagement.service.TableLayout.cellAt(index++);
                        Button btn = TableMapButtons.forTable(table, selectedTableNumber != null && selectedTableNumber == num);
                        btn.setOnAction(e -> selectTable(num));
                        tableGrid.add(btn, cell.column(), cell.row());
                        tableButtonByNumber.put(num, btn);
                    }
                    TakeAwayPanel.addButtons(tableGrid, tables.isEmpty() ? com.coffeeshop.coffeeshopmanagement.service.TableLayout.COLUMNS : index, loaded.getValue(), takeAway && takeAwayOrderId == null, takeAwayOrderId,
                            this::selectTakeAway, () -> selectTakeAway(null)); // "Mang đi" and the open take-away orders (TODO 4.25)
                    if (selectedTableNumber != null && !tableDataByNumber.containsKey(selectedTableNumber)) {
                        selectedTableNumber = null; // the table was removed on another till (TODO 4.18): never keep selling on it
                    }
                    if (selectedTableNumber == null && !takeAway && !tables.isEmpty()) {
                        selectTable(tables.get(0).getTableNumber());
                    } else if (selectedTableNumber != null) {
                        updateSelectedTableInfo(selectedTableNumber);
                    }
                    if (quiet) {
                        stampRefresh();
                    }
                },
                error -> {
                    if (quiet) {
                        LOGGER.log(Level.FINE, "Table refresh failed", error);
                    } else {
                        AlertUtil.error(I18n.t("common.error.title"), I18n.t("pos.load.tables", error.getMessage()));
                    }
                }
        );
    }

    public void selectTable(int number) {
        if (paymentInFlight) {
            return; // a payment is being saved (TODO 2.79)
        }
        selectedTableNumber = number;
        takeAway = false;
        takeAwayOrderId = null;
        highlightSelectedTableButton(number);
        updateSelectedTableInfo(number);
        loadTableOrderFromDb(number);
    }

    private void updateSelectedTableInfo(int number) {
        if (selectedTableLabel != null) {
            DiningTable named = tableDataByNumber.get(number);
            selectedTableLabel.setText(named != null ? named.displayName() : I18n.t("orders.table", number)); // the shop's own name (TODO 4.18)
        }
        DiningTable table = tableDataByNumber.get(number);
        boolean occupied = table != null && table.isOccupied();
        if (selectedTableStatusLabel != null) {
            selectedTableStatusLabel.setText(I18n.t(occupied ? "tables.status.occupied" : "tables.status.free"));
            selectedTableStatusLabel.getStyleClass().setAll(occupied ? "occupied-badge" : "empty-badge");
        }
        if (selectedTableCapacityLabel != null && table != null) {
            selectedTableCapacityLabel.setText(I18n.t("pos.capacity", table.getCapacity()));
        }
    }

    /** {@code number} null = the take-away order {@link #takeAwayOrderId} (or none yet), TODO 4.25. */
    private void loadTableOrderFromDb(Integer number) {
        final Integer wantedTakeAway = takeAwayOrderId;
        Async.run(
                () -> {
                    Optional<Order> openOrder = number != null ? orderDAO.findOpenOrderByTable(number)
                            : wantedTakeAway == null ? Optional.<Order>empty() : orderDAO.findOpenTakeAway().stream().filter(o -> o.getId() == wantedTakeAway).findFirst();
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
                    if (!java.util.Objects.equals(selectedTableNumber, number) || (number == null && !java.util.Objects.equals(takeAwayOrderId, wantedTakeAway))) return;
                    DiningTable table = tableDataByNumber.get(number);
                    boolean occupied = table != null && table.isOccupied();
                    Order order = data.order();
                    boolean serving = order != null || occupied; // an order, or a table marked taken with no order saved yet
                    activeOpenOrder = order;
                    attachedCustomer = data.customer();
                    cart.setAll(data.items());
                    orderInProgress = serving;
                    if (orderStatusLabel != null) {
                        orderStatusLabel.setText(I18n.t(serving ? "pos.order.serving" : "tables.status.free"));
                        orderStatusLabel.getStyleClass().setAll(serving ? "order-status-badge" : "order-status-badge-empty");
                    }
                    if (orderIdLabel != null) {
                        orderIdLabel.setText(order != null ? com.coffeeshop.coffeeshopmanagement.util.OrderNumber.format(order.getId())
                                : I18n.t(occupied ? "pos.order.serving" : "pos.order.none"));
                    }
                    if (orderTableLabel != null) {
                        orderTableLabel.setText(TakeAwayPanel.tableLabel(number));
                    }
                    updateClockDisplay();
                    Customer known = data.customer();
                    if (customerNameField != null) {
                        customerNameField.setText(known != null ? known.getFullName() : serving ? I18n.t("pos.walkin") : "");
                    }
                    if (customerPhoneField != null) {
                        customerPhoneField.setText(known != null && known.getPhone() != null ? known.getPhone() : "");
                    }
                    if (order != null) {
                        showCustomerInfo(known, false);
                    }
                    orderDetailTable.getItems().setAll(cart);
                    totalAmountLabel.setText(order != null ? CurrencyUtil.format(cartSubtotal()) : I18n.t("pos.money.zero"));
                    if (cartItemCountLabel != null) {
                        long totalQty = cartRules.totalQuantity();
                        cartItemCountLabel.setText(I18n.t("pos.cart.count", cart.size(), totalQty));
                    }
                },
                error -> {
                    LOGGER.log(Level.WARNING, "Could not load the order of table " + number, error);
                    AlertUtil.error(I18n.t("common.error.title"), I18n.t("pos.load.order", number, error.getMessage()));
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

    /** The till moves to a take-away order (TODO 4.25): a saved one ({@code orderId}), or a new one that is saved at the first product. */
    private void selectTakeAway(Integer orderId) {
        if (paymentInFlight) return;
        selectedTableNumber = null;
        takeAway = true;
        takeAwayOrderId = orderId;
        TakeAwayPanel.showOn(selectedTableLabel, selectedTableStatusLabel, selectedTableCapacityLabel, orderId != null);
        loadTableOrderFromDb(null);
        loadTablesFromDb();
    }

    @FXML
    public void handleToggleTableStatus() {
        if (paymentInFlight) {
            return; // a payment is being saved (TODO 2.79)
        }
        if (selectedTableNumber == null) {
            if (takeAway && activeOpenOrder != null) { // the take-away way of emptying a table: cancel the unpaid order (TODO 4.25)
                TakeAwayPanel.cancel(activeOpenOrder, () -> selectTakeAway(null));
                return;
            }
            AlertUtil.info(I18n.t("pos.notable.title"), I18n.t("pos.notable.toggle"));
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
            AlertUtil.warning(I18n.t("admin.only.title"),
                    com.coffeeshop.coffeeshopmanagement.service.TablePolicy.refusedMessage());
            return;
        }
        // setEmpty() also cancels the table's OPEN order, so never do that silently.
        if (!newOccupied && activeOpenOrder != null && !cart.isEmpty()
                && !AlertUtil.confirm(I18n.t("pos.cancel.title"),
                I18n.t("pos.cancel.message", tNum))) {
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
                    AlertUtil.info(I18n.t("common.success"),
                            I18n.t("pos.toggle.done", tNum, I18n.t(newOccupied ? "tables.status.occupied" : "tables.status.free")));
                },
                error -> AlertUtil.error(I18n.t("common.error.title"), I18n.t("pos.toggle.fail", error.getMessage()))
        );
    }

    @FXML
    public void handleTableDetail() {
        if (selectedTableNumber == null) {
            AlertUtil.info(I18n.t("pos.notable.title"), I18n.t("pos.notable.detail"));
            return;
        }
        DiningTable table = tableDataByNumber.get(selectedTableNumber);
        String status = I18n.t((table != null && table.isOccupied()) ? "tables.status.occupied" : "tables.status.free");
        AlertUtil.info(I18n.t("pos.detail.title", selectedTableLabel.getText()),
                (table != null ? I18n.t("pos.capacity", table.getCapacity()) + "\n" : "") +
                I18n.t("pos.detail.status", status));
    }

    @FXML
    public void handleStartOrder() {
        if (paymentInFlight) {
            return; // a payment is being saved (TODO 2.79)
        }
        if (selectedTableNumber == null && !takeAway) {
            AlertUtil.warning(I18n.t("pos.notable.title"), I18n.t("pos.notable.start"));
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
    private void startOrderForTable(Integer tNum, Runnable afterStart) { // null: a take-away order, no table (TODO 4.25)
        if (startingOrder) return;
        startingOrder = true;
        final String name = customerNameField != null && customerNameField.getText() != null ? customerNameField.getText().trim() : "";
        final String phone = customerPhoneField != null && customerPhoneField.getText() != null ? customerPhoneField.getText().trim() : "";
        Async.runOrdered(
                () -> {
                    Customer cust = ensureCustomerInDb(name, phone);
                    Order order = new Order();
                    order.setOrderDate(AppClock.now()); // the same clock as paid_at (TODO 6.50)
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
                    if (tNum == null) takeAwayOrderId = started.order().getId();
                    attachedCustomer = started.customer();
                    showCustomerInfo(started.customer(), false);
                    orderInProgress = true;
                    cart.clear();
                    if (orderStatusLabel != null) {
                        orderStatusLabel.setText(I18n.t("pos.order.serving"));
                        orderStatusLabel.getStyleClass().setAll("order-status-badge");
                    }
                    if (orderIdLabel != null) {
                        orderIdLabel.setText(com.coffeeshop.coffeeshopmanagement.util.OrderNumber.format(started.order().getId()));
                    }
                    if (orderTableLabel != null) {
                        orderTableLabel.setText(TakeAwayPanel.tableLabel(tNum));
                    }
                    updateClockDisplay();
                    orderDetailTable.getItems().setAll(cart);
                    totalAmountLabel.setText(I18n.t("pos.money.zero"));
                    loadTablesFromDb();
                    if (afterStart != null) afterStart.run();
                },
                error -> {
                    startingOrder = false;
                    LOGGER.log(Level.WARNING, "Could not start order for table " + tNum, error);
                    AlertUtil.error(I18n.t("common.error.title"), I18n.t("pos.start.fail", TakeAwayPanel.tableLabel(tNum), error.getMessage()));
                }
        );
    }

    // =================================================================== Cart

    private void addToCart(Product product) {
        if (paymentInFlight) {
            return; // a payment is being saved (TODO 2.79)
        }
        Cart.Result result = cartRules.add(product);
        if (result.outcome() == Cart.Outcome.LINE_LIMIT) {
            AlertUtil.warning(I18n.t("pos.qty.limit.title"), I18n.t("pos.qty.limit.message", Limits.MAX_LINE_QUANTITY));
            return;
        }
        if (result.outcome() == Cart.Outcome.NOT_ENOUGH_STOCK) {
            AlertUtil.warning(I18n.t("pos.stock.short.title"),
                    I18n.t("pos.stock.short.message", SeedNames.product(product.getName()), result.stock()));
            return;
        }
        refreshCart();
    }

    private void adjustQuantity(OrderItem line, int delta) {
        changeQuantity(line, line.getQuantity() + delta);
    }

    /**
     * The one place that decides whether a cart line may get a new quantity: "+", "−" and a typed number (TODO 5.27) all come
     * here, so the stock warning and the "remove the line?" question cannot be skipped by typing.
     */
    private void changeQuantity(OrderItem line, int newQty) {
        if (paymentInFlight) {
            return; // a payment is being saved (TODO 2.79)
        }
        int current = line.getQuantity();
        if (newQty == current) {
            return;
        }
        if (newQty > current) {
            if (Cart.exceedsLineLimit(newQty)) { // TODO 3.59: same ceiling for "+" and a typed number
                AlertUtil.warning(I18n.t("pos.qty.limit.title"), I18n.t("pos.qty.limit.message", Limits.MAX_LINE_QUANTITY));
                return;
            }
            Product product = findProductById(line.getProductId());
            if (cartRules.exceedsStock(product, line, newQty)) { // counts the product's other lines too (TODO 4.9)
                AlertUtil.warning(I18n.t("pos.stock.short.title"),
                        I18n.t("pos.stock.short.message", SeedNames.product(line.getProductName()), product.getStock()));
                return;
            }
        }
        // Taking the last cup off a line (with "−" or by typing 0) removes the line: ask first, one mis-tap should not lose it (TODO 5.5).
        if (newQty < current && newQty <= 0 && !AlertUtil.confirm(I18n.t("pos.remove.title"),
                I18n.t("pos.remove.message", SeedNames.product(line.getProductName())))) {
            return;
        }
        setLineQuantity(line, newQty);
    }

    private void setLineQuantity(OrderItem item, int newQty) {
        if (paymentInFlight) {
            return; // a payment is being saved (TODO 2.79)
        }
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
            long totalQty = cartRules.totalQuantity();
            cartItemCountLabel.setText(I18n.t("pos.cart.count", cart.size(), totalQty));
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
                            AlertUtil.warning(I18n.t("pos.closed.title"), I18n.t("pos.closed.message"));
                            loadTablesFromDb();
                            if (selectedTableNumber != null || takeAway) loadTableOrderFromDb(selectedTableNumber);
                        }
                    },
                    err -> {
                        LOGGER.log(Level.WARNING, "Autosave of the open order failed", err);
                        if (!autosaveWarningShown) {
                            autosaveWarningShown = true;
                            AlertUtil.warning(I18n.t("pos.autosave.title"), I18n.t("pos.autosave.message", err.getMessage()));
                        }
                    }
            );
        }
    }

    @FXML
    public void handleEditOrder() {
        if (paymentInFlight) {
            return; // a payment is being saved (TODO 2.79)
        }
        if (!orderInProgress) {
            AlertUtil.warning(I18n.t("pos.noorder.title"), I18n.t("pos.noorder.message"));
            return;
        }
        // A search box over at most 20 rows found by the database, not a drop-down with every customer (TODO 3.52).
        CustomerPickerDialog.choose(attachedCustomer).ifPresent(customer -> {
            attachedCustomer = customer;
            if (customerNameField != null) customerNameField.setText(customer.getFullName());
            if (customerPhoneField != null) customerPhoneField.setText(customer.getPhone() != null ? customer.getPhone() : "");
            syncOpenOrderToDb();
        });
    }

    // =================================================================== Payment / checkout

    @FXML
    public void handlePayment() {
        if (paymentInFlight) {
            return; // a payment is being saved (TODO 2.79)
        }
        if (!orderInProgress) {
            AlertUtil.warning(I18n.t("pos.noorder.title"), I18n.t("pos.noorder.pay"));
            return;
        }
        if (cart.isEmpty()) {
            AlertUtil.warning(I18n.t("pos.empty.title"), I18n.t("pos.empty.message"));
            return;
        }
        // An account locked mid-shift must not be able to complete a sale.
        if (!SessionGuard.validateNow()) {
            cart.clear();
            SessionGuard.forceLogout();
            return;
        }
        verifyStockThenOpenPayment();
    }

    /**
     * TODO 3.1 (small version): the cart holds no stock, so before the payment box opens the stock of the products in it is read
     * again. If an item ran out (another table sold the last one, the list on screen was 20 seconds old, or the product was
     * switched off) the cashier is told which one, with nothing charged, and the product tiles are refreshed. The screen is locked
     * while the one short query runs, the way it is while a payment is saved, so a second click cannot open two boxes. This is only
     * a warning: the payment itself still refuses a sale the stock cannot cover. If the re-read fails (database unreachable) the
     * box opens anyway and the payment will say what is wrong.
     */
    private void verifyStockThenOpenPayment() {
        final List<OrderItem> lines = new ArrayList<>(cart);
        setPaymentInFlight(true);
        Async.run(
                () -> com.coffeeshop.coffeeshopmanagement.service.StockCheck.check(lines, productDAO),
                shortages -> {
                    setPaymentInFlight(false);
                    if (shortages.isEmpty()) {
                        openPaymentDialog();
                        return;
                    }
                    StringBuilder message = new StringBuilder();
                    for (com.coffeeshop.coffeeshopmanagement.service.StockCheck.Shortage shortage : shortages) {
                        String name = SeedNames.product(shortage.name());
                        message.append(shortage.kind() == com.coffeeshop.coffeeshopmanagement.service.StockCheck.Kind.UNAVAILABLE
                                ? I18n.t("pos.stock.recheck.off", name)
                                : I18n.t("pos.stock.recheck.short", name, shortage.wanted(), shortage.available())).append('\n');
                    }
                    message.append('\n').append(I18n.t("pos.stock.recheck.footer"));
                    AlertUtil.warning(I18n.t("pos.stock.short.title"), message.toString());
                    loadCategoriesAndProducts(true); // the tiles and the "+" limits show the stock that was just found
                },
                error -> {
                    setPaymentInFlight(false);
                    LOGGER.log(Level.WARNING, "The stock could not be re-read before the payment box", error);
                    openPaymentDialog();
                });
    }

    private void openPaymentDialog() {
        BigDecimal subtotal = cartSubtotal();

        Dialog<PaymentResult> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("pos.pay.title"));
        AlertUtil.configure(dialog);
        ButtonType confirmButtonType = new ButtonType(I18n.t("pos.pay.confirm"), ButtonData.OK_DONE);
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
        // The e-wallets (TODO 4.34): one radio each, in a second row, so the report can tell them from a bank transfer.
        java.util.Map<PaymentMethod, RadioButton> walletRadios = new java.util.LinkedHashMap<>();
        for (PaymentMethod wallet : PaymentMethod.eWallets()) {
            RadioButton walletRadio = new RadioButton(wallet.label());
            walletRadio.setToggleGroup(methodGroup);
            walletRadios.put(wallet, walletRadio);
        }
        // What is chosen now; cash when nothing is (cannot happen: cash starts selected and a toggle group keeps one).
        java.util.function.Supplier<PaymentMethod> chosenMethod = () -> {
            for (java.util.Map.Entry<PaymentMethod, RadioButton> entry : walletRadios.entrySet()) {
                if (entry.getValue().isSelected()) return entry.getKey();
            }
            return cashRadio.isSelected() ? PaymentMethod.CASH : transferRadio.isSelected() ? PaymentMethod.TRANSFER : PaymentMethod.CARD;
        };

        TextField discountField = new TextField("0");
        TextField discountReasonField = new TextField();
        discountReasonField.setPromptText(I18n.t("pos.discount.reason.prompt"));
        TextLengthLimiter.limit(discountReasonField, DiscountPolicy.REASON_MAX_LENGTH);
        // Only worth asking for a reason when there is a discount.
        discountReasonField.setDisable(true);
        discountField.textProperty().addListener((obs, o, n) -> {
            BigDecimal typed = parseNonNegative(n);
            discountReasonField.setDisable(typed == null || typed.signum() == 0);
        });
        Role currentRole = Session.getCurrentRole();
        BigDecimal maxDiscount = DiscountPolicy.maxDiscount(currentRole, subtotal);
        Label discountHint = new Label(currentRole == Role.ADMIN ? I18n.t("pos.discount.admin")
                : I18n.t("pos.discount.staff", DiscountPolicy.STAFF_MAX_PERCENT, CurrencyUtil.format(maxDiscount)));
        discountHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #5b7e45;");
        TextField cashReceivedField = new TextField();
        TextLengthLimiter.limit(discountField, TextLengthLimiter.NUMBER_MAX);
        TextLengthLimiter.limit(cashReceivedField, TextLengthLimiter.NUMBER_MAX);
        cashReceivedField.disableProperty().bind(cashRadio.selectedProperty().not()); // only cash has "tiền khách đưa"
        Label totalPreviewLabel = new Label(CurrencyUtil.format(subtotal));
        Label changePreviewLabel = new Label(CurrencyUtil.format(BigDecimal.ZERO));
        SplitPaymentPanel split = new SplitPaymentPanel(subtotal); // part in cash, the rest with another method (TODO 4.32)

        // Spending loyalty points (TODO 4.16): only for a customer already attached to this sale, and only when they have some. The
        // balance is read again now (the attached object can be older than the last sale). The database checks it once more at payment.
        final Customer pointsCustomer = attachedCustomer;
        final int pointsBalance = pointsCustomer == null ? 0 : currentPoints(pointsCustomer);
        final boolean canSpendPoints = pointsCustomer != null && pointsBalance > 0;
        TextField pointsField = new TextField("0");
        TextLengthLimiter.limit(pointsField, 9);
        Label pointsHint = new Label();
        pointsHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #5b7e45;");
        Button pointsMaxButton = new Button(I18n.t("pos.points.max"));
        // The points that count in the preview: what is typed when it is allowed, else none (the confirm button says why not).
        java.util.function.IntSupplier previewPoints = () -> {
            Integer typed = LoyaltyPolicy.parsePoints(pointsField.getText());
            BigDecimal discountNow = parseNonNegative(discountField.getText());
            if (!canSpendPoints || typed == null || discountNow == null || discountNow.compareTo(subtotal) > 0) return 0;
            return LoyaltyPolicy.redemptionProblem(typed, pointsBalance, subtotal.subtract(discountNow)) == null ? typed : 0;
        };
        Runnable refreshPointsHint = () -> {
            BigDecimal discountNow = parseNonNegative(discountField.getText());
            BigDecimal after = discountNow == null || discountNow.compareTo(subtotal) > 0 ? subtotal : subtotal.subtract(discountNow);
            pointsHint.setText(I18n.t("pos.points.hint", pointsBalance, CurrencyUtil.format(LoyaltyPolicy.VND_PER_POINT_SPENT),
                    LoyaltyPolicy.maxRedeemable(pointsBalance, after)));
        };
        refreshPointsHint.run();
        pointsMaxButton.setOnAction(e -> {
            BigDecimal discountNow = parseNonNegative(discountField.getText());
            BigDecimal after = discountNow == null || discountNow.compareTo(subtotal) > 0 ? subtotal : subtotal.subtract(discountNow);
            pointsField.setText(String.valueOf(LoyaltyPolicy.maxRedeemable(pointsBalance, after)));
        });
        pointsField.setDisable(!canSpendPoints);
        pointsMaxButton.setDisable(!canSpendPoints);

        // Quick cash buttons (TODO 5.1): "Đủ tiền" fills the exact total, the others a common note; a note below the total is disabled.
        BigDecimal[] currentTotal = {subtotal};
        Button exactButton = new Button(I18n.t("pos.pay.exact"));
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
            BigDecimal total = subtotal.subtract(discount).subtract(LoyaltyPolicy.redemptionOf(previewPoints.getAsInt()).value());
            currentTotal[0] = total;
            split.totalChanged(total);
            noteButtons.forEach((button, value) -> button.setDisable(value.compareTo(total) < 0));
            totalPreviewLabel.setText(CurrencyUtil.format(total));
            BigDecimal received = parseNonNegative(cashReceivedField.getText());
            BigDecimal change = received != null ? received.subtract(total) : null;
            changePreviewLabel.setText(change != null && change.compareTo(BigDecimal.ZERO) >= 0
                    ? CurrencyUtil.format(change) : "-");
        };
        discountField.textProperty().addListener((obs, o, n) -> {
            refreshPointsHint.run();
            updatePreview.run();
        });
        pointsField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        cashReceivedField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        cardRadio.selectedProperty().addListener((obs, o, n) -> updatePreview.run());
        transferRadio.selectedProperty().addListener((obs, o, n) -> updatePreview.run());
        updatePreview.run();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label(I18n.t("pos.pay.subtotal")), new Label(CurrencyUtil.format(subtotal)));
        grid.addRow(1, new Label(I18n.t("pos.pay.discount")), new VBox(2, discountField, discountHint));
        grid.addRow(2, new Label(I18n.t("pos.pay.discount.reason")), discountReasonField);
        if (canSpendPoints) {
            grid.addRow(3, new Label(I18n.t("pos.points.label")), new VBox(2, new HBox(8, pointsField, pointsMaxButton), pointsHint));
        } else if (pointsCustomer != null) {
            grid.addRow(3, new Label(I18n.t("pos.points.label")), new Label(I18n.t("pos.points.none")));
        } else {
            grid.addRow(3, new Label(I18n.t("pos.points.label")), new Label(I18n.t("pos.points.attach")));
        }
        grid.addRow(4, new Label(I18n.t("pos.pay.total")), totalPreviewLabel);
        grid.addRow(5, new Label(I18n.t("pos.pay.method")), new VBox(6, new HBox(14, cashRadio, cardRadio, transferRadio),
                new HBox(14, walletRadios.values().toArray(new RadioButton[0]))));
        grid.addRow(6, new Label(I18n.t("pos.pay.cash")), new VBox(6, cashReceivedField, quickCash));
        grid.addRow(7, new Label(I18n.t("pos.pay.change")), changePreviewLabel);

        // Bank transfer: the shop's own QR picture and the amount, shown only for this method.
        // The cashier must tick that the money arrived before the order can be paid.
        CheckBox transferReceivedBox = new CheckBox(I18n.t("pos.pay.transfer.received"));
        // The bank's reference of this transfer, so a disputed one can be traced later; optional (TODO 4.31).
        TextField transferRefField = new TextField();
        transferRefField.setPromptText(I18n.t("pos.pay.transfer.ref.prompt"));
        TextLengthLimiter.limit(transferRefField, com.coffeeshop.coffeeshopmanagement.service.PaymentDetails.REFERENCE_MAX);
        VBox transferBox = PaymentBoxes.transfer(split.dueLabel(), transferReceivedBox, transferRefField, activeOpenOrder);
        transferBox.visibleProperty().bind(transferRadio.selectedProperty());
        transferBox.managedProperty().bind(transferRadio.selectedProperty());
        grid.add(split.node(), 0, 8, 2, 1);
        grid.add(transferBox, 0, 9, 2, 1);
        // An e-wallet: the amount, the transaction code (optional) and the same "money arrived" tick as a bank transfer (TODO 4.34).
        CheckBox walletReceivedBox = new CheckBox(I18n.t("pos.pay.wallet.received"));
        TextField walletRefField = new TextField();
        walletRefField.setPromptText(I18n.t("pos.pay.wallet.ref.prompt"));
        TextLengthLimiter.limit(walletRefField, com.coffeeshop.coffeeshopmanagement.service.PaymentDetails.REFERENCE_MAX);
        VBox walletBox = PaymentBoxes.wallet(split.dueLabel(), walletReceivedBox, walletRefField);
        javafx.beans.binding.BooleanBinding walletChosen = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> chosenMethod.get().isEWallet(), methodGroup.selectedToggleProperty());
        walletBox.visibleProperty().bind(walletChosen);
        walletBox.managedProperty().bind(walletChosen);
        grid.add(walletBox, 0, 10, 2, 1);
        dialog.getDialogPane().setContent(grid);

        PaymentResult[] pendingResult = {null};
        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(confirmButtonType);
        methodGroup.selectedToggleProperty().addListener((obs, was, isNow) -> {
            split.methodChanged(chosenMethod.get());
            confirmButton.setText(I18n.t(chosenMethod.get().needsReceivedConfirmation() ? "pos.pay.received" : "pos.pay.confirm"));
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
                AlertUtil.warning(I18n.t("pos.discount.invalid.title"), I18n.t("pos.discount.invalid.number"));
                event.consume();
                return;
            }
            DiscountPolicy.Result discountResult = DiscountPolicy.check(currentRole, subtotal, discount, discountReasonField.getText());
            String discountProblem = discountResult == DiscountPolicy.Result.OK ? null
                    : DiscountPolicy.messageFor(discountResult, currentRole, subtotal);
            if (discountProblem != null) {
                AlertUtil.warning(I18n.t("pos.discount.invalid.title"), discountProblem);
                event.consume();
                return;
            }
            // Points spent (TODO 4.16): a typed number that is not allowed stops the payment with the reason in plain words.
            Integer typedPoints = LoyaltyPolicy.parsePoints(pointsField.getText());
            String pointsProblem = typedPoints == null ? I18n.t("pos.points.invalid")
                    : LoyaltyPolicy.redemptionProblem(typedPoints, canSpendPoints ? pointsBalance : (pointsCustomer == null ? null : 0),
                            subtotal.subtract(discount));
            if (pointsProblem != null) {
                AlertUtil.warning(I18n.t("pos.points.label"), pointsProblem);
                event.consume();
                return;
            }
            final int spentPoints = typedPoints;
            BigDecimal total = subtotal.subtract(discount).subtract(LoyaltyPolicy.redemptionOf(spentPoints).value());
            PaymentMethod method = chosenMethod.get();
            if (split.problem() != null) {
                AlertUtil.warning(I18n.t("pos.split.title"), split.problem());
                event.consume();
                return;
            }
            if (method == PaymentMethod.TRANSFER && !transferReceivedBox.isSelected()) {
                AlertUtil.warning(I18n.t("pos.unconfirmed.title"), I18n.t("pos.unconfirmed.message"));
                event.consume();
                return;
            }
            if (method.isEWallet() && !walletReceivedBox.isSelected()) {
                AlertUtil.warning(I18n.t("pos.unconfirmed.title"), I18n.t("pos.unconfirmed.wallet", method.label()));
                event.consume();
                return;
            }
            BigDecimal cashHandedOver = null; // what the customer gave, saved with the order (TODO 3.17)
            if (method == PaymentMethod.CASH) {
                BigDecimal received = parseNonNegative(cashReceivedField.getText());
                if (received == null) {
                    AlertUtil.warning(I18n.t("pos.amount.invalid.title"), I18n.t("pos.amount.invalid.message"));
                    event.consume();
                    return;
                }
                if (received.compareTo(total) < 0) {
                    AlertUtil.warning(I18n.t("pos.amount.short.title"), I18n.t("pos.amount.short.message"));
                    event.consume();
                    return;
                }
                cashHandedOver = received;
            }
            pendingResult[0] = new PaymentResult(method, discount,
                    DiscountPolicy.reasonToStore(discount, discountReasonField.getText()), total, cashHandedOver,
                    method == PaymentMethod.TRANSFER
                            ? com.coffeeshop.coffeeshopmanagement.service.PaymentDetails.cleanReference(transferRefField.getText())
                            : method.isEWallet()
                            ? com.coffeeshop.coffeeshopmanagement.service.PaymentDetails.cleanReference(walletRefField.getText()) : null,
                    spentPoints, split.cashPart());
        });

        dialog.showAndWait();
        if (pendingResult[0] != null) {
            completeOrder(pendingResult[0]);
        }
    }

    private BigDecimal parseNonNegative(String text) {
        return MoneyParser.parseOptionalDong(text);
    }

    /** The points the customer has now (TODO 4.16): read again from the database, falling back to what the screen holds. */
    private static int currentPoints(Customer customer) {
        try {
            return new CustomerDAO().findById(customer.getId()).map(Customer::getLoyaltyPoints).orElse(customer.getLoyaltyPoints());
        } catch (RuntimeException e) {
            return customer.getLoyaltyPoints();
        }
    }

    private void completeOrder(PaymentResult payment) {
        final Integer tNum = selectedTableNumber; // null for a take-away order
        final List<OrderItem> items = new ArrayList<>(cart);
        final Customer knownCustomer = attachedCustomer;
        final String name = customerNameField != null && customerNameField.getText() != null ? customerNameField.getText().trim() : "";
        final String phone = customerPhoneField != null && customerPhoneField.getText() != null ? customerPhoneField.getText().trim() : "";
        final Order openOrder = activeOpenOrder;
        final BigDecimal subtotal = cartSubtotal();
        final Integer employeeId = Session.getCurrentEmployee() != null ? Session.getCurrentEmployee().getId() : null;
        final Role actingRole = Session.getCurrentRole(); // read here, on the screen thread; the service enforces the discount rule (TODO 6.45)
        setPaymentInFlight(true);
        paymentButton.setDisable(true);
        // Ordered so it runs after any autosave still in flight.
        Async.runOrdered(
                () -> checkoutService.pay(tNum, items, subtotal, knownCustomer, name, phone, openOrder,
                        new CheckoutService.Payment(payment.method(), payment.discount(), payment.discountReason(), payment.total(),
                                payment.cashReceived(), payment.transferRef(), payment.pointsRedeemed(), payment.cashPart()),
                        employeeId, actingRole),
                paid -> {
                    // First thing, so nothing below can leave the screen locked (TODO 2.79).
                    setPaymentInFlight(false);
                    Order savedOrder = paid.order();
                    paymentButton.setDisable(false);
                    lastCompletedOrder = savedOrder;
                    lastCompletedItems = items;
                    lastCompletedCustomerName = paid.customer() != null ? paid.customer().getFullName() : null;
                    String customerName = paid.customer() != null ? paid.customer().getFullName() : null;
                    // The sale is saved: make the till ready for the next one BEFORE the receipt is opened (TODO 2.65). If the
                    // receipt window fails to open, the paid lines are already gone from the screen and a second click cannot
                    // record the same sale twice. The screen is cleared only when it still shows the table that was paid.
                    if (tNum != null ? java.util.Objects.equals(selectedTableNumber, tNum) : takeAway && activeOpenOrder == openOrder) {
                        activeOpenOrder = null;
                        takeAwayOrderId = null;
                        orderInProgress = false;
                        attachedCustomer = paid.customer();
                        loadProducts();
                        cart.clear();
                        orderDetailTable.getItems().clear();
                        totalAmountLabel.setText(I18n.t("pos.money.zero"));
                        resetOrderAfterPayment();
                    } else {
                        loadProducts();
                    }
                    loadTablesFromDb();
                    try {
                        // No "success" box any more: the receipt window opens with a green "ĐÃ THANH TOÁN" line (TODO 5.21).
                        openInvoiceWindow(savedOrder, items, customerName);
                    } catch (RuntimeException e) {
                        LOGGER.log(Level.WARNING, "The receipt window could not be opened", e);
                        AlertUtil.warning(I18n.t("pos.paid.status", savedOrder.getId()),
                                I18n.t("pos.receipt.openfail"));
                    }
                },
                error -> {
                    setPaymentInFlight(false);
                    paymentButton.setDisable(false);
                    LOGGER.log(Level.WARNING, "Payment failed", error);
                    AlertUtil.error(I18n.t("pos.pay.fail.title"), I18n.t("pos.pay.fail.message", error.getMessage()));
                    loadProducts();
                    loadTablesFromDb();
                }
        );
    }

    /** Locks or unlocks the whole screen around a payment that is being saved (TODO 2.79). */
    private void setPaymentInFlight(boolean on) {
        paymentInFlight = on;
        javafx.scene.Scene scene = paymentButton != null ? paymentButton.getScene() : null;
        if (scene != null && scene.getRoot() != null) {
            scene.getRoot().setDisable(on);
            scene.setCursor(on ? javafx.scene.Cursor.WAIT : javafx.scene.Cursor.DEFAULT);
        }
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
        orderStatusLabel.setText(I18n.t("pos.order.none"));
        orderIdLabel.setText("-");
        if (customerNameField != null) customerNameField.clear();
        if (customerPhoneField != null) customerPhoneField.clear();
        showCustomerInfo(null, false);
    }

    private void openInvoiceWindow(Order order, List<OrderItem> items, String customerName) {
        HoaDonController controller = SceneNavigator.loadAndShowNewWindow(
                "/fxml/donthanhtoan.fxml", ReceiptText.t("pos.invoice.title", order.getId()), ReceiptText.bundle());
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
            AlertUtil.warning(I18n.t("pos.noreceipt.title"), I18n.t("pos.noreceipt.message"));
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
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("admin.only.message"));
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
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("admin.only.message"));
            return;
        }
        if (!confirmLeaveOrder()) return;
        SceneNavigator.switchScene(event, "/fxml/quanlydanhmuc.fxml");
    }

    @FXML
    public void openAccountManagement(ActionEvent event) {
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("admin.only.message"));
            return;
        }
        if (!confirmLeaveOrder()) return;
        SceneNavigator.switchScene(event, "/fxml/quanlytaikhoan.fxml");
    }

    @FXML
    public void openStaffManagement(ActionEvent event) {
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("admin.only.message"));
            return;
        }
        if (!confirmLeaveOrder()) return;
        SceneNavigator.switchScene(event, "/fxml/quanlynhanvien.fxml");
    }

    @FXML
    public void logout(ActionEvent event) {
        if (!confirmLeaveOrder()) return;
        BackupDialogs.offerBeforeLeaving(BackupDialogs.windowOf(event), () -> { // TODO 5.18
            Session.clear();
            SceneNavigator.switchScene(event, "/fxml/dangnhap.fxml");
        });
    }
}
