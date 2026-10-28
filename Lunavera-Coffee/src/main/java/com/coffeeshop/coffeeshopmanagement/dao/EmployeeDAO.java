package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Employee;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.StaffRow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EmployeeDAO {

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(EmployeeDAO.class.getName());

    /**
     * A damaged date must not stop the whole account list or that person's login (TODO 3.64): the first ten characters are tried
     * ("2026-10-18 00:00:00" from an import still reads), and anything else gives no date and a warning (the id, never the text).
     */
    static LocalDate readHireDate(int employeeId, String text) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();
        try {
            return LocalDate.parse(trimmed.length() > 10 ? trimmed.substring(0, 10) : trimmed);
        } catch (java.time.format.DateTimeParseException e) {
            LOGGER.warning("Staff record " + employeeId + " has a hire date that cannot be read; it is shown as empty");
            return null;
        }
    }

    public Employee insert(Employee employee) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            return insert(connection, employee);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create employee", e);
        }
    }

    /** Same as {@link #insert(Employee)} on a connection the caller owns (for one transaction with the account row). */
    public Employee insert(Connection connection, Employee employee) {
        employee.setPhone(blankToNull(com.coffeeshop.coffeeshopmanagement.util.ValidationUtil.normalizePhone(employee.getPhone())));
        String sql = "INSERT INTO employees (full_name, phone, email, address, position, salary, hire_date, active) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, employee);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    employee.setId(keys.getInt(1));
                }
            }
            return employee;
        } catch (SQLException e) {
            throw duplicatePhoneOr(e, "Failed to create employee");
        }
    }

    /**
     * The database's unique-phone rule (TODO 2.17) becomes a clear message when two tills save the same phone at once.
     * Recognised by error code, not message text (TODO 6.36); the employees table has no other unique rule.
     */
    private static DataAccessException duplicatePhoneOr(SQLException e, String fallback) {
        return UniqueViolation.isUnique(e)
                ? new DataAccessException(I18n.t("employee.phone.taken"), e)
                : new DataAccessException(fallback, e);
    }

    public void update(Employee employee) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            update(connection, employee);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update employee", e);
        }
    }

    /** Same as {@link #update(Employee)} on a connection the caller owns. */
    public void update(Connection connection, Employee employee) {
        employee.setPhone(blankToNull(com.coffeeshop.coffeeshopmanagement.util.ValidationUtil.normalizePhone(employee.getPhone())));
        String sql = "UPDATE employees SET full_name=?, phone=?, email=?, address=?, position=?, salary=?, " +
                "hire_date=?, active=? WHERE id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, employee);
            statement.setInt(9, employee.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw duplicatePhoneOr(e, "Failed to update employee");
        }
    }

    public Optional<Employee> findById(int id) {
        String sql = "SELECT * FROM employees WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load employee", e);
        }
        return Optional.empty();
    }

    public List<Employee> findAll() {
        String sql = "SELECT * FROM employees ORDER BY id";
        List<Employee> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load employees", e);
        }
        return result;
    }

    /**
     * Every staff record with its login account, if any (TODO 4.12). One line per person: when more than one account points at the
     * same staff record (the program never makes that, but an old import might), the oldest account is shown. Accounts of the
     * customer role are not staff and are left out, so a customer account never appears next to a staff record.
     */
    public List<StaffRow> findAllWithAccounts() {
        String sql = "SELECT e.*, u.id AS user_id, u.username AS user_name, u.role AS user_role, u.status AS user_status " +
                "FROM employees e LEFT JOIN users u ON u.employee_id = e.id AND u.role <> " + com.coffeeshop.coffeeshopmanagement.config.SqlStatus.ROLE_CUSTOMER + " " +
                "ORDER BY e.id, u.id";
        List<StaffRow> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            int lastEmployeeId = -1;
            while (rs.next()) {
                Employee employee = map(rs);
                if (employee.getId() == lastEmployeeId) {
                    continue; // a second account of the same person: the first (oldest) one stays
                }
                lastEmployeeId = employee.getId();
                int userId = rs.getInt("user_id");
                if (rs.wasNull()) {
                    result.add(new StaffRow(employee, null, null, null, null));
                } else {
                    result.add(new StaffRow(employee, userId, rs.getString("user_name"),
                            Role.valueOf(rs.getString("user_role")), AccountStatus.valueOf(rs.getString("user_status"))));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load staff", e);
        }
        return result;
    }

    public void setActive(int id, boolean active) {
        String sql = "UPDATE employees SET active = ? WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, active);
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to change employee status", e);
        }
    }

    /**
     * True when this person appears anywhere in the shop's records (TODO 4.29): an order they opened or took the money for, a line of
     * the stock log, a closed shift. Such a person cannot be deleted without making those records lie (and the orders' foreign
     * key refuses it); they can be made anonymous instead. Asked on the caller's connection so the answer belongs to its transaction.
     */
    public static boolean hasHistory(Connection connection, int employeeId) throws SQLException {
        String sql = "SELECT CASE WHEN EXISTS (SELECT 1 FROM orders WHERE employee_id = ? OR paid_by = ?) "
                + "OR EXISTS (SELECT 1 FROM stock_log WHERE employee_id = ?) "
                + "OR EXISTS (SELECT 1 FROM shift_closes WHERE closed_by = ?) THEN 1 ELSE 0 END";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 1; i <= 4; i++) statement.setInt(i, employeeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    /** Same as {@link #hasHistory(Connection, int)} on its own connection, for the screen to choose its wording. */
    public boolean hasHistory(int employeeId) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            return hasHistory(connection, employeeId);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check the staff history", e);
        }
    }

    public int countActive() {
        String sql = "SELECT COUNT(*) FROM employees WHERE active = 1";
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count employees", e);
        }
    }

    /** True when another employee (not {@code excludeId}) already has this phone, compared in normalised form. */
    public boolean existsByPhone(String phone, int excludeId) {
        String normalized = com.coffeeshop.coffeeshopmanagement.util.ValidationUtil.normalizePhone(phone);
        if (normalized == null || normalized.isBlank()) return false;
        String sql = "SELECT 1 FROM employees WHERE phone = ? AND id <> ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalized);
            statement.setInt(2, excludeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check employee phone", e);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void bind(PreparedStatement statement, Employee employee) throws SQLException {
        statement.setString(1, com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.nfc(employee.getFullName()));
        statement.setString(2, employee.getPhone());
        statement.setString(3, employee.getEmail());
        statement.setString(4, employee.getAddress());
        statement.setString(5, employee.getPosition());
        Long salaryDong = com.coffeeshop.coffeeshopmanagement.util.Money.toDongOrNull(employee.getSalary());
        if (salaryDong != null) {
            statement.setLong(6, salaryDong);
        } else {
            statement.setNull(6, java.sql.Types.INTEGER);
        }
        statement.setString(7, employee.getHireDate() != null ? employee.getHireDate().toString() : null);
        statement.setBoolean(8, employee.isActive());
    }

    private Employee map(ResultSet rs) throws SQLException {
        Employee employee = new Employee();
        employee.setId(rs.getInt("id"));
        employee.setFullName(rs.getString("full_name"));
        employee.setPhone(rs.getString("phone"));
        employee.setEmail(rs.getString("email"));
        employee.setAddress(rs.getString("address"));
        employee.setPosition(rs.getString("position"));
        long salaryDong = rs.getLong("salary");
        employee.setSalary(com.coffeeshop.coffeeshopmanagement.util.Money.fromDongOrNull(salaryDong, rs.wasNull()));
        String hireDate = rs.getString("hire_date");
        employee.setHireDate(readHireDate(employee.getId(), hireDate));
        employee.setActive(rs.getBoolean("active"));
        employee.setAnonymisedAt(com.coffeeshop.coffeeshopmanagement.util.DbTime.parse(rs.getString("anonymised_at")));
        return employee;
    }
}
