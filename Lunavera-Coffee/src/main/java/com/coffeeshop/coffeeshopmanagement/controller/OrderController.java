package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.config.SeedNames;
import com.coffeeshop.coffeeshopmanagement.service.OrderScreenRules;
import com.coffeeshop.coffeeshopmanagement.service.PaymentFlowRules;
import com.coffeeshop.coffeeshopmanagement.service.SplitBillRules;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptText;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.TableStatus;
import com.coffeeshop.coffeeshopmanagement.service.AttachedCustomerRule;
import com.coffeeshop.coffeeshopmanagement.service.Cart;
import com.coffeeshop.coffeeshopmanagement.service.QuantityInput;
import com.coffeeshop.coffeeshopmanagement.service.Limits;
import com.coffeeshop.coffeeshopmanagement.service.CheckoutService;
import com.coffeeshop.coffeeshopmanagement.service.CustomerService;
import com.coffeeshop.coffeeshopmanagement.service.OrderService;
import com.coffeeshop.coffeeshopmanagement.service.RepeatOrder;
import com.coffeeshop.coffeeshopmanagement.service.StockHolds;
import com.coffeeshop.coffeeshopmanagement.service.PosLeaveRules;
import com.coffeeshop.coffeeshopmanagement.service.PosService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.AutoRefresh;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SessionGuard;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Backs the POS/order-creation screen (quanlydonhang.fxml) - a dine-in table grid + a live
 * cart + checkout.
 */
public class OrderController extends ScreenNavigation implements com.coffeeshop.coffeeshopmanagement.util.ScreenWork {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    private final com.coffeeshop.coffeeshopmanagement.service.TableService tableService = new com.coffeeshop.coffeeshopmanagement.service.TableService(); // marking a table in use or empty (TODO 6.21)

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
    private Integer selectedTableNumber;
    /** A take-away order (no table) is on screen, or a new one is about to start (TODO 4.25); {@code takeAwayOrderId} is its saved order, once there is one. */
    private boolean takeAway;
    private Integer takeAwayOrderId;
    private boolean orderInProgress;
    private static final int REFRESH_SECONDS = 20;
    private Timeline sharedDataRefresh;
    private final ReservationPanel reservations = new ReservationPanel(); // TODO 4.43
    @FXML
    private Label tableRefreshLabel;
    /** True from "Thanh toán" being confirmed until the save has finished (TODO 2.79): the screen is disabled and shortcuts are ignored meanwhile. */
    private boolean paymentInFlight;
    private List<OrderItem> splitPart;  // TODO 4.10: the pieces paid first; null for an ordinary payment
    private List<OrderItem> splitRest;
    private Customer attachedCustomer;
    private PosCustomerBox customerBox; // the name and phone fields, the match line and the "same as last time" button (TODO 4.56)
    private OrderClock clock;
    private Order activeOpenOrder;
    private Order lastCompletedOrder;
    private List<OrderItem> lastCompletedItems = List.of();
    /** Name of the customer on the last paid order, kept for "In lại hóa đơn" (TODO 2.46). */
    private String lastCompletedCustomerName;


    private static void hide(javafx.scene.Node node) {
        if (node != null) { node.setVisible(false); node.setManaged(false); }
    }

    // =================================================================== initialize

