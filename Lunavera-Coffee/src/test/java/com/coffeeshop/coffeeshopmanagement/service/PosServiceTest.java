package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TestDatabaseSupport;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.21: what the till reads comes through {@link PosService} and equals what the DAOs themselves return. Runs on the shared test
 * database. Not run where this was written (no SQLite driver offline).
 */
public class PosServiceTest {

    private final PosService service = new PosService();

    @BeforeClass
    public static void setUpDatabase() {
        TestDatabaseSupport.ensureReady();
    }

    @Test
    public void theMenuIsTheCategoriesAndTheProductsForSale() {
        PosService.Menu menu = service.menu(0);
        assertEquals(new CategoryDAO().findAll().size(), menu.categories().size());
        assertEquals(new ProductDAO().findAllForSale(0).size(), menu.products().size());
        assertEquals(menu.products().size(), service.productsForSale(0).size());
    }

    @Test
    public void theTableMapIsTheTablesAndTheOpenTakeAwayOrders() {
        PosService.TableMap map = service.tableMap();
        assertEquals(new TableDAO().findAll().size(), map.tables().size());
        assertEquals(new OrderDAO().findOpenTakeAway().size(), map.takeAway().size());
    }

    @Test
    public void anEmptyCartHasNoStockShortage() {
        assertTrue(service.stockShortages(List.of()).isEmpty());
    }

    @Test
    public void recentUnitsSoldIsNeverNull() {
        assertTrue(service.recentUnitsSold() != null);
    }
}
