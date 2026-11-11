package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class ProductDAO {

    private static final String SELECT_WITH_CATEGORY =
            "SELECT p.*, c.name AS category_name, "
                    // TODO 4.41: a product with a recipe can be sold as many times as its scarcest ingredient allows (null = no recipe)
                    + "(SELECT MIN(i.stock * 1.0 / r.amount) FROM recipes r JOIN ingredients i ON i.id = r.ingredient_id WHERE r.product_id = p.id) AS recipe_servings "
                    + "FROM products p LEFT JOIN categories c ON p.category_id = c.id";
    /**
     * The products as the till may SELL them (TODO 3.1): what other OPEN orders already hold is not for sale again. A line in an open
     * order is the reservation, so nothing is stored and nothing has to be released: paying, cancelling or emptying a table makes the
     * lines stop counting by itself. Two numbers are worked out next to the true stock: {@code held_cups}, the pieces of a product
     * without a recipe that other open orders hold, and {@code recipe_servings_net}, the servings of a recipe product that are left
     * once the ingredient amounts of the other open orders' recipe lines (any product that uses the same ingredient) are taken off.
     * The two {@code ?} before the ids are the order that must not count against itself (0 = none: a new order). The statement is
     * plain SQL that SQLite and MariaDB both run.
     */
    static String forSaleSql(int idCount) {
        String others = "o2.status = " + com.coffeeshop.coffeeshopmanagement.config.SqlStatus.OPEN + " AND o2.id <> ?";
        String sql = "SELECT p.*, c.name AS category_name, "
                + "(SELECT MIN(i.stock * 1.0 / r.amount) FROM recipes r JOIN ingredients i ON i.id = r.ingredient_id WHERE r.product_id = p.id) AS recipe_servings, "
                + "(SELECT MIN((i.stock - COALESCE((SELECT SUM(r2.amount * oi2.quantity) FROM order_items oi2 "
                + "JOIN orders o2 ON o2.id = oi2.order_id JOIN recipes r2 ON r2.product_id = oi2.product_id "
                + "WHERE " + others + " AND r2.ingredient_id = i.id), 0)) * 1.0 / r.amount) "
                + "FROM recipes r JOIN ingredients i ON i.id = r.ingredient_id WHERE r.product_id = p.id) AS recipe_servings_net, "
                + "COALESCE((SELECT SUM(oi3.quantity) FROM order_items oi3 JOIN orders o3 ON o3.id = oi3.order_id "
                + "WHERE o3.status = " + com.coffeeshop.coffeeshopmanagement.config.SqlStatus.OPEN + " AND o3.id <> ? AND oi3.product_id = p.id), 0) AS held_cups "
                + "FROM products p LEFT JOIN categories c ON p.category_id = c.id";
        if (idCount > 0) {
            sql += " WHERE p.id IN (" + String.join(", ", java.util.Collections.nCopies(idCount, "?")) + ")";
        }
        return sql + " ORDER BY p.sort_order, p.id";
    }

    /**
     * Every product with its stock taken down to what {@code exceptOrderId} may still sell: the true stock less what the other open
     * orders hold ({@link Product#getHeldElsewhere()} says how much that was). Pass the open order on the screen, or 0 when there is
     * none. For the sales screen only; the product screens use {@link #findAll()}, which is the shelf.
     */
    public List<Product> findAllForSale(int exceptOrderId) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            return forSale(connection, null, exceptOrderId);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load products", e);
        }
    }

    /**
     * The same on a connection the caller already holds: the autosave of an open order asks it, inside its own transaction, whether
     * the pieces it is about to keep are really free (TODO 3.1). {@code ids} null or empty = every product.
     */
    static List<Product> forSale(Connection connection, java.util.Collection<Integer> ids, int exceptOrderId) throws SQLException {
        List<Product> result = new ArrayList<>();
        int count = ids == null ? 0 : ids.size();
        if (ids != null && ids.isEmpty()) {
            return result;
        }
        try (PreparedStatement statement = connection.prepareStatement(forSaleSql(count))) {
            statement.setInt(1, exceptOrderId);
            statement.setInt(2, exceptOrderId);
            int position = 3;
            if (ids != null) {
                for (int id : ids) {
                    statement.setInt(position++, id);
                }
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapForSale(rs));
                }
            }
        }
        return result;
    }

    private static Product mapForSale(ResultSet rs) throws SQLException {
        Product product = new ProductDAO().map(rs);
        int shelf = product.getStock();
        int net;
        if (product.isRecipeBased()) {
            double servings = rs.getDouble("recipe_servings_net");
            net = (int) Math.max(0, Math.min(Math.floor(servings + 1e-9), Integer.MAX_VALUE));
        } else {
            net = Math.max(0, shelf - rs.getInt("held_cups"));
        }
        product.setHeldElsewhere(shelf - net);
        product.setStock(net);
        product.setLoadedStock(net);
        return product;
    }

    /** Products with a recipe count their ingredients, not their cup number, so the cup-count low-stock card skips them (TODO 4.41). */
    private static final String NO_RECIPE = " AND NOT EXISTS (SELECT 1 FROM recipes r WHERE r.product_id = p.id)";

    public Product insert(Product product) {
        Permissions.requireAdmin("add a product");
        String sql = "INSERT INTO products (name, category_id, price, cost, stock, description, image_path, active, reorder_level, code, sort_order) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(statement, product);
                statement.setInt(11, nextSortOrder(connection, product.getCategoryId())); // a new product goes last in its category (TODO 5.31)
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        product.setId(keys.getInt(1));
                    }
                }
                if (product.getStock() > 0) { // the opening stock is a typed number too (TODO 4.5)
                    StockLogDAO.logAdjustment(connection, product.getId(), product.getName(), 0, product.getStock());
                }
                connection.commit();
                return product;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw codeTakenOr(e, "Failed to create product");
        }
    }

    /**
     * Adds all the products in ONE transaction: if any row is refused, none is stored (TODO 2.69). One connection for the
     * whole list, which also makes a long import over a network much faster.
     */
    public void insertAll(java.util.List<Product> products) {
        com.coffeeshop.coffeeshopmanagement.util.Permissions.requireAdmin("import products");
        if (products == null || products.isEmpty()) return;
        String sql = "INSERT INTO products (name, category_id, price, cost, stock, description, image_path, active, reorder_level, code, sort_order) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            java.util.Map<Integer, Integer> nextByCategory = new java.util.HashMap<>(); // category id (-1 = none) -> the next number
            try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                for (Product product : products) {
                    bind(statement, product);
                    int key = product.getCategoryId() == null ? -1 : product.getCategoryId();
                    int sortOrder = nextByCategory.containsKey(key) ? nextByCategory.get(key) : nextSortOrder(connection, product.getCategoryId());
                    nextByCategory.put(key, sortOrder + 1);
                    statement.setInt(11, sortOrder);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (keys.next()) product.setId(keys.getInt(1));
                    }
                }
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw codeTakenOr(e, "Failed to create products");
        }
    }

    /**
     * True when another product (id != excludeId; pass 0 when adding) in the same category already has this name, ignoring
     * upper/lower case, accents' case and extra spaces (TODO 2.13: two "Latte" rows are confusing at the till). Compared in Java,
     * because SQLite's LOWER() only understands plain ASCII letters, not Vietnamese ones.
     */
    public boolean existsByNameInCategory(String name, Integer categoryId, int excludeId) {
        String wanted = normalizeName(name);
        String sql = categoryId == null
                ? "SELECT name FROM products WHERE category_id IS NULL AND id <> ?"
                : "SELECT name FROM products WHERE category_id = ? AND id <> ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int i = 1;
            if (categoryId != null) statement.setInt(i++, categoryId);
            statement.setInt(i, excludeId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    if (normalizeName(rs.getString(1)).equals(wanted)) return true;
                }
            }
            return false;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check product name", e);
        }
    }

    /** How many products other than {@code excludeId} still point at this picture (a picture is deleted only when 0). */
    public int countUsingImage(String imagePath, int excludeId) {
        if (imagePath == null || imagePath.isBlank()) return 0;
        String sql = "SELECT COUNT(*) FROM products WHERE image_path = ? AND id <> ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, imagePath);
            statement.setInt(2, excludeId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check picture use", e);
        }
    }

    /**
     * The product with exactly this code (TODO 4.42), whether or not it is on sale: the caller decides what a stopped product
     * means. The code is cleaned the same way it is stored, so a scanner's trailing line break or a lower-case typing still finds it.
     */
    public java.util.Optional<Product> findByCode(String code) {
        String wanted = com.coffeeshop.coffeeshopmanagement.service.ProductCode.clean(code);
        if (wanted == null) return java.util.Optional.empty();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_WITH_CATEGORY + " WHERE p.code = ?")) {
            statement.setString(1, wanted);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? java.util.Optional.of(map(rs)) : java.util.Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to look up a product code", e);
        }
    }

    /** The product with this id as the database holds it now, or empty when it is gone. */
    public java.util.Optional<Product> findById(int id) {
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_WITH_CATEGORY + " WHERE p.id = ?")) {
            statement.setInt(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? java.util.Optional.of(map(rs)) : java.util.Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read a product", e);
        }
    }

    /**
     * The products with these ids as they are in the database now (a missing id is simply not in the answer). One query, used to
     * re-read the stock of the few products in a cart just before the payment (TODO 3.1).
     */
    public List<Product> findByIds(java.util.Collection<Integer> ids) {
        List<Product> result = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        String marks = String.join(", ", java.util.Collections.nCopies(ids.size(), "?"));
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(SELECT_WITH_CATEGORY + " WHERE p.id IN (" + marks + ")")) {
            int position = 1;
            for (int id : ids) {
                statement.setInt(position++, id);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to re-read the stock of the products in a cart", e);
        }
        return result;
    }

    /** True when another product (id != excludeId; pass 0 when adding) already has this code; an empty code is never taken. */
    public boolean codeTaken(String code, int excludeId) {
        String wanted = com.coffeeshop.coffeeshopmanagement.service.ProductCode.clean(code);
        if (wanted == null) return false;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM products WHERE code = ? AND id <> ?")) {
            statement.setString(1, wanted);
            statement.setInt(2, excludeId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check the product code", e);
        }
    }

    /**
     * The database's own unique rule on {@code code} (two tills saving the same code at the same moment, or a hand edit) becomes a
     * sentence fit to show. Safe to name the rule: {@code products} has no other unique column (see {@link UniqueViolation}).
     */
    private static DataAccessException codeTakenOr(SQLException e, String fallback) {
        return UniqueViolation.isUnique(e)
                ? new DataAccessException(com.coffeeshop.coffeeshopmanagement.util.I18n.t("products.code.taken"), e)
                : new DataAccessException(fallback, e);
    }

    static String normalizeName(String name) {
        return com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.key(name);
    }

    public void update(Product product) {
        Permissions.requireAdmin("change a product");
        String sql = "UPDATE products SET name=?, category_id=?, price=?, cost=?, stock=COALESCE(?, stock), description=?, " +
                "image_path=?, active=?, reorder_level=?, code=? WHERE id=?";
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, product);
                // Saving a product whose stock number the person did not change must not put the old number back over
                // sales made since it was read (TODO 3.2): a NULL here keeps the stock the database holds now. A stock
                // number that was typed in (a stock-take) is still written as given, and now also logged (TODO 4.5).
                boolean typedStock = !product.isStockUntouchedSinceLoad();
                int before = typedStock ? currentStock(connection, product.getId()) : 0;
                if (!typedStock) {
                    statement.setNull(5, Types.INTEGER);
                }
                statement.setInt(11, product.getId());
                Long[] oldMoney = readPriceAndCost(connection, product.getId()); // for the audit line (TODO 4.2)
                statement.executeUpdate();
                if (typedStock) {
                    StockLogDAO.logAdjustment(connection, product.getId(), product.getName(), before, product.getStock());
                }
                auditEdit(connection, product, oldMoney, typedStock, before);
                connection.commit();
                product.setLoadedStock(product.getStock());
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw codeTakenOr(e, "Failed to update product");
        }
    }

    /** The price and the cost (null = none) the database holds now, in whole dong; null when the product is gone. */
    private static Long[] readPriceAndCost(Connection connection, int productId) throws SQLException {
        try (PreparedStatement read = connection.prepareStatement("SELECT price, cost FROM products WHERE id = ?")) {
            read.setInt(1, productId);
            try (ResultSet rs = read.executeQuery()) {
                if (!rs.next()) return null;
                long cost = rs.getLong(2);
                return new Long[]{rs.getLong(1), rs.wasNull() ? null : cost};
            }
        }
    }

    /**
     * The audit lines of one product edit (TODO 4.2), on the saving transaction: a changed price, a changed cost, and a stock number
     * that was typed over. A save that changes none of these writes nothing.
     */
    private static void auditEdit(Connection connection, Product product, Long[] old, boolean typedStock, int stockBefore) throws SQLException {
        if (old == null) return;
        Long newPrice = com.coffeeshop.coffeeshopmanagement.util.Money.toDong(product.getPrice());
        Long newCost = com.coffeeshop.coffeeshopmanagement.util.Money.toDongOrNull(product.getCost());
        String name = product.getName();
        Integer id = product.getId();
        if (!java.util.Objects.equals(old[0], newPrice)) {
            AuditDAO.write(connection, AuditAction.PRODUCT_PRICE, AuditTarget.PRODUCT, id, name, String.valueOf(old[0]), String.valueOf(newPrice), null);
        }
        if (!java.util.Objects.equals(old[1], newCost)) {
            AuditDAO.write(connection, AuditAction.PRODUCT_COST, AuditTarget.PRODUCT, id, name,
                    old[1] == null ? null : String.valueOf(old[1]), newCost == null ? null : String.valueOf(newCost), null);
        }
        if (typedStock && stockBefore != product.getStock()) {
            AuditDAO.write(connection, AuditAction.PRODUCT_STOCK, AuditTarget.PRODUCT, id, name,
                    String.valueOf(stockBefore), String.valueOf(product.getStock()), null);
        }
    }

    /** The stock the database holds right now (0 when the product is gone), read on the saving connection. */
    private static int currentStock(Connection connection, int productId) throws SQLException {
        try (PreparedStatement read = connection.prepareStatement("SELECT stock FROM products WHERE id = ?")) {
            read.setInt(1, productId);
            try (ResultSet rs = read.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Hard delete - callers must first confirm no order_items reference this product. */
    public void delete(int id) {
        Permissions.requireAdmin("delete a product");
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM products WHERE id = ?")) {
            statement.setInt(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete product", e);
        }
    }

    public void setActive(int id, boolean active) {
        Permissions.requireAdmin("stop or resume selling a product");
        String sql = "UPDATE products SET active = ? WHERE id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, active);
            statement.setInt(2, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to change product status", e);
        }
    }

    public List<Product> findAll() {
        List<Product> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(SELECT_WITH_CATEGORY + " ORDER BY p.sort_order, p.id")) { // the shop's own order, else creation order (TODO 5.31)
            while (rs.next()) {
                result.add(map(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load products", e);
        }
        return result;
    }

    /** Blocks hard-deleting a product that historical orders still reference. */
    public int countOrderReferences(int productId) {
        String sql = "SELECT COUNT(*) FROM order_items WHERE product_id = ?";
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, productId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check product order history", e);
        }
    }

    /** The reorder level of a product that has none of its own (TODO 4.19); see {@code StockLevel}. */
    public static final int LOW_STOCK_THRESHOLD = com.coffeeshop.coffeeshopmanagement.service.StockLevel.DEFAULT;

    /** Active products at or below THEIR reorder level ({@code threshold} for those with none), lowest stock first. */
    public List<Product> findLowStock(int threshold, int limit) {
        List<Product> result = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     SELECT_WITH_CATEGORY + " WHERE p.active = 1 AND p.stock <= COALESCE(p.reorder_level, ?)" + NO_RECIPE + " ORDER BY p.stock ASC, " + DatabaseConfig.dialect().vietnameseOrder("p.name") + " LIMIT ?")) {
            statement.setInt(1, threshold);
            statement.setInt(2, limit);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to load the products to reorder", e);
        }
        return result;
    }

    public int countLowStock(int threshold) {
        String sql = "SELECT COUNT(*) FROM products p WHERE p.active = 1 AND p.stock <= COALESCE(p.reorder_level, ?)" + NO_RECIPE;
        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, threshold);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count low-stock products", e);
        }
    }

    /** One more than the largest number among the products of that category (none: no category), so a new product goes last. */
    private static int nextSortOrder(Connection connection, Integer categoryId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(sort_order), 0) + 1 FROM products WHERE "
                + (categoryId == null ? "category_id IS NULL" : "category_id = ?");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (categoryId != null) {
                statement.setInt(1, categoryId);
            }
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Moves a product one place up ({@code delta < 0}) or down ({@code delta > 0}) among the products of its category that are on
     * sale (or among those that are not: they are a group of their own, so an arrow always changes what the sales screen shows),
     * TODO 5.31. Every product is numbered again 1, 2, 3... in one transaction, only the two that swap change place. Returns false
     * when it is already first or last.
     */
    public boolean move(int productId, int delta) {
        Permissions.requireAdmin("change the order of the products");
        List<Product> all = findAll();
        List<Integer> before = new ArrayList<>();
        for (Product product : all) before.add(product.getId());
        List<Integer> after = com.coffeeshop.coffeeshopmanagement.service.DisplayOrder.moved(all, Product::getId,
                product -> java.util.List.of(product.getCategoryId() == null ? -1 : product.getCategoryId(), product.isActive()),
                productId, delta);
        if (after.equals(before)) {
            return false;
        }
        saveOrder(after);
        return true;
    }

    /** Numbers the products 1, 2, 3... in the order given. */
    void saveOrder(List<Integer> idsInOrder) {
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("UPDATE products SET sort_order = ? WHERE id = ?")) {
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
            throw new DataAccessException("Failed to change the order of the products", e);
        }
    }

    public int countAll() {
        try (Connection connection = DatabaseConfig.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM products")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count products", e);
        }
    }

    private void bind(PreparedStatement statement, Product product) throws SQLException {
        // The last line of defence behind the form and the CSV import (TODO 3.59).
        if (com.coffeeshop.coffeeshopmanagement.service.Limits.priceTooBig(product.getPrice())
                || com.coffeeshop.coffeeshopmanagement.service.Limits.priceTooBig(product.getCost())) {
            throw new DataAccessException(com.coffeeshop.coffeeshopmanagement.util.I18n.t("products.val.price.max",
                    com.coffeeshop.coffeeshopmanagement.service.Limits.maxPriceText()));
        }
        statement.setString(1, com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.nfc(product.getName()));
        if (product.getCategoryId() != null) {
            statement.setInt(2, product.getCategoryId());
        } else {
            statement.setNull(2, Types.INTEGER);
        }
        statement.setLong(3, com.coffeeshop.coffeeshopmanagement.util.Money.toDong(product.getPrice()));
        Long costDong = com.coffeeshop.coffeeshopmanagement.util.Money.toDongOrNull(product.getCost());
        if (costDong != null) {
            statement.setLong(4, costDong);
        } else {
            statement.setNull(4, Types.INTEGER);
        }
        statement.setInt(5, product.getStock());
        statement.setString(6, com.coffeeshop.coffeeshopmanagement.util.TextNormalizer.nfc(product.getDescription())); // composed accents (TODO 3.18)
        statement.setString(7, product.getImagePath());
        statement.setBoolean(8, product.isActive());
        if (product.getReorderLevel() != null) {
            statement.setInt(9, product.getReorderLevel());
        } else {
            statement.setNull(9, Types.INTEGER);
        }
        // The last line of defence behind the form and the CSV import (TODO 4.42): stored in capitals, or null.
        String code = com.coffeeshop.coffeeshopmanagement.service.ProductCode.clean(product.getCode());
        if (code != null && com.coffeeshop.coffeeshopmanagement.service.ProductCode.problem(code) != null) {
            throw new DataAccessException(com.coffeeshop.coffeeshopmanagement.util.I18n.t(
                    com.coffeeshop.coffeeshopmanagement.service.ProductCode.problem(code),
                    com.coffeeshop.coffeeshopmanagement.service.ProductCode.MAX_LENGTH));
        }
        if (code != null) {
            statement.setString(10, code);
        } else {
            statement.setNull(10, Types.VARCHAR);
        }
        product.setCode(code);
    }

    private Product map(ResultSet rs) throws SQLException {
        Product product = new Product();
        product.setId(rs.getInt("id"));
        product.setName(rs.getString("name"));
        int categoryId = rs.getInt("category_id");
        product.setCategoryId(rs.wasNull() ? null : categoryId);
        product.setCategoryName(rs.getString("category_name"));
        product.setPrice(com.coffeeshop.coffeeshopmanagement.util.Money.fromDong(rs.getLong("price")));
        long costDong = rs.getLong("cost");
        product.setCost(com.coffeeshop.coffeeshopmanagement.util.Money.fromDongOrNull(costDong, rs.wasNull()));
        double servings = rs.getDouble("recipe_servings");
        boolean hasRecipe = !rs.wasNull();
        product.setStock(hasRecipe ? (int) Math.min(Math.floor(servings + 1e-9), Integer.MAX_VALUE) : rs.getInt("stock"));
        product.setRecipeBased(hasRecipe);
        product.setLoadedStock(product.getStock());
        product.setDescription(rs.getString("description"));
        product.setImagePath(rs.getString("image_path"));
        product.setActive(rs.getBoolean("active"));
        product.setSortOrder(rs.getInt("sort_order"));
        int reorder = rs.getInt("reorder_level");
        product.setReorderLevel(rs.wasNull() ? null : reorder);
        product.setCode(rs.getString("code"));
        return product;
    }
}