    @FXML
    private void initialize() {
        javafx.application.Platform.runLater(this::installCloseGuard);
        if (adminNameLabel != null) adminNameLabel.setText(Session.getDisplayName());
        if (adminRoleLabel != null) adminRoleLabel.setText(I18n.t(Session.isAdmin() ? "role.admin" : "role.employee"));

        if (!Session.isAdmin()) {
            hide(productMenuButton);
            hide(categorySeparator);
            hide(adminMenuBox);
        }

        if (orderStatusLabel != null) {
            orderStatusLabel.setText(I18n.t("tables.status.free"));
            orderStatusLabel.getStyleClass().setAll("order-status-badge-empty");
        }
        if (orderIdLabel != null) orderIdLabel.setText(I18n.t("pos.order.none"));
        if (orderTableLabel != null) orderTableLabel.setText("-");
        if (totalAmountLabel != null) CartPromotionNote.attach(totalAmountLabel, cart, I18n.t("pos.money.zero")); // TODO 4.55

        clock = new OrderClock(orderTimeLabel, () -> activeOpenOrder);
        customerBox = new PosCustomerBox(customerNameField, customerPhoneField, customerInfoLabel, customerService, orderService,
                () -> attachedCustomer, customer -> attachedCustomer = customer, this::syncOpenOrderToDb, this::repeatLast);
        tableMap = new TableMapView(posService, tableGrid, selectedTableLabel, selectedTableStatusLabel, selectedTableCapacityLabel, reservations,
                () -> new TableMapView.State(selectedTableNumber, takeAway && takeAwayOrderId == null, takeAwayOrderId), this::selectTable,
                this::selectTakeAway, this::openReservations, this::openTableMoves, this::afterTableMapLoaded); // the table map of the till (TODO 6.54)
        loadTablesFromDb();
        startSharedDataRefresh();

        detailIndexColumn.setCellValueFactory(data -> new SimpleIntegerProperty(
                orderDetailTable.getItems().indexOf(data.getValue()) + 1));
        productNameColumn.setCellValueFactory(data -> new SimpleStringProperty(SeedNames.product(data.getValue().getProductName())));
        productNameColumn.setCellFactory(col -> new NoteNameCell(this::editNote)); // the note under the name, and a button for it (TODO 4.9)
        quantityColumn.setCellFactory(col -> new QuantityCell(this::adjustQuantity, this::changeQuantity, () -> paymentInFlight)); // TODO 5.27
        unitPriceColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyUtil.format(data.getValue().getUnitPrice())));
        subtotalColumn.setCellValueFactory(data ->
                new SimpleStringProperty(CurrencyUtil.format(data.getValue().getLineTotal())));

        catalog = new ProductCatalog(posService, productSearchField, categoryTabBox, productCatalogFlow, this::handleQuickAddProduct,
                () -> paymentInFlight, () -> activeOpenOrder == null ? 0 : activeOpenOrder.getId()); // the menu side of the till (TODO 6.54)
        catalog.setUp();
        installShortcuts();
        catalog.reload();
        catalog.loadRecentSales();
        refreshCart();
    }

    // =================================================================== Real-Time Clock
    /** TODO 5.37: with several tills every 20 seconds the table map and product list are read again (the cart, table and search are kept; not while paying). */
    private void startSharedDataRefresh() {
        if (tableGrid != null) sharedDataRefresh = AutoRefresh.every(tableGrid, Duration.seconds(REFRESH_SECONDS), this::refreshSharedData);
    }

    private void refreshSharedData() {
        if (paymentInFlight || startingOrder) {
            return;
        }
        tableMap.load(true);
        catalog.reload(true);
    }

    private void stampRefresh() {
        if (tableRefreshLabel != null) {
            tableRefreshLabel.setText(I18n.t("pos.updatedat", AppClock.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))));
            tableRefreshLabel.setVisible(true);
            tableRefreshLabel.setManaged(true);
        }
    }

    // =================================================================== Customer Auto-Link
    private static final Logger LOGGER = Logger.getLogger(OrderController.class.getName());
    private boolean autosaveWarningShown;
    private final HoldNotice holdNotice = new HoldNotice(); // another table holds what the last change asked for (TODO 3.1)
    private boolean startingOrder;

    private final CustomerService customerService = new CustomerService();
    private ProductCatalog catalog; // built in initialize
    private TableMapView tableMap; // built in initialize
    private final PosService posService = new PosService(); // the menu, the table map, best sellers, the stock re-read (TODO 6.21)
    private final CheckoutService checkoutService = new CheckoutService();
    private final OrderService orderService = new OrderService(); // start, load, autosave (TODO 6.5)

    // =================================================================== Category & Product Catalog


    /** The till's keys (TODO 5.7, 5.45): Ctrl+F, Ctrl+Enter, Esc, Delete, + and -, F4, Ctrl+L, F1; see {@link PosShortcuts}. */
    private void installShortcuts() {
        if (productSearchField == null) return;
        PosShortcuts.install(productSearchField, customerNameField, orderDetailTable,
                () -> { if (paymentButton != null && !paymentButton.isDisabled()) handlePayment(); }, this::adjustQuantity);
        if (paymentButton != null) paymentButton.setTooltip(new javafx.scene.control.Tooltip(I18n.t("pos.tip.pay")));
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
            if (BookedTables.confirm(selectedTableNumber, I18n.t("orders.table", selectedTableNumber))) startOrderForTable(selectedTableNumber, () -> addToCart(product));
        } else {
            addToCart(product);
        }
    }

    /** Asks for a new note for one cart line and puts it on the line (TODO 4.9); the cart is saved and drawn again. */
    private void editNote(OrderItem line) {
        if (!paymentInFlight && line != null && CartLineFlow.editNote(cartRules, line)) { // not while a payment is being saved (TODO 2.79)
            refreshCart();
        }
    }

    // =================================================================== Table selection & management (3 CỘT x 4 DÒNG)

    public void loadTablesFromDb() {
        tableMap.load(false);
    }

    /** The map has been drawn: keep the selected table, or choose one. {@code quiet}: a background refresh (TODO 5.37) also stamps the time. */
    private void afterTableMapLoaded(boolean quiet) {
        if (selectedTableNumber != null && !tableMap.has(selectedTableNumber)) {
            selectedTableNumber = null; // the table was removed on another till (TODO 4.18): never keep selling on it
        }
        Integer first = tableMap.firstTableNumber();
        if (selectedTableNumber == null && !takeAway && first != null) {
            selectTable(first);
        } else if (selectedTableNumber != null) {
            tableMap.showSelected(selectedTableNumber);
        }
        if (quiet) stampRefresh();
    }

    /** "Chuyển / Gộp / Tách" (TODO 4.10): the last autosave is written first, so the move works on what the screen shows. */
    private void openTableMoves() {
        if (paymentInFlight || startingOrder) return;
        Async.flushOrdered(3000);
        TableMovePanel.open(selectedTableNumber, activeOpenOrder != null && !takeAway && !cart.isEmpty(),
                tableMap.all(), new ArrayList<>(cart), this::startSplit, number -> {
                    selectedTableNumber = number;
                    loadTablesFromDb();
                    selectTable(number);
                });
    }

    /** Pays the pieces chosen in the split box; the rest stays open on the table. */
    private void startSplit(Map<Integer, Integer> take) {
        SplitBillRules.Split split = SplitBillRules.split(new ArrayList<>(cart), take);
        splitPart = split.part();
        splitRest = split.remainder();
        try {
            openPaymentDialog();
        } finally {
            splitPart = null;
            splitRest = null;
        }
    }

    /** "Đặt bàn" (TODO 4.43): the booking window; "Nhận bàn" there comes back to {@link #seatReservation}. */
    private void openReservations() {
        ReservationPanel.open(tableMap.all().stream().sorted(java.util.Comparator.comparingInt(DiningTable::getTableNumber)).toList(),
                this::seatReservation);
    }

    /** Seats the guests of a booking: the table must be free; its order is opened with the customer on it, then the booking is closed as seated. */
    private void seatReservation(com.coffeeshop.coffeeshopmanagement.model.Reservation booking) {
        DiningTable table = tableMap.table(booking.tableNumber());
        switch (OrderScreenRules.canSeat(paymentInFlight, startingOrder, table)) { // the rule is OrderScreenRules (TODO 6.5)
            case IGNORE -> { return; }
            case TABLE_BUSY -> { AlertUtil.warning(I18n.t("reservation.title"), I18n.t("reservation.seat.busy", booking.customerName())); return; }
            case OK -> { }
        }
        selectedTableNumber = booking.tableNumber();
        takeAway = false;
        takeAwayOrderId = null;
        tableMap.highlight(booking.tableNumber());
        tableMap.showSelected(booking.tableNumber());
        activeOpenOrder = null;
        cart.clear();
        orderDetailTable.getItems().clear();
        if (customerNameField != null) customerNameField.setText(booking.customerName());
        if (customerPhoneField != null) customerPhoneField.setText(booking.phone() == null ? "" : booking.phone());
        startOrderForTable(booking.tableNumber(), () -> Async.run(
                () -> { posService.markSeated(booking.id()); return null; },
                done -> loadTablesFromDb(),
                error -> LOGGER.log(Level.WARNING, "Could not close the reservation " + booking.id(), error)));
    }

    public void selectTable(int number) {
        if (paymentInFlight) {
            return; // a payment is being saved (TODO 2.79)
        }
        selectedTableNumber = number;
        takeAway = false;
        takeAwayOrderId = null;
        tableMap.highlight(number);
        tableMap.showSelected(number);
        loadTableOrderFromDb(number);
    }

    /** {@code number} null = the take-away order {@link #takeAwayOrderId} (or none yet), TODO 4.25. */
    private void loadTableOrderFromDb(Integer number) {
        final Integer wantedTakeAway = takeAwayOrderId;
        Async.run(
                () -> orderService.load(number, wantedTakeAway),
                data -> {
                    // An answer for a table that is no longer the selected one must not overwrite the screen (TODO 2.55).
                    if (OrderScreenRules.isStale(selectedTableNumber, number, takeAwayOrderId, wantedTakeAway)) return;
                    DiningTable table = tableMap.table(number);
                    boolean occupied = table != null && table.isOccupied();
                    Order order = data.order();
                    OrderScreenRules.Shown view = OrderScreenRules.shown(order, data.customer(), occupied, I18n.t("pos.walkin")); // what the box says: OrderScreenRules (TODO 6.5)
                    boolean serving = view.serving(); // an order, or a table marked taken with no order saved yet
                    activeOpenOrder = order;
                    attachedCustomer = data.customer();
                    cart.setAll(data.items());
                    orderInProgress = serving;
                    if (orderStatusLabel != null) {
                        orderStatusLabel.setText(I18n.t(serving ? "pos.order.serving" : "tables.status.free"));
                        orderStatusLabel.getStyleClass().setAll(serving ? "order-status-badge" : "order-status-badge-empty");
                    }
                    if (orderIdLabel != null) {
                        orderIdLabel.setText(switch (view.idText()) {
                            case NUMBER -> com.coffeeshop.coffeeshopmanagement.util.OrderNumber.format(order.getId());
                            case SERVING -> I18n.t("pos.order.serving");
                            case NONE -> I18n.t("pos.order.none");
                        });
                    }
                    if (orderTableLabel != null) orderTableLabel.setText(TakeAwayPanel.tableLabel(number));
                    clock.update();
                    if (customerNameField != null) customerNameField.setText(view.customerName());
                    if (customerPhoneField != null) customerPhoneField.setText(view.customerPhone());
                    if (view.showCustomerBox()) customerBox.show(data.customer(), false);
                    orderDetailTable.getItems().setAll(cart);
                    totalAmountLabel.setText(view.totalFromCart() ? CurrencyUtil.format(cartSubtotal()) : I18n.t("pos.money.zero"));
                    if (cartItemCountLabel != null) {
                        long totalQty = cartRules.totalQuantity();
                        cartItemCountLabel.setText(I18n.t("pos.cart.count", cart.size(), totalQty));
                    }
                    catalog.reload(true); // what this order may still take depends on which order it is (TODO 3.1)
                },
                error -> {
                    LOGGER.log(Level.WARNING, "Could not load the order of table " + number, error);
                    AlertUtil.error(I18n.t("common.error.title"), I18n.t("pos.load.order", number, error.getMessage()));
                }
        );
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
        TableStatusActions.toggle(selectedTableNumber, takeAway, activeOpenOrder, tableOnMap(), cart.isEmpty(), tableService,
                () -> selectTakeAway(null), tNum -> {
                    loadTablesFromDb();
                    loadTableOrderFromDb(tNum);
                });
    }

    @FXML
    public void handleTableDetail() {
        TableStatusActions.detail(selectedTableNumber, tableOnMap(), selectedTableLabel.getText());
    }

    /** The selected table as the map has it, or null (nothing selected, or a take-away order). */
    private DiningTable tableOnMap() {
        return selectedTableNumber == null ? null : tableMap.table(selectedTableNumber);
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
        if (BookedTables.confirm(selectedTableNumber, I18n.t("orders.table", selectedTableNumber))) startOrderForTable(selectedTableNumber, null);
    }

    /** Starts a new OPEN order for the table (the one place that does this); {@code afterStart} runs on the FX thread once it is on screen (may be null). */
    private void startOrderForTable(Integer tNum, Runnable afterStart) { // null: a take-away order, no table (TODO 4.25)
        if (startingOrder) return;
        startingOrder = true;
        final String name = customerNameField != null && customerNameField.getText() != null ? customerNameField.getText().trim() : "";
        final String phone = customerPhoneField != null && customerPhoneField.getText() != null ? customerPhoneField.getText().trim() : "";
        final Integer employeeId = Session.getCurrentEmployee() != null ? Session.getCurrentEmployee().getId() : null; // read on the screen thread
        Async.runOrdered(
                () -> orderService.start(tNum, TakeAwayPanel.current(), name, phone, employeeId),
                started -> {
                    startingOrder = false;
                    activeOpenOrder = started.order();
                    if (tNum == null) takeAwayOrderId = started.order().getId();
                    attachedCustomer = started.customer();
                    customerBox.show(started.customer(), false);
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
                    clock.update();
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
        var choice = CartLineFlow.choose(product); // TODO 4.47: size and extras
        if (choice.isEmpty()) {
            return;
        }
        Cart.Result result = cartRules.add(product, choice.get());
        if (result.outcome() == Cart.Outcome.LINE_LIMIT) {
            AlertUtil.warning(I18n.t("pos.qty.limit.title"), I18n.t("pos.qty.limit.message", Limits.MAX_LINE_QUANTITY));
            return;
        }
        if (result.outcome() == Cart.Outcome.NOT_ENOUGH_STOCK) {
            AlertUtil.warning(I18n.t("pos.stock.short.title"), StockHolds.refusal(product));
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
        // The decision (limit, stock, "remove the line?") is Cart.decideQuantity (TODO 6.5); the stock is read only when it is needed.
        switch (Cart.decideQuantity(line.getQuantity(), newQty, () -> cartRules.exceedsStock(findProductById(line.getProductId()), line, newQty))) {
            case UNCHANGED -> { return; }
            case LINE_LIMIT -> { AlertUtil.warning(I18n.t("pos.qty.limit.title"), I18n.t("pos.qty.limit.message", Limits.MAX_LINE_QUANTITY)); return; }
            case NOT_ENOUGH_STOCK -> { AlertUtil.warning(I18n.t("pos.stock.short.title"), StockHolds.refusal(findProductById(line.getProductId()))); return; }
            case ASK_REMOVE -> { // taking the last cup off a line removes it: ask first, one mis-tap should not lose it (TODO 5.5)
                if (!AlertUtil.confirm(I18n.t("pos.remove.title"), I18n.t("pos.remove.message", SeedNames.product(line.getProductName())))) return;
            }
            case SET -> { }
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
        return catalog.findById(id);
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

    /** Shows the saved order again: the tables and, when one is selected (or a take-away), its lines. */
    private void reloadSelected() {
        loadTablesFromDb();
        if (OrderScreenRules.somethingSelected(selectedTableNumber, takeAway)) loadTableOrderFromDb(selectedTableNumber);
    }

    private void syncOpenOrderToDb() {
        if (OrderScreenRules.isSaved(activeOpenOrder)) {
            OrderService.applyCart(activeOpenOrder, cartSubtotal(), attachedCustomer);
            final Order orderToSave = activeOpenOrder;
            final List<OrderItem> itemsToSave = new ArrayList<>(cart);
            // Ordered: saves (and the payment that follows) run in the order they were made, so an
            // older cart can never overwrite a newer one or land after the order was paid.
            Async.runOrdered(
                    () -> orderService.autosave(orderToSave, itemsToSave),
                    result -> {
                        autosaveWarningShown = false;
                        holdNotice.after(result, () -> { catalog.reload(true); if (OrderScreenRules.somethingSelected(selectedTableNumber, takeAway)) loadTableOrderFromDb(selectedTableNumber); });
                        if (!result.stillOpen()) {
                            // Paid or cancelled elsewhere: say so now, not at the payment (TODO 3.28).
                            AlertUtil.warning(I18n.t("pos.closed.title"), I18n.t("pos.closed.message"));
                            reloadSelected();
                        }
                    },
                    err -> {
                        if (StaleOrderAlert.show(err, itemsToSave, this::reloadSelected)) return; // another till changed the order (TODO 2.91)
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

    /** "The same as last time" (TODO 4.56): the lines of the customer's last paid order into the cart; says what was left out. */
    private void repeatLast(OrderDAO.LastOrder last) {
        if (paymentInFlight) return;
        if (selectedTableNumber == null && !takeAway) {
            AlertUtil.warning(I18n.t("pos.notable.title"), I18n.t("pos.notable.order"));
            return;
        }
        List<Product> onSale = catalog.onSale();
        boolean fresh = !orderInProgress || activeOpenOrder == null;
        if (fresh) { // no table is taken for a repeat that would put nothing in the cart
            RepeatOrder.Result dry = RepeatOrder.apply(new Cart(new ArrayList<>()), last.items(), onSale);
            if (dry.nothing()) {
                AlertUtil.warning(I18n.t("pos.repeat.title"), I18n.t("pos.repeat.nothing") + "\n" + RepeatOrder.notice(dry));
                return;
            }
        }
        Runnable fill = () -> {
            RepeatOrder.Result result = RepeatOrder.apply(cartRules, last.items(), onSale);
            if (result.pieces() > 0) refreshCart();
            if (!result.left().isEmpty()) AlertUtil.warning(I18n.t("pos.repeat.title"), RepeatOrder.notice(result));
        };
        if (fresh) startOrderForTable(selectedTableNumber, fill); else fill.run();
    }

    // =================================================================== Payment / checkout

    @FXML
    public void handlePayment() {
        beginPayment(this::openPaymentDialog);
    }

    /** "Tặng / Free drink" (TODO 4.61): closes the bill with no money for the staff, the owner or a tasting; the window is {@link ComplimentaryDialog}. */
    @FXML
    public void handleGiveAway() {
        if (splitPart != null) { AlertUtil.warning(I18n.t("pos.giveaway"), I18n.t("complimentary.problem.split")); return; }
        beginPayment(this::openComplimentaryDialog);
    }

    /** The people a free drink can go to are read in the background first (TODO 6.14); the screen is locked for that short read so a second click cannot open two boxes. */
    private void openComplimentaryDialog() {
        final List<OrderItem> lines = new ArrayList<>(cart);
        final com.coffeeshop.coffeeshopmanagement.model.Role role = Session.getCurrentRole();
        setPaymentInFlight(true);
        Async.run(
                () -> new com.coffeeshop.coffeeshopmanagement.service.ComplimentaryService().staffToChoose(),
                staff -> {
                    setPaymentInFlight(false);
                    ComplimentaryDialog.ask(lines, role, staff).ifPresent(payment -> completeOrder(payment, null, null));
                },
                error -> {
                    setPaymentInFlight(false);
                    LOGGER.log(Level.WARNING, "The staff list of the free-drink box could not be read", error);
                    ComplimentaryDialog.ask(lines, role, List.of()).ifPresent(payment -> completeOrder(payment, null, null)); // the box says to choose a person; the payment refuses an unknown one
                });
    }

    private void beginPayment(Runnable open) {
        switch (PaymentFlowRules.canStart(paymentInFlight, orderInProgress, cart.isEmpty())) { // the rule is PaymentFlowRules (TODO 6.5)
            case ALREADY_PAYING -> { return; } // a payment is being saved (TODO 2.79)
            case NO_ORDER -> { AlertUtil.warning(I18n.t("pos.noorder.title"), I18n.t("pos.noorder.pay")); return; }
            case EMPTY_CART -> { AlertUtil.warning(I18n.t("pos.empty.title"), I18n.t("pos.empty.message")); return; }
            case OK -> { }
        }
        // An account locked mid-shift must not be able to complete a sale.
        if (!SessionGuard.validateNow()) {
            cart.clear();
            SessionGuard.forceLogout();
            return;
        }
        verifyStockThenOpenPayment(open);
    }

    /**
     * TODO 3.1: the cart holds no stock, so before the payment (or free-drink) box opens the stock is read again; if an item ran out the cashier is
     * told which, nothing is charged and the tiles are refreshed. The screen is locked during the one short query so a second click cannot open two
     * boxes. Only a warning: the payment itself still refuses a sale the stock cannot cover. If the re-read fails the box opens anyway.
     */
    private void verifyStockThenOpenPayment(Runnable open) {
        final List<OrderItem> lines = new ArrayList<>(cart);
        setPaymentInFlight(true);
        Async.run(
                () -> posService.stockShortages(lines),
                shortages -> {
                    setPaymentInFlight(false);
                    if (shortages.isEmpty()) {
                        open.run();
                        return;
                    }
                    AlertUtil.warning(I18n.t("pos.stock.short.title"), com.coffeeshop.coffeeshopmanagement.service.StockCheck.message(shortages));
                    catalog.reload(true); // the tiles and the "+" limits show the stock that was just found
                },
                error -> {
                    setPaymentInFlight(false);
                    LOGGER.log(Level.WARNING, "The stock could not be re-read before the payment box", error);
                    open.run();
                });
    }

    private void openPaymentDialog() {
        BigDecimal subtotal = splitPart != null ? SplitBillRules.sum(splitPart) : cartSubtotal();
        final List<OrderItem> part = splitPart, rest = splitRest; // startSplit clears them as soon as this returns; the box opens a moment later
        setPaymentInFlight(true); // the box is built from what is read in the background: one click only (TODO 6.14)
        PaymentDialog.ask(subtotal, splitPart != null ? splitPart : cart, attachedCustomer, activeOpenOrder, customerService, () -> setPaymentInFlight(false),
                payment -> completeOrder(payment, part, rest)); // the box itself is PaymentDialog (TODO 6.54)
    }

    private void completeOrder(CheckoutService.Payment payment, List<OrderItem> part, List<OrderItem> rest) { // part and rest: the split of TODO 4.10, or null
        final Integer tNum = selectedTableNumber; // null for a take-away order
        final List<OrderItem> items = new ArrayList<>(cart);
        final Customer knownCustomer = attachedCustomer;
        final String name = customerNameField != null && customerNameField.getText() != null ? customerNameField.getText().trim() : "";
        final String phone = customerPhoneField != null && customerPhoneField.getText() != null ? customerPhoneField.getText().trim() : "";
        final Order openOrder = activeOpenOrder;
        final BigDecimal subtotal = cartSubtotal();
        final List<OrderItem> receipt = PaymentFlowRules.receiptLines(part, payment.complimentary() != null, items); // TODO 4.10, 4.61, 6.5
        final Integer employeeId = Session.getCurrentEmployee() != null ? Session.getCurrentEmployee().getId() : null;
        final Role actingRole = Session.getCurrentRole(); // read here, on the screen thread; the service enforces the discount rule (TODO 6.45)
        setPaymentInFlight(true);
        paymentButton.setDisable(true);
        // Ordered so it runs after any autosave still in flight.
        Async.runOrdered(
                () -> {
                    CheckoutService.Payment paying = payment;
                    return part != null
                            ? checkoutService.paySplit(tNum, openOrder, part, rest, knownCustomer, name, phone, paying, employeeId, actingRole)
                            : checkoutService.pay(tNum, items, subtotal, knownCustomer, name, phone, openOrder, paying, employeeId, actingRole);
                },
                paid -> {
                    // First thing, so nothing below can leave the screen locked (TODO 2.79).
                    setPaymentInFlight(false);
                    Order savedOrder = paid.order();
                    paymentButton.setDisable(false);
                    lastCompletedOrder = savedOrder;
                    lastCompletedItems = receipt;
                    lastCompletedCustomerName = paid.customer() != null ? paid.customer().getFullName() : null;
                    String customerName = paid.customer() != null ? paid.customer().getFullName() : null;
                    // The sale is saved: make the till ready for the next one BEFORE the receipt is opened (TODO 2.65). If the
                    // receipt window fails to open, the paid lines are already gone from the screen and a second click cannot
                    // record the same sale twice. The screen is cleared only when it still shows the table that was paid.
                    catalog.refreshProducts(); // in every case; what else the screen does is PaymentFlowRules.screenAfter (TODO 6.5)
                    switch (PaymentFlowRules.screenAfter(part != null, tNum, selectedTableNumber, takeAway, activeOpenOrder == openOrder)) {
                        case RELOAD_REST -> loadTableOrderFromDb(tNum); // the rest of the order stays open: show it again (TODO 4.10)
                        case CLEAR_ORDER -> {
                            activeOpenOrder = null;
                            takeAwayOrderId = null;
                            orderInProgress = false;
                            attachedCustomer = paid.customer();
                            cart.clear();
                            orderDetailTable.getItems().clear();
                            totalAmountLabel.setText(I18n.t("pos.money.zero"));
                            resetOrderAfterPayment();
                        }
                        case KEEP -> { }
                    }
                    loadTablesFromDb();
                    try {
                        // No "success" box any more: the receipt window opens with a green "ĐÃ THANH TOÁN" line (TODO 5.21).
                        openInvoiceWindow(savedOrder, receipt, customerName, true);
                    } catch (RuntimeException e) {
                        LOGGER.log(Level.WARNING, "The receipt window could not be opened", e);
                        AlertUtil.warning(I18n.t("pos.paid.status", savedOrder.getId()),
                                I18n.t("pos.receipt.openfail"));
                    }
                },
                error -> {
                    setPaymentInFlight(false);
                    paymentButton.setDisable(false);
                    if (StaleOrderAlert.show(error, items, this::reloadSelected)) return; // another till changed the order (TODO 2.91)
                    LOGGER.log(Level.WARNING, "Payment failed", error);
                    com.coffeeshop.coffeeshopmanagement.util.AlertPlayer.play(com.coffeeshop.coffeeshopmanagement.util.AlertSounds.Cue.PAYMENT_REFUSED); // TODO 5.47
                    AlertUtil.error(I18n.t("pos.pay.fail.title"), I18n.t("pos.pay.fail.message", error.getMessage()));
                    catalog.refreshProducts();
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

    private javafx.stage.Window screenWindow() {
        return paymentButton != null && paymentButton.getScene() != null ? paymentButton.getScene().getWindow() : null;
    }

    private void installCloseGuard() {
        javafx.stage.Window window = screenWindow();
        if (window != null) {
            window.setOnCloseRequest(e -> {
                if (!confirmLeaveOrder()) e.consume();
            });
        }
    }

    private void removeCloseGuard() {
        javafx.stage.Window window = screenWindow();
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
        customerBox.show(null, false);
    }

    private void openInvoiceWindow(Order order, List<OrderItem> items, String customerName, boolean afterPayment) {
        HoaDonController controller = SceneNavigator.loadAndShowNewWindow(
                "/fxml/donthanhtoan.fxml", ReceiptText.t("pos.invoice.title", order.getId()), ReceiptText.bundle());
        if (controller == null) return;
        controller.setInvoiceData(order, items, Session.getDisplayName(), customerName);
        if (afterPayment) javafx.application.Platform.runLater(controller::printAfterPayment); // TODO 5.39: only right after a sale
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
        openInvoiceWindow(lastCompletedOrder, lastCompletedItems, lastCompletedCustomerName, false);
    }

    // =================================================================== Language switch guard (TODO 1.7)

    @Override
    public List<String> unsavedWork() {
        boolean saved = Async.flushOrdered(3000) && !autosaveWarningShown; // the last autosave reached the database
        Customer c = attachedCustomer;
        boolean typed = PosLeaveRules.customerTyped(customerNameField.getText(), customerPhoneField.getText(),
                c == null ? null : c.getFullName(), c == null ? null : c.getPhone(), I18n.t("pos.walkin"));
        return PosLeaveRules.lost(new PosLeaveRules.State(saved, startingOrder,
                OrderScreenRules.isSaved(activeOpenOrder), cart.size(), typed));
    }

    @Override
    public Object snapshot() {
        return new PosLeaveRules.Place(selectedTableNumber, takeAway, takeAwayOrderId);
    }

    @Override
    public void resume(Object place) {
        if (!(place instanceof PosLeaveRules.Place p)) return;
        if (p.table() != null) selectTable(p.table());
        else if (p.takeAway()) selectTakeAway(p.takeAwayOrderId());
    }

    // =================================================================== Navigation
    // The sidebar buttons and the sign-out are in ScreenNavigation (TODO 6.54); the till only adds the question that goes before them.

    /** Before any sidebar button or the sign-out: an order that is still open is asked about (and saved) first. */
    @Override
    protected boolean confirmLeaving() {
        return confirmLeaveOrder();
    }
}
