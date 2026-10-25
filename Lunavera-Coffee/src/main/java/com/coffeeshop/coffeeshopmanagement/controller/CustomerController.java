package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.service.CustomerStats;
import com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.PagedTable;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.Callback;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Backs Customer Management (quanlykhachhang.fxml). Its own class rather than a 7th screen on
 * the old shared admin controller (since split, see AdminScreen) - same reasoning as OrderController (see its javadoc and progress.md,
 * Session 6/7): that class was already large and this is a clean, simple screen to start fresh
 * with distinct fx:id names, avoiding the collision risk entirely rather than adding to it.
 *
 * The screen lists and searches customers, adds and edits them, and opens a customer's purchase history (double-click or the
 * history button, through {@code OrderHistoryWindow.showForCustomer}). There is deliberately no delete button: a customer may
 * be referenced by past orders via customer_id or by a login account, and removing one safely is a bigger feature than this
 * screen needs. (The data layer's {@code deleteIfUnused} exists only to undo a customer created for a payment that then
 * failed.)
 */
public class CustomerController {

    private final CustomerDAO customerDAO = new CustomerDAO();

    @FXML private Label accountNameLabel;
    @FXML private Label accountRoleLabel;
    @FXML private TextField customerSearchField;
    @FXML private TableView<Customer> customerTable;
    @FXML private TableColumn<Customer, Number> customerIndexColumn;
    @FXML private TableColumn<Customer, String> customerNameColumn;
    @FXML private TableColumn<Customer, String> customerPhoneColumn;
    @FXML private TableColumn<Customer, String> customerEmailColumn;
    @FXML private TableColumn<Customer, Number> customerLoyaltyColumn;
    @FXML private TableColumn<Customer, String> customerTierColumn;
    @FXML private TableColumn<Customer, Number> customerVisitsColumn;
    @FXML private TableColumn<Customer, String> customerSpentColumn;
    @FXML private TableColumn<Customer, String> customerLastVisitColumn;
    @FXML private TableColumn<Customer, Void> customerActionColumn;
    @FXML private javafx.scene.layout.HBox customerPagerBox;
    @FXML private javafx.scene.control.Label customerPaginationLabel;
    private PagedTable<Customer> customerPaged;

    @FXML private Button addCustomerButton;
    @FXML private Button productMenuButton;
    @FXML private javafx.scene.control.Separator categorySeparator;
    @FXML private javafx.scene.layout.VBox adminMenuBox;

    private List<Customer> allCustomers = List.of();
    /** Visits, total spent and last visit per customer id (TODO 4.39). */
    private java.util.Map<Integer, CustomerStats.Figures> stats = java.util.Map.of();
    private javafx.scene.control.ComboBox<CustomerStats.Filter> groupFilter;

    private record Loaded(List<Customer> customers, java.util.Map<Integer, CustomerStats.Figures> stats) {
    }

