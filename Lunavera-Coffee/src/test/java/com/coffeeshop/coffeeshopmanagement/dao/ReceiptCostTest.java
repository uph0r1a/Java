package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.AuditAction;
import com.coffeeshop.coffeeshopmanagement.model.AuditEntry;
import com.coffeeshop.coffeeshopmanagement.model.AuditTarget;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.service.ReceiptCost;
import com.coffeeshop.coffeeshopmanagement.service.ReceiptCost.Mode;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 4.52: receiving stock at a new price can update the product's cost (keep, set, or the weighted average); the change is audited. */
public class ReceiptCostTest {

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    // ---- the rule, no database ----

    @Test
    public void theAverageIsWeightedByTheStockInHandAndRoundedToWholeDong() {
        assertEquals(0, d("11000").compareTo(ReceiptCost.average(10, d("10000"), 10, d("12000"))));
        assertEquals(0, d("10667").compareTo(ReceiptCost.average(20, d("10000"), 10, d("12000")))); // 10666.67
        assertEquals(0, d("12000").compareTo(ReceiptCost.average(0, d("10000"), 10, d("12000"))));   // nothing in stock: the new price
        assertEquals(0, d("12000").compareTo(ReceiptCost.average(-3, d("10000"), 10, d("12000")))); // stock below zero counts for nothing
        assertEquals(0, d("12000").compareTo(ReceiptCost.average(10, null, 10, d("12000"))));       // no old cost
    }

    @Test
    public void onlyTheModeAndATypedPriceChangeTheCost() {
        assertEquals(0, d("10000").compareTo(ReceiptCost.resulting(Mode.KEEP, 5, d("10000"), 5, d("12000"))));
        assertEquals(0, d("12000").compareTo(ReceiptCost.resulting(Mode.REPLACE, 5, d("10000"), 5, d("12000"))));
        assertEquals(0, d("11000").compareTo(ReceiptCost.resulting(Mode.AVERAGE, 5, d("10000"), 5, d("12000"))));
        assertEquals(0, d("10000").compareTo(ReceiptCost.resulting(Mode.REPLACE, 5, d("10000"), 5, null)));   // no price typed
        assertEquals(0, d("10000").compareTo(ReceiptCost.resulting(Mode.REPLACE, 5, d("10000"), 0, d("12000")))); // nothing arrived
        assertNull(ReceiptCost.resulting(Mode.KEEP, 5, null, 5, d("12000")));
        assertEquals(0, d("10000").compareTo(ReceiptCost.resulting(null, 5, d("10000"), 5, d("12000"))));
    }

    @Test
    public void aQuestionIsAskedOnlyWhenThePriceDiffers() {
        assertFalse(ReceiptCost.differs(d("10000"), null));
        assertFalse(ReceiptCost.differs(d("10000"), d("10000.00")));
        assertTrue(ReceiptCost.differs(d("10000"), d("10500")));
        assertTrue(ReceiptCost.differs(null, d("10500")));
    }

    // ---- on the database ----

