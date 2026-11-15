

package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.OrderCancelService;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptText;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO.HistoryFilter;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO.OrderSummary;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.PageBar;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Order / invoice history, opened from the POS screen's header. Built in code as its own plain
 * (non-modal) window rather than a new FXML screen: it needs no sidebar of its own, and a
 * non-modal window sidesteps the dialog-modality problems seen on Linux (progress.md, Sessions
 * 4-5). Lists past orders, re-opens any receipt, and lets an admin cancel (refund) a paid order.
 *
 * Filtering and paging both happen in SQL now (progress.md, "order-history paging" session) -
 * the previous version loaded up to 500 rows into memory and filtered client-side, so a date
 * range further back than the most recent 500 orders silently showed nothing even when matching
 * orders existed. There is no upper bound on total history size anymore.
 */
public final class OrderHistoryWindow {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    // The status names follow the language in use (TODO 1.7); methods, not constants, so they are read when the window opens.
    private static String allStatuses() { return I18n.t("history.status.all"); }
    private static String statusPaid() { return I18n.t("orders.status.paid"); }
    private static String statusCancelled() { return I18n.t("orders.status.cancelled"); }
    private static String statusOpen() { return I18n.t("orders.status.open"); }

    /** The language-neutral code remembered for the status filter, so the choice survives a change of language. */
    private static String statusCode(String choice) {
        if (statusPaid().equals(choice)) return "PAID";
        if (statusCancelled().equals(choice)) return "CANCELLED";
        if (statusOpen().equals(choice)) return "OPEN";
        return "ALL";
    }

    private static String statusChoice(String code) {
        return switch (code == null ? "" : code) {
            case "PAID" -> statusPaid();
            case "CANCELLED" -> statusCancelled();
            case "OPEN" -> statusOpen();
            default -> allStatuses();
        };
    }

    private record HistoryPage(List<OrderSummary> rows, int totalCount) {
    }

    private final com.coffeeshop.coffeeshopmanagement.service.OrderHistoryService historyService = new com.coffeeshop.coffeeshopmanagement.service.OrderHistoryService();
    private final TableView<OrderSummary> table = new TableView<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final DatePicker fromPicker = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker();
    private final DatePicker toPicker = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker();
    private final Label countLabel = new Label();
    private final PageBar pageBar = new PageBar(50, 20, 50, 100, 200).remember("history.pageSize");
    private final java.util.Map<TableColumn<OrderSummary, ?>, OrderDAO.HistorySortKey> sortKeys = new java.util.HashMap<>();
    private OrderDAO.HistorySort sort = OrderDAO.HistorySort.NEWEST_FIRST;
    private final Button viewButton = new Button(I18n.t("history.view"));
    private final Button cancelButton = new Button(I18n.t("history.cancel"));
    private final Button refundLinesButton = new Button(I18n.t("refund.button")); // TODO 4.37
    private final Button exportButton = new Button(I18n.t("common.exportcsv"));
    private int totalCount = 0;
    /** Set for the "purchase history of one customer" window (TODO 4.4); null for the full history. */
    private Integer onlyCustomerId;
    private String onlyCustomerName;

    private OrderHistoryWindow() {
    }

    private static final java.util.Map<Integer, Stage> CUSTOMER_WINDOWS = new java.util.HashMap<>();

    /** The orders of one customer; asking again for the same customer brings its window to the front. */
    public static void showForCustomer(int customerId, String customerName) {
        Stage existing = CUSTOMER_WINDOWS.get(customerId);
        if (existing != null && existing.isShowing()) {
            existing.toFront();
            return;
        }
        OrderHistoryWindow window = new OrderHistoryWindow();
        window.onlyCustomerId = customerId;
        window.onlyCustomerName = customerName;
        window.open();
        CUSTOMER_WINDOWS.put(customerId, window.stage);
    }

    private Stage stage;

    /** The one open history window; asking again brings it to the front instead of opening a second (TODO 2.30). */
    private static Stage instance;

    public static void show() {
        if (instance != null && instance.isShowing()) {
            instance.toFront();
            return;
        }
        new OrderHistoryWindow().open();
    }

    /** Bumped on every query; an answer from an older query is ignored (TODO 2.33). */
    private int requestCounter = 0;
    private final javafx.animation.PauseTransition searchPause = new javafx.animation.PauseTransition(javafx.util.Duration.millis(250));

