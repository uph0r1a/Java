package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.StockLogEntry;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.TestSession;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * TODO 4.33: waste and breakage ("hao hụt") is a third kind of stock log line. It lowers the stock and writes the line in one
 * transaction, records the cost price of that moment, needs a reason, and the report counts it as a cost.
 */
public class WasteLogTest {

    private static final LocalDate DAY = LocalDate.of(2031, 5, 10);

    private final StockLogDAO stockLog = new StockLogDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final ReportDAO reports = new ReportDAO();
    private Category category;

    @BeforeClass
    public static void setUpDatabase() {
        TestSession.admin();
        TestDatabaseSupport.ensureReady();
    }

    @Before
    public void signInAndMakeCategory() {
        TestSession.admin();
        AppClock.fix(LocalDateTime.of(DAY, java.time.LocalTime.of(14, 30)));
        category = new Category();
        category.setName("test-waste-category-" + UUID.randomUUID());
        category.setActive(true);
        new CategoryDAO().insert(category);
    }

    @After
    public void resetClockAndSession() {
        AppClock.reset();
        TestSession.admin();
    }

    private Product newProduct(int stock, String cost) {
        Product p = new Product();
        p.setName("test-waste-product-" + UUID.randomUUID());
        p.setCategoryId(category.getId());
        p.setPrice(new BigDecimal("30000"));
        p.setCost(cost == null ? null : new BigDecimal(cost));
        p.setStock(stock);
        p.setActive(true);
        return productDAO.insert(p);
    }

    private int stockOf(Product p) {
        return productDAO.findById(p.getId()).orElseThrow().getStock();
    }

    private StockLogEntry wasteLineOf(Product p) {
        return stockLog.findRecent(p.getId(), 20).stream().filter(e -> StockLogEntry.WASTE.equals(e.kind())).findFirst().orElseThrow();
    }

    @Test
    public void wasteLowersTheStockAndWritesALineWithReasonCostWhoAndWhen() {
        Product p = newProduct(10, "8000");
        int after = stockLog.writeOff(p.getId(), 3, "  Hết   hạn \n");
        assertEquals(7, after);
        assertEquals(7, stockOf(p));

        StockLogEntry line = wasteLineOf(p);
        assertEquals(-3, line.quantity());
        assertEquals(10, line.stockBefore());
        assertEquals(7, line.stockAfter());
        assertEquals(0, new BigDecimal("8000").compareTo(line.unitCost()));
        assertEquals("Hết hạn", line.note());
        assertNull("a waste line has no supplier", line.supplier());
        assertEquals(LocalDateTime.of(DAY, java.time.LocalTime.of(14, 30)), line.loggedAt());
        assertNotNull("who", line.employeeName());
        assertEquals(p.getName(), line.productName());
    }

    @Test
    public void theCostOnTheLineIsTheCostOfThatMomentNotOfToday() {
        Product p = newProduct(10, "8000");
        stockLog.writeOff(p.getId(), 2, "Vỡ");
        Product loaded = productDAO.findById(p.getId()).orElseThrow();
        loaded.setLoadedStock(loaded.getStock());
        loaded.setCost(new BigDecimal("9500")); // the price goes up later
        productDAO.update(loaded);
        assertEquals(0, new BigDecimal("8000").compareTo(wasteLineOf(p).unitCost()));
    }

    @Test
    public void aProductWithoutACostPriceGivesALineWithoutACost() {
        Product p = newProduct(5, null);
        stockLog.writeOff(p.getId(), 1, "Đổ");
        assertNull(wasteLineOf(p).unitCost());
    }

    @Test
    public void allThePiecesMayBeWrittenOffAndNoMore() {
        Product p = newProduct(4, "1000");
        for (int tooMany : new int[] {5, 100}) {
            DataAccessException refused = assertThrows(DataAccessException.class, () -> stockLog.writeOff(p.getId(), tooMany, "Hỏng"));
            assertEquals(I18n.t("stock.waste.val.more", 4), refused.getMessage());
        }
        assertEquals("nothing changed", 4, stockOf(p));
        assertTrue("and no line", stockLog.findRecent(p.getId(), 20).stream().noneMatch(e -> StockLogEntry.WASTE.equals(e.kind())));
        assertEquals(0, stockLog.writeOff(p.getId(), 4, "Hỏng hết"));
        assertEquals(0, stockOf(p));
    }

