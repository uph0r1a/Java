package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Employee;

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
                ? new DataAccessException("Số điện thoại này đã thuộc về một nhân viên khác.", e)
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
        employee.setHireDate(hireDate != null ? LocalDate.parse(hireDate) : null);
        employee.setActive(rs.getBoolean("active"));
        return employee;
    }
}
