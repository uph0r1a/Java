package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * Creates, edits and locks staff accounts. Every operation is ONE database transaction, so a failure half-way (a
 * duplicate username, a locked file) leaves nothing behind: no employee without an account, no new role with the old
 * status (TODO 2.32). Locking changes the account and the employee together (TODO 2.31), and the "last active admin
 * must remain" rule is enforced inside the same statement that changes the account, so two admins acting at once
 * cannot both succeed (TODO 2A.2).
 */
public class AccountService {

    public static String lastAdminMessage() {
        return I18n.t("account.lastadmin");
    }

    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    /** The values typed in the account dialog. {@code employee} carries name, phone, position, address, salary, hire date. */
    public record Form(String username, String password, Role role, boolean active, Employee employee) {}

    /** Creates the employee and the account together. */
    public User create(Form form) {
        Permissions.requireAdmin("create an account");
        return createAccount(form, false);
    }

    /**
     * The very first administrator, made by the owner on the first start (TODO 2A.5). Nobody is signed in then, so no
     * permission check; instead the transaction refuses when any account already exists.
     */
    public User createFirstAdmin(Form form) {
        if (form.role() != Role.ADMIN) throw new IllegalArgumentException("The first account must be an administrator");
        return createAccount(form, true);
    }

    /**
     * The rules for a user name, a password and a phone, kept here and not only in the dialogs (TODO 2A.35), so a second
     * caller cannot skip them. A blank password is allowed only when {@code passwordOptional} (editing an account without
     * changing its password). The messages are the ones the person sees.
     */
    static void checkRules(Form form, boolean passwordOptional) {
        if (form == null || form.username() == null || !ValidationUtil.isValidUsername(form.username())) {
            throw new DataAccessException(I18n.t("account.rule.username"));
        }
        String password = form.password();
        boolean blank = password == null || password.isBlank();
        if (blank ? !passwordOptional
                : (!ValidationUtil.isValidPassword(password) || ValidationUtil.containsUsername(password, form.username()))) {
            throw new DataAccessException(I18n.t("account.rule.password", ValidationUtil.MAX_PASSWORD_LENGTH));
        }
        Employee employee = form.employee();
        if (employee != null && !ValidationUtil.isBlank(employee.getPhone()) && !ValidationUtil.isValidPhone(employee.getPhone())) {
            throw new DataAccessException(I18n.t("account.rule.phone"));
        }
    }