    @Test
    public void aReasonIsRequiredAndAQuantityMustBeAWholeNumberFromOne() {
        Product p = newProduct(5, "1000");
        for (String blank : new String[] {null, "", "   ", "\n\t"}) {
            DataAccessException refused = assertThrows(DataAccessException.class, () -> stockLog.writeOff(p.getId(), 1, blank));
            assertEquals(I18n.t("stock.waste.val.reason"), refused.getMessage());
        }
        for (int bad : new int[] {0, -1}) {
            DataAccessException refused = assertThrows(DataAccessException.class, () -> stockLog.writeOff(p.getId(), bad, "Hỏng"));
            assertEquals(I18n.t("stock.val.quantity"), refused.getMessage());
        }
        assertEquals(5, stockOf(p));
    }

    @Test
    public void aLongReasonIsCutAtTheTextLimitAndAProductThatIsGoneIsSaidSo() {
        Product p = newProduct(5, "1000");
        stockLog.writeOff(p.getId(), 1, "x".repeat(300));
        assertEquals(com.coffeeshop.coffeeshopmanagement.service.StockRules.TEXT_MAX, wasteLineOf(p).note().length());

        Product gone = newProduct(5, "1000");
        productDAO.delete(gone.getId());
        DataAccessException refused = assertThrows(DataAccessException.class, () -> stockLog.writeOff(gone.getId(), 1, "Hỏng"));
        assertEquals(I18n.t("stock.product.gone"), refused.getMessage());
    }

    @Test
    public void onlyAnAdministratorMayRecordWaste() {
        Product p = newProduct(5, "1000");
        TestSession.clear();
        assertThrows(Permissions.PermissionDeniedException.class, () -> stockLog.writeOff(p.getId(), 1, "Hỏng"));
        TestSession.admin();
        assertEquals(5, stockOf(p));
    }

    // ------------------------------------------------------------------ the report

    @Test
    public void theReportCountsWasteLinesPiecesAndCostForTheDaysAskedFor() {
        ReportDAO.Waste before = reports.waste(DAY, DAY);
        Product milk = newProduct(20, "8000");
        Product cup = newProduct(20, "500");
        Product unpriced = newProduct(20, null);
        stockLog.writeOff(milk.getId(), 3, "Hết hạn");   // 24 000
        stockLog.writeOff(cup.getId(), 10, "Vỡ");        //  5 000
        stockLog.writeOff(unpriced.getId(), 2, "Đổ");    // lines without a cost: counted apart, not priced at 0

        ReportDAO.Waste after = reports.waste(DAY, DAY);
        assertEquals(before.lines() + 3, after.lines());
        assertEquals(before.pieces() + 15, after.pieces());
        assertEquals(0, before.cost().add(new BigDecimal("29000")).compareTo(after.cost()));
        assertEquals(before.linesWithoutCost() + 1, after.linesWithoutCost());
    }

    @Test
    public void theReportKeepsToTheDayOfTheLogLineAndIgnoresReceiptsAndEdits() {
        Product p = newProduct(10, "1000");
        stockLog.receive(p.getId(), 50, new BigDecimal("1000"), "NCC", null); // a receipt is not waste
        ReportDAO.Waste base = reports.waste(DAY, DAY);
        ReportDAO.Waste dayBefore = reports.waste(DAY.minusDays(1), DAY.minusDays(1));
        ReportDAO.Waste dayAfter = reports.waste(DAY.plusDays(1), DAY.plusDays(1));

        stockLog.writeOff(p.getId(), 2, "Hỏng");
        AppClock.fix(LocalDateTime.of(DAY, java.time.LocalTime.of(23, 59, 59)));
        stockLog.writeOff(p.getId(), 1, "Hỏng muộn");

        ReportDAO.Waste after = reports.waste(DAY, DAY);
        assertEquals(base.lines() + 2, after.lines());
        assertEquals(base.pieces() + 3, after.pieces());
        assertEquals(dayBefore, reports.waste(DAY.minusDays(1), DAY.minusDays(1)));
        assertEquals(dayAfter, reports.waste(DAY.plusDays(1), DAY.plusDays(1)));
        assertEquals("single days add up to the range", dayBefore.lines() + after.lines() + dayAfter.lines(),
                reports.waste(DAY.minusDays(1), DAY.plusDays(1)).lines());
    }

    @Test
    public void aRangeWithNoWasteIsZeroNotAnError() {
        LocalDate quiet = LocalDate.of(1999, 1, 1);
        ReportDAO.Waste none = reports.waste(quiet, quiet);
        assertEquals(0, none.lines());
        assertEquals(0, none.pieces());
        assertEquals(0, BigDecimal.ZERO.compareTo(none.cost()));
        assertEquals(0, none.linesWithoutCost());
    }

    @Test
    public void wasteDoesNotChangeTheProfitFigureOfTheSalesReport() {
        Product p = newProduct(10, "1000");
        ReportDAO.Profit before = reports.profit(DAY, DAY);
        stockLog.writeOff(p.getId(), 5, "Hỏng");
        assertEquals(before, reports.profit(DAY, DAY));
    }
}
