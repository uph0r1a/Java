package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.StockLogEntry;
import com.coffeeshop.coffeeshopmanagement.service.Limits;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** TODO 4.5: goods received and hand-typed stock numbers go into the stock log, with the stock and the line in one transaction. */
public class StockLogDAOTest {

    private final StockLogDAO stockLog = new StockLogDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private Category category;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void signInAndMakeCategory() {
        TestSession.admin();
        category = new Category();
        category.setName("test-stock-category-" + UUID.randomUUID());
        category.setActive(true);
        categoryDAO.insert(category);
    }

    @After
    public void signInAgain() {
        TestSession.admin();
    }

    private Product newProduct(int stock) {
        Product p = new Product();
        p.setName("test-stock-product-" + UUID.randomUUID());
        p.setCategoryId(category.getId());
        p.setPrice(new BigDecimal("30000"));
        p.setStock(stock);
        p.setActive(true);
        return productDAO.insert(p);
    }

    private int stockOf(Product p) {
        return productDAO.findById(p.getId()).orElseThrow().getStock();
    }

    @Test
    public void receivingRaisesTheStockAndWritesALogLine() {
        Product p = newProduct(4);
        int after = stockLog.receive(p.getId(), 20, new BigDecimal("12000"), "  Nhà cung cấp A ", "đợt 1");
        assertEquals(24, after);
        assertEquals(24, stockOf(p));

        StockLogEntry line = stockLog.findRecent(p.getId(), 10).stream()
                .filter(e -> StockLogEntry.RECEIPT.equals(e.kind())).findFirst().orElseThrow();
        assertEquals(20, line.quantity());
        assertEquals(4, line.stockBefore());
        assertEquals(24, line.stockAfter());
        assertEquals(0, new BigDecimal("12000").compareTo(line.unitCost()));
        assertEquals("Nhà cung cấp A", line.supplier());
        assertEquals("đợt 1", line.note());
        assertEquals(p.getName(), line.productName());
        assertNotNull(line.loggedAt());
    }

    @Test
    public void aReceiptWithNoCostKeepsTheCostEmpty() {
        Product p = newProduct(0);
        stockLog.receive(p.getId(), 3, null, null, null);
        StockLogEntry line = stockLog.findRecent(p.getId(), 5).get(0);
        assertNull(line.unitCost());
        assertNull(line.supplier());
        assertNull(line.note());
    }

    @Test
    public void aReceiptThatWouldPassTheCeilingChangesNothing() {
        Product p = newProduct(Limits.MAX_STOCK - 2);
        try {
            stockLog.receive(p.getId(), 3, null, null, null);
            fail("the stock would pass the ceiling");
        } catch (DataAccessException expected) {
            // fine
        }
        assertEquals(Limits.MAX_STOCK - 2, stockOf(p));
        assertTrue("no receipt line", stockLog.findRecent(p.getId(), 10).stream()
                .noneMatch(e -> StockLogEntry.RECEIPT.equals(e.kind())));
    }

    @Test(expected = DataAccessException.class)
    public void aQuantityOfZeroIsRefused() {
        stockLog.receive(newProduct(1).getId(), 0, null, null, null);
    }

    @Test
    public void aProductThatIsGoneIsSaidSo() {
        Product p = newProduct(1);
        productDAO.delete(p.getId());
        try {
            stockLog.receive(p.getId(), 1, null, null, null);
            fail("no such product");
        } catch (DataAccessException expected) {
            // fine
        }
    }

    @Test
    public void aNumberTypedOverOnTheProductFormIsLoggedAsAnEdit() {
        Product p = newProduct(10);
        Product loaded = productDAO.findById(p.getId()).orElseThrow();
        loaded.setLoadedStock(loaded.getStock());
        loaded.setStock(7); // a stock-take found 7
        productDAO.update(loaded);
        assertEquals(7, stockOf(p));

        StockLogEntry line = stockLog.findRecent(p.getId(), 10).stream()
                .filter(e -> StockLogEntry.ADJUST.equals(e.kind()) && e.quantity() == -3).findFirst().orElseThrow();
        assertEquals(10, line.stockBefore());
        assertEquals(7, line.stockAfter());
    }

    @Test
    public void savingAProductWithoutTouchingTheStockWritesNoLine() {
        Product p = newProduct(10);
        long linesBefore = stockLog.findRecent(p.getId(), 50).size(); // the opening stock is one line
        Product loaded = productDAO.findById(p.getId()).orElseThrow();
        loaded.setLoadedStock(loaded.getStock());
        loaded.setPrice(new BigDecimal("31000"));
        productDAO.update(loaded);
        assertEquals(linesBefore, stockLog.findRecent(p.getId(), 50).size());
    }

    @Test
    public void theOpeningStockOfANewProductIsALine() {
        Product p = newProduct(12);
        List<StockLogEntry> lines = stockLog.findRecent(p.getId(), 10);
        assertEquals(1, lines.size());
        assertEquals(StockLogEntry.ADJUST, lines.get(0).kind());
        assertEquals(0, lines.get(0).stockBefore());
        assertEquals(12, lines.get(0).stockAfter());
        assertTrue("no line for a product with no stock", stockLog.findRecent(newProduct(0).getId(), 10).isEmpty());
    }

    @Test
    public void onlyAnAdministratorMayReceiveOrReadTheLog() {
        Product p = newProduct(1);
        TestSession.clear();
        try {
            stockLog.receive(p.getId(), 1, null, null, null);
            fail("not signed in");
        } catch (Permissions.PermissionDeniedException expected) {
            // fine
        }
        try {
            stockLog.findRecent(null, 5);
            fail("not signed in");
        } catch (Permissions.PermissionDeniedException expected) {
            // fine
        }
    }
}
