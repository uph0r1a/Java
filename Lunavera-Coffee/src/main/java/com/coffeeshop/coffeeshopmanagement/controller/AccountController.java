package com.coffeeshop.coffeeshopmanagement.controller;

import java.util.Locale;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.dao.UserDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.service.AccountService;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.PagedTable;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Backs account management (quanlytaikhoan.fxml): the account table, filters, the detail card, and the add/edit/lock
 * dialogs. Split out of the old {@code AdminController} (TODO 6.4).
 */
public class AccountController extends AdminScreen {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final UserDAO userDAO = new UserDAO();
    private final AccountService accountService = new AccountService();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    // ---------------------------------------------------------------- Account management
    @FXML private Label totalAccountLabel;
    @FXML private Label activeAccountLabel;
    @FXML private Label lockedAccountLabel;
    @FXML private Label adminAccountLabel;
    @FXML private TextField accountSearchField;
    @FXML private ComboBox<String> roleFilter;
    @FXML private ComboBox<String> accountStatusFilter;
    @FXML private Button addAccountButton;
    @FXML private Label accountCountLabel;
    @FXML private TableView<User> accountTable;
    @FXML private TableColumn<User, Number> accountIndexColumn;
    @FXML private TableColumn<User, String> usernameColumn;
    @FXML private TableColumn<User, String> fullNameColumn;
    @FXML private TableColumn<User, String> phoneColumn;
    @FXML private TableColumn<User, String> roleColumn;
    @FXML private TableColumn<User, String> statusColumn;
    @FXML private TableColumn<User, String> createdDateColumn;
    @FXML private TableColumn<User, Void> accountActionColumn;
    @FXML private Label accountPaginationLabel;
    @FXML private HBox accountPagerBox;
    @FXML private javafx.scene.layout.VBox accountDetailCard;
    @FXML private Label detailUsername;
    @FXML private Label detailRole;
    @FXML private Label detailUsernameLabel;
    @FXML private Label detailFullNameLabel;
    @FXML private Label detailPhoneLabel;
    @FXML private Label detailRoleLabel;
    @FXML private Label detailStatusLabel;
    @FXML private Label detailCreatedDateLabel;
    @FXML private Label detailPositionLabel;
    @FXML private Label detailHireDateLabel;
    @FXML private Label detailSalaryLabel;
    @FXML private Label detailAddressLabel;
    @FXML private javafx.scene.layout.VBox permissionBox;
    @FXML private Button lockAccountButton;

    private PagedTable<User> accountPaged;
    private List<User> allAccounts = List.of();
    // Loaded once per reload instead of calling employeeDAO.findById(id) per row/per
    // keystroke in employeeNameFor()/employeePhoneFor() - see progress.md performance notes.
    private Map<Integer, Employee> employeesById = Map.of();
    private User selectedAccount;

    @FXML
    private void initialize() {
        if (blockNonAdmin(accountTable)) {
            return;
        }
        initAccountScreen();
    }

    // =================================================================== Account management