    private void open() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY); // every column visible, no sideways scroll
        buildColumns();

        searchField.setPromptText(I18n.t("history.search.prompt"));
        searchField.setPrefWidth(200);
        TextLengthLimiter.limit(searchField, TextLengthLimiter.SEARCH_MAX);
        searchPause.setOnFinished(e -> resetAndReload());
        searchField.textProperty().addListener((obs, old, value) -> searchPause.playFromStart());
        statusFilter.getItems().setAll(allStatuses(), statusPaid(), statusCancelled(), statusOpen());
        String savedStatus = statusChoice(com.coffeeshop.coffeeshopmanagement.util.UiSettings.get("history.status", "ALL"));
        if (statusFilter.getItems().contains(savedStatus)) statusFilter.setValue(savedStatus); else statusFilter.getSelectionModel().selectFirst();
        statusFilter.setOnAction(e -> {
            if (onlyCustomerId == null && statusFilter.getValue() != null) {
                com.coffeeshop.coffeeshopmanagement.util.UiSettings.put("history.status", statusCode(statusFilter.getValue()));
            }
            resetAndReload();
        });

        viewButton.setDisable(true);
        cancelButton.setDisable(true);
        refundLinesButton.setDisable(true);
        viewButton.setOnAction(e -> viewSelectedReceipt());
        cancelButton.setOnAction(e -> cancelSelectedOrder());
        refundLinesButton.setOnAction(e -> refundSelectedLines());
        CopyMenus.history(table, viewButton, cancelButton, refundLinesButton); // right-click menu (TODO 5.46)
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> updateButtons(selected));
        table.setPlaceholder(new Label(I18n.t("history.empty")));
        table.setOnMouseClicked(e -> { if (e.getClickCount() == 2) viewSelectedReceipt(); });

        fromPicker.setPromptText(I18n.t("history.from"));
        toPicker.setPromptText(I18n.t("history.to"));
        fromPicker.setOnAction(e -> resetAndReload());
        toPicker.setOnAction(e -> resetAndReload());
        Button clearDatesButton = new Button(I18n.t("history.cleardates"));
        clearDatesButton.setOnAction(e -> {
            fromPicker.setValue(null);
            toPicker.setValue(null);
            resetAndReload();
        });

        pageBar.setOnChange(this::reload);
        // Sorting is done by the database over ALL matching orders, then paged - the TableView's
        // own sort would only reorder the rows currently on screen.
        table.setSortPolicy(tv -> {
            TableColumn<OrderSummary, ?> first = tv.getSortOrder().isEmpty() ? null : tv.getSortOrder().get(0);
            OrderDAO.HistorySortKey key = first != null ? sortKeys.get(first) : null;
            OrderDAO.HistorySort next = key == null ? OrderDAO.HistorySort.NEWEST_FIRST
                    : new OrderDAO.HistorySort(key, first.getSortType() == TableColumn.SortType.DESCENDING);
            if (!next.equals(sort)) {
                sort = next;
                pageBar.setCurrentPage(1);
                reload();
            }
            return true;
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusFilter.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        clearDatesButton.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        countLabel.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        fromPicker.setPrefWidth(130);
        toPicker.setPrefWidth(130);
        HBox top = new HBox(8, searchField, statusFilter, fromPicker, toPicker);
        top.getChildren().addAll(com.coffeeshop.coffeeshopmanagement.util.DatePresets.buttons(fromPicker, toPicker, this::resetAndReload));
        top.getChildren().addAll(clearDatesButton, spacer, countLabel);
        // Orders carry customer names and takings: only an admin may export them (TODO 4.21).
        exportButton.setVisible(Session.isAdmin());
        exportButton.setManaged(Session.isAdmin());
        exportButton.setOnAction(e -> exportCsv());
        HBox bottom = new HBox(10, viewButton, cancelButton, refundLinesButton, exportButton, new Region(), pageBar);
        HBox.setHgrow(bottom.getChildren().get(3), Priority.ALWAYS);
        VBox root = new VBox(12, top, table, bottom);
        root.setPadding(new Insets(16));
        VBox.setVgrow(table, Priority.ALWAYS);

        stage = com.coffeeshop.coffeeshopmanagement.util.SceneNavigator.newOwnedStage();
        if (onlyCustomerId == null) instance = stage;
        stage.setTitle(onlyCustomerId == null ? I18n.t("history.title") : I18n.t("history.title.customer", onlyCustomerName));
        com.coffeeshop.coffeeshopmanagement.util.AppIcon.apply(stage);
        stage.setScene(new Scene(root, 1100, 620));
        com.coffeeshop.coffeeshopmanagement.util.StageSize.remember(stage, onlyCustomerId == null ? "history.window" : "customerHistory.window", 1100, 620);
        AlertUtil.closeOnEscape(stage.getScene());
        stage.show();

        reload();
    }

    private void buildColumns() {
        addColumn(OrderDAO.HistorySortKey.ID, column(I18n.t("history.col.id"), 100, s -> com.coffeeshop.coffeeshopmanagement.util.CopyText.orderNumber(s.order().getId())));
        addColumn(OrderDAO.HistorySortKey.DATE, column(I18n.t("history.col.time"), 150, s -> shownTime(s.order()) != null
                ? shownTime(s.order()).format(TIME_FORMAT) : "-"));
        addColumn(OrderDAO.HistorySortKey.EMPLOYEE, column(I18n.t("history.col.employee"), 150, s -> orDash(s.employeeName())));
        addColumn(OrderDAO.HistorySortKey.CUSTOMER, column(I18n.t("history.col.customer"), 150, s -> s.customerName() != null ? s.customerName() : I18n.t("receipt.guest")));
        addColumn(OrderDAO.HistorySortKey.PAYMENT, column(I18n.t("history.col.payment"), 170, s -> com.coffeeshop.coffeeshopmanagement.util.PaymentText.of(s.order())));
        // Who gave what discount and why, visible to the owner without opening the database (TODO 2.37).
        table.getColumns().add(column(I18n.t("history.col.discount"), 200, com.coffeeshop.coffeeshopmanagement.util.HistoryText::discount));
        addColumn(OrderDAO.HistorySortKey.TOTAL, column(I18n.t("history.col.total"), 120, s -> CurrencyUtil.format(s.order().getTotal())));
        addColumn(OrderDAO.HistorySortKey.STATUS, column(I18n.t("history.col.status"), 120, s -> statusLabel(s.order().getStatus()) + (s.linesRefunded() ? " " + I18n.t("history.refunded.mark") : ""))); // TODO 4.37: some lines were given back
        table.getColumns().add(column(I18n.t("history.col.cash"), 140, com.coffeeshop.coffeeshopmanagement.util.HistoryText::cashGiven)); // TODO 3.17
        table.getColumns().add(column(I18n.t("history.col.change"), 110, com.coffeeshop.coffeeshopmanagement.util.HistoryText::change));
    }

    private void addColumn(OrderDAO.HistorySortKey key, TableColumn<OrderSummary, String> column) {
        sortKeys.put(column, key);
        table.getColumns().add(column);
    }

    private TableColumn<OrderSummary, String> column(String title, double width,
                                                     java.util.function.Function<OrderSummary, String> text) {
        TableColumn<OrderSummary, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(data -> new SimpleStringProperty(text.apply(data.getValue())));
        return column;
    }

    private static String orDash(String value) {
        return value != null ? value : "-";
    }

    private static String statusLabel(OrderStatus status) {
        return switch (status) {
            case PAID -> statusPaid();
            case CANCELLED -> statusCancelled();
            case OPEN -> statusOpen();
        };
    }

    private HistoryFilter currentFilter() {
        String statusChoice = statusFilter.getValue();
        OrderStatus status = statusPaid().equals(statusChoice) ? OrderStatus.PAID
                : statusCancelled().equals(statusChoice) ? OrderStatus.CANCELLED
                : statusOpen().equals(statusChoice) ? OrderStatus.OPEN : null;
        HistoryFilter filter = new HistoryFilter(searchField.getText(), status, fromPicker.getValue(), toPicker.getValue());
        return onlyCustomerId == null ? filter : filter.forCustomer(onlyCustomerId);
    }

    /** A search/status/date change invalidates whatever page we were on (there may not even be
     *  that many pages of the new, narrower result set), so always jump back to page 1. */
    private void resetAndReload() {
        pageBar.setCurrentPage(1);
        reload();
    }

    /** The date range the person was last told about, so the warning is shown once and not on every key press (TODO 3.58). */
    private String warnedRange;

    private void reload() {
        HistoryFilter filter = currentFilter();
        if (filter.hasReversedDates()) {
            // "From" after "To" matches nothing: say so instead of showing an empty list that looks like "no orders" (TODO 3.58).
            requestCounter++; // an answer still on its way must not fill the table
            table.getItems().clear();
            totalCount = 0;
            pageBar.setTotalItems(0);
            countLabel.setText(I18n.t("history.dates.invalid"));
            String range = filter.from() + ">" + filter.to();
            if (!range.equals(warnedRange)) {
                warnedRange = range;
                AlertUtil.warning(I18n.t("history.title"), I18n.t("history.dates.invalid"));
            }
            return;
        }
        warnedRange = null;
        OrderDAO.HistorySort order = sort;
        int pageSize = pageBar.getPageSize();
        int page = pageBar.getCurrentPage();
        final int myRequest = ++requestCounter;
        countLabel.setText(I18n.t("common.loading"));
        Async.run(
                () -> new HistoryPage(
                        historyService.findHistoryPage(filter, order, pageSize, (int) Math.min(Integer.MAX_VALUE, (long) (page - 1) * pageSize)),
                        historyService.countHistory(filter)),
                result -> {
                    if (myRequest != requestCounter) return; // a newer query has been started: this answer is out of date
                    table.getItems().setAll(result.rows());
                    totalCount = result.totalCount();
                    countLabel.setText(I18n.t("history.count", totalCount));
                    pageBar.setTotalItems(totalCount);
                    if (pageBar.getCurrentPage() != page) {
                        reload(); // the page we asked for no longer exists (e.g. after a cancel) - load the clamped one
                    }
                },
                error -> {
                    if (myRequest != requestCounter) return;
                    AlertUtil.error(I18n.t("dashboard.load.title"), I18n.t("history.load.error", error.getMessage()));
                }
        );
    }

    /** Writes every order matching the filters on screen (not just the page shown), in the sort order on screen. */
    private void exportCsv() {
        if (!stillAllowed()) return;
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("history.export.adminonly"));
            return;
        }
        if (currentFilter().hasReversedDates()) {
            AlertUtil.warning(I18n.t("history.export.title"), I18n.t("history.dates.invalid")); // no empty file (TODO 3.58)
            return;
        }
        OrderHistoryExport.run(stage, exportButton, historyService, currentFilter(), sort);
    }

    private void updateButtons(OrderSummary selected) {
        viewButton.setDisable(selected == null);
        cancelButton.setDisable(selected == null || !Session.isAdmin()
                || selected.order().getStatus() != OrderStatus.PAID);
        refundLinesButton.setDisable(selected == null || !Session.isAdmin()
                || selected.order().getStatus() != OrderStatus.PAID);
    }

    /** "Hoàn từng dòng": refund some pieces of some lines of the selected paid order, not the whole order (TODO 4.37). */
    private void refundSelectedLines() {
        OrderSummary selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || !stillAllowed()) return;
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("history.cancel.adminonly"));
            return;
        }
        RefundLinesDialog.show(selected.order(), this::reload);
    }

    /** Re-reads the account before showing customer data or refunding: a demoted or locked person is sent to login. */
    private boolean stillAllowed() {
        if (!com.coffeeshop.coffeeshopmanagement.util.SessionGuard.validateNow()) {
            com.coffeeshop.coffeeshopmanagement.util.SessionGuard.forceLogout();
            return false;
        }
        return true;
    }

    private void viewSelectedReceipt() {
        OrderSummary selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || !stillAllowed()) return;
        Async.run(
                () -> historyService.findItemsByOrderId(selected.order().getId()),
                (List<OrderItem> items) -> {
                    HoaDonController controller = SceneNavigator.loadAndShowNewWindow(
                            "/fxml/donthanhtoan.fxml", ReceiptText.t("pos.invoice.title", selected.order().getId()), ReceiptText.bundle());
                    if (controller != null) {
                        controller.setInvoiceData(selected.order(), items,
                                selected.employeeName(), selected.customerName());
                    }
                },
                error -> AlertUtil.error(I18n.t("common.error.title"), I18n.t("history.detail.error", error.getMessage()))
        );
    }

    private void cancelSelectedOrder() {
        OrderSummary selected = table.getSelectionModel().getSelectedItem();
        if (selected == null || !stillAllowed()) return;
        if (!OrderCancelService.mayCancelPaid()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("history.cancel.adminonly"));
            return;
        }
        OrderCancelFlow.run(selected.order().getId(), selected.order().getTotal(), cancelButton, this::reload); // the question, then the cancel
    }

    /** The time of payment when there is one, else the time the order was opened: the same day the money is counted under (TODO 2.67). */
    static java.time.LocalDateTime shownTime(com.coffeeshop.coffeeshopmanagement.model.Order order) {
        return order.getPaidAt() != null ? order.getPaidAt() : order.getOrderDate();
    }
}
