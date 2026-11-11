package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.service.SizeScale;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Takes stock off for a paid order and puts it back for a refund, on the caller's connection and transaction (TODO 4.41, rule D12).
 * A product with a recipe takes its ingredients off ({@code amount x quantity}, each guarded so none goes below zero); a product
 * without one keeps the cup count in {@code products.stock}, exactly as before. Which of the two applies is decided per product by
 * whether it has recipe lines, so a shop can move its drinks over one at a time.
 *
 * <p>What each line took is written down per piece in {@code stock_takes} (TODO 2.96): one row per ingredient, or one row with no
 * ingredient for a cup. A refund or a cancelled paid order puts back exactly that, so changing the recipe afterwards cannot make the
 * shelf wrong. A line paid before the table existed has no rows; it falls back to the recipe the product has now, as it always did.
 */
final class StockTaker {

    private record Part(int ingredientId, String name, long amount) {
    }

    private StockTaker() {
    }

    /**
     * Takes the stock of every line of {@code orderId} and records what each took; on a shortage the connection is rolled back and a
     * {@link DataAccessException} says which item. {@code items} are the lines just saved for the order, in the order they were saved.
     */
    static void take(Connection connection, int orderId, List<OrderItem> items) throws SQLException {
        List<Integer> itemIds = itemIds(connection, orderId);
        try (PreparedStatement note = connection.prepareStatement(
                "INSERT INTO stock_takes (order_item_id, ingredient_id, amount) VALUES (?, ?, ?)");
             PreparedStatement cups = connection.prepareStatement(
                "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ? AND active = 1");
             PreparedStatement parts = connection.prepareStatement(
                     "UPDATE ingredients SET stock = stock - ? WHERE id = ? AND stock >= ?")) {
            int index = -1;
            for (OrderItem item : items) {
                index++;
                if (item.getProductId() == null) continue;
                Integer itemId = index < itemIds.size() && itemIds.size() == items.size() ? itemIds.get(index) : null;
                List<Part> recipe = recipeOf(connection, item.getProductId());
                if (recipe.isEmpty()) {
                    cups.setInt(1, item.getQuantity());
                    cups.setInt(2, item.getProductId());
                    cups.setInt(3, item.getQuantity());
                    if (cups.executeUpdate() == 0) refuse(connection, item, null);
                    record(note, itemId, null, 1);
                    continue;
                }
                if (isOff(connection, item.getProductId())) refuse(connection, item, null);
                for (Part part : recipe) {
                    long need = SizeScale.scale(part.amount(), item.getSizePercent()) * item.getQuantity(); // TODO 4.47: a Large takes more
                    parts.setLong(1, need);
                    parts.setInt(2, part.ingredientId());
                    parts.setLong(3, need);
                    if (parts.executeUpdate() == 0) refuse(connection, item, part.name());
                    record(note, itemId, part.ingredientId(), SizeScale.scale(part.amount(), item.getSizePercent())); // what one piece of this size took (TODO 2.96 with 4.47)
                }
            }
        }
    }

    private static void record(PreparedStatement note, Integer itemId, Integer ingredientId, long perPiece) throws SQLException {
        if (itemId == null) return; // the line cannot be told apart: nothing is recorded and a refund uses the recipe it has then
        note.setInt(1, itemId);
        if (ingredientId == null) note.setNull(2, java.sql.Types.INTEGER); else note.setInt(2, ingredientId);
        note.setLong(3, perPiece);
        note.executeUpdate();
    }