    private void initAccountScreen() {
        accountPaged = new PagedTable<>(accountTable, accountPagerBox, 10)
                .unsortable(accountIndexColumn, accountActionColumn)
                .sortKey(createdDateColumn, User::getCreatedAt); // real date order, not the dd/MM text
        accountIndexColumn.setCellValueFactory(data ->
                new javafx.beans.property.SimpleIntegerProperty(
                        accountTable.getItems().indexOf(data.getValue()) + accountPaged.getFromIndex()));
        usernameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUsername()));
        fullNameColumn.setCellValueFactory(data -> new SimpleStringProperty(employeeNameFor(data.getValue())));
        phoneColumn.setCellValueFactory(data -> new SimpleStringProperty(employeePhoneFor(data.getValue())));
        roleColumn.setCellValueFactory(data -> new SimpleStringProperty(roleLabel(data.getValue().getRole())));
        statusColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().getStatus() == AccountStatus.ACTIVE ? I18n.t("accounts.status.active") : I18n.t("accounts.status.locked")));
        createdDateColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getCreatedAt() != null ? data.getValue().getCreatedAt().format(DATE_FORMAT) : "-"));
        accountActionColumn.setCellFactory(actionColumnFactory(this::showAccountDetail, null));

        if (roleFilter != null) {
            roleFilter.getItems().setAll(I18n.t("accounts.filter.allroles"), I18n.t("role.admin"), I18n.t("role.employee"), I18n.t("history.col.customer"));
            roleFilter.getSelectionModel().selectFirst();
        }
        if (accountStatusFilter != null) {
            accountStatusFilter.getItems().setAll(I18n.t("history.status.all"), I18n.t("accounts.status.active"), I18n.t("accounts.status.locked"));
            accountStatusFilter.getSelectionModel().selectFirst();
        }
        if (accountSearchField != null) {
            TextLengthLimiter.limit(accountSearchField, TextLengthLimiter.SEARCH_MAX);
            accountSearchField.textProperty().addListener((obs, old, value) -> applyAccountFilter());
        }
        if (!Session.isAdmin() && permissionBox != null) {
            // Employees can view accounts but not modify them - enforced here in code, not
            // just by hiding buttons (see progress.md section on permission checks).
            if (addAccountButton != null) addAccountButton.setDisable(true);
        }

        reloadAccounts();
    }

    private void reloadAccounts() {
        Async.run(
                () -> new Object[]{userDAO.findAll(), employeeDAO.findAll()},
                result -> {
                    allAccounts = castList(result[0]);
                    List<Employee> employees = castList(result[1]);
                    employeesById = employees.stream().collect(Collectors.toMap(Employee::getId, e -> e));
                    applyAccountFilter();
                },
                error -> AlertUtil.error(I18n.t("common.error.title"), error.getMessage())
        );
    }

    private void applyAccountFilter() {
        String keyword = accountSearchField != null && accountSearchField.getText() != null
                ? com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(accountSearchField.getText()) : "";
        String roleChoice = roleFilter != null ? roleFilter.getValue() : null;
        String statusChoice = accountStatusFilter != null ? accountStatusFilter.getValue() : null;

        List<User> filtered = allAccounts.stream()
                .filter(u -> keyword.isEmpty()
                        || com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(u.getUsername()).contains(keyword)
                        || com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(employeeNameFor(u)).contains(keyword)
                        || employeePhoneFor(u).toLowerCase(Locale.ROOT).contains(keyword))
                .filter(u -> roleChoice == null || roleChoice.startsWith(I18n.t("pos.category.all")) || roleLabel(u.getRole()).equals(roleChoice))
                .filter(u -> statusChoice == null || statusChoice.startsWith(I18n.t("pos.category.all"))
                        || (u.getStatus() == AccountStatus.ACTIVE) == statusChoice.equals(I18n.t("accounts.status.active")))
                .collect(Collectors.toList());

        accountPaged.setItems(filtered);

        if (totalAccountLabel != null) totalAccountLabel.setText(String.valueOf(allAccounts.size()));
        if (activeAccountLabel != null) {
            activeAccountLabel.setText(String.valueOf(allAccounts.stream().filter(u -> u.getStatus() == AccountStatus.ACTIVE).count()));
        }
        if (lockedAccountLabel != null) {
            lockedAccountLabel.setText(String.valueOf(allAccounts.stream().filter(u -> u.getStatus() == AccountStatus.LOCKED).count()));
        }
        if (adminAccountLabel != null) {
            adminAccountLabel.setText(String.valueOf(allAccounts.stream().filter(u -> u.getRole() == Role.ADMIN).count()));
        }
        if (accountCountLabel != null) accountCountLabel.setText(I18n.t("accounts.count", filtered.size()));
        if (accountPaginationLabel != null) {
            accountPaginationLabel.setText(I18n.t("accounts.pagination", accountPaged.getFromIndex(), accountPaged.getToIndex(), accountPaged.getTotalCount()));
        }
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private Employee employeeOf(User user) {
        return user.getEmployeeId() == null ? null : employeesById.get(user.getEmployeeId());
    }

    private String employeeNameFor(User user) {
        if (user.getEmployeeId() == null) return "-";
        Employee employee = employeesById.get(user.getEmployeeId());
        return employee != null ? employee.getFullName() : "-";
    }

    private String employeePhoneFor(User user) {
        if (user.getEmployeeId() == null) return "-";
        Employee employee = employeesById.get(user.getEmployeeId());
        return employee != null && employee.getPhone() != null ? employee.getPhone() : "-";
    }

    private String roleLabel(Role role) {
        return switch (role) {
            case ADMIN -> I18n.t("role.admin");
            case EMPLOYEE -> I18n.t("role.employee");
            case CUSTOMER -> I18n.t("history.col.customer");
        };
    }

    @FXML
    public void handleAccountSearch() {
        applyAccountFilter();
    }

    @FXML
    public void handleFilterChange() {
        applyAccountFilter();
    }

    @FXML
    public void handleAddAccount() {
        if (!requireAdmin()) return;
        openAccountDialog(null);
    }

    private void openAccountDialog(User existing) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? I18n.t("accounts.dialog.add") : I18n.t("accounts.dialog.edit"));
        AlertUtil.configure(dialog);
        ButtonType saveButtonType = new ButtonType(I18n.t("common.save"), ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        AlertUtil.setDefaultButton(dialog, saveButtonType);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        Employee existingEmployee = existing != null && existing.getEmployeeId() != null
                ? employeeDAO.findById(existing.getEmployeeId()).orElse(null) : null;

        TextField fullNameField = new TextField(existingEmployee != null ? existingEmployee.getFullName() : "");
        TextLengthLimiter.limit(fullNameField, TextLengthLimiter.NAME_MAX);
        TextField phoneField = new TextField(existingEmployee != null ? existingEmployee.getPhone() : "");
        TextLengthLimiter.limit(phoneField, TextLengthLimiter.PHONE_MAX);
        TextField usernameField = new TextField(existing != null ? existing.getUsername() : "");
        TextLengthLimiter.limit(usernameField, TextLengthLimiter.USERNAME_MAX);
        usernameField.setDisable(existing != null); // username is immutable once created
        PasswordField passwordField = new PasswordField();
        TextLengthLimiter.limit(passwordField, TextLengthLimiter.PASSWORD_MAX);
        passwordField.setPromptText(existing == null ? "" : I18n.t("accounts.password.prompt"));
        ComboBox<Role> roleBox = new ComboBox<>();
        roleBox.getItems().setAll(Role.ADMIN, Role.EMPLOYEE);
        roleBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Role role) { return role == null ? "" : roleLabel(role); }
            @Override public Role fromString(String text) { return null; }
        });
        roleBox.setValue(existing != null && existing.getRole() != Role.CUSTOMER ? existing.getRole() : Role.EMPLOYEE);
        if (existing != null && existing.getRole() == Role.CUSTOMER) {
            // TODO 2.84: an old customer-role account keeps its role (shown, not editable); saving the form must not make it staff.
            roleBox.getItems().setAll(Role.CUSTOMER);
            roleBox.setValue(Role.CUSTOMER);
            roleBox.setDisable(true);
        }
        CheckBox activeBox = new CheckBox(I18n.t("accounts.status.active"));
        activeBox.setSelected(existing == null || existing.getStatus() == AccountStatus.ACTIVE);

        // Employee details (TODO 2.10): saved with the staff record and shown in the account's detail panel.
        // An account with no staff record (the first admin, made by the program) has nowhere to keep them.
        boolean hasDetailsRecord = existing == null || existingEmployee != null;
        TextField positionField = new TextField(existingEmployee != null && existingEmployee.getPosition() != null ? existingEmployee.getPosition() : "");
        TextLengthLimiter.limit(positionField, TextLengthLimiter.POSITION_MAX);
        TextField addressField = new TextField(existingEmployee != null && existingEmployee.getAddress() != null ? existingEmployee.getAddress() : "");
        TextLengthLimiter.limit(addressField, TextLengthLimiter.ADDRESS_MAX);
        TextField salaryField = new TextField(existingEmployee != null && existingEmployee.getSalary() != null
                ? existingEmployee.getSalary().toPlainString() : "");
        TextLengthLimiter.limit(salaryField, TextLengthLimiter.NUMBER_MAX);
        salaryField.setPromptText(I18n.t("accounts.salary.prompt"));
        DatePicker hireDatePicker = new DatePicker(existingEmployee != null ? existingEmployee.getHireDate() : null);
        if (existing != null && Session.getCurrentUser() != null && existing.getId() == Session.getCurrentUser().getId()) {
            // Own account (TODO 2.48): the Khóa button refuses it, so the dialog must too.
            roleBox.setDisable(true);
            activeBox.setDisable(true);
        }
        if (!hasDetailsRecord) {
            // No staff record: name and phone have nowhere to be saved either (TODO 2.49).
            fullNameField.setDisable(true);
            phoneField.setDisable(true);
            positionField.setDisable(true);
            addressField.setDisable(true);
            salaryField.setDisable(true);
            hireDatePicker.setDisable(true);
            positionField.setPromptText(I18n.t("accounts.noprofile"));
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label(I18n.t("customers.label.name")), fullNameField);
        grid.addRow(1, new Label(I18n.t("customers.label.phone")), phoneField);
        grid.addRow(2, new Label(I18n.t("accounts.label.username")), usernameField);
        grid.addRow(3, new Label(I18n.t("accounts.label.password")), passwordField);
        grid.addRow(4, new Label(I18n.t("accounts.label.role")), roleBox);
        grid.addRow(5, new Label(""), activeBox);
        grid.addRow(6, new Label(I18n.t("accounts.label.position")), positionField);
        grid.addRow(7, new Label(I18n.t("accounts.label.address")), addressField);
        grid.addRow(8, new Label(I18n.t("accounts.label.salary")), salaryField);
        grid.addRow(9, new Label(I18n.t("accounts.label.startdate")), hireDatePicker);
        dialog.getDialogPane().setContent(grid);

        // Event filter on the button itself (not setResultConverter) so an invalid/failed save
        // consumes the click and keeps the dialog open with everything the user typed intact -
        // see openCategoryDialog's comment for why.
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            if ((hasDetailsRecord && ValidationUtil.isBlank(fullNameField.getText())) || ValidationUtil.isBlank(usernameField.getText())) {
                AlertUtil.warning(I18n.t("common.missing.title"), I18n.t("accounts.val.required"));
                event.consume();
                return;
            }
            if (!ValidationUtil.isValidUsername(usernameField.getText())) {
                AlertUtil.warning(I18n.t("accounts.val.username.title"),
                        I18n.t("accounts.val.username.message"));
                event.consume();
                return;
            }
            if (hasDetailsRecord && !ValidationUtil.isBlank(phoneField.getText()) && !ValidationUtil.isValidPhone(phoneField.getText())) {
                AlertUtil.warning(I18n.t("customers.val.phone.title"), I18n.t("customers.val.phone.message"));
                event.consume();
                return;
            }
            BigDecimal salary;
            try {
                salary = MoneyParser.parseDong(salaryField.getText());
            } catch (IllegalArgumentException e) {
                AlertUtil.warning(I18n.t("accounts.val.salary.title"), e.getMessage());
                event.consume();
                return;
            }
            if (hireDatePicker.getValue() != null && hireDatePicker.getValue().isAfter(com.coffeeshop.coffeeshopmanagement.util.AppClock.today())) {
                AlertUtil.warning(I18n.t("accounts.val.date.title"), I18n.t("accounts.val.date.message"));
                event.consume();
                return;
            }
            try {
                Employee typed = new Employee();
                typed.setFullName(fullNameField.getText().trim());
                typed.setPhone(ValidationUtil.isBlank(phoneField.getText()) ? null : phoneField.getText().trim());
                typed.setPosition(blankToNull(positionField.getText()));
                typed.setAddress(blankToNull(addressField.getText()));
                typed.setSalary(salary);
                typed.setHireDate(hireDatePicker.getValue());
                AccountService.Form form = new AccountService.Form(usernameField.getText().trim(),
                        passwordField.getText(), roleBox.getValue(), activeBox.isSelected(), typed);
                if (existing == null) {
                    if (!ValidationUtil.isValidPassword(passwordField.getText())
                            || ValidationUtil.containsUsername(passwordField.getText(), usernameField.getText())) {
                        AlertUtil.warning(I18n.t("password.weak.title"), I18n.t("password.problem.weak"));
                        event.consume();
                        return;
                    }
                    accountService.create(form);
                } else {
                    if (!ValidationUtil.isBlank(passwordField.getText())
                            && (!ValidationUtil.isValidPassword(passwordField.getText())
                            || ValidationUtil.containsUsername(passwordField.getText(), usernameField.getText()))) {
                        AlertUtil.warning(I18n.t("password.weak.title"), I18n.t("password.problem.weak"));
                        event.consume();
                        return;
                    }
                    accountService.update(existing, existingEmployee, form);
                }
            } catch (DataAccessException e) {
                AlertUtil.error(I18n.t("accounts.save.fail"), e.getMessage());
                event.consume();
            }
        });

        AlertUtil.confirmBeforeDiscarding(dialog, ButtonType.CANCEL);
        dialog.showAndWait();
        reloadAccounts();
    }

    @FXML
    public void handleEditAccount() {
        if (!requireAdmin()) return;
        if (selectedAccount == null) {
            AlertUtil.info(I18n.t("accounts.none.title"), I18n.t("accounts.none.message"));
            return;
        }
        openAccountDialog(selectedAccount);
    }

    @FXML
    public void handleLockAccount() {
        if (!requireAdmin()) return;
        if (selectedAccount == null) {
            AlertUtil.info(I18n.t("accounts.none.title"), I18n.t("accounts.none.message"));
            return;
        }
        if (selectedAccount.getUsername().equals(Session.getCurrentUser().getUsername())) {
            AlertUtil.warning(I18n.t("accounts.lock.fail.title"), I18n.t("accounts.lock.self"));
            return;
        }
        final User target = selectedAccount;
        final boolean nowLocking = target.getStatus() == AccountStatus.ACTIVE;
        if (nowLocking && target.getRole() == Role.ADMIN) {
            // Counting the active administrators reads the database: off the UI thread (TODO 6.14). The rule itself is
            // enforced again, in one transaction, by AccountService.setLocked.
            Async.run(
                    userDAO::countActiveAdmins,
                    activeAdmins -> {
                        if (activeAdmins <= 1) {
                            AlertUtil.warning(I18n.t("accounts.lock.fail.title"),
                                    I18n.t("accounts.lock.last"));
                            return;
                        }
                        confirmAndSetLocked(target, nowLocking);
                    },
                    error -> AlertUtil.error(I18n.t("common.error.title"), error.getMessage()),
                    accountTable);
            return;
        }
        confirmAndSetLocked(target, nowLocking);
    }

    private void confirmAndSetLocked(User target, boolean nowLocking) {
        boolean confirmed = AlertUtil.confirm(nowLocking ? I18n.t("accounts.lock.title") : I18n.t("accounts.unlock.title"),
                I18n.t("accounts.lock.confirm", (nowLocking ? I18n.t("accounts.lock.verb") : I18n.t("accounts.unlock.verb")), target.getUsername()));
        if (!confirmed) {
            return;
        }
        Async.run(
                () -> {
                    accountService.setLocked(target, nowLocking);
                    return Boolean.TRUE;
                },
                done -> {
                    reloadAccounts();
                    handleCloseDetail();
                },
                error -> AlertUtil.error(I18n.t("accounts.status.fail"), error.getMessage()),
                accountTable);
    }

    @FXML
    public void handleSelectAccount() {
        User selected = accountTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        showAccountDetail(selected);
    }

    private void showAccountDetail(User user) {
        selectedAccount = user;
        if (detailUsername != null) detailUsername.setText(user.getUsername());
        if (detailRole != null) detailRole.setText(roleLabel(user.getRole()));
        if (detailUsernameLabel != null) detailUsernameLabel.setText(user.getUsername());
        if (detailFullNameLabel != null) detailFullNameLabel.setText(employeeNameFor(user));
        if (detailPhoneLabel != null) detailPhoneLabel.setText(employeePhoneFor(user));
        if (detailRoleLabel != null) detailRoleLabel.setText(roleLabel(user.getRole()));
        if (detailStatusLabel != null) {
            detailStatusLabel.setText(user.getStatus() == AccountStatus.ACTIVE ? I18n.t("accounts.status.active") : I18n.t("accounts.status.locked"));
        }
        if (detailCreatedDateLabel != null) {
            detailCreatedDateLabel.setText(user.getCreatedAt() != null ? user.getCreatedAt().format(DATE_FORMAT) : "-");
        }
        Employee staff = employeeOf(user);
        if (detailPositionLabel != null) detailPositionLabel.setText(staff != null && staff.getPosition() != null ? staff.getPosition() : "-");
        if (detailHireDateLabel != null) {
            detailHireDateLabel.setText(staff != null && staff.getHireDate() != null ? staff.getHireDate().format(DATE_FORMAT) : "-");
        }
        // The account screen is admin-only (requireAdmin), so showing the salary here is not a leak.
        if (detailSalaryLabel != null) {
            detailSalaryLabel.setText(staff != null && staff.getSalary() != null ? CurrencyUtil.format(staff.getSalary()) : "-");
        }
        if (detailAddressLabel != null) detailAddressLabel.setText(staff != null && staff.getAddress() != null ? staff.getAddress() : "-");
        if (lockAccountButton != null) {
            lockAccountButton.setText(user.getStatus() == AccountStatus.ACTIVE ? I18n.t("accounts.button.lock") : I18n.t("accounts.button.unlock"));
        }
        if (accountDetailCard != null) {
            accountDetailCard.setVisible(true);
        }
    }

    @FXML
    public void handleCloseDetail() {
        selectedAccount = null;
        if (accountDetailCard != null) {
            accountDetailCard.setVisible(false);
        }
        if (accountTable != null) {
            accountTable.getSelectionModel().clearSelection();
        }
    }
}