    private final StockLogDAO stockLog = new StockLogDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private Category category;
    private LocalDateTime savedNow;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void makeCategory() {
        TestSession.admin();
        category = new Category();
        category.setName("test-receiptcost-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
    }

    @After
    public void signInAgain() {
        TestSession.admin();
    }

    private Product product(int stock, String cost) {
        Product p = new Product();
        p.setName("test-receiptcost-product-" + UUID.randomUUID());
        p.setCategoryId(category.getId());
        p.setPrice(new BigDecimal("30000"));
        p.setCost(cost == null ? null : new BigDecimal(cost));
        p.setStock(stock);
        p.setActive(true);
        return productDAO.insert(p);
    }

    private BigDecimal costOf(Product p) {
        return productDAO.findById(p.getId()).orElseThrow().getCost();
    }

    private List<AuditEntry> costAudit(Product p) {
        return new AuditDAO().find(new AuditDAO.Filter(null, null, AuditAction.PRODUCT_COST, null, String.valueOf(p.getId())), 50, 0).stream()
                .filter(e -> AuditTarget.PRODUCT.equals(e.targetType()) && e.targetId() != null && e.targetId() == p.getId()).toList();
    }

    @Test
    public void keepingTheCostChangesNothingAndWritesNoAuditLine() {
        Product p = product(10, "10000");
        stockLog.receive(p.getId(), 10, d("12000"), "NCC", null);                       // the old form: keep
        stockLog.receive(p.getId(), 10, d("13000"), "NCC", null, Mode.KEEP);
        assertEquals(0, d("10000").compareTo(costOf(p)));
        assertTrue(costAudit(p).isEmpty());
    }

    @Test
    public void settingTheCostUsesTheNewPriceAndIsAudited() {
        Product p = product(10, "10000");
        stockLog.receive(p.getId(), 10, d("12000"), "NCC", null, Mode.REPLACE);
        assertEquals(0, d("12000").compareTo(costOf(p)));
        assertEquals(20, productDAO.findById(p.getId()).orElseThrow().getStock());
        List<AuditEntry> audit = costAudit(p);
        assertEquals(1, audit.size());
        assertEquals("10000", audit.get(0).oldValue());
        assertEquals("12000", audit.get(0).newValue());
        assertEquals("stock receipt", audit.get(0).detail());
    }

    @Test
    public void theAverageUsesTheStockInHandAtTheOldCost() {
        Product p = product(30, "10000");
        stockLog.receive(p.getId(), 10, d("14000"), null, null, Mode.AVERAGE); // (30*10000 + 10*14000) / 40 = 11000
        assertEquals(0, d("11000").compareTo(costOf(p)));
        assertEquals("stock receipt, average", costAudit(p).get(0).detail());
    }

    @Test
    public void aProductWithNoCostGetsTheFirstPriceAndASamePriceWritesNothing() {
        Product none = product(5, null);
        stockLog.receive(none.getId(), 5, d("8000"), null, null, Mode.AVERAGE);
        assertEquals(0, d("8000").compareTo(costOf(none)));
        assertNull(costAudit(none).get(0).oldValue());
        Product same = product(5, "8000");
        stockLog.receive(same.getId(), 5, d("8000"), null, null, Mode.REPLACE);
        assertTrue(costAudit(same).isEmpty());
        Product noPrice = product(5, "8000");
        stockLog.receive(noPrice.getId(), 5, null, null, null, Mode.REPLACE); // no price typed: nothing to set
        assertEquals(0, d("8000").compareTo(costOf(noPrice)));
    }

    @Test
    public void aRefusedReceiptChangesNeitherTheStockNorTheCost() {
        Product p = product(com.coffeeshop.coffeeshopmanagement.service.Limits.MAX_STOCK, "10000");
        try {
            stockLog.receive(p.getId(), 1, d("12000"), null, null, Mode.REPLACE);
            fail("over the ceiling");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(0, d("10000").compareTo(costOf(p)));
    }

    private int paidLine(Product p) throws Exception {
        OrderItem item = new OrderItem();
        item.setProductId(p.getId());
        item.setProductName(p.getName());
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("30000"));
        item.setLineTotal(new BigDecimal("30000"));
        Order order = new Order();
        order.setOrderDate(AppClock.now());
        order.setStatus(OrderStatus.PAID);
        order.setSubtotal(new BigDecimal("30000"));
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("30000"));
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaidAt(AppClock.now());
        return new OrderDAO().insert(order, List.of(item)).getId();
    }

    private Long unitCostOfLine(int orderId) throws Exception {
        try (Connection c = com.coffeeshop.coffeeshopmanagement.config.DatabaseConfig.getConnection();
             PreparedStatement st = c.prepareStatement("SELECT unit_cost FROM order_items WHERE order_id = ?")) {
            st.setInt(1, orderId);
            try (ResultSet rs = st.executeQuery()) {
                rs.next();
                long v = rs.getLong(1);
                return rs.wasNull() ? null : v;
            }
        }
    }

    @Test
    public void linesAlreadyPaidKeepTheirOldCostAndLaterSalesUseTheNewOne() throws Exception {
        Product p = product(50, "10000");
        int before = paidLine(p);
        stockLog.receive(p.getId(), 10, d("12000"), null, null, Mode.REPLACE);
        int after = paidLine(p);
        assertEquals(Long.valueOf(10_000), unitCostOfLine(before));
        assertEquals(Long.valueOf(12_000), unitCostOfLine(after));
    }
}