    /** The ids of the order's lines in the order they were saved (the lines of one order are saved in one batch, so ids grow with it). */
    private static List<Integer> itemIds(Connection connection, int orderId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement st = connection.prepareStatement("SELECT id FROM order_items WHERE order_id = ? ORDER BY id")) {
            st.setInt(1, orderId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    /**
     * Puts {@code quantity} pieces of a line back: what that line took when it was paid (TODO 2.96). A line with no record (paid before
     * the record existed) puts back by the recipe its product has now, or its cup count.
     */
    static void putBack(Connection connection, int itemId, int productId, int quantity) throws SQLException {
        List<long[]> taken = new ArrayList<>(); // {ingredient id or -1 for a cup, amount per piece}
        try (PreparedStatement st = connection.prepareStatement("SELECT ingredient_id, amount FROM stock_takes WHERE order_item_id = ?")) {
            st.setInt(1, itemId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    int ingredient = rs.getInt(1);
                    taken.add(new long[]{rs.wasNull() ? -1 : ingredient, rs.getLong(2)});
                }
            }
        }
        if (taken.isEmpty()) {
            putBackByCurrentRecipe(connection, productId, quantity, sizePercentOf(connection, itemId)); // the size of that line (TODO 4.47)
            return;
        }
        try (PreparedStatement cups = connection.prepareStatement("UPDATE products SET stock = stock + ? WHERE id = ?");
             PreparedStatement parts = connection.prepareStatement("UPDATE ingredients SET stock = stock + ? WHERE id = ?")) {
            for (long[] one : taken) {
                if (one[0] < 0) {
                    cups.setLong(1, one[1] * quantity);
                    cups.setInt(2, productId);
                    cups.executeUpdate();
                } else {
                    parts.setLong(1, one[1] * quantity);
                    parts.setInt(2, (int) one[0]);
                    parts.executeUpdate();
                }
            }
        }
    }

    private static void putBackByCurrentRecipe(Connection connection, int productId, int quantity, Integer sizePercent) throws SQLException {
        List<Part> recipe = recipeOf(connection, productId);
        if (recipe.isEmpty()) {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE products SET stock = stock + ? WHERE id = ?")) {
                statement.setInt(1, quantity);
                statement.setInt(2, productId);
                statement.executeUpdate();
            }
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE ingredients SET stock = stock + ? WHERE id = ?")) {
            for (Part part : recipe) {
                statement.setLong(1, SizeScale.scale(part.amount(), sizePercent) * quantity);
                statement.setInt(2, part.ingredientId());
                statement.executeUpdate();
            }
        }
    }

    /** The size percent of one order line, null for a line with no size; for a refund of that line. */
    static Integer sizePercentOf(Connection connection, int orderItemId) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement("SELECT size_percent FROM order_items WHERE id = ?")) {
            st.setInt(1, orderItemId);
            try (ResultSet rs = st.executeQuery()) {
                if (!rs.next()) return null;
                int percent = rs.getInt(1);
                return rs.wasNull() ? null : percent;
            }
        }
    }

    private static void refuse(Connection connection, OrderItem item, String ingredient) throws SQLException {
        boolean off = isOff(connection, item.getProductId());
        connection.rollback();
        if (off) throw new DataAccessException(I18n.t("order.item.stopped", item.getProductName()));
        if (ingredient != null) throw new DataAccessException(I18n.t("order.item.noingredient", item.getProductName(), ingredient));
        throw new DataAccessException(I18n.t("order.item.nostock", item.getProductName()));
    }

    private static boolean isOff(Connection connection, int productId) throws SQLException {
        try (PreparedStatement st = connection.prepareStatement("SELECT active FROM products WHERE id = ?")) {
            st.setInt(1, productId);
            try (ResultSet rs = st.executeQuery()) {
                return rs.next() && rs.getInt(1) == 0;
            }
        }
    }

    private static List<Part> recipeOf(Connection connection, int productId) throws SQLException {
        List<Part> result = new ArrayList<>();
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT r.ingredient_id, i.name, r.amount FROM recipes r JOIN ingredients i ON i.id = r.ingredient_id WHERE r.product_id = ?")) {
            st.setInt(1, productId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) result.add(new Part(rs.getInt(1), rs.getString(2), rs.getLong(3)));
            }
        }
        return result;
    }
}
