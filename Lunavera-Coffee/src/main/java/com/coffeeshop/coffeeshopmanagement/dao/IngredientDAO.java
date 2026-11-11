package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.model.Ingredient;
import com.coffeeshop.coffeeshopmanagement.service.ExpenseRules;
import com.coffeeshop.coffeeshopmanagement.service.IngredientCostRules;
import com.coffeeshop.coffeeshopmanagement.service.IngredientRules;
import com.coffeeshop.coffeeshopmanagement.service.Limits;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.DbTime;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Ingredients and recipes (TODO 4.41, rule D12). Reading is open to everyone signed in (the sales screen and the dashboards show
 * the low-stock card); every change is for an administrator, is one transaction, and writes an audit line (TODO 4.2).
 * Paying an order takes the amounts off in {@link StockTaker}, inside the payment's own transaction.
 */
public class IngredientDAO {

    /**
     * One line of a recipe: how much of an ingredient one serving of the product takes, and the ingredient's price per unit now
     * (null = not priced), which is what the recipe's cost is worked out from (TODO 4.51).
     */
    public record RecipeLine(int ingredientId, String name, String unit, long amount, Long unitCost) {

        /** This line as the cost rules take it. */
        public IngredientCostRules.Line toCostLine() {
            return new IngredientCostRules.Line(name, amount, unitCost);
        }
    }

    /** What a delivery left behind: the stock now and the price per unit now (null = still not priced). */
    public record Receipt(long stockAfter, Long unitCostAfter) {
    }

    private static final String COLUMNS = "id, name, unit, stock, min_stock, unit_cost";

    public List<Ingredient> findAll() {
        return query("SELECT " + COLUMNS + " FROM ingredients ORDER BY " + DatabaseConfig.dialect().vietnameseOrder("name"), null, "Failed to load the ingredients");
    }

    /** The ingredients at or below their minimum, the emptiest first (the low-stock card). */
    public List<Ingredient> findLow(int limit) {
        return query("SELECT " + COLUMNS + " FROM ingredients WHERE stock <= min_stock ORDER BY stock * 1.0 / (min_stock + 1), " + DatabaseConfig.dialect().vietnameseOrder("name") + " LIMIT " + Math.max(1, limit),
                null, "Failed to load the ingredients to reorder");
    }

