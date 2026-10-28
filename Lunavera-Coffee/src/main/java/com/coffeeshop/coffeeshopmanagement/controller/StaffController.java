package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.StaffRow;
import com.coffeeshop.coffeeshopmanagement.service.StaffRules;
import com.coffeeshop.coffeeshopmanagement.service.StaffService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.PagedTable;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.TextNormalizer;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Backs the staff screen (quanlynhanvien.fxml, TODO 4.12): every staff record, with or without a login account, with search,
 * filters, a detail card, an add/edit form and "left / working again". It is administrator-only, like the account screen,
 * because it shows salaries.
 *
 * <p>This screen never touches passwords or roles: a person's login is made, given a role and reset on the account screen.
 * The detail card says which account belongs to the person and the "left" button on a person with an account locks that
 * account through {@link StaffService}, so the two screens cannot disagree about who is working.
 */
public class StaffController extends AdminScreen {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int ALL = 0;
    private static final int WORKING_OR_WITH_LOGIN = 1;

    private final StaffService staffService = new StaffService();

    @FXML private Label totalStaffLabel;
    @FXML private Label workingStaffLabel;
    @FXML private Label leftStaffLabel;
    @FXML private Label loginStaffLabel;
    @FXML private TextField staffSearchField;
    @FXML private ComboBox<String> staffStatusFilter;
    @FXML private ComboBox<String> staffLoginFilter;
    @FXML private Button addStaffButton;
    @FXML private Label staffCountLabel;
    @FXML private TableView<StaffRow> staffTable;
    @FXML private TableColumn<StaffRow, Number> staffIndexColumn;
    @FXML private TableColumn<StaffRow, String> staffNameColumn;
    @FXML private TableColumn<StaffRow, String> staffPhoneColumn;
    @FXML private TableColumn<StaffRow, String> staffPositionColumn;
    @FXML private TableColumn<StaffRow, String> staffStartColumn;
    @FXML private TableColumn<StaffRow, String> staffLoginColumn;
    @FXML private TableColumn<StaffRow, String> staffStatusColumn;
    @FXML private TableColumn<StaffRow, Void> staffActionColumn;
    @FXML private Label staffPaginationLabel;
    @FXML private HBox staffPagerBox;
    @FXML private VBox staffDetailCard;
    @FXML private Label detailName;
    @FXML private Label detailState;
    @FXML private Label detailPhoneLabel;
    @FXML private Label detailEmailLabel;
    @FXML private Label detailPositionLabel;
    @FXML private Label detailStartLabel;
    @FXML private Label detailSalaryLabel;
    @FXML private Label detailAddressLabel;
    @FXML private Label detailLoginLabel;
    @FXML private Label detailRoleLabel;
    @FXML private Button editStaffButton;
    @FXML private Button leftStaffButton;
    private Button removeStaffButton;     // built in code, under the two buttons of the detail card (TODO 4.29)
    private Button anonymiseStaffButton;

    private PagedTable<StaffRow> staffPaged;
    private List<StaffRow> allStaff = List.of();
    private StaffRow selected;

