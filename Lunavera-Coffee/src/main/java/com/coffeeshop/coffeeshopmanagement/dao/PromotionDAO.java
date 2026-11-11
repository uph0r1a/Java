package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Kind;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Promotion;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionRules.Target;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The automatic promotions the shop has set up (TODO 4.55; the arithmetic is {@link AutoPromotionRules}). Only an administrator makes,
 * changes, switches or removes one. A promotion and its targets are written in one transaction, so a promotion is never left with half
 * its targets. Removing a product or a category removes its targets too (the database does that); a promotion left with no target is
 * simply ignored by the rules until someone gives it one.
 */
public class PromotionDAO {

    private static final String COLUMNS = "id, name, kind, percent, fixed_price, buy_qty, free_qty, days, time_from, time_to, "
            + "valid_from, valid_to, active";

    /** Every promotion, oldest first, with its targets. */
    public List<Promotion> findAll() {
        return load("SELECT " + COLUMNS + " FROM promotions ORDER BY id");
    }

    /** The promotions that are switched on (the rules still check their days and hours), with their targets; what the till reads. */
    public List<Promotion> findActive() {
        return load("SELECT " + COLUMNS + " FROM promotions WHERE active = 1 ORDER BY id");
    }

    private List<Promotion> load(String sql) {
        List<Promotion> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection()) {
            Map<Integer, List<Target>> targets = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT promotion_id, product_id, category_id, quantity FROM promotion_targets ORDER BY id");
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int product = rs.getInt("product_id");
                    boolean noProduct = rs.wasNull();
                    int category = rs.getInt("category_id");
                    boolean noCategory = rs.wasNull();
                    if (noProduct == noCategory) {
                        continue; // a row that is neither or both cannot be a target; ignored rather than failing the till
                    }
                    targets.computeIfAbsent(rs.getInt("promotion_id"), k -> new ArrayList<>())
                            .add(new Target(noProduct ? null : product, noCategory ? null : category, Math.max(1, rs.getInt("quantity"))));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(read(rs, targets.getOrDefault(rs.getInt("id"), List.of())));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the promotions", e);
        }
        return result;
    }

    /**
     * Saves a promotion: a new one when {@code promotion.id()} is 0, else that one is replaced (its targets with it). Refused with a plain
     * message when the promotion does not pass {@link AutoPromotionRules#problemWith}.
     *
     * @return the saved promotion with its id
     */
    public Promotion save(Promotion promotion) {
        Permissions.requireAdmin("save a promotion");
        String problem = AutoPromotionRules.problemWith(promotion);
        if (problem != null) {
            throw new DataAccessException(com.coffeeshop.coffeeshopmanagement.service.AutoPromotionService.problemText(problem));
        }
        boolean isNew = promotion.id() <= 0;
        String insertSql = "INSERT INTO promotions (name, kind, percent, fixed_price, buy_qty, free_qty, days, time_from, time_to, "
                + "valid_from, valid_to, active, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String updateSql = "UPDATE promotions SET name = ?, kind = ?, percent = ?, fixed_price = ?, buy_qty = ?, free_qty = ?, days = ?, "
                + "time_from = ?, time_to = ?, valid_from = ?, valid_to = ?, active = ? WHERE id = ?";
        int id;
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = isNew
                        ? connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)
                        : connection.prepareStatement(updateSql)) {
                    statement.setString(1, promotion.name().trim());
                    statement.setString(2, promotion.kind().name());
                    statement.setInt(3, promotion.kind() == Kind.PERCENT ? promotion.percent() : 0);
                    statement.setLong(4, promotion.kind() == Kind.FIXED_PRICE || promotion.kind() == Kind.COMBO ? promotion.fixedPrice() : 0);
                    statement.setInt(5, promotion.kind() == Kind.BUY_GET ? promotion.buy() : 0);
                    statement.setInt(6, promotion.kind() == Kind.BUY_GET ? promotion.free() : 0);
                    statement.setString(7, AutoPromotionRules.encodeDays(promotion.days()));
                    statement.setString(8, promotion.from() == null ? null : promotion.from().toString());
                    statement.setString(9, promotion.to() == null ? null : promotion.to().toString());
                    statement.setString(10, promotion.validFrom() == null ? null : promotion.validFrom().toString());
                    statement.setString(11, promotion.validTo() == null ? null : promotion.validTo().toString());
                    statement.setInt(12, promotion.active() ? 1 : 0);
                    if (isNew) {
                        statement.setString(13, DbTime.write(AppClock.now()));
                        statement.executeUpdate();
                        try (ResultSet keys = statement.getGeneratedKeys()) {
                            keys.next();
                            id = keys.getInt(1);
                        }
                    } else {
                        statement.setInt(13, promotion.id());
                        if (statement.executeUpdate() == 0) {
                            throw new DataAccessException(I18n.t("promo.val.gone"));
                        }
                        id = promotion.id();
                    }
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM promotion_targets WHERE promotion_id = ?")) {
                    delete.setInt(1, id);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO promotion_targets (promotion_id, product_id, category_id, quantity) VALUES (?, ?, ?, ?)")) {
                    for (Target target : promotion.targets()) {
                        insert.setInt(1, id);
                        if (target.productId() == null) insert.setNull(2, java.sql.Types.INTEGER); else insert.setInt(2, target.productId());
                        if (target.categoryId() == null) insert.setNull(3, java.sql.Types.INTEGER); else insert.setInt(3, target.categoryId());
                        insert.setInt(4, target.quantity());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException | RuntimeException failed) {
                connection.rollback();
                throw failed;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save the promotion", e);
        }
        SecurityLog.event(SecurityLog.actor() + (isNew ? " created" : " changed") + " promotion #" + id);
        return new Promotion(id, promotion.name().trim(), promotion.kind(), promotion.active(), promotion.percent(), promotion.fixedPrice(),
                promotion.buy(), promotion.free(), promotion.days(), promotion.from(), promotion.to(), promotion.validFrom(),
                promotion.validTo(), promotion.targets());
    }

    /** Switches a promotion on or off; a switched-off promotion is never applied. */
    public void setActive(int id, boolean active) {
        Permissions.requireAdmin("switch a promotion");
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE promotions SET active = ? WHERE id = ?")) {
            statement.setInt(1, active ? 1 : 0);
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to change the promotion", e);
        }
        SecurityLog.event(SecurityLog.actor() + (active ? " switched on" : " switched off") + " promotion #" + id);
    }

    /**
     * Removes a promotion and its targets. Orders already paid keep the promotion's name on their lines (it is copied onto the line),
     * so nothing in the history changes.
     */
    public void delete(int id) {
        Permissions.requireAdmin("delete a promotion");
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement targets = connection.prepareStatement("DELETE FROM promotion_targets WHERE promotion_id = ?")) {
                    targets.setInt(1, id);
                    targets.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM promotions WHERE id = ?")) {
                    statement.setInt(1, id);
                    statement.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException failed) {
                connection.rollback();
                throw failed;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete the promotion", e);
        }
        SecurityLog.event(SecurityLog.actor() + " deleted promotion #" + id);
    }

    /** The category of each of these products (a product without one is left out); what a category promotion matches on. */
    public Map<Integer, Integer> categoriesOf(java.util.Collection<Integer> productIds) {
        Map<Integer, Integer> categories = new LinkedHashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return categories;
        }
        String marks = String.join(", ", java.util.Collections.nCopies(productIds.size(), "?"));
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, category_id FROM products WHERE id IN (" + marks + ")")) {
            int at = 1;
            for (Integer id : productIds) {
                statement.setInt(at++, id);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    int category = rs.getInt("category_id");
                    if (!rs.wasNull()) {
                        categories.put(rs.getInt("id"), category);
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read the categories of the products", e);
        }
        return categories;
    }

    private static Promotion read(ResultSet rs, List<Target> targets) throws SQLException {
        String from = rs.getString("time_from");
        String to = rs.getString("time_to");
        String validFrom = rs.getString("valid_from");
        String validTo = rs.getString("valid_to");
        Kind kind;
        try {
            kind = Kind.valueOf(rs.getString("kind"));
        } catch (IllegalArgumentException unknown) {
            kind = null; // a value from a newer version: the rules ignore a promotion without a kind
        }
        return new Promotion(rs.getInt("id"), rs.getString("name"), kind, rs.getInt("active") == 1, rs.getInt("percent"),
                rs.getLong("fixed_price"), rs.getInt("buy_qty"), rs.getInt("free_qty"), AutoPromotionRules.decodeDays(rs.getString("days")),
                from == null || from.isBlank() ? null : LocalTime.parse(from), to == null || to.isBlank() ? null : LocalTime.parse(to),
                validFrom == null || validFrom.length() < 10 ? null : LocalDate.parse(validFrom.substring(0, 10)),
                validTo == null || validTo.length() < 10 ? null : LocalDate.parse(validTo.substring(0, 10)), targets);
    }
}
