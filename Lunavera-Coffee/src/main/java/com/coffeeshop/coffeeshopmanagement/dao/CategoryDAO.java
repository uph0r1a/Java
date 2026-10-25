package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CategoryDAO {

    /** Two names that differ only in letter case, spacing or how an accent is encoded are the same name (TODO 3.18). */
    private void rejectDuplicateName(String name, int excludeId) {
        String wanted = com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.key(name);
        for (Category other : findAll()) {
            if (other.getId() != excludeId
                    && com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.key(other.getName()).equals(wanted)) {
                throw new DataAccessException(I18n.t("category.exists"));
            }
        }
    }

    public Category insert(Category category) {
        Permissions.requireAdmin("add a category");
        rejectDuplicateName(category.getName(), 0);
        String sql = "INSERT INTO categories (name, description, active, sort_order) VALUES (?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.nfc(category.getName()));
            statement.setString(2, category.getDescription());
            statement.setBoolean(3, category.isActive());
            statement.setInt(4, nextSortOrder(connection)); // a new category is the last tab (TODO 5.31)
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    category.setId(keys.getInt(1));
                }
            }
            return category;
        } catch (SQLException e) {
            if (isUniqueConstraintViolation(e)) {
                throw new DataAccessException(I18n.t("category.exists"));
            }
            throw new DataAccessException("Failed to create category", e);
        }
    }

    public void update(Category category) {
        Permissions.requireAdmin("change a category");
        rejectDuplicateName(category.getName(), category.getId());
        String sql = "UPDATE categories SET name = ?, description = ?, active = ? WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.nfc(category.getName()));
            statement.setString(2, category.getDescription());
            statement.setBoolean(3, category.isActive());
            statement.setInt(4, category.getId());
            statement.executeUpdate();
        } catch (SQLException e) {
            if (isUniqueConstraintViolation(e)) {
                throw new DataAccessException(I18n.t("category.exists"));
            }
            throw new DataAccessException("Failed to update category", e);
        }
    }

    /**
     * Deletes a category only when no product references it, to avoid orphaning products
     * or invalidating historical order data. Returns false (and deletes nothing) if products
     * still depend on it; the caller should offer to deactivate instead.
     */
    public boolean deleteIfUnused(int categoryId) {
        Permissions.requireAdmin("delete a category");
        String countSql = "SELECT COUNT(*) FROM products WHERE category_id = ?";
        try (Connection connection = DatabaseConfig.getConnection()) {
            try (PreparedStatement count = connection.prepareStatement(countSql)) {
                count.setInt(1, categoryId);
                try (ResultSet rs = count.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        return false;
                    }
                }
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM categories WHERE id = ?")) {
                delete.setInt(1, categoryId);
                delete.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete category", e);
        }
    }

    public Optional<Category> findById(int id) {
        String sql = "SELECT * FROM categories WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load category", e);
        }
        return Optional.empty();
    }

    public List<Category> findAll() {
        String sql = "SELECT * FROM categories ORDER BY sort_order, id"; // the shop's own order, else the order of creation (TODO 5.31)
        List<Category> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load categories", e);
        }
        return result;
    }

    public int countProductsInCategory(int categoryId) {
        String sql = "SELECT COUNT(*) FROM products WHERE category_id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, categoryId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count products in category", e);
        }
    }

    /**
     * Product count for every category in one query, keyed by category id. Prefer this over
     * calling {@link #countProductsInCategory(int)} once per category in a loop (e.g. from a
     * table's cell-value-factory) - that pattern re-opens a connection per category, per
     * refresh, and gets noticeably slow as soon as a screen re-renders on every keystroke.
     */
    public Map<Integer, Integer> countProductsByCategory() {
        String sql = "SELECT category_id, COUNT(*) AS cnt FROM products " +
                "WHERE category_id IS NOT NULL GROUP BY category_id";
        Map<Integer, Integer> result = new HashMap<>();
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getInt("category_id"), rs.getInt("cnt"));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count products by category", e);
        }
        return result;
    }

    private boolean isUniqueConstraintViolation(SQLException e) {
        return UniqueViolation.isUnique(e); // by error code, not message text (TODO 6.40)
    }

    private Category map(ResultSet rs) throws SQLException {
        Category category = new Category();
        category.setId(rs.getInt("id"));
        category.setName(rs.getString("name"));
        category.setDescription(rs.getString("description"));
        category.setActive(rs.getBoolean("active"));
        category.setSortOrder(rs.getInt("sort_order"));
        return category;
    }

    /** One more than the largest number in use, so a new category goes last. */
    private static int nextSortOrder(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM categories")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * Moves a category one place up ({@code delta < 0}) or down ({@code delta > 0}) among the tabs of the sales screen (TODO 5.31).
     * All categories are numbered again 1, 2, 3... in their new order in one transaction, so equal numbers (every old category has 0)
     * can never leave an arrow without effect. Returns false when it is already first or last.
     */
    public boolean move(int categoryId, int delta) {
        Permissions.requireAdmin("change the order of the categories");
        List<Category> all = findAll();
        List<Integer> before = new ArrayList<>();
        for (Category category : all) before.add(category.getId());
        List<Integer> after = com.coffeeshop.coffeeshopmanagement.service.DisplayOrder.moved(
                all, Category::getId, category -> Boolean.TRUE, categoryId, delta);
        if (after.equals(before)) {
            return false;
        }
        saveOrder(after);
        return true;
    }

    /** Numbers the categories 1, 2, 3... in the order given. */
    void saveOrder(List<Integer> idsInOrder) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("UPDATE categories SET sort_order = ? WHERE id = ?")) {
                int position = 1;
                for (int id : idsInOrder) {
                    statement.setInt(1, position++);
                    statement.setInt(2, id);
                    statement.addBatch();
                }
                statement.executeBatch();
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to change the order of the categories", e);
        }
    }
}