    @FXML
    private void initialize() {
        if (blockNonAdmin(staffTable)) {
            return;
        }
        staffPaged = new PagedTable<>(staffTable, staffPagerBox, 10)
                .unsortable(staffIndexColumn, staffActionColumn)
                .sortKey(staffStartColumn, row -> row.employee().getHireDate()); // real date order, not the dd/MM text
        staffIndexColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(staffTable.getItems().indexOf(data.getValue()) + staffPaged.getFromIndex()));
        staffNameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().employee().getFullName()));
        staffPhoneColumn.setCellValueFactory(data -> new SimpleStringProperty(orDash(data.getValue().employee().getPhone())));
        staffPositionColumn.setCellValueFactory(data -> new SimpleStringProperty(orDash(data.getValue().employee().getPosition())));
        staffStartColumn.setCellValueFactory(data -> new SimpleStringProperty(startText(data.getValue().employee())));
        staffLoginColumn.setCellValueFactory(data -> new SimpleStringProperty(loginText(data.getValue())));
        staffStatusColumn.setCellValueFactory(data -> new SimpleStringProperty(statusText(data.getValue())));
        staffActionColumn.setCellFactory(actionColumnFactory(this::showDetail, null));

        staffStatusFilter.getItems().setAll(I18n.t("history.status.all"), I18n.t("staff.status.working"), I18n.t("staff.status.left"));
        staffStatusFilter.getSelectionModel().selectFirst();
        staffLoginFilter.getItems().setAll(I18n.t("staff.filter.allaccounts"), I18n.t("staff.filter.haslogin"), I18n.t("staff.filter.nologin"));
        staffLoginFilter.getSelectionModel().selectFirst();
        TextLengthLimiter.limit(staffSearchField, TextLengthLimiter.SEARCH_MAX);
        staffSearchField.textProperty().addListener((obs, old, value) -> applyFilter());
        staffDetailCard.managedProperty().bind(staffDetailCard.visibleProperty());
        staffDetailCard.setVisible(false);
        addRemoveButtons();
        reload();
    }

    /**
     * "Xóa hồ sơ" and "Ẩn danh hóa" under the buttons of the detail card (TODO 4.29): a former member of staff can be deleted when they
     * took part in no sale, stock line or closed shift, and made anonymous (name and contact details removed, sales kept) when they did.
     */
    private void addRemoveButtons() {
        if (leftStaffButton == null || !(leftStaffButton.getParent() instanceof javafx.scene.layout.Pane row)) return;
        removeStaffButton = new Button(I18n.t("staff.button.remove"));
        removeStaffButton.getStyleClass().add("danger-button");
        removeStaffButton.setMaxWidth(Double.MAX_VALUE);
        removeStaffButton.setOnAction(e -> handleRemoveStaff());
        anonymiseStaffButton = new Button(I18n.t("staff.button.anonymise"));
        anonymiseStaffButton.getStyleClass().add("secondary-button");
        anonymiseStaffButton.setMaxWidth(Double.MAX_VALUE);
        anonymiseStaffButton.setOnAction(e -> handleAnonymiseStaff());
        HBox second = new HBox(8, removeStaffButton, anonymiseStaffButton);
        HBox.setHgrow(removeStaffButton, javafx.scene.layout.Priority.ALWAYS);
        HBox.setHgrow(anonymiseStaffButton, javafx.scene.layout.Priority.ALWAYS);
        if (row.getParent() instanceof VBox column) {
            column.getChildren().add(column.getChildren().indexOf(row) + 1, second);
        } else {
            row.getChildren().add(second);
        }
    }

    private void handleRemoveStaff() {
        if (!requireAdmin()) return;
        if (selected == null) {
            AlertUtil.info(I18n.t("staff.none.title"), I18n.t("staff.none.message"));
            return;
        }
        final StaffRow target = selected;
        Async.run(
                () -> staffService.hasHistory(target),
                history -> {
                    if (history) {
                        // too late to delete without breaking the records: say what can be done instead
                        AlertUtil.info(I18n.t("staff.remove.history.title"), I18n.t("staff.remove.history"));
                        return;
                    }
                    String name = target.employee().getFullName();
                    if (!AlertUtil.confirm(I18n.t("staff.remove.confirm.title"),
                            I18n.t(target.hasAccount() ? "staff.remove.confirm.login" : "staff.remove.confirm", name))) {
                        return;
                    }
                    Async.run(
                            () -> {
                                staffService.remove(target);
                                return Boolean.TRUE;
                            },
                            done -> {
                                handleCloseDetail();
                                reload();
                            },
                            error -> AlertUtil.error(I18n.t("staff.remove.fail"), error.getMessage()),
                            staffTable);
                },
                error -> AlertUtil.error(I18n.t("staff.remove.fail"), error.getMessage()),
                staffTable);
    }

    private void handleAnonymiseStaff() {
        if (!requireAdmin()) return;
        if (selected == null) {
            AlertUtil.info(I18n.t("staff.none.title"), I18n.t("staff.none.message"));
            return;
        }
        final StaffRow target = selected;
        if (target.isWorking()) {
            AlertUtil.info(I18n.t("staff.anon.title"), I18n.t("staff.anon.working"));
            return;
        }
        if (!AlertUtil.confirm(I18n.t("staff.anon.title"), I18n.t("staff.anon.confirm", target.employee().getFullName()))) {
            return;
        }
        Async.run(
                () -> {
                    staffService.makeAnonymous(target);
                    return Boolean.TRUE;
                },
                done -> {
                    handleCloseDetail();
                    reload();
                },
                error -> AlertUtil.error(I18n.t("staff.anon.fail"), error.getMessage()),
                staffTable);
    }

    private void reload() {
        Async.run(staffService::list,
                rows -> {
                    allStaff = rows;
                    applyFilter();
                    if (selected != null) { // keep the card in step with what was just saved
                        int id = selected.employee().getId();
                        allStaff.stream().filter(r -> r.employee().getId() == id).findFirst()
                                .ifPresentOrElse(this::showDetail, this::handleCloseDetail);
                    }
                },
                error -> AlertUtil.error(I18n.t("common.error.title"), error.getMessage()));
    }

    private void applyFilter() {
        String keyword = TextNormalizer.searchKey(staffSearchField.getText() == null ? "" : staffSearchField.getText());
        int statusChoice = staffStatusFilter.getSelectionModel().getSelectedIndex();
        int loginChoice = staffLoginFilter.getSelectionModel().getSelectedIndex();

        List<StaffRow> filtered = allStaff.stream()
                .filter(r -> keyword.isEmpty() || matches(r, keyword))
                .filter(r -> statusChoice <= ALL || r.isWorking() == (statusChoice == WORKING_OR_WITH_LOGIN))
                .filter(r -> loginChoice <= ALL || r.hasAccount() == (loginChoice == WORKING_OR_WITH_LOGIN))
                .collect(Collectors.toList());
        staffPaged.setItems(filtered);

        totalStaffLabel.setText(String.valueOf(allStaff.size()));
        long working = allStaff.stream().filter(StaffRow::isWorking).count();
        workingStaffLabel.setText(String.valueOf(working));
        leftStaffLabel.setText(String.valueOf(allStaff.size() - working));
        loginStaffLabel.setText(String.valueOf(allStaff.stream().filter(StaffRow::hasAccount).count()));
        staffCountLabel.setText(I18n.t("staff.count", filtered.size()));
        staffPaginationLabel.setText(I18n.t("staff.pagination", staffPaged.getFromIndex(), staffPaged.getToIndex(), staffPaged.getTotalCount()));
    }

    private static boolean matches(StaffRow row, String keyword) {
        Employee e = row.employee();
        return TextNormalizer.searchKey(e.getFullName()).contains(keyword)
                || (e.getPhone() != null && e.getPhone().toLowerCase(Locale.ROOT).contains(keyword))
                || (e.getPosition() != null && TextNormalizer.searchKey(e.getPosition()).contains(keyword))
                || (row.username() != null && TextNormalizer.searchKey(row.username()).contains(keyword));
    }

    // ------------------------------------------------------------------ text for the table and the card

    private static String orDash(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    private static String startText(Employee e) {
        return e.getHireDate() == null ? "-" : e.getHireDate().format(DATE_FORMAT);
    }

    private static String loginText(StaffRow row) {
        return row.hasAccount() ? row.username() : I18n.t("staff.nologin");
    }

    private static String statusText(StaffRow row) {
        return row.isWorking() ? I18n.t("staff.status.working") : I18n.t("staff.status.left");
    }

    private static String roleText(Role role) {
        return role == Role.ADMIN ? I18n.t("role.admin") : I18n.t("role.employee");
    }

    // ------------------------------------------------------------------ handlers

    @FXML
    public void handleSearch() {
        applyFilter();
    }

    @FXML
    public void handleFilterChange() {
        applyFilter();
    }

    @FXML
    public void handleSelectStaff() {
        StaffRow row = staffTable.getSelectionModel().getSelectedItem();
        if (row != null) {
            showDetail(row);
        }
    }

    private void showDetail(StaffRow row) {
        selected = row;
        Employee e = row.employee();
        detailName.setText(e.getFullName());
        detailState.setText(statusText(row));
        detailPhoneLabel.setText(orDash(e.getPhone()));
        detailEmailLabel.setText(orDash(e.getEmail()));
        detailPositionLabel.setText(orDash(e.getPosition()));
        detailStartLabel.setText(startText(e));
        // The screen is administrator-only (requireAdmin and the service), so showing the salary here is not a leak.
        detailSalaryLabel.setText(e.getSalary() == null ? "-" : CurrencyUtil.format(e.getSalary()));
        detailAddressLabel.setText(orDash(e.getAddress()));
        detailLoginLabel.setText(loginText(row));
        detailRoleLabel.setText(row.hasAccount() ? roleText(row.role()) : "-");
        leftStaffButton.setText(row.isWorking() ? I18n.t("staff.button.left") : I18n.t("staff.button.working"));
        boolean anonymous = e.isAnonymised(); // TODO 4.29: nothing personal is left to edit, and they cannot come back
        editStaffButton.setDisable(anonymous);
        leftStaffButton.setDisable(anonymous);
        if (anonymiseStaffButton != null) {
            anonymiseStaffButton.setDisable(anonymous || row.isWorking());
        }
        staffDetailCard.setVisible(true);
    }

    @FXML
    public void handleCloseDetail() {
        selected = null;
        staffDetailCard.setVisible(false);
        staffTable.getSelectionModel().clearSelection();
    }

    @FXML
    public void handleAddStaff() {
        if (!requireAdmin()) return;
        openStaffDialog(null);
    }

    @FXML
    public void handleEditStaff() {
        if (!requireAdmin()) return;
        if (selected == null) {
            AlertUtil.info(I18n.t("staff.none.title"), I18n.t("staff.none.message"));
            return;
        }
        openStaffDialog(selected);
    }

    @FXML
    public void handleToggleWorking() {
        if (!requireAdmin()) return;
        if (selected == null) {
            AlertUtil.info(I18n.t("staff.none.title"), I18n.t("staff.none.message"));
            return;
        }
        final StaffRow target = selected;
        final boolean nowWorking = target.isWorking();
        String name = target.employee().getFullName();
        boolean confirmed = nowWorking
                ? AlertUtil.confirm(I18n.t("staff.left.confirm.title"),
                        I18n.t(target.hasAccount() ? "staff.left.confirm.login" : "staff.left.confirm", name))
                : AlertUtil.confirm(I18n.t("staff.working.confirm.title"), I18n.t("staff.working.confirm", name));
        if (!confirmed) {
            return;
        }
        Async.run(
                () -> {
                    staffService.setWorking(target, !nowWorking);
                    return Boolean.TRUE;
                },
                done -> reload(),
                error -> AlertUtil.error(I18n.t("staff.status.fail"), error.getMessage()),
                staffTable);
    }

    // ------------------------------------------------------------------ the add / edit form

    private void openStaffDialog(StaffRow existing) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? I18n.t("staff.dialog.add") : I18n.t("staff.dialog.edit"));
        AlertUtil.configure(dialog);
        ButtonType saveButtonType = new ButtonType(I18n.t("common.save"), ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        AlertUtil.setDefaultButton(dialog, saveButtonType);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        Employee current = existing == null ? null : existing.employee();
        TextField nameField = limited(text(current == null ? null : current.getFullName()), TextLengthLimiter.NAME_MAX);
        TextField phoneField = limited(text(current == null ? null : current.getPhone()), TextLengthLimiter.PHONE_MAX);
        TextField emailField = limited(text(current == null ? null : current.getEmail()), TextLengthLimiter.EMAIL_MAX);
        TextField positionField = limited(text(current == null ? null : current.getPosition()), TextLengthLimiter.POSITION_MAX);
        TextField addressField = limited(text(current == null ? null : current.getAddress()), TextLengthLimiter.ADDRESS_MAX);
        TextField salaryField = limited(text(current == null || current.getSalary() == null ? null : current.getSalary().toPlainString()),
                TextLengthLimiter.NUMBER_MAX);
        salaryField.setPromptText(I18n.t("accounts.salary.prompt"));
        DatePicker startPicker = new DatePicker(current == null ? null : current.getHireDate());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label(I18n.t("staff.label.name")), nameField);
        grid.addRow(1, new Label(I18n.t("staff.label.phone")), phoneField);
        grid.addRow(2, new Label(I18n.t("staff.label.email")), emailField);
        grid.addRow(3, new Label(I18n.t("staff.label.position")), positionField);
        grid.addRow(4, new Label(I18n.t("staff.label.address")), addressField);
        grid.addRow(5, new Label(I18n.t("staff.label.salary")), salaryField);
        grid.addRow(6, new Label(I18n.t("staff.label.startdate")), startPicker);
        Label hint = new Label(I18n.t("staff.dialog.hint"));
        hint.setWrapText(true);
        hint.setMaxWidth(380);
        grid.add(hint, 0, 7, 2, 1);
        dialog.getDialogPane().setContent(grid);

        // An event filter on the button (not a result converter) so a refused save keeps the dialog open with everything typed,
        // as on the account dialog.
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            BigDecimal salary;
            try {
                salary = MoneyParser.parseDong(salaryField.getText());
            } catch (IllegalArgumentException e) {
                AlertUtil.warning(I18n.t("accounts.val.salary.title"), e.getMessage());
                event.consume();
                return;
            }
            Employee typed = new Employee();
            typed.setId(current == null ? 0 : current.getId());
            typed.setFullName(nameField.getText() == null ? "" : nameField.getText().trim());
            typed.setPhone(StaffRules.clean(phoneField.getText()));
            typed.setEmail(StaffRules.clean(emailField.getText()));
            typed.setPosition(StaffRules.clean(positionField.getText()));
            typed.setAddress(StaffRules.clean(addressField.getText()));
            typed.setSalary(salary);
            typed.setHireDate(startPicker.getValue());
            // The service checks the same rules again (it is the one that must not be bypassed); asking first here only lets the
            // warning have the title of a missing field instead of a save failure.
            String problem = StaffRules.problem(typed, com.coffeeshop.coffeeshopmanagement.util.AppClock.today());
            if (problem != null) {
                AlertUtil.warning(ValidationUtil.isBlank(typed.getFullName()) ? I18n.t("common.missing.title") : I18n.t("staff.val.title"), problem);
                event.consume();
                return;
            }
            try {
                staffService.save(typed);
            } catch (DataAccessException e) {
                AlertUtil.error(I18n.t("staff.save.fail"), e.getMessage());
                event.consume();
            }
        });

        AlertUtil.confirmBeforeDiscarding(dialog, ButtonType.CANCEL);
        dialog.showAndWait();
        reload();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static TextField limited(String initial, int max) {
        TextField field = new TextField(initial);
        TextLengthLimiter.limit(field, max);
        return field;
    }
}
