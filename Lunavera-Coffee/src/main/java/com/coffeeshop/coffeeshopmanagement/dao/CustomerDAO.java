package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.service.BirthdayRules;
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
        String sql = "INSERT INTO customers (full_name, phone, email, loyalty_points, lifetime_points, created_at, birthday) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.nfc(customer.getFullName()));
            statement.setString(2, customer.getPhone());
            statement.setString(3, ValidationUtil.cleanEmail(customer.getEmail()));
            statement.setInt(4, customer.getLoyaltyPoints());
            statement.setInt(5, Math.max(customer.getLifetimePoints(), customer.getLoyaltyPoints())); // points a customer starts with count as earned
            statement.setString(6, DbTime.write(AppClock.now()));
            statement.setString(7, customer.getBirthday() == null ? null : customer.getBirthday().toString());
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
        // never on an anonymous customer (TODO 3.4): nothing personal may be written back into it
        String sql = "UPDATE customers SET full_name = ?, phone = ?, email = ?, birthday = ? WHERE id = ? AND anonymised_at IS NULL";
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String oldBirthday = null;
                try (PreparedStatement read = connection.prepareStatement("SELECT birthday FROM customers WHERE id = ?")) {
                    read.setInt(1, customer.getId());
                    try (ResultSet rs = read.executeQuery()) {
                        if (rs.next()) oldBirthday = rs.getString(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.nfc(customer.getFullName()));
                    statement.setString(2, customer.getPhone());
                    statement.setString(3, ValidationUtil.cleanEmail(customer.getEmail()));
                    statement.setString(4, customer.getBirthday() == null ? null : customer.getBirthday().toString());
                    statement.setInt(5, customer.getId());
                    int changed = statement.executeUpdate();
                    // the date itself is personal data: the audit line says only that it was set, changed or removed (TODO 7, birthdays)
                    java.time.LocalDate before = BirthdayRules.parse(oldBirthday);
                    if (changed == 1 && !java.util.Objects.equals(before, customer.getBirthday())) {
                        com.coffeeshop.coffeeshopmanagement.dao.AuditDAO.write(connection,
                                com.coffeeshop.coffeeshopmanagement.model.AuditAction.BIRTHDAY_CHANGED,
                                com.coffeeshop.coffeeshopmanagement.model.AuditTarget.CUSTOMER, customer.getId(), "#" + customer.getId(), null, null,
                                before == null ? "set" : customer.getBirthday() == null ? "removed" : "changed");
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw duplicatePhoneOr(e, "Failed to update customer");
        }
    }

    /** Deletes a customer that has no orders, no points and no login account; returns whether a row was removed (TODO 3.27). */
    public boolean deleteIfUnused(int id) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            return deleteIfUnused(connection, id);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to remove an unused customer", e);
        }
    }

    /** Same as {@link #deleteIfUnused(int)} on a connection the caller owns, so it can be part of the caller's transaction. */
    public boolean deleteIfUnused(Connection connection, int id) throws SQLException {
        String sql = "DELETE FROM customers WHERE id = ? AND loyalty_points = 0 "
                + "AND NOT EXISTS (SELECT 1 FROM orders WHERE customer_id = ?) "
                + "AND NOT EXISTS (SELECT 1 FROM users WHERE customer_id = ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setInt(2, id);
            statement.setInt(3, id);
            return statement.executeUpdate() > 0;
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

    /** The grouped statement of {@link #figures()}, on its own so that the tests can run it. */
    static String figuresSql() {
        String when = "COALESCE(o.paid_at, o.order_date)";
        String sql = "SELECT o.customer_id, COUNT(*) AS visits, SUM(o.total) AS gross, SUM(COALESCE(rf.amount, 0)) AS refunded, "
                + "MAX(CASE WHEN " + when + " LIKE '____-__-_____:__%' THEN " + when + " END) AS last_time "
                + "FROM orders o LEFT JOIN (SELECT order_id, SUM(amount) AS amount FROM refunds GROUP BY order_id) rf ON rf.order_id = o.id "
                + "WHERE o.status = " + com.coffeeshop.coffeeshopmanagement.config.SqlStatus.PAID + " AND o.customer_id IS NOT NULL "
                + "GROUP BY o.customer_id";
        return sql;
    }

    /**
     * What every customer who has a paid order has bought: visits, total spent and last visit (TODO 4.39), worked out by the
     * database in one grouped query (TODO 2.101), so only one row per customer comes back, not one per order ever paid. The total is
     * what was paid <b>less the line refunds</b> ({@code refunds}), as the shift close, the reports and the dashboard count it; an
     * order that was refunded whole is no longer PAID and is not counted at all. A time that cannot be read is left out of "last
     * visit" only (the visit and the money still count): the pattern accepts both the {@code T} of SQLite and the space of MariaDB.
     */
    public java.util.Map<Integer, com.coffeeshop.coffeeshopmanagement.service.CustomerStats.Figures> figures() {
        String sql = figuresSql();
        java.util.Map<Integer, com.coffeeshop.coffeeshopmanagement.service.CustomerStats.Figures> result = new java.util.HashMap<>();
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                LocalDateTime last;
                try {
                    last = DbTime.parse(rs.getString("last_time"));
                } catch (RuntimeException e) {
                    last = null;
                }
                result.put(rs.getInt("customer_id"), com.coffeeshop.coffeeshopmanagement.service.CustomerStats.figures(rs.getInt("visits"),
                        com.coffeeshop.coffeeshopmanagement.util.Money.fromDong(rs.getLong("gross")),
                        com.coffeeshop.coffeeshopmanagement.util.Money.fromDong(rs.getLong("refunded")), last));
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
        // An anonymous customer (TODO 3.4) is never offered: it has no name or phone to find, and nothing may be attached to it again.
        StringBuilder sql = new StringBuilder("SELECT * FROM customers WHERE anonymised_at IS NULL");
        List<String> params = new ArrayList<>();
        if (!keyword.isEmpty()) {
            String like = "%" + OrderDAO.escapeLike(keyword) + "%";
            String phoneKey = ValidationUtil.normalizePhone(keyword);
            sql.append(" AND (").append(DatabaseConfig.searchKeySql("full_name")).append(" LIKE ? ESCAPE '!'")
                    .append(" OR phone LIKE ? ESCAPE '!'")
                    .append(" OR ").append(DatabaseConfig.searchKeySql("COALESCE(email, '')")).append(" LIKE ? ESCAPE '!')");
            params.add(like);
            params.add("%" + OrderDAO.escapeLike(phoneKey == null ? keyword : phoneKey) + "%");
            params.add(like);
        }
        sql.append(" ORDER BY ").append(DatabaseConfig.dialect().vietnameseOrder("full_name")).append(", id LIMIT ?"); // 3.84: before the cut
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
        customer.setLifetimePoints(rs.getInt("lifetime_points"));
        String createdAt = rs.getString("created_at");
        customer.setCreatedAt(DbTime.parse(createdAt));
        customer.setAnonymisedAt(DbTime.parse(rs.getString("anonymised_at")));
        customer.setBirthday(BirthdayRules.parse(rs.getString("birthday")));
        customer.setBirthdayUsedOn(BirthdayRules.parse(rs.getString("birthday_used_on"))); // TODO 3.66
        return customer;
    }

    /**
     * Records that the birthday discount is given to {@code customerId} on {@code today} (TODO 3.66, rule D25 of {@link BirthdayRules}),
     * on the connection of the payment, so it is saved or lost with the sale. Refuses, with a sentence fit to show, when there is no
     * such customer or it is anonymous, when today is not their birthday, and when it was already given today: the write only succeeds if
     * the day is not already there, so two tills paying two orders of the same customer at the same moment cannot both give it. It is
     * one line in the audit trail (customer, order, who).
     */
    static void useBirthday(Connection connection, int customerId, int orderId, java.time.LocalDate today) throws SQLException {
        String name;
        java.time.LocalDate birthday;
        try (PreparedStatement read = connection.prepareStatement(
                "SELECT full_name, birthday FROM customers WHERE id = ? AND anonymised_at IS NULL")) {
            read.setInt(1, customerId);
            try (ResultSet rs = read.executeQuery()) {
                if (!rs.next()) throw new DataAccessException(I18n.t("birthday.use.nocustomer"));
                name = rs.getString(1);
                birthday = BirthdayRules.parse(rs.getString(2));
            }
        }
        if (!BirthdayRules.isBirthday(birthday, today)) throw new DataAccessException(I18n.t("birthday.use.notToday"));
        try (PreparedStatement mark = connection.prepareStatement(
                "UPDATE customers SET birthday_used_on = ? WHERE id = ? AND (birthday_used_on IS NULL OR birthday_used_on <> ?)")) {
            mark.setString(1, today.toString());
            mark.setInt(2, customerId);
            mark.setString(3, today.toString());
            if (mark.executeUpdate() == 0) throw new DataAccessException(I18n.t("birthday.use.already"));
        }
        // the date itself is personal data: the line says only that the discount was given, to whom and on which order
        AuditDAO.write(connection, com.coffeeshop.coffeeshopmanagement.model.AuditAction.BIRTHDAY_DISCOUNT_USED,
                com.coffeeshop.coffeeshopmanagement.model.AuditTarget.CUSTOMER, customerId, name, null, null, "order #" + orderId);
    }
}