    public int countLow() {
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM ingredients WHERE stock <= min_stock")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count the ingredients to reorder", e);
        }
    }

    private List<Ingredient> query(String sql, Integer param, String failure) {
        List<Ingredient> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (param != null) statement.setInt(1, param);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) result.add(new Ingredient(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5), unitCostOf(rs, 6)));
            }
        } catch (SQLException e) {
            throw new DataAccessException(failure, e);
        }
        return result;
    }

    /** The price per unit in column {@code index}, null when the column is NULL. */
    private static Long unitCostOf(ResultSet rs, int index) throws SQLException {
        long cost = rs.getLong(index);
        return rs.wasNull() ? null : cost;
    }

    private static void setUnitCost(PreparedStatement statement, int index, Long unitCost) throws SQLException {
        if (unitCost == null) statement.setNull(index, Types.BIGINT);
        else statement.setLong(index, unitCost);
    }

    public Ingredient insert(String name, String unit, long stock, long minStock) {
        return insert(name, unit, stock, minStock, null);
    }

    /** Adds an ingredient with the price of one unit ({@code unitCost} null = not priced yet). */
    public Ingredient insert(String name, String unit, long stock, long minStock, Long unitCost) {
        Permissions.requireAdmin("add an ingredient");
        check(name, unit, stock, minStock);
        checkUnitCost(unitCost);
        String cleanName = IngredientRules.clean(name);
        String cleanUnit = IngredientRules.clean(unit);
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireFreeName(connection, cleanName, 0);
                int id;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO ingredients (name, unit, stock, min_stock, created_at, unit_cost) VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, cleanName);
                    statement.setString(2, cleanUnit);
                    statement.setLong(3, stock);
                    statement.setLong(4, minStock);
                    statement.setString(5, DbTime.write(AppClock.now()));
                    setUnitCost(statement, 6, unitCost);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getInt(1);
                    }
                }
                AuditDAO.write(connection, AuditAction.INGREDIENT_SAVED, AuditTarget.INGREDIENT, id, cleanName, null, null,
                        "new, " + stock + " " + cleanUnit + ", minimum " + minStock + (unitCost == null ? "" : ", unit cost " + unitCost));
                connection.commit();
                return new Ingredient(id, cleanName, cleanUnit, stock, minStock, unitCost);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to add the ingredient", e);
        }
    }

    /** Renames the ingredient, changes its unit and minimum. The stock is changed with {@link #changeStock}, so a sale made meanwhile is not overwritten. */
    public void update(int id, String name, String unit, long minStock) {
        Permissions.requireAdmin("change an ingredient");
        check(name, unit, 0, minStock);
        String cleanName = IngredientRules.clean(name);
        String cleanUnit = IngredientRules.clean(unit);
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireFreeName(connection, cleanName, id);
                try (PreparedStatement statement = connection.prepareStatement("UPDATE ingredients SET name = ?, unit = ?, min_stock = ? WHERE id = ?")) {
                    statement.setString(1, cleanName);
                    statement.setString(2, cleanUnit);
                    statement.setLong(3, minStock);
                    statement.setInt(4, id);
                    if (statement.executeUpdate() != 1) throw new DataAccessException(I18n.t("ingredient.gone"));
                }
                AuditDAO.write(connection, AuditAction.INGREDIENT_SAVED, AuditTarget.INGREDIENT, id, cleanName, null, null,
                        "changed, unit " + cleanUnit + ", minimum " + minStock);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to change the ingredient", e);
        }
    }

    /**
     * Like {@link #update(int, String, String, long)} and also sets the price of one unit ({@code unitCost} null removes the price).
     * The change of price is written to the audit line.
     */
    public void update(int id, String name, String unit, long minStock, Long unitCost) {
        Permissions.requireAdmin("change an ingredient");
        check(name, unit, 0, minStock);
        checkUnitCost(unitCost);
        String cleanName = IngredientRules.clean(name);
        String cleanUnit = IngredientRules.clean(unit);
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireFreeName(connection, cleanName, id);
                Long before;
                try (PreparedStatement read = connection.prepareStatement("SELECT unit_cost FROM ingredients WHERE id = ?")) {
                    read.setInt(1, id);
                    try (ResultSet rs = read.executeQuery()) {
                        if (!rs.next()) throw new DataAccessException(I18n.t("ingredient.gone"));
                        before = unitCostOf(rs, 1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE ingredients SET name = ?, unit = ?, min_stock = ?, unit_cost = ? WHERE id = ?")) {
                    statement.setString(1, cleanName);
                    statement.setString(2, cleanUnit);
                    statement.setLong(3, minStock);
                    setUnitCost(statement, 4, unitCost);
                    statement.setInt(5, id);
                    if (statement.executeUpdate() != 1) throw new DataAccessException(I18n.t("ingredient.gone"));
                }
                AuditDAO.write(connection, AuditAction.INGREDIENT_SAVED, AuditTarget.INGREDIENT, id, cleanName, null, null,
                        "changed, unit " + cleanUnit + ", minimum " + minStock + costChange(before, unitCost));
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to change the ingredient", e);
        }
    }

    /**
     * A delivery (TODO 4.51): adds {@code quantity} to the stock and, when {@code paid} says what the whole lot cost, brings the
     * ingredient's price per unit to the weighted average of what is on the shelf and what arrived ({@link IngredientCostRules#afterDelivery}).
     * With {@code recordExpense} the amount paid is also written as a supplies expense, in the same transaction, so the expenses
     * report shows it; it is left to the caller's choice because a shop that already types its supplies into the expenses would
     * count them twice.
     *
     * @param paid the total paid for the lot in whole dong, or null when it is not known (the price is then left as it was)
     */
    public Receipt receive(int id, long quantity, Long paid, boolean recordExpense, String reason) {
        Permissions.requireAdmin("receive a delivery of an ingredient");
        String problem = IngredientCostRules.deliveryProblem(quantity, paid);
        if (problem == null && recordExpense && paid != null) {
            LocalDate today = AppClock.today();
            problem = ExpenseRules.problem(today, BigDecimal.valueOf(paid), ExpenseRules.SUPPLIES, today);
        }
        if (problem != null) throw new DataAccessException(problem);
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String name;
                String unit;
                long before;
                Long costBefore;
                try (PreparedStatement read = connection.prepareStatement("SELECT name, unit, stock, unit_cost FROM ingredients WHERE id = ?")) {
                    read.setInt(1, id);
                    try (ResultSet rs = read.executeQuery()) {
                        if (!rs.next()) throw new DataAccessException(I18n.t("ingredient.gone"));
                        name = rs.getString(1);
                        unit = rs.getString(2);
                        before = rs.getLong(3);
                        costBefore = unitCostOf(rs, 4);
                    }
                }
                String stockProblem = IngredientRules.amountProblem(before + quantity);
                if (stockProblem != null) throw new DataAccessException(stockProblem);
                Long costAfter = costBefore;
                if (paid != null) {
                    costAfter = IngredientCostRules.afterDelivery(before, costBefore, quantity, IngredientCostRules.unitCostFromPaid(paid, quantity));
                }
                try (PreparedStatement statement = connection.prepareStatement("UPDATE ingredients SET stock = stock + ?, unit_cost = ? WHERE id = ?")) {
                    statement.setLong(1, quantity);
                    setUnitCost(statement, 2, costAfter);
                    statement.setInt(3, id);
                    if (statement.executeUpdate() != 1) throw new DataAccessException(I18n.t("ingredient.gone"));
                }
                String detail = (reason == null || reason.isBlank() ? "" : reason.trim())
                        + (paid == null ? "" : (reason == null || reason.isBlank() ? "" : "; ") + "paid " + paid) + costChange(costBefore, costAfter);
                AuditDAO.write(connection, AuditAction.INGREDIENT_STOCK, AuditTarget.INGREDIENT, id, name,
                        String.valueOf(before), String.valueOf(before + quantity), detail.isBlank() ? null : detail);
                if (recordExpense && paid != null) {
                    ExpenseDAO.insert(connection, AppClock.today(), BigDecimal.valueOf(paid), ExpenseRules.SUPPLIES,
                            I18n.t("ingredient.expense.note", name, quantity, unit), false);
                }
                connection.commit();
                return new Receipt(before + quantity, costAfter);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to receive the delivery", e);
        }
    }

    /** ", unit cost A -> B" when the price changed (an empty text when it did not), for the audit line. */
    private static String costChange(Long before, Long after) {
        if (java.util.Objects.equals(before, after)) return "";
        return ", unit cost " + (before == null ? "none" : before) + " -> " + (after == null ? "none" : after);
    }

    private static void checkUnitCost(Long unitCost) {
        if (unitCost == null) return;
        String problem = IngredientCostRules.unitCostProblem(unitCost);
        if (problem != null) throw new DataAccessException(problem);
    }

    /**
     * Adds {@code delta} to the stock (a delivery; negative for waste or a count that came out lower), guarded so it never goes
     * below zero, and logs the old and new number. {@code delta = 0} is refused.
     */
    public long changeStock(int id, long delta, String reason) {
        Permissions.requireAdmin("change an ingredient's stock");
        if (delta == 0) throw new DataAccessException(I18n.t("ingredient.val.nochange"));
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String name;
                long before;
                try (PreparedStatement read = connection.prepareStatement("SELECT name, stock FROM ingredients WHERE id = ?")) {
                    read.setInt(1, id);
                    try (ResultSet rs = read.executeQuery()) {
                        if (!rs.next()) throw new DataAccessException(I18n.t("ingredient.gone"));
                        name = rs.getString(1);
                        before = rs.getLong(2);
                    }
                }
                long after = before + delta;
                String problem = IngredientRules.amountProblem(after);
                if (problem != null) throw new DataAccessException(problem);
                try (PreparedStatement statement = connection.prepareStatement("UPDATE ingredients SET stock = stock + ? WHERE id = ? AND stock + ? >= 0")) {
                    statement.setLong(1, delta);
                    statement.setInt(2, id);
                    statement.setLong(3, delta);
                    if (statement.executeUpdate() != 1) throw new DataAccessException(I18n.t("ingredient.val.negative"));
                }
                AuditDAO.write(connection, AuditAction.INGREDIENT_STOCK, AuditTarget.INGREDIENT, id, name,
                        String.valueOf(before), String.valueOf(before + delta), reason == null || reason.isBlank() ? null : reason.trim());
                connection.commit();
                return before + delta;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to change the ingredient's stock", e);
        }
    }

    /** What a stock count did: the stock found when it was saved, and the stock now (the counted number). */
    public record Counted(long before, long after) {
    }

    /**
     * Saves a stock count (TODO 3.75): the counted number itself, not a difference from what a window showed, so cups sold while
     * the shelf was being counted cannot corrupt it. The row is read and written in one transaction (the write only succeeds if
     * the stock is still what was read), and the difference found is logged with the reason. Counting what is already there changes nothing.
     */
    public Counted setCounted(int id, long counted, String reason) {
        Permissions.requireAdmin("count an ingredient's stock");
        String problem = IngredientRules.amountProblem(counted);
        if (problem != null) throw new DataAccessException(problem);
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String name;
                long before;
                try (PreparedStatement read = connection.prepareStatement("SELECT name, stock FROM ingredients WHERE id = ?")) {
                    read.setInt(1, id);
                    try (ResultSet rs = read.executeQuery()) {
                        if (!rs.next()) throw new DataAccessException(I18n.t("ingredient.gone"));
                        name = rs.getString(1);
                        before = rs.getLong(2);
                    }
                }
                if (before == counted) {
                    connection.rollback();
                    return new Counted(before, counted);
                }
                try (PreparedStatement statement = connection.prepareStatement("UPDATE ingredients SET stock = ? WHERE id = ? AND stock = ?")) {
                    statement.setLong(1, counted);
                    statement.setInt(2, id);
                    statement.setLong(3, before);
                    if (statement.executeUpdate() != 1) throw new DataAccessException(I18n.t("ingredient.count.moved"));
                }
                AuditDAO.write(connection, AuditAction.INGREDIENT_STOCK, AuditTarget.INGREDIENT, id, name,
                        String.valueOf(before), String.valueOf(counted), reason == null || reason.isBlank() ? null : reason.trim());
                connection.commit();
                return new Counted(before, counted);
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save the stock count", e);
        }
    }

    /** Removes an ingredient no recipe uses. */
    public void delete(int id) {
        Permissions.requireAdmin("delete an ingredient");
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String name;
                try (PreparedStatement read = connection.prepareStatement("SELECT name FROM ingredients WHERE id = ?")) {
                    read.setInt(1, id);
                    try (ResultSet rs = read.executeQuery()) {
                        if (!rs.next()) throw new DataAccessException(I18n.t("ingredient.gone"));
                        name = rs.getString(1);
                    }
                }
                try (PreparedStatement used = connection.prepareStatement("SELECT COUNT(*) FROM recipes WHERE ingredient_id = ?")) {
                    used.setInt(1, id);
                    try (ResultSet rs = used.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) throw new DataAccessException(I18n.t("ingredient.inuse", name, rs.getInt(1)));
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM ingredients WHERE id = ?")) {
                    statement.setInt(1, id);
                    statement.executeUpdate();
                }
                AuditDAO.write(connection, AuditAction.INGREDIENT_DELETED, AuditTarget.INGREDIENT, id, name, null, null, null);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete the ingredient", e);
        }
    }

    public List<RecipeLine> recipeOf(int productId) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            return readRecipe(connection, productId);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the recipe", e);
        }
    }

    private static List<RecipeLine> readRecipe(Connection connection, int productId) throws SQLException {
        List<RecipeLine> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT r.ingredient_id, i.name, i.unit, r.amount, i.unit_cost FROM recipes r JOIN ingredients i ON i.id = r.ingredient_id "
                        + "WHERE r.product_id = ? ORDER BY " + DatabaseConfig.dialect().vietnameseOrder("i.name"))) {
            statement.setInt(1, productId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) result.add(new RecipeLine(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getLong(4), unitCostOf(rs, 5)));
            }
        }
        return result;
    }

    /** The cost of one serving of the product's saved recipe at the ingredients' prices now (TODO 4.51). */
    public IngredientCostRules.RecipeCost recipeCostOf(int productId) {
        return IngredientCostRules.recipeCost(recipeOf(productId).stream().map(RecipeLine::toCostLine).toList());
    }

    /**
     * Makes the cost of the product the cost of its recipe (TODO 4.51): what profit is worked out from, for the sales paid from now on
     * (a line already paid keeps the cost it was paid with). Refused when the product has no recipe or an ingredient of it has no
     * price, because a half-priced recipe would make the drink look cheaper than it is. The change is on the audit line of the
     * product's cost, like one made in the product form.
     *
     * @return the new cost in whole dong
     */
    public long useRecipeCostAsProductCost(int productId) {
        Permissions.requireAdmin("set a product's cost from its recipe");
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String name;
                Long old;
                try (PreparedStatement read = connection.prepareStatement("SELECT name, cost FROM products WHERE id = ?")) {
                    read.setInt(1, productId);
                    try (ResultSet rs = read.executeQuery()) {
                        if (!rs.next()) throw new DataAccessException(I18n.t("recipe.product.gone"));
                        name = rs.getString(1);
                        old = unitCostOf(rs, 2);
                    }
                }
                IngredientCostRules.RecipeCost cost = IngredientCostRules.recipeCost(
                        readRecipe(connection, productId).stream().map(RecipeLine::toCostLine).toList());
                if (cost.lines() == 0) throw new DataAccessException(I18n.t("recipe.cost.norecipe"));
                if (!cost.complete()) throw new DataAccessException(I18n.t("recipe.cost.missing", String.join(", ", cost.missing())));
                if (cost.total() > Limits.MAX_PRICE.longValue()) throw new DataAccessException(I18n.t("products.val.price.max", Limits.maxPriceText()));
                if (!java.util.Objects.equals(old, cost.total())) {
                    try (PreparedStatement statement = connection.prepareStatement("UPDATE products SET cost = ? WHERE id = ?")) {
                        statement.setLong(1, cost.total());
                        statement.setInt(2, productId);
                        statement.executeUpdate();
                    }
                    AuditDAO.write(connection, AuditAction.PRODUCT_COST, AuditTarget.PRODUCT, productId, name,
                            old == null ? null : String.valueOf(old), String.valueOf(cost.total()), "from the recipe");
                }
                connection.commit();
                return cost.total();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set the product's cost from its recipe", e);
        }
    }

    /** The number of products that have a recipe. */
    public int countRecipes() {
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(DISTINCT product_id) FROM recipes")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count the recipes", e);
        }
    }

    /**
     * Replaces the recipe of a product with {@code lines} (ingredient id, amount per serving). An empty list removes it: the product
     * goes back to counting cups in {@code products.stock}.
     */
    public void saveRecipe(int productId, List<long[]> lines) {
        Permissions.requireAdmin("change a recipe");
        String problem = IngredientRules.recipeProblem(lines);
        if (problem != null) throw new DataAccessException(problem);
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String productName;
                try (PreparedStatement read = connection.prepareStatement("SELECT name FROM products WHERE id = ?")) {
                    read.setInt(1, productId);
                    try (ResultSet rs = read.executeQuery()) {
                        if (!rs.next()) throw new DataAccessException(I18n.t("recipe.product.gone"));
                        productName = rs.getString(1);
                    }
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM recipes WHERE product_id = ?")) {
                    delete.setInt(1, productId);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO recipes (product_id, ingredient_id, amount) VALUES (?, ?, ?)");
                     PreparedStatement exists = connection.prepareStatement("SELECT 1 FROM ingredients WHERE id = ?")) {
                    for (long[] line : lines) {
                        exists.setLong(1, line[0]);
                        try (ResultSet rs = exists.executeQuery()) {
                            if (!rs.next()) throw new DataAccessException(I18n.t("ingredient.gone"));
                        }
                        insert.setInt(1, productId);
                        insert.setLong(2, line[0]);
                        insert.setLong(3, line[1]);
                        insert.executeUpdate();
                    }
                }
                AuditDAO.write(connection, AuditAction.RECIPE_CHANGED, AuditTarget.PRODUCT, productId, productName, null, null,
                        lines.isEmpty() ? "recipe removed (cups are counted again)" : lines.size() + " ingredient(s)");
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save the recipe", e);
        }
    }

    private static void check(String name, String unit, long stock, long minStock) {
        String problem = IngredientRules.problem(name, unit);
        if (problem == null) problem = IngredientRules.amountProblem(stock);
        if (problem == null) problem = IngredientRules.amountProblem(minStock);
        if (problem != null) throw new DataAccessException(problem);
    }

    private static void requireFreeName(Connection connection, String name, int exceptId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM ingredients WHERE LOWER(name) = LOWER(?) AND id <> ?")) {
            statement.setString(1, name);
            statement.setInt(2, exceptId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) throw new DataAccessException(I18n.t("ingredient.val.duplicate", name));
            }
        }
    }
}
