package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Ingredient;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 6.84: {@link StockTaker} itself, on a connection of its own, without going through a payment (the payment path is in
 * {@code IngredientTest}). First case, item 2.96: a refund gives back what the sale took, not what today's recipe says.
 *
 * <p>Each test builds its own order, lines, products and ingredients with unique names, so the tests do not disturb each other or the
 * counts of other tests.
 */
public class StockTakerTest {

    private final IngredientDAO ingredients = new IngredientDAO();
    private final ProductDAO products = new ProductDAO();
    private Category category;
    private Ingredient beans;
    private Ingredient milk;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @AfterClass
    public static void signOut() {
        TestSession.clear();
    }

    @Before
    public void fresh() {
        TestSession.admin();
        category = new Category();
        category.setName("test-taker-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        beans = ingredients.insert("test-taker-beans-" + UUID.randomUUID(), "g", 1000, 0);
        milk = ingredients.insert("test-taker-milk-" + UUID.randomUUID(), "ml", 1000, 0);
    }

    // ------------------------------------------------------------------ helpers

    private Product product(int stock, boolean active) {
        Product p = new Product();
        p.setName("test-taker-" + UUID.randomUUID());
        p.setCategoryId(category.getId());
        p.setPrice(new BigDecimal("30000"));
        p.setStock(stock);
        p.setActive(active);
        return products.insert(p);
    }

    private Product recipeProduct(long beansPerCup, long milkPerCup) {
        Product p = product(0, true);
        ingredients.saveRecipe(p.getId(), List.of(new long[]{beans.id(), beansPerCup}, new long[]{milk.id(), milkPerCup}));
        return p;
    }

    private static OrderItem line(Product p, int quantity, Integer sizePercent) {
        OrderItem item = new OrderItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(quantity);
        item.setUnitPrice(p.getPrice());
        item.setLineTotal(p.getPrice().multiply(BigDecimal.valueOf(quantity)));
        item.setSizePercent(sizePercent);
        return item;
    }

    private static int newOrder(Connection c) throws SQLException {
        try (PreparedStatement st = c.prepareStatement("INSERT INTO orders (order_date, status, subtotal, discount, total) VALUES ('3000-01-01 10:00:00', 'PAID', 0, 0, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            st.executeUpdate();
            try (ResultSet keys = st.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** Saves the lines of an order the way a payment does, one after the other, and returns the ids they got. */
    private static List<Integer> saveLines(Connection c, int orderId, List<OrderItem> items) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement st = c.prepareStatement(
                "INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, line_total, size_percent) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            for (OrderItem item : items) {
                st.setInt(1, orderId);
                st.setInt(2, item.getProductId());
                st.setString(3, item.getProductName());
                st.setInt(4, item.getQuantity());
                st.setBigDecimal(5, item.getUnitPrice());
                st.setBigDecimal(6, item.getLineTotal());
                if (item.getSizePercent() == null) st.setNull(7, java.sql.Types.INTEGER); else st.setInt(7, item.getSizePercent());
                st.executeUpdate();
                try (ResultSet keys = st.getGeneratedKeys()) {
                    keys.next();
                    ids.add(keys.getInt(1));
                }
            }
        }
        return ids;
    }

    private Connection open() throws SQLException {
        Connection c = DatabaseConfig.getConnection();
        c.setAutoCommit(false);
        return c;
    }

    /** Pays: saves the order and its lines, takes the stock, commits. */
    private List<Integer> take(List<OrderItem> items) throws SQLException {
        try (Connection c = open()) {
            int order = newOrder(c);
            List<Integer> ids = saveLines(c, order, items);
            c.commit(); // the lines stay whatever the stock take says
            StockTaker.take(c, order, items);
            c.commit();
            return ids;
        }
    }

    private List<long[]> takes(int itemId) throws SQLException {
        List<long[]> rows = new ArrayList<>();
        try (Connection c = DatabaseConfig.getConnection(); PreparedStatement st = c.prepareStatement(
                "SELECT ingredient_id, amount FROM stock_takes WHERE order_item_id = ? ORDER BY ingredient_id")) {
            st.setInt(1, itemId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    int ingredient = rs.getInt(1);
                    rows.add(new long[]{rs.wasNull() ? -1 : ingredient, rs.getLong(2)});
                }
            }
        }
        return rows;
    }

    private long stock(Ingredient i) {
        return ingredients.findAll().stream().filter(x -> x.id() == i.id()).findFirst().orElseThrow().stock();
    }

    /** The cup count as it is stored. {@code ProductDAO.findById} shows a product with a recipe as the servings its ingredients allow, which is not what these tests look at. */
    private int cups(Product p) {
        try (Connection c = open(); java.sql.PreparedStatement read = c.prepareStatement("SELECT stock FROM products WHERE id = ?")) {
            read.setInt(1, p.getId());
            try (java.sql.ResultSet rs = read.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void putBack(int itemId, int productId, int quantity) throws SQLException {
        try (Connection c = open()) {
            StockTaker.putBack(c, itemId, productId, quantity);
            c.commit();
        }
    }

    // ------------------------------------------------------------------ take

    @Test
    public void aProductWithoutARecipeTakesCupsAndRecordsOneRowWithNoIngredient() throws SQLException {
        Product water = product(10, true);
        List<Integer> ids = take(List.of(line(water, 3, null)));
        assertEquals(7, cups(water));
        List<long[]> rows = takes(ids.get(0));
        assertEquals(1, rows.size());
        assertEquals("no ingredient: a cup", -1, rows.get(0)[0]);
        assertEquals("one cup per piece", 1, rows.get(0)[1]);
    }

    @Test
    public void aProductWithARecipeTakesItsIngredientsPerPieceAndNeverTouchesItsCupCount() throws SQLException {
        Product latte = recipeProduct(18, 150);
        List<Integer> ids = take(List.of(line(latte, 2, null)));
        assertEquals(1000 - 36, stock(beans));
        assertEquals(1000 - 300, stock(milk));
        assertEquals("a recipe product does not count cups", 0, cups(latte));
        List<long[]> rows = takes(ids.get(0));
        assertEquals(2, rows.size());
        for (long[] row : rows) {
            assertEquals("per piece, not per line", row[0] == beans.id() ? 18 : 150, row[1]);
        }
    }

    @Test
    public void aLargerSizeTakesMoreOfEveryIngredientAndRecordsWhatOnePieceTook() throws SQLException {
        Product latte = recipeProduct(18, 150);
        List<Integer> ids = take(List.of(line(latte, 2, 130)));
        assertEquals("18 x 130% = 23 (rounded), two pieces", 1000 - 46, stock(beans));
        assertEquals("150 x 130% = 195, two pieces", 1000 - 390, stock(milk));
        for (long[] row : takes(ids.get(0))) {
            assertEquals(row[0] == beans.id() ? 23 : 195, row[1]);
        }
    }

    @Test
    public void aLineWithoutAProductTakesNothing() throws SQLException {
        Product water = product(5, true);
        OrderItem free = line(water, 1, null);
        free.setProductId(null);
        try (Connection c = open()) {
            int order = newOrder(c);
            OrderItem saved = line(water, 1, null);
            saveLines(c, order, List.of(saved, saved));
            c.commit();
            StockTaker.take(c, order, List.of(free, line(water, 2, null)));
            c.commit();
        }
        assertEquals("only the second line took", 3, cups(water));
    }

    @Test
    public void aShortageRollsBackEveryLineOfTheOrderAndNamesTheIngredient() throws SQLException {
        Product water = product(10, true);
        Product latte = recipeProduct(18, 600);
        List<OrderItem> items = List.of(line(water, 2, null), line(latte, 2, null)); // 1200 ml of milk, only 1000
        try (Connection c = open()) {
            int order = newOrder(c);
            saveLines(c, order, items);
            c.commit();
            try {
                StockTaker.take(c, order, items);
                fail("not enough milk");
            } catch (DataAccessException expected) {
                assertEquals(I18n.t("order.item.noingredient", latte.getName(), milk.name()), expected.getMessage());
            }
        }
        assertEquals("the water of the first line is back", 10, cups(water));
        assertEquals(1000, stock(beans));
        assertEquals(1000, stock(milk));
    }

    @Test
    public void notEnoughCupsRefusesTheOrderWithTheProductsName() throws SQLException {
        Product water = product(1, true);
        List<OrderItem> items = List.of(line(water, 2, null));
        try (Connection c = open()) {
            int order = newOrder(c);
            saveLines(c, order, items);
            c.commit();
            try {
                StockTaker.take(c, order, items);
                fail("only one cup");
            } catch (DataAccessException expected) {
                assertEquals(I18n.t("order.item.nostock", water.getName()), expected.getMessage());
            }
        }
        assertEquals(1, cups(water));
    }

    @Test
    public void aProductThatIsNoLongerSoldIsRefusedWhetherOrNotItHasARecipe() throws SQLException {
        Product stopped = product(10, false);
        Product stoppedLatte = recipeProduct(18, 150);
        products.setActive(stoppedLatte.getId(), false);
        for (Product p : List.of(stopped, stoppedLatte)) {
            List<OrderItem> items = List.of(line(p, 1, null));
            try (Connection c = open()) {
                int order = newOrder(c);
                saveLines(c, order, items);
                c.commit();
                try {
                    StockTaker.take(c, order, items);
                    fail("stopped: " + p.getName());
                } catch (DataAccessException expected) {
                    assertEquals(I18n.t("order.item.stopped", p.getName()), expected.getMessage());
                }
            }
        }
        assertEquals(10, cups(stopped));
        assertEquals(1000, stock(beans));
    }

    @Test
    public void whenTheSavedLinesCannotBeToldApartNothingIsRecordedButTheStockIsStillTaken() throws SQLException {
        Product water = product(10, true);
        List<OrderItem> items = List.of(line(water, 1, null), line(water, 1, null));
        int itemId;
        try (Connection c = open()) {
            int order = newOrder(c);
            itemId = saveLines(c, order, List.of(items.get(0))).get(0); // one row saved, two lines given
            c.commit();
            StockTaker.take(c, order, items);
            c.commit();
        }
        assertEquals(8, cups(water));
        assertTrue("no row: the refund will use the recipe of the day", takes(itemId).isEmpty());
    }

    // ------------------------------------------------------------------ putBack

    @Test
    public void aRefundGivesBackWhatTheSaleTookEvenAfterTheRecipeWasChanged() throws SQLException {
        Product latte = recipeProduct(18, 150);
        List<Integer> ids = take(List.of(line(latte, 3, null)));
        assertEquals(1000 - 54, stock(beans));
        ingredients.saveRecipe(latte.getId(), List.of(new long[]{beans.id(), 30})); // later: more beans, no milk
        putBack(ids.get(0), latte.getId(), 2);
        assertEquals("2 pieces x the 18 g the sale took, not x 30", 1000 - 54 + 36, stock(beans));
        assertEquals("milk was taken, so milk comes back although the recipe lost it", 1000 - 450 + 300, stock(milk));
        putBack(ids.get(0), latte.getId(), 1);
        assertEquals("the rest: everything is back", 1000, stock(beans));
        assertEquals(1000, stock(milk));
    }

    @Test
    public void aCupSoldBeforeTheDrinkGotARecipeComesBackAsACupNotAsIngredients() throws SQLException {
        Product drink = product(10, true);
        List<Integer> ids = take(List.of(line(drink, 2, null)));
        ingredients.saveRecipe(drink.getId(), List.of(new long[]{beans.id(), 18}));
        putBack(ids.get(0), drink.getId(), 2);
        assertEquals(10, cups(drink));
        assertEquals("no beans appear from nowhere", 1000, stock(beans));
    }

    @Test
    public void aLineWithNoRecordIsPutBackByTheRecipeItsProductHasNow() throws SQLException {
        Product latte = recipeProduct(18, 150);
        Product water = product(5, true);
        int latteLine;
        int waterLine;
        try (Connection c = open()) { // paid "before the table existed": lines saved, nothing taken, nothing recorded
            int order = newOrder(c);
            List<Integer> ids = saveLines(c, order, List.of(line(latte, 2, 130), line(water, 2, null)));
            latteLine = ids.get(0);
            waterLine = ids.get(1);
            c.commit();
        }
        putBack(latteLine, latte.getId(), 2);
        assertEquals("the size of that line counts: 18 x 130% = 23, two pieces", 1000 + 46, stock(beans));
        assertEquals(1000 + 390, stock(milk));
        putBack(waterLine, water.getId(), 2);
        assertEquals("a product without a recipe gets its cups back", 7, cups(water));
    }

    // ------------------------------------------------------------------ sizePercentOf

    @Test
    public void theSizeOfALineIsReadBackAsNullForNoSizeAndForALineThatIsNotThere() throws SQLException {
        Product latte = recipeProduct(18, 150);
        try (Connection c = open()) {
            int order = newOrder(c);
            List<Integer> ids = saveLines(c, order, List.of(line(latte, 1, null), line(latte, 1, 130), line(latte, 1, 70)));
            c.commit();
            assertNull("no size", StockTaker.sizePercentOf(c, ids.get(0)));
            assertEquals(Integer.valueOf(130), StockTaker.sizePercentOf(c, ids.get(1)));
            assertEquals(Integer.valueOf(70), StockTaker.sizePercentOf(c, ids.get(2)));
            assertNull("a line that does not exist", StockTaker.sizePercentOf(c, 2_000_000_000));
        }
    }
}
