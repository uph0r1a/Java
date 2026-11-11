package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.model.OptionKind;
import com.coffeeshop.coffeeshopmanagement.model.ProductOption;
import com.coffeeshop.coffeeshopmanagement.service.OptionRules;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Money;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The sizes and extras of the products (TODO 4.47): read for the till, saved by the owner. A product's options are always replaced
 * as a whole list in one transaction, checked by {@link OptionRules} on the way in, so the table never holds a half-saved or an invalid
 * list. An order line never points at these rows (it copies the name and price at the sale), so replacing or deleting them changes no
 * past order.
 */
public class ProductOptionDAO {

    private static final String COLUMNS = "id, product_id, kind, name, price_delta, cost_delta, recipe_percent, is_default, active";
    private static final String ORDER = " ORDER BY product_id, CASE kind WHEN 'SIZE' THEN 0 ELSE 1 END, id";

    /** Every option of one product, switched-off ones too (the owner's screen). Sizes first, in the order they were saved. */
    public List<ProductOption> findByProduct(int productId) {
        String sql = "SELECT " + COLUMNS + " FROM product_options WHERE product_id = ?" + ORDER;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            try (ResultSet rs = statement.executeQuery()) {
                return read(rs).values().stream().findFirst().orElseGet(List::of);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the options", e);
        }
    }

    /** The options the till offers: every switched-on option, grouped by product id. A product with none is not in the map. */
    public Map<Integer, List<ProductOption>> findAllActive() {
        String sql = "SELECT " + COLUMNS + " FROM product_options WHERE active = 1" + ORDER;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return read(rs);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the options", e);
        }
    }

    /** Replaces the options of a product with {@code options} (an empty list removes them all). Owner only. */
    public void save(int productId, List<ProductOption> options) {
        Permissions.requireAdmin("change the sizes and extras of a product");
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String name = replace(connection, productId, options);
                AuditDAO.write(connection, AuditAction.OPTIONS_CHANGED, AuditTarget.PRODUCT, productId, name, null, null,
                        options.isEmpty() ? "options removed" : OptionRules.sizesOf(options).size() + " size(s), "
                                + OptionRules.extrasOf(options).size() + " extra(s)");
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save the options", e);
        }
    }

    /**
     * Gives every OTHER product of the same category the options of {@code productId}, replacing what they had (the owner sets S / M / L
     * once and copies it to the whole menu of drinks). Returns how many products were changed. All or none: if the options do not fit
     * one of the products (a size that would take a cheaper drink below zero), nothing is copied and the message names that product.
     */
    public int copyToCategory(int productId) {
        Permissions.requireAdmin("copy the sizes and extras to a category");
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                List<ProductOption> source;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT " + COLUMNS + " FROM product_options WHERE product_id = ?" + ORDER)) {
                    select.setInt(1, productId);
                    try (ResultSet rs = select.executeQuery()) {
                        source = read(rs).values().stream().findFirst().orElseGet(List::of);
                    }
                }
                List<Integer> targets = new ArrayList<>();
                String sourceName = null;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT p.id, s.name FROM products p JOIN products s ON s.category_id = p.category_id AND s.id = ? "
                                + "WHERE p.id <> s.id ORDER BY p.id")) {
                    select.setInt(1, productId);
                    try (ResultSet rs = select.executeQuery()) {
                        while (rs.next()) {
                            targets.add(rs.getInt(1));
                            sourceName = rs.getString(2);
                        }
                    }
                }
                for (int target : targets) replace(connection, target, source);
                if (!targets.isEmpty()) {
                    AuditDAO.write(connection, AuditAction.OPTIONS_CHANGED, AuditTarget.PRODUCT, productId,
                            sourceName, null, null, "copied to " + targets.size() + " product(s) of the same category");
                }
                connection.commit();
                return targets.size();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to copy the options", e);
        }
    }

    /** Checks and writes one product's list on the caller's connection; returns the product's name. */
    private static String replace(Connection connection, int productId, List<ProductOption> options) throws SQLException {
        String productName;
        java.math.BigDecimal price;
        try (PreparedStatement read = connection.prepareStatement("SELECT name, price FROM products WHERE id = ?")) {
            read.setInt(1, productId);
            try (ResultSet rs = read.executeQuery()) {
                if (!rs.next()) throw new DataAccessException(I18n.t("recipe.product.gone"));
                productName = rs.getString(1);
                price = Money.fromDong(rs.getLong(2));
            }
        }
        List<ProductOption> clean = OptionRules.normalise(options);
        String problem = OptionRules.problem(price, clean);
        if (problem != null) throw new DataAccessException(productName + ": " + problem);
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM product_options WHERE product_id = ?")) {
            delete.setInt(1, productId);
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO product_options "
                + "(product_id, kind, name, price_delta, cost_delta, recipe_percent, is_default, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (ProductOption o : clean) {
                insert.setInt(1, productId);
                insert.setString(2, o.kind().name());
                insert.setString(3, o.name());
                insert.setLong(4, Money.toDong(o.priceDelta()));
                insert.setLong(5, Money.toDong(o.costDelta()));
                insert.setInt(6, o.recipePercent());
                insert.setInt(7, o.isDefault() ? 1 : 0);
                insert.setInt(8, o.active() ? 1 : 0);
                insert.addBatch();
            }
            insert.executeBatch();
        }
        return productName;
    }

    private static Map<Integer, List<ProductOption>> read(ResultSet rs) throws SQLException {
        Map<Integer, List<ProductOption>> byProduct = new LinkedHashMap<>();
        while (rs.next()) {
            ProductOption option = new ProductOption(rs.getInt("id"), rs.getInt("product_id"), OptionKind.valueOf(rs.getString("kind")),
                    rs.getString("name"), Money.fromDong(rs.getLong("price_delta")), Money.fromDong(rs.getLong("cost_delta")),
                    rs.getInt("recipe_percent"), rs.getInt("is_default") != 0, rs.getInt("active") != 0);
            byProduct.computeIfAbsent(option.productId(), k -> new ArrayList<>()).add(option);
        }
        return byProduct;
    }
}