    private User createAccount(Form form, boolean onlyWhenNoAccountExists) {
        checkRules(form, false);
        String username = form.username().trim();
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (onlyWhenNoAccountExists && anyAccountExists(connection)) {
                    throw new DataAccessException(I18n.t("account.setup.done"));
                }
                if (usernameTaken(connection, username)) {
                    throw new DataAccessException(I18n.t("account.username.taken"));
                }
                if (phoneTaken(connection, form.employee().getPhone(), 0)) {
                    throw new DataAccessException(I18n.t("employee.phone.taken"));
                }
                Employee employee = form.employee();
                employee.setActive(form.active());
                employeeDAO.insert(connection, employee);
                User user = new User();
                user.setUsername(username);
                user.setPasswordHash(PasswordUtil.hash(form.password()));
                user.setRole(form.role());
                user.setStatus(form.active() ? AccountStatus.ACTIVE : AccountStatus.LOCKED);
                user.setEmployeeId(employee.getId());
                // The admin knows this password, so the person must replace it at the first login (TODO 2A.18); the owner
                // making their own first account is the exception.
                user.setMustChangePassword(!onlyWhenNoAccountExists);
                insertUser(connection, user);
                connection.commit();
                SecurityLog.event(SecurityLog.actor() + " created account '" + user.getUsername() + "' (id " + user.getId()
                        + ", " + user.getRole() + ", " + user.getStatus() + ")");
                return user;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw translate(I18n.t("account.create.fail"), e);
        }
    }

    /**
     * Saves an edit: employee details (when the account has an employee), a new password when {@code form.password()}
     * is not blank, then role and status.
     */
    public void update(User existing, Employee existingEmployee, Form form) {
        Permissions.requireAdmin("change an account");
        // TODO 2.84: an old customer-role account may be edited (fixed, locked) but never turned into a staff account by saving
        // a form: the dialog used to show "Nhân viên" for it and Save then gave a customer login a cashier's rights.
        if (existing.getRole() == Role.CUSTOMER && form.role() != Role.CUSTOMER) {
            throw new DataAccessException(I18n.t("account.customerrole"));
        }
        checkRules(form, true);
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (existingEmployee != null) {
                    if (phoneTaken(connection, form.employee().getPhone(), existingEmployee.getId())) {
                        throw new DataAccessException(I18n.t("employee.phone.taken"));
                    }
                    Employee e = existingEmployee;
                    Employee in = form.employee();
                    e.setFullName(in.getFullName());
                    e.setPhone(in.getPhone());
                    e.setPosition(in.getPosition());
                    e.setAddress(in.getAddress());
                    e.setSalary(in.getSalary());
                    e.setHireDate(in.getHireDate());
                    e.setActive(form.active());
                    employeeDAO.update(connection, e);
                }
                String newHash = null;
                if (form.password() != null && !form.password().isBlank()) {
                    newHash = PasswordUtil.hash(form.password());
                    // A password an admin typed is known to the admin: the person must replace it at the next login (TODO 2A.18),
                    // unless the admin changed their own.
                    boolean own = Session.getCurrentUser() != null && Session.getCurrentUser().getId() == existing.getId();
                    try (PreparedStatement st = connection.prepareStatement(
                            "UPDATE users SET password_hash = ?, must_change_password = ? WHERE id = ?")) {
                        st.setString(1, newHash);
                        st.setInt(2, own ? 0 : 1);
                        st.setInt(3, existing.getId());
                        st.executeUpdate();
                    }
                }
                changeRoleAndStatus(connection, existing.getId(), form.role(),
                        form.active() ? AccountStatus.ACTIVE : AccountStatus.LOCKED);
                connection.commit();
                if (newHash != null) Session.passwordChangedBySelf(existing.getId(), newHash); // no-op for someone else's account
                boolean newPassword = form.password() != null && !form.password().isBlank();
                SecurityLog.event(SecurityLog.actor() + " changed account '" + existing.getUsername() + "' (id " + existing.getId()
                        + "): role " + existing.getRole() + " -> " + form.role() + ", status "
                        + (form.active() ? "ACTIVE" : "LOCKED") + (newPassword ? ", password reset" : ""));
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw translate(I18n.t("accounts.save.fail"), e);
        }
    }

    /** Locks or unlocks: the account status and the employee's {@code active} flag change together. */
    public void setLocked(User target, boolean lock) {
        Permissions.requireAdmin("lock or unlock an account");
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                changeRoleAndStatus(connection, target.getId(), target.getRole(),
                        lock ? AccountStatus.LOCKED : AccountStatus.ACTIVE);
                if (target.getEmployeeId() != null) {
                    try (PreparedStatement st = connection.prepareStatement("UPDATE employees SET active = ? WHERE id = ?")) {
                        st.setBoolean(1, !lock);
                        st.setInt(2, target.getEmployeeId());
                        st.executeUpdate();
                    }
                }
                connection.commit();
                SecurityLog.event(SecurityLog.actor() + (lock ? " locked" : " unlocked") + " account '" + target.getUsername()
                        + "' (id " + target.getId() + ")");
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw translate(I18n.t("account.status.change.fail"), e);
        }
    }

    /**
     * One UPDATE that refuses to run when it would remove the last active admin: the guard is evaluated by the
     * database in the same statement, not by a count read earlier.
     */
    private static void changeRoleAndStatus(Connection connection, int userId, Role role, AccountStatus status)
            throws SQLException {
        String sql = "UPDATE users SET role = ?, status = ? WHERE id = ? AND (" +
                "? = 1 OR NOT (role = " + SqlStatus.ROLE_ADMIN + " AND status = " + SqlStatus.ACTIVE + ") OR " +
                "(SELECT COUNT(*) FROM users WHERE role = " + SqlStatus.ROLE_ADMIN + " AND status = " + SqlStatus.ACTIVE + " AND id <> ?) >= 1)";
        boolean stillActiveAdmin = role == Role.ADMIN && status == AccountStatus.ACTIVE;
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, role.name());
            st.setString(2, status.name());
            st.setInt(3, userId);
            st.setInt(4, stillActiveAdmin ? 1 : 0);
            st.setInt(5, userId);
            if (st.executeUpdate() == 0) {
                throw new DataAccessException(lastAdminMessage());
            }
        }
    }

    private static boolean anyAccountExists(Connection connection) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement("SELECT 1 FROM users LIMIT 1");
             ResultSet rs = st.executeQuery()) {
            return rs.next();
        }
    }

    private static boolean usernameTaken(Connection connection, String username) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement("SELECT 1 FROM users WHERE lower(username) = lower(?)")) {
            st.setString(1, username);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean phoneTaken(Connection connection, String phone, int excludeEmployeeId) throws SQLException {
        String normalized = ValidationUtil.normalizePhone(phone);
        if (normalized == null || normalized.isBlank()) return false;
        try (PreparedStatement st = connection.prepareStatement("SELECT 1 FROM employees WHERE phone = ? AND id <> ?")) {
            st.setString(1, normalized);
            st.setInt(2, excludeEmployeeId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void insertUser(Connection connection, User user) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, role, status, employee_id, customer_id, created_at, must_change_password) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement st = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            st.setString(1, user.getUsername());
            st.setString(2, user.getPasswordHash());
            st.setString(3, user.getRole().name());
            st.setString(4, user.getStatus().name());
            if (user.getEmployeeId() == null) st.setNull(5, java.sql.Types.INTEGER); else st.setInt(5, user.getEmployeeId());
            if (user.getCustomerId() == null) st.setNull(6, java.sql.Types.INTEGER); else st.setInt(6, user.getCustomerId());
            st.setString(7, com.coffeeshop.coffeeshopmanagement.util.DbTime.write(AppClock.now()));
            st.setInt(8, user.isMustChangePassword() ? 1 : 0);
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                if (keys.next()) user.setId(keys.getInt(1));
            }
        }
    }

    /**
     * A duplicate is recognised by the error code ({@link com.coffeeshop.coffeeshopmanagement.dao.UniqueViolation}), which
     * also covers MariaDB, whose text the old check never matched. Two rules can fail here (login name, staff phone), so the
     * driver's text is read only to pick the wording; an unrecognised duplicate gets a general message, not a raw SQL one.
     */
    private static DataAccessException translate(String what, SQLException e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (com.coffeeshop.coffeeshopmanagement.dao.UniqueViolation.isUnique(e)) {
            if (m.contains("username")) return new DataAccessException(I18n.t("account.username.taken"), e);
            if (m.contains("phone")) return new DataAccessException(I18n.t("employee.phone.taken"), e);
            return new DataAccessException(I18n.t("account.exists"), e);
        }
        return new DataAccessException(what + ": " + m, e);
    }
}