    private static final java.time.format.DateTimeFormatter LAST_VISIT = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML
    private void initialize() {
        if (accountNameLabel != null) accountNameLabel.setText(Session.getDisplayName());
        if (accountRoleLabel != null) accountRoleLabel.setText(I18n.t(Session.isAdmin() ? "role.admin" : "role.employee"));

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

        customerPaged = new PagedTable<>(customerTable, customerPagerBox, 10)
                .unsortable(customerIndexColumn, customerActionColumn)
                .sortKey(customerTierColumn, Customer::getLoyaltyPoints) // rank by points, not by the label text
                .sortKey(customerVisitsColumn, c -> CustomerStats.of(stats, c.getId()).visits())
                .sortKey(customerSpentColumn, c -> CustomerStats.of(stats, c.getId()).spent())
                .sortKey(customerLastVisitColumn, c -> {
                    java.time.LocalDateTime last = CustomerStats.of(stats, c.getId()).lastVisit();
                    return last == null ? java.time.LocalDateTime.MIN : last;
                });
        customerIndexColumn.setCellValueFactory(data -> new SimpleIntegerProperty(
                customerTable.getItems().indexOf(data.getValue()) + customerPaged.getFromIndex()));
        customerNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFullName()));
        customerPhoneColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getPhone() != null ? data.getValue().getPhone() : "-"));
        customerEmailColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getEmail() != null ? data.getValue().getEmail() : "-"));
        customerLoyaltyColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getLoyaltyPoints()));
        customerTierColumn.setCellValueFactory(data -> new SimpleStringProperty(LoyaltyPolicy.tierName(data.getValue().getLoyaltyPoints())));
        customerVisitsColumn.setCellValueFactory(data -> new SimpleIntegerProperty(CustomerStats.of(stats, data.getValue().getId()).visits()));
        customerSpentColumn.setCellValueFactory(data -> {
            CustomerStats.Figures f = CustomerStats.of(stats, data.getValue().getId());
            return new SimpleStringProperty(f.visits() == 0 ? "-"
                    : com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil.format(f.spent()));
        });
        customerLastVisitColumn.setCellValueFactory(data -> {
            java.time.LocalDateTime last = CustomerStats.of(stats, data.getValue().getId()).lastVisit();
            return new SimpleStringProperty(last == null ? "-" : last.format(LAST_VISIT));
        });
        customerActionColumn.setCellFactory(editOnlyActionColumn(this::openEditDialog));

        addExtraButtons();

        TextLengthLimiter.limit(customerSearchField, TextLengthLimiter.SEARCH_MAX);
        customerSearchField.textProperty().addListener((obs, old, value) -> applyFilter());

        reloadCustomers();
    }

    /** "Lịch sử mua" for the selected customer (TODO 4.4) and, for an admin, "Xuất CSV" of the customer list (TODO 4.21). */
    private void addExtraButtons() {
        if (addCustomerButton == null || !(addCustomerButton.getParent() instanceof javafx.scene.layout.HBox bar)) return;
        Button history = new Button(I18n.t("customers.history"));
        history.setPrefHeight(42);
        history.setDisable(true);
        history.setOnAction(e -> openPurchaseHistory());
        customerTable.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> history.setDisable(selected == null));
        customerTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) openPurchaseHistory();
        });
        groupFilter = new javafx.scene.control.ComboBox<>(javafx.collections.FXCollections.observableArrayList(CustomerStats.Filter.values()));
        groupFilter.setPrefHeight(42);
        groupFilter.setValue(CustomerStats.Filter.ALL);
        groupFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(CustomerStats.Filter f) {
                if (f == null) return "";
                return switch (f) {
                    case REGULARS -> I18n.t("customers.group.regulars", CustomerStats.REGULAR_VISITS);
                    case TOP -> I18n.t("customers.group.top", CustomerStats.TOP_COUNT);
                    case LAPSED -> I18n.t("customers.group.lapsed", CustomerStats.LAPSED_DAYS);
                    default -> I18n.t("customers.group.all");
                };
            }
            @Override public CustomerStats.Filter fromString(String text) { return CustomerStats.Filter.ALL; }
        });
        groupFilter.valueProperty().addListener((o, old, value) -> applyFilter());
        int searchAt = bar.getChildren().indexOf(customerSearchField);
        bar.getChildren().add(searchAt + 1, groupFilter);
        int at = bar.getChildren().indexOf(addCustomerButton);
        bar.getChildren().add(at, history);
        if (Session.isAdmin()) {
            Button export = new Button(I18n.t("common.exportcsv"));
            export.setPrefHeight(42);
            export.setOnAction(e -> exportCsv());
            bar.getChildren().add(at + 1, export);
        }
    }

    private void openPurchaseHistory() {
        Customer selected = customerTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        com.coffeeshop.coffeeshopmanagement.controller.OrderHistoryWindow.showForCustomer(selected.getId(), selected.getFullName());
    }

    /** Exports the whole customer list, not only the rows matching the search box. */
    private void exportCsv() {
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("customers.export.adminonly"));
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle(I18n.t("customers.export.title"));
        chooser.setInitialFileName("khach-hang-" + java.time.LocalDate.now() + ".csv");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV", "*.csv"));
        java.io.File file = chooser.showSaveDialog(customerTable.getScene().getWindow());
        if (file == null) return;
        Runnable pointer = com.coffeeshop.coffeeshopmanagement.util.BusyIndicator.waitCursor(customerTable); // TODO 5.16
        Async.run(
                () -> {
                    com.coffeeshop.coffeeshopmanagement.util.Permissions.requireAdmin("export customers");
                    List<Customer> all = customerDAO.findAll();
                    java.util.Map<Integer, CustomerStats.Figures> figures = CustomerStats.aggregate(customerDAO.paidOrderRows());
                    com.coffeeshop.coffeeshopmanagement.util.ExportCsv.write(file.toPath(),
                            com.coffeeshop.coffeeshopmanagement.util.ExportCsv.customers(all, figures));
                    com.coffeeshop.coffeeshopmanagement.util.SecurityLog.event("exported " + all.size() + " customers to CSV");
                    return all.size();
                },
                count -> {
                    pointer.run();
                    AlertUtil.info(I18n.t("customers.export.title"), I18n.t("customers.export.done", count, file.getAbsolutePath()));
                },
                error -> {
                    pointer.run();
                    AlertUtil.error(I18n.t("common.file.savefail"), error.getMessage());
                });
    }

    private void reloadCustomers() {
        Async.run(
                () -> new Loaded(customerDAO.findAll(), CustomerStats.aggregate(customerDAO.paidOrderRows())),
                result -> { allCustomers = result.customers(); stats = result.stats(); applyFilter(); },
                error -> AlertUtil.error(I18n.t("dashboard.load.title"), I18n.t("customers.load.error", error.getMessage()))
        );
    }

    private void applyFilter() {
        // Name, phone and e-mail, by the rule in CustomerSearch (TODO 5.35; a typed "+84 901 111 222" finds "0901111222", TODO 3.39).
        String typed = customerSearchField.getText();
        List<Customer> filtered = allCustomers.stream()
                .filter(c -> com.coffeeshop.coffeeshopmanagement.service.CustomerSearch.matches(c, typed))
                .collect(Collectors.toList());
        if (groupFilter != null) {
            filtered = CustomerStats.apply(groupFilter.getValue(), filtered, stats, com.coffeeshop.coffeeshopmanagement.util.AppClock.today());
        }
        customerPaged.setItems(filtered);
        if (customerPaginationLabel != null) {
            customerPaginationLabel.setText(I18n.t("customers.pagination", customerPaged.getFromIndex(), customerPaged.getToIndex(), customerPaged.getTotalCount()));
        }
    }

    @FXML
    public void handleAddCustomer() {
        openCustomerDialog(null);
    }

    private void openEditDialog(Customer customer) {
        openCustomerDialog(customer);
    }

    private void openCustomerDialog(Customer existing) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(I18n.t(existing == null ? "customers.dialog.add" : "customers.dialog.edit"));
        AlertUtil.configure(dialog);
        ButtonType saveButtonType = new ButtonType(I18n.t("common.save"), ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        AlertUtil.setDefaultButton(dialog, saveButtonType);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        TextField nameField = new TextField(existing != null ? existing.getFullName() : "");
        TextLengthLimiter.limit(nameField, TextLengthLimiter.NAME_MAX);
        TextField phoneField = new TextField(existing != null ? existing.getPhone() : "");
        TextLengthLimiter.limit(phoneField, TextLengthLimiter.PHONE_MAX);
        // The phone is the customer's identity (loyalty points follow it), so only an administrator may change it (TODO 2A.24).
        final String originalPhone = existing != null && existing.getPhone() != null
                ? ValidationUtil.normalizePhone(existing.getPhone()) : "";
        if (existing != null && !Session.isAdmin()) {
            phoneField.setDisable(true);
            phoneField.setTooltip(new javafx.scene.control.Tooltip(I18n.t("customers.phone.adminonly")));
        }
        TextField emailField = new TextField(existing != null ? existing.getEmail() : "");
        TextLengthLimiter.limit(emailField, TextLengthLimiter.EMAIL_MAX);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label(I18n.t("customers.label.name")), nameField);
        grid.addRow(1, new Label(I18n.t("customers.label.phone")), phoneField);
        grid.addRow(2, new Label("Email:"), emailField);
        dialog.getDialogPane().setContent(grid);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (ValidationUtil.isBlank(nameField.getText())) {
                AlertUtil.warning(I18n.t("common.missing.title"), I18n.t("customers.val.name"));
                event.consume();
                return;
            }
            if (!ValidationUtil.isBlank(phoneField.getText()) && !ValidationUtil.isValidPhone(phoneField.getText())) {
                AlertUtil.warning(I18n.t("customers.val.phone.title"), I18n.t("customers.val.phone.message"));
                event.consume();
                return;
            }
            if (!ValidationUtil.isBlank(emailField.getText()) && !ValidationUtil.isValidEmail(emailField.getText())) {
                AlertUtil.warning(I18n.t("customers.val.email.title"), I18n.t("customers.val.email.message"));
                event.consume();
                return;
            }
            if (!ValidationUtil.isBlank(phoneField.getText())
                    && customerDAO.existsByPhone(phoneField.getText().trim(), existing != null ? existing.getId() : 0)) {
                AlertUtil.warning(I18n.t("customers.val.dup.title"), I18n.t("customers.val.dup.message"));
                event.consume();
                return;
            }
            String typedPhone = ValidationUtil.isBlank(phoneField.getText()) ? "" : ValidationUtil.normalizePhone(phoneField.getText());
            boolean phoneChanged = existing != null && !typedPhone.equals(originalPhone);
            if (phoneChanged && !Session.isAdmin()) {
                AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("customers.phone.adminonly"));
                event.consume();
                return;
            }
            if (phoneChanged) {
                com.coffeeshop.coffeeshopmanagement.util.SecurityLog.event(com.coffeeshop.coffeeshopmanagement.util.SecurityLog.actor()
                        + " changed the phone of customer id " + existing.getId());
            }
            Customer customer = existing != null ? existing : new Customer();
            customer.setFullName(nameField.getText().trim());
            customer.setPhone(ValidationUtil.isBlank(phoneField.getText()) ? null : phoneField.getText().trim());
            customer.setEmail(ValidationUtil.isBlank(emailField.getText()) ? null : emailField.getText().trim());
            try {
                if (existing == null) {
                    customer.setLoyaltyPoints(0);
                    customerDAO.insert(customer);
                } else {
                    customerDAO.update(customer);
                }
            } catch (DataAccessException ex) {
                AlertUtil.error(I18n.t("customers.save.fail"), ex.getMessage());
                event.consume();
            }
        });

        AlertUtil.confirmBeforeDiscarding(dialog, ButtonType.CANCEL);
        dialog.showAndWait();
        reloadCustomers();
    }

    /** Single-button (edit only, no delete - see class javadoc) action column, same visual
     *  pattern as the ✎/🗑 pair used elsewhere, just without the delete half. */
    private Callback<TableColumn<Customer, Void>, TableCell<Customer, Void>> editOnlyActionColumn(Consumer<Customer> onEdit) {
        return column -> new TableCell<>() {
            private final Button editButton = new Button("✎");
            {
                editButton.setTooltip(new javafx.scene.control.Tooltip(I18n.t("common.edit")));
                editButton.setAccessibleText(I18n.t("common.edit"));
                editButton.setOnAction(e -> onEdit.accept(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editButton);
            }
        };
    }

    // =================================================================== Navigation
    // Duplicated from AdminScreen/OrderController rather than shared - see OrderController's
    // javadoc for the reasoning; this is now the third occurrence, worth extracting to a shared
    // base class next time a new screen needs the same navigation set.

    @FXML
    public void openDashboard(ActionEvent event) {
        String target = Session.isAdmin() ? "/fxml/admin-trangchu.fxml" : "/fxml/employee-trangchu.fxml";
        SceneNavigator.switchScene(event, target);
    }

    @FXML
    public void openProductManagement(ActionEvent event) {
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("admin.only.message"));
            return;
        }
        SceneNavigator.switchScene(event, "/fxml/quanlysanpham.fxml");
    }

    @FXML
    public void openOrderManagement(ActionEvent event) {
        SceneNavigator.switchScene(event, "/fxml/quanlydonhang.fxml");
    }

    @FXML
    public void openCategoryManagement(ActionEvent event) {
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("admin.only.message"));
            return;
        }
        SceneNavigator.switchScene(event, "/fxml/quanlydanhmuc.fxml");
    }

    @FXML
    public void openAccountManagement(ActionEvent event) {
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("admin.only.message"));
            return;
        }
        SceneNavigator.switchScene(event, "/fxml/quanlytaikhoan.fxml");
    }

    @FXML
    public void logout(ActionEvent event) {
        BackupDialogs.offerBeforeLeaving(BackupDialogs.windowOf(event), () -> { // TODO 5.18
            Session.clear();
            SceneNavigator.switchScene(event, "/fxml/dangnhap.fxml");
        });
    }
}
