package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.SqlStatus;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.model.AccountStatus;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;

public class UserDAO {

    /**
     * Finds the account a login name belongs to. The username is matched first (any capitals); only when no
     * username matches is the text tried as an employee or customer phone, in its normalised form (so +84901234567
     * and 0901234567 are the same). The order is fixed, so a phone shared by two people always opens the same
     * (oldest) account instead of an arbitrary one (TODO 2.17).
     */
    public Optional<User> findByUsername(String username) {
        if (username == null) return Optional.empty();
        String name = username.trim();
        String phone = com.coffeeshop.coffeeshopmanagement.util.ValidationUtil.normalizePhone(name);
        String sql = "SELECT u.* FROM users u " +
                "LEFT JOIN employees e ON u.employee_id = e.id " +
                "WHERE lower(u.username) = lower(?) OR e.phone = ? " +
                "ORDER BY (lower(u.username) = lower(?)) DESC, u.id LIMIT 1";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, phone);
            statement.setString(3, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up user by username", e);
        }
        return Optional.empty();
    }

public Optional<User> findById(int id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up user by id", e);
        }
        return Optional.empty();
    }

    public boolean existsByUsername(String username) {
        String sql = "SELECT 1 FROM users WHERE lower(username) = lower(?)";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check username uniqueness", e);
        }
    }

    public User insert(User user) {
        String sql = "INSERT INTO users (username, password_hash, role, status, employee_id, customer_id, created_at, must_change_password) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getPasswordHash());
            statement.setString(3, user.getRole().name());
            statement.setString(4, user.getStatus().name());
            setNullableInt(statement, 5, user.getEmployeeId());
            setNullableInt(statement, 6, user.getCustomerId());
            statement.setString(7, DbTime.write(AppClock.now()));
            statement.setInt(8, user.isMustChangePassword() ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getInt(1));
                }
            }
            return user;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create user account", e);
        }
    }

    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY id";
        List<User> users = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                users.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load user accounts", e);
        }
        return users;
    }

    public void updateStatus(int userId, AccountStatus status) {
        Permissions.requireAdmin("change an account status");
        String sql = "UPDATE users SET status = ? WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setInt(2, userId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update account status", e);
        }
    }

    /**
     * Stores the same password with a newer iteration count (TODO 2.72). Unlike {@link #updatePasswordHash} it leaves
     * {@code must_change_password} alone: an account whose password an administrator just reset must still be asked to choose
     * its own, even when the silent upgrade at login runs first.
     */
    public void rehashPassword(int userId, String passwordHash) {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setInt(2, userId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update password", e);
        }
    }

    public void updatePasswordHash(int userId, String passwordHash) {
        // The person chose (or re-hashed) their own password: any "must change" mark is over (TODO 2A.18).
        String sql = "UPDATE users SET password_hash = ?, must_change_password = 0 WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setInt(2, userId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update password", e);
        }
    }

    public void updateRole(int userId, Role role) {
        Permissions.requireAdmin("change an account role");
        String sql = "UPDATE users SET role = ? WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, role.name());
            statement.setInt(2, userId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update account role", e);
        }
    }

    /** Active ADMIN accounts - used to stop the last one being demoted or locked out. */
    public int countActiveAdmins() {
        String sql = "SELECT COUNT(*) FROM users WHERE role = " + SqlStatus.ROLE_ADMIN + " AND status = " + SqlStatus.ACTIVE;
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count admins", e);
        }
    }

    public int countByStatus(AccountStatus status) {
        String sql = "SELECT COUNT(*) FROM users WHERE status = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count users by status", e);
        }
    }

    private void setNullableInt(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private User map(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(Role.fromDb(rs.getString("role")));
        user.setStatus(AccountStatus.fromDb(rs.getString("status")));
        int employeeId = rs.getInt("employee_id");
        user.setEmployeeId(rs.wasNull() ? null : employeeId);
        int customerId = rs.getInt("customer_id");
        user.setCustomerId(rs.wasNull() ? null : customerId);
        String createdAt = rs.getString("created_at");
        user.setCreatedAt(DbTime.parse(createdAt));
        user.setMustChangePassword(rs.getInt("must_change_password") != 0);
        return user;
    }

    // ============================================================ The PIN of a cashier (TODO 4.36)

    /** A cashier who has a PIN, for the list on the lock screen. {@code name} is the person's name, else the user name. */
    public record PinHolder(int userId, String name) {
    }

    /** The stored PIN hash, or null when the account has none (or does not exist). */
    public String pinHash(int userId) {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT pin_hash FROM users WHERE id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the PIN", e);
        }
    }

    /** Stores a new PIN hash and forgets earlier wrong PINs. */
    public void setPinHash(int userId, String pinHash) {
        update("UPDATE users SET pin_hash = ?, pin_failures = 0 WHERE id = ?", pinHash, userId);
    }

    /** Removes the PIN (the person sets a new one after signing in with the password). */
    public void clearPin(int userId) {
        update("UPDATE users SET pin_hash = NULL, pin_failures = 0 WHERE id = ?", null, userId);
    }

    /** A right PIN: the count of wrong ones in a row starts again. */
    public void resetPinFailures(int userId) {
        update("UPDATE users SET pin_failures = 0 WHERE id = ? AND pin_failures <> 0", null, userId);
    }

    /**
     * Counts one more wrong PIN and returns how many there are in a row now. One statement does the adding, so two tills guessing
     * at the same moment cannot both read the same old number.
     */
    public int addPinFailure(int userId) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users SET pin_failures = pin_failures + 1 WHERE id = ? AND pin_hash IS NOT NULL")) {
                    statement.setInt(1, userId);
                    statement.executeUpdate();
                }
                int failures = 0;
                try (PreparedStatement statement = connection.prepareStatement("SELECT pin_failures FROM users WHERE id = ?")) {
                    statement.setInt(1, userId);
                    try (ResultSet rs = statement.executeQuery()) {
                        if (rs.next()) failures = rs.getInt(1);
                    }
                }
                connection.commit();
                return failures;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count the wrong PIN", e);
        }
    }

    /** Active staff accounts (not administrators) that have a PIN, by name. */
    public List<PinHolder> pinHolders() {
        String sql = "SELECT u.id, COALESCE(e.full_name, u.username) AS shown FROM users u LEFT JOIN employees e ON e.id = u.employee_id "
                + "WHERE u.pin_hash IS NOT NULL AND u.role = '" + Role.EMPLOYEE.name() + "' AND u.status = " + SqlStatus.ACTIVE
                + " ORDER BY shown, u.id";
        List<PinHolder> holders = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) holders.add(new PinHolder(rs.getInt(1), rs.getString(2)));
        } catch (SQLException e) {
            throw new DataAccessException("Failed to list the PINs", e);
        }
        return holders;
    }

    private void update(String sql, String text, int userId) {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (sql.contains("pin_hash = ?")) statement.setString(index++, text);
            statement.setInt(index, userId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update the PIN", e);
        }
    }
}
