package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.util.ValidationUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomerDAO {

    public Customer insert(Customer customer) {
        customer.setPhone(blankToNull(ValidationUtil.normalizePhone(customer.getPhone())));
        String sql = "INSERT INTO customers (full_name, phone, email, loyalty_points, created_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.nfc(customer.getFullName()));
            statement.setString(2, customer.getPhone());
            statement.setString(3, customer.getEmail());
            statement.setInt(4, customer.getLoyaltyPoints());
            statement.setString(5, DbTime.write(AppClock.now()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    customer.setId(keys.getInt(1));
                }
            }
            return customer;
        } catch (SQLException e) {
            throw duplicatePhoneOr(e, "Failed to create customer");
        }
    }

    public void update(Customer customer) {
        customer.setPhone(blankToNull(ValidationUtil.normalizePhone(customer.getPhone())));
        String sql = "UPDATE customers SET full_name = ?, phone = ?, email = ? WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.nfc(customer.getFullName()));
            statement.setString(2, customer.getPhone());
            statement.setString(3, customer.getEmail());
            statement.setInt(4, customer.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw duplicatePhoneOr(e, "Failed to update customer");
        }
    }

    /** Deletes a customer that has no orders, no points and no login account; returns whether a row was removed (TODO 3.27). */
    public boolean deleteIfUnused(int id) {
        String sql = "DELETE FROM customers WHERE id = ? AND loyalty_points = 0 "
                + "AND NOT EXISTS (SELECT 1 FROM orders WHERE customer_id = ?) "
                + "AND NOT EXISTS (SELECT 1 FROM users WHERE customer_id = ?)";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setInt(2, id);
            statement.setInt(3, id);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to remove an unused customer", e);
        }
    }

    /** A customer without a phone is stored as NULL, never as "": MariaDB's unique phone key lets NULL repeat but not "". */
    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }

    /**
     * A UNIQUE violation on the phone index (TODO 2.7) becomes a clear message instead of a raw SQL error. The violation is
     * recognised by its error code ({@link UniqueViolation}), not by the driver's message text (TODO 6.36). The customers
     * table has no other unique rule, so a unique violation here can only be the phone.
     */
    private static DataAccessException duplicatePhoneOr(SQLException e, String fallback) {
        if (UniqueViolation.isUnique(e)) {
            return new DataAccessException(I18n.t("customer.phone.taken"), e);
        }
        return new DataAccessException(fallback, e);
    }

    /** True if another customer (id != excludeId; pass 0 when adding) already has this phone. */
    public boolean existsByPhone(String phone, int excludeId) {
        String sql = "SELECT 1 FROM customers WHERE phone = ? AND id <> ? LIMIT 1";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ValidationUtil.normalizePhone(phone));
            statement.setInt(2, excludeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check phone uniqueness", e);
        }
    }

    public Optional<Customer> findById(int id) {
        String sql = "SELECT * FROM customers WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load customer", e);
        }
        return Optional.empty();
    }

    public Optional<Customer> findByPhone(String phone) {
        String sql = "SELECT * FROM customers WHERE phone = ? LIMIT 1";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ValidationUtil.normalizePhone(phone));
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load customer by phone", e);
        }
        return Optional.empty();
    }

    /**
     * Every paid order that has a customer, for the visits / total spent / last visit figures (TODO 4.39). One query; the sums
     * are made in Java ({@code CustomerStats}). A row whose stored time cannot be read still counts as a visit and as money.
     */
    public List<com.coffeeshop.coffeeshopmanagement.service.CustomerStats.Row> paidOrderRows() {
        String sql = "SELECT customer_id, COALESCE(paid_at, order_date) AS paid_time, total FROM orders WHERE status = "
                + com.coffeeshop.coffeeshopmanagement.config.SqlStatus.PAID + " AND customer_id IS NOT NULL";
        List<com.coffeeshop.coffeeshopmanagement.service.CustomerStats.Row> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                LocalDateTime time;
                try {
                    time = DbTime.parse(rs.getString("paid_time"));
                } catch (RuntimeException e) {
                    time = null;
                }
                result.add(new com.coffeeshop.coffeeshopmanagement.service.CustomerStats.Row(rs.getInt("customer_id"), time,
                        com.coffeeshop.coffeeshopmanagement.util.Money.fromDong(rs.getLong("total"))));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load customer purchases", e);
        }
        return result;
    }

    public List<Customer> findAll() {
        String sql = "SELECT * FROM customers ORDER BY id";
        List<Customer> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load customers", e);
        }
        return result;
    }

    /**
     * At most {@code limit} customers whose name, phone or e-mail contains {@code typed}, by name (TODO 3.52). The name and the e-mail
     * are compared without accents or capitals ({@code "nguyen"} finds {@code "Nguyễn"}), the phone as typed or in its canonical form
     * ({@code "+84 901 111"} finds {@code 0901111222}). A %, _ or ! typed by the person matches itself. A blank text matches everybody,
     * so the first {@code limit} by name come back. This is the query behind the POS "attach a customer" box: the list on screen is never
     * the whole customers table.
     */
    public List<Customer> search(String typed, int limit) {
        String keyword = com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.searchKey(typed);
        StringBuilder sql = new StringBuilder("SELECT * FROM customers");
        List<String> params = new ArrayList<>();
        if (!keyword.isEmpty()) {
            String like = "%" + OrderDAO.escapeLike(keyword) + "%";
            String phoneKey = ValidationUtil.normalizePhone(keyword);
            sql.append(" WHERE ").append(DatabaseConfig.searchKeySql("full_name")).append(" LIKE ? ESCAPE '!'")
                    .append(" OR phone LIKE ? ESCAPE '!'")
                    .append(" OR ").append(DatabaseConfig.searchKeySql("COALESCE(email, '')")).append(" LIKE ? ESCAPE '!'");
            params.add(like);
            params.add("%" + OrderDAO.escapeLike(phoneKey == null ? keyword : phoneKey) + "%");
            params.add(like);
        }
        sql.append(" ORDER BY full_name, id LIMIT ?");
        List<Customer> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (String param : params) {
                statement.setString(index++, param);
            }
            statement.setInt(index, Math.max(1, limit));
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to search customers", e);
        }
        return result;
    }

    /**
     * How many phone numbers are used by more than one customer (TODO 3.41). Always 0 once the unique-phone index exists; it is
     * above 0 only while old duplicates keep the index from being built, so the dashboard can tell the owner to fix them.
     */
    public int countSharedPhones() {
        String sql = "SELECT COUNT(*) FROM (SELECT phone FROM customers WHERE phone IS NOT NULL AND phone <> '' "
                + "GROUP BY phone HAVING COUNT(*) > 1) shared";
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count shared phone numbers", e);
        }
    }

    public int countAll() {
        String sql = "SELECT COUNT(*) FROM customers";
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count customers", e);
        }
    }

    private Customer map(ResultSet rs) throws SQLException {
        Customer customer = new Customer();
        customer.setId(rs.getInt("id"));
        customer.setFullName(rs.getString("full_name"));
        customer.setPhone(rs.getString("phone"));
        customer.setEmail(rs.getString("email"));
        customer.setLoyaltyPoints(rs.getInt("loyalty_points"));
        String createdAt = rs.getString("created_at");
        customer.setCreatedAt(DbTime.parse(createdAt));
        return customer;
    }
}
