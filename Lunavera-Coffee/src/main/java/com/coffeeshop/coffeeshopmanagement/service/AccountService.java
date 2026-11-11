package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.AuditDAO;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
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
import java.util.List;
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
                AuditDAO.write(connection, AuditAction.ACCOUNT_CREATED, AuditTarget.ACCOUNT, user.getId(), user.getUsername(), null,
                        user.getRole().name(), user.getStatus().name()); // TODO 4.2
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
                String[] before = readRoleAndStatus(connection, existing.getId()); // what the database holds now, for the audit (TODO 4.2)
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
                            "UPDATE users SET password_hash = ?, must_change_password = ?, pin_hash = NULL, pin_failures = 0 WHERE id = ?")) {
                        st.setString(1, newHash);
                        st.setInt(2, own ? 0 : 1);
                        st.setInt(3, existing.getId());
                        st.executeUpdate();
                    }
                }
                changeRoleAndStatus(connection, existing.getId(), form.role(),
                        form.active() ? AccountStatus.ACTIVE : AccountStatus.LOCKED);
                auditAccountChange(connection, existing, before, form.role(), form.active() ? AccountStatus.ACTIVE : AccountStatus.LOCKED, newHash != null);
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
                if (!lock && target.getEmployeeId() != null && isAnonymised(connection, target.getEmployeeId())) {
                    throw new DataAccessException(I18n.t("staff.anon.nounlock")); // TODO 4.29: their details are gone for good
                }
                String[] before = readRoleAndStatus(connection, target.getId());
                changeRoleAndStatus(connection, target.getId(), target.getRole(),
                        lock ? AccountStatus.LOCKED : AccountStatus.ACTIVE);
                auditAccountChange(connection, target, before, target.getRole(), lock ? AccountStatus.LOCKED : AccountStatus.ACTIVE, false);
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

    /** The role and the status stored for this account right now ({"ADMIN", "ACTIVE"}), or {null, null} when it is gone. */
    private static String[] readRoleAndStatus(Connection connection, int userId) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement("SELECT role, status FROM users WHERE id = ?")) {
            st.setInt(1, userId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() ? new String[]{rs.getString(1), rs.getString(2)} : new String[]{null, null};
            }
        }
    }

    /**
     * Writes what an account edit really changed to the audit log (TODO 4.2), on the edit's transaction: a new role, a lock or an
     * unlock, a password the administrator set. A save that changes none of these writes nothing, and no password or hash is ever
     * written, only the fact that it was reset.
     */
    private static void auditAccountChange(Connection connection, User account, String[] before, Role newRole, AccountStatus newStatus,
                                           boolean passwordReset) throws SQLException {
        Integer id = account.getId();
        String name = account.getUsername();
        if (before[0] != null && !before[0].equals(newRole.name())) {
            AuditDAO.write(connection, AuditAction.ACCOUNT_ROLE_CHANGED, AuditTarget.ACCOUNT, id, name, before[0], newRole.name(), null);
        }
        if (before[1] != null && !before[1].equals(newStatus.name())) {
            AuditDAO.write(connection, newStatus == AccountStatus.LOCKED ? AuditAction.ACCOUNT_LOCKED : AuditAction.ACCOUNT_UNLOCKED,
                    AuditTarget.ACCOUNT, id, name, before[1], newStatus.name(), null);
        }
        if (passwordReset) {
            AuditDAO.write(connection, AuditAction.PASSWORD_RESET, AuditTarget.ACCOUNT, id, name, null, null, null);
        }
    }

    /**
     * Deletes a person who never took part in a sale (TODO 4.29): their login (when they have one) and their staff record, in one
     * transaction. Refused for your own account, for the last active administrator (the same one-statement guard as locking), and for
     * anyone who appears in an order, the stock log or a closed shift, who can only be made anonymous ({@link #anonymiseStaff}).
     */
    public void removeStaff(com.coffeeshop.coffeeshopmanagement.model.StaffRow row) {
        Permissions.requireAdmin("remove a staff member");
        if (row == null) {
            throw new DataAccessException(I18n.t("staff.gone"));
        }
        User me = Session.getCurrentUser();
        if (row.hasAccount() && me != null && me.getId() == row.userId()) {
            throw new DataAccessException(I18n.t("staff.remove.self"));
        }
        int employeeId = row.employee().getId();
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (row.hasAccount()) {
                    String sql = "DELETE FROM users WHERE id = ? AND (NOT (role = " + SqlStatus.ROLE_ADMIN + " AND status = " + SqlStatus.ACTIVE
                            + ") OR (SELECT COUNT(*) FROM users WHERE role = " + SqlStatus.ROLE_ADMIN + " AND status = " + SqlStatus.ACTIVE
                            + " AND id <> ?) >= 1)";
                    try (PreparedStatement st = connection.prepareStatement(sql)) {
                        st.setInt(1, row.userId());
                        st.setInt(2, row.userId());
                        if (st.executeUpdate() == 0) {
                            throw new DataAccessException(lastAdminMessage()); // or the account is already gone: the list is reloaded either way
                        }
                    }
                }
                if (EmployeeDAO.hasHistory(connection, employeeId)) {
                    throw new DataAccessException(I18n.t("staff.remove.history"));
                }
                try (PreparedStatement st = connection.prepareStatement("DELETE FROM employees WHERE id = ?")) {
                    st.setInt(1, employeeId);
                    st.executeUpdate();
                }
                AuditDAO.write(connection, AuditAction.STAFF_REMOVED, AuditTarget.STAFF, employeeId, row.employee().getFullName(), null, null,
                        row.hasAccount() ? row.username() : null); // TODO 4.2: the login name, when there was a login
                connection.commit();
                SecurityLog.event(SecurityLog.actor() + " removed staff record " + employeeId
                        + (row.hasAccount() ? " and its account (id " + row.userId() + ")" : ""));
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            // an order saved at another till between the check and the delete is stopped by the orders' foreign key
            throw new DataAccessException(I18n.t("staff.remove.history"), e);
        }
    }

    /**
     * Removes the personal details of a person who has left and appears in the shop's records (TODO 4.29): the name becomes
     * "Former staff #12", the phone, email, address and salary are cleared, the login name is replaced and the password made
     * unusable, and the names copied into the stock log, the closed shifts, refunds, expenses, the time clock, gift cards and bookings are rewritten too (TODO 2.87), so nothing of them is left. The
     * orders keep pointing at the same record, so the sales stay in the reports. Only for someone who is no longer working, and
     * only once: it cannot be undone.
     */
    public void anonymiseStaff(com.coffeeshop.coffeeshopmanagement.model.StaffRow row) {
        Permissions.requireAdmin("make a former staff member anonymous");
        if (row == null) {
            throw new DataAccessException(I18n.t("staff.gone"));
        }
        if (row.employee().isAnonymised()) {
            throw new DataAccessException(I18n.t("staff.anon.already"));
        }
        if (row.isWorking()) {
            throw new DataAccessException(I18n.t("staff.anon.working"));
        }
        int employeeId = row.employee().getId();
        String newName = I18n.t("staff.former.name", employeeId);
        final String[] loginNameAfter = new String[1]; // the login name the account gets, for the audit rewrite below
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<String> oldNames = namesToMatch(connection, row); // read before the record is renamed
                try (PreparedStatement st = connection.prepareStatement(
                        "UPDATE employees SET full_name = ?, phone = NULL, email = NULL, address = NULL, salary = NULL, active = ?, "
                                + "anonymised_at = ? WHERE id = ? AND anonymised_at IS NULL")) {
                    st.setString(1, newName);
                    st.setBoolean(2, false);
                    st.setString(3, com.coffeeshop.coffeeshopmanagement.util.DbTime.write(AppClock.now()));
                    st.setInt(4, employeeId);
                    if (st.executeUpdate() == 0) {
                        throw new DataAccessException(I18n.t("staff.anon.already"));
                    }
                }
                if (row.hasAccount()) {
                    String loginName = "former-" + employeeId;
                    if (usernameTaken(connection, loginName)) {
                        loginName = loginName + "-" + row.userId();
                    }
                    loginNameAfter[0] = loginName;
                    // only a locked account: a person who can still sign in is not a former member of staff
                    try (PreparedStatement st = connection.prepareStatement(
                            "UPDATE users SET username = ?, password_hash = ?, must_change_password = 0, pin_hash = NULL, pin_failures = 0 WHERE id = ? AND status = " + SqlStatus.LOCKED)) {
                        st.setString(1, loginName);
                        st.setString(2, PasswordUtil.hash(java.util.UUID.randomUUID().toString()));
                        st.setInt(3, row.userId());
                        if (st.executeUpdate() == 0) {
                            throw new DataAccessException(I18n.t("staff.anon.working"));
                        }
                    }
                }
                try (PreparedStatement st = connection.prepareStatement("UPDATE stock_log SET employee_name = ? WHERE employee_id = ?")) {
                    st.setString(1, newName);
                    st.setInt(2, employeeId);
                    st.executeUpdate();
                }
                try (PreparedStatement st = connection.prepareStatement("UPDATE shift_closes SET closed_by_name = ? WHERE closed_by = ?")) {
                    st.setString(1, newName);
                    st.setInt(2, employeeId);
                    st.executeUpdate();
                }
                scrubStaffNames(connection, employeeId, row.hasAccount() ? row.userId() : null, oldNames, newName);
                // The audit log keeps no name either (TODO 4.2): this line is about the anonymous name, and every older line that
                // names the person, their login or what they did under their own name is rewritten in the same transaction.
                AuditDAO.write(connection, AuditAction.STAFF_ANONYMISED, AuditTarget.STAFF, employeeId, newName, null, null, null);
                AuditDAO.scrubActor(connection, employeeId, newName);
                AuditDAO.scrubTarget(connection, AuditTarget.STAFF, employeeId, newName, true);
                if (row.hasAccount()) {
                    AuditDAO.scrubTarget(connection, AuditTarget.ACCOUNT, row.userId(), loginNameAfter[0], false);
                }
                connection.commit();
                SecurityLog.event(SecurityLog.actor() + " made staff record " + employeeId + " anonymous");
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw translate(I18n.t("staff.anon.fail"), e);
        }
    }

    /**
     * The name copied into every other table that records who did something (TODO 2.87), in the same transaction: refunds,
     * expenses, the time clock, gift cards and bookings. The first three have an id next to the name (the staff record for refunds
     * and expenses, the account for the time clock); gift cards and bookings got one with this change, and a row from before that has
     * only the text, so it is matched on the old name, but only a name nobody else carries ({@link #namesToMatch}).
     */
    private static void scrubStaffNames(Connection connection, int employeeId, Integer userId, List<String> oldNames, String newName)
            throws SQLException {
        updateText(connection, "UPDATE refunds SET refunded_by_name = ? WHERE refunded_by = ?", newName, employeeId);
        updateText(connection, "UPDATE expenses SET recorded_by_name = ? WHERE recorded_by = ?", newName, employeeId);
        if (userId != null) {
            updateText(connection, "UPDATE time_clock SET user_name = ? WHERE user_id = ?", newName, userId);
        }
        updateText(connection, "UPDATE gift_cards SET sold_by = ? WHERE sold_by_id = ?", newName, employeeId);
        updateText(connection, "UPDATE reservations SET created_by = ? WHERE created_by_id = ?", newName, employeeId);
        for (String old : oldNames) {
            updateText(connection, "UPDATE gift_cards SET sold_by = ? WHERE sold_by_id IS NULL AND sold_by = ?", newName, old);
            updateText(connection, "UPDATE reservations SET created_by = ? WHERE created_by_id IS NULL AND created_by = ?", newName, old);
        }
    }

    private static void updateText(Connection connection, String sql, String text, Object key) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement(sql)) {
            st.setString(1, text);
            if (key instanceof Integer id) st.setInt(2, id);
            else st.setString(2, (String) key);
            st.executeUpdate();
        }
    }

    /**
     * The texts this person's name was copied under into rows that have no id: the staff record's full name and the login name (what
     * the display name falls back to). A text that any other staff record or login also carries is left out, so that making one person
     * anonymous never rewrites the rows of someone who only has the same name.
     */
    private static List<String> namesToMatch(Connection connection, com.coffeeshop.coffeeshopmanagement.model.StaffRow row) throws SQLException {
        List<String> names = new java.util.ArrayList<>();
        int ownUser = row.userId() == null ? -1 : row.userId();
        for (String text : new String[]{row.employee().getFullName(), row.username()}) {
            if (text == null || text.isBlank() || names.contains(text)) continue;
            boolean shared = false;
            try (PreparedStatement st = connection.prepareStatement(
                    "SELECT 1 FROM employees WHERE full_name = ? AND id <> ? UNION ALL SELECT 1 FROM users WHERE username = ? AND id <> ?")) {
                st.setString(1, text);
                st.setInt(2, row.employee().getId());
                st.setString(3, text);
                st.setInt(4, ownUser);
                try (ResultSet rs = st.executeQuery()) {
                    shared = rs.next();
                }
            }
            if (!shared) names.add(text);
        }
        return names;
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

    private static boolean isAnonymised(Connection connection, int employeeId) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement("SELECT 1 FROM employees WHERE id = ? AND anonymised_at IS NOT NULL")) {
            st.setInt(1, employeeId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next();
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

    public java.util.List<User> findAllUsers() {
        return new com.coffeeshop.coffeeshopmanagement.dao.UserDAO().findAll();
    }

    public java.util.List<Employee> findAllEmployees() {
        return employeeDAO.findAll();
    }

    public java.util.Optional<Employee> findEmployee(int id) {
        return employeeDAO.findById(id);
    }

    public int countActiveAdmins() {
        return new com.coffeeshop.coffeeshopmanagement.dao.UserDAO().countActiveAdmins();
    }
}
