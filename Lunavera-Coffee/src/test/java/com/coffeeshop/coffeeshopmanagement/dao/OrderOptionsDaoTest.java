package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Ingredient;
import com.coffeeshop.coffeeshopmanagement.model.LineChoice;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.service.Cart;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 4.47: a size and extras on an order line are saved, read back, kept when the order is saved again and paid, change what the line
 * costs and how much of the recipe it takes, come back on a refund, and show in the report by size and extra.
 */
public class OrderOptionsDaoTest {

    private final OrderDAO orderDAO = new OrderDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final IngredientDAO ingredientDAO = new IngredientDAO();
    private Product latte;
    private Ingredient beans;
    private String sizeL;
    private String extraShot;

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
    public void makeProduct() {
        TestSession.admin();
        Category category = new Category();
        category.setName("test-optorder-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
        latte = new Product();
        latte.setName("test-optorder-" + UUID.randomUUID());
        latte.setCategoryId(category.getId());
        latte.setPrice(new BigDecimal("40000"));
        latte.setCost(new BigDecimal("10000"));
        latte.setStock(100);
        latte.setActive(true);
        productDAO.insert(latte);
        beans = ingredientDAO.insert("test-optbeans-" + UUID.randomUUID(), "g", 200, 0);
        sizeL = "L" + UUID.randomUUID().toString().substring(0, 6);
        extraShot = "Shot" + UUID.randomUUID().toString().substring(0, 6);
    }

    /** A cart line of the latte with a Large (+10.000, costs 4.000 more, 130% of the recipe) and optionally an extra shot (+10.000). */
    private OrderItem line(int quantity, boolean large, boolean shot) {
        List<OrderItem> lines = new ArrayList<>();
        Cart cart = new Cart(lines);
        LineChoice choice = new LineChoice(large ? sizeL : null, large ? 130 : null,
                shot ? List.of(new LineChoice.Extra(extraShot, new BigDecimal("10000"))) : List.of(),
                new BigDecimal(large ? 10000 : 0).add(new BigDecimal(shot ? 10000 : 0)), new BigDecimal(large ? 4000 : 0));
        cart.add(latte, choice);
        cart.setQuantity(lines.get(0), quantity);
        return lines.get(0);
    }

    private Order order(OrderStatus status, List<OrderItem> items) {
        BigDecimal total = items.stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setStatus(status);
        order.setSubtotal(total);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(total);
        if (status == OrderStatus.PAID) {
            order.setPaymentMethod(PaymentMethod.CASH);
            order.setPaidAt(AppClock.now());
        }
        return order;
    }

    private long unitCostOf(int itemId) throws Exception {
        try (Connection c = DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT unit_cost FROM order_items WHERE id = ?")) {
            st.setInt(1, itemId);
            try (ResultSet rs = st.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    private long beansLeft() {
        return ingredientDAO.findAll().stream().filter(i -> i.id() == beans.id()).findFirst().orElseThrow().stock();
    }

    @Test
    public void theSizeAndExtrasAreSavedAndReadBackOnTheLine() {
        OrderItem sized = line(2, true, true);
        Order saved = orderDAO.insert(order(OrderStatus.PAID, List.of(sized, line(1, false, false))), List.of(sized, line(1, false, false)));
        List<OrderItem> read = orderDAO.findItemsByOrderId(saved.getId());
        assertEquals(2, read.size());
        OrderItem back = read.get(0);
        assertEquals(sizeL, back.getSizeName());
        assertEquals(Integer.valueOf(130), back.getSizePercent());
        assertEquals(extraShot + "=10000", back.getExtras());
        assertEquals(0, new BigDecimal("60000").compareTo(back.getUnitPrice()));
        assertEquals(0, new BigDecimal("120000").compareTo(back.getLineTotal()));
        assertNull("a plain line stays plain", read.get(1).getSizeName());
        assertNull(read.get(1).getExtras());
        assertNull(read.get(1).getSizePercent());
    }

    @Test
    public void anOpenOrderKeepsItsOptionsWhenSavedAgainAndAChangedSizeIsAChangedLine() {
        OrderItem a = line(1, true, false);
        OrderItem b = line(1, false, false);
        Order saved = orderDAO.insert(order(OrderStatus.OPEN, List.of(a, b)), List.of(a, b));
        int plainRow = orderDAO.findItemsByOrderId(saved.getId()).get(1).getId();

        assertTrue(orderDAO.updateIfStillOpen(saved, List.of(line(1, true, false), line(1, false, false))));
        List<OrderItem> same = orderDAO.findItemsByOrderId(saved.getId());
        assertEquals(sizeL, same.get(0).getSizeName());
        assertEquals("an unchanged line keeps its row", plainRow, same.get(1).getId());

        assertTrue(orderDAO.updateIfStillOpen(saved, List.of(line(1, true, true), line(1, false, false))));
        List<OrderItem> changed = orderDAO.findItemsByOrderId(saved.getId());
        // another extra is another line (OrderItemDiff, TODO 4.47): the old line is deleted and the new one is written after the lines that stayed,
        // so it is looked for by its extra and not by its place
        assertEquals(2, changed.size());
        OrderItem withShot = changed.stream().filter(i -> i.getExtras() != null).findFirst()
                .orElseThrow(() -> new AssertionError("no line carries the extra"));
        assertEquals("the extra was added", extraShot + "=10000", withShot.getExtras());
        assertEquals(sizeL, withShot.getSizeName());
        assertEquals("40.000 + Large 10.000 + shot 10.000", 0, new BigDecimal("60000").compareTo(withShot.getUnitPrice()));
        assertTrue("the plain line kept its row", changed.stream().anyMatch(i -> i.getId() == plainRow));
    }

    @Test
    public void payingAnOpenOrderKeepsTheOptions() {
        OrderItem a = line(1, true, true);
        Order saved = orderDAO.insert(order(OrderStatus.OPEN, List.of(a)), List.of(a));
        orderDAO.payExistingOrder(saved.getId(), null, PaymentMethod.CASH, BigDecimal.ZERO, null,
                new BigDecimal("60000"), List.of(line(1, true, true)), null);
        OrderItem back = orderDAO.findItemsByOrderId(saved.getId()).get(0);
        assertEquals(sizeL, back.getSizeName());
        assertEquals(extraShot + "=10000", back.getExtras());
    }

    @Test
    public void theCostOfASizedLineIsTheScaledProductCostPlusTheOptionCost() throws Exception {
        OrderItem sized = line(1, true, false);
        OrderItem plain = line(1, false, false);
        Order saved = orderDAO.insert(order(OrderStatus.PAID, List.of(sized, plain)), List.of(sized, plain));
        List<OrderItem> read = orderDAO.findItemsByOrderId(saved.getId());
        assertEquals("10.000 x 130% + 4.000", 17000, unitCostOf(read.get(0).getId()));
        assertEquals("a plain line costs the product cost", 10000, unitCostOf(read.get(1).getId()));
    }

    @Test
    public void aSizeTakesItsShareOfTheRecipeAndARefundBringsThatBack() {
        ingredientDAO.saveRecipe(latte.getId(), List.of(new long[]{beans.id(), 18}));
        OrderItem sized = line(2, true, false);   // 18 x 130% = 23 per piece
        Order saved = orderDAO.insert(order(OrderStatus.PAID, List.of(sized)), List.of(sized));
        assertEquals(200 - 46, beansLeft());
        int itemId = orderDAO.findItemsByOrderId(saved.getId()).get(0).getId();
        new RefundDAO().refund(saved.getId(), Map.of(itemId, 1), true, PaymentMethod.CASH, null);
        assertEquals("one piece of that size comes back", 200 - 23, beansLeft());
    }

    @Test
    public void cancellingAPaidOrderBringsTheSizedAmountBack() {
        ingredientDAO.saveRecipe(latte.getId(), List.of(new long[]{beans.id(), 18}));
        OrderItem normal = line(1, false, false);
        OrderItem sized = line(1, true, false);
        Order saved = orderDAO.insert(order(OrderStatus.PAID, List.of(normal, sized)), List.of(normal, sized));
        assertEquals(200 - 18 - 23, beansLeft());
        orderDAO.cancelPaidOrder(saved.getId(), true);
        assertEquals(200, beansLeft());
    }

    @Test
    public void theRefundScreenCanTellTheSizesApart() {
        OrderItem sized = line(1, true, true);
        Order saved = orderDAO.insert(order(OrderStatus.PAID, List.of(sized)), List.of(sized));
        String name = new RefundDAO().load(saved.getId()).lines().get(0).productName();
        assertTrue(name, name.endsWith("(" + sizeL + ", +" + extraShot + ")"));
    }

    @Test
    public void theReportCountsSizesAndExtrasAndTakesRefundsOff() {
        OrderItem sized = line(2, true, true);     // 2 x 60.000
        OrderItem other = line(1, true, false);    // 1 x 50.000
        Order saved = orderDAO.insert(order(OrderStatus.PAID, List.of(sized, other)), List.of(sized, other));
        int firstItem = orderDAO.findItemsByOrderId(saved.getId()).get(0).getId();
        new RefundDAO().refund(saved.getId(), Map.of(firstItem, 1), false, PaymentMethod.CASH, null);

        List<ReportDAO.OptionSales> rows = new ReportDAO().optionSales(AppClock.today().minusDays(1), AppClock.today().plusDays(1));
        ReportDAO.OptionSales size = rows.stream().filter(r -> r.name().equals(sizeL)).findFirst().orElseThrow();
        assertEquals(com.coffeeshop.coffeeshopmanagement.model.OptionKind.SIZE, size.kind());
        assertEquals("3 sold, 1 refunded", 2, size.quantity());
        assertEquals(0, new BigDecimal("110000").compareTo(size.revenue())); // 60.000 + 50.000
        ReportDAO.OptionSales shot = rows.stream().filter(r -> r.name().equals(extraShot)).findFirst().orElseThrow();
        assertEquals(com.coffeeshop.coffeeshopmanagement.model.OptionKind.EXTRA, shot.kind());
        assertEquals("2 sold, 1 refunded", 1, shot.quantity());
        assertEquals(0, new BigDecimal("10000").compareTo(shot.revenue()));
    }
}
