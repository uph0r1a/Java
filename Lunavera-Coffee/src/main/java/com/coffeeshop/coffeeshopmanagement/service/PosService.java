package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.CategoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ReservationDAO;
import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import com.coffeeshop.coffeeshopmanagement.model.Category;
import com.coffeeshop.coffeeshopmanagement.model.DiningTable;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.Reservation;
import com.coffeeshop.coffeeshopmanagement.util.BusinessDay;

import java.util.List;
import java.util.Map;

/**
 * What the till screen reads from the database and the one small write it makes besides a payment (TODO 6.21): the menu, the best
 * sellers, the table map, the stock re-read before the payment box, and closing a reservation once its guests are seated. The till
 * ({@code OrderController}) used to call six DAOs for these; it now asks this class, so the screen holds no database call of its own
 * and a test can read the same answers without a window. Meant to be called from a background thread (TODO 6.14).
 */
public class PosService {

    /** The categories and the products that can be sold now, read together. */
    public record Menu(List<Category> categories, List<Product> products) {
    }

    /** What the table map is drawn from, apart from the reservations: the tables and the open take-away orders. */
    public record TableMap(List<DiningTable> tables, List<Order> takeAway) {
    }

    /** How many days back the best-sellers order of the product tiles looks. */
    static final int BEST_SELLER_DAYS = 30;

    private final CategoryDAO categoryDAO;
    private final ProductDAO productDAO;
    private final TableDAO tableDAO;
    private final OrderDAO orderDAO;
    private final ReportDAO reportDAO;
    private final ReservationDAO reservationDAO;

    public PosService() {
        this(new CategoryDAO(), new ProductDAO(), new TableDAO(), new OrderDAO(), new ReportDAO(), new ReservationDAO());
    }

    PosService(CategoryDAO categoryDAO, ProductDAO productDAO, TableDAO tableDAO, OrderDAO orderDAO, ReportDAO reportDAO,
               ReservationDAO reservationDAO) {
        this.categoryDAO = categoryDAO;
        this.productDAO = productDAO;
        this.tableDAO = tableDAO;
        this.orderDAO = orderDAO;
        this.reportDAO = reportDAO;
        this.reservationDAO = reservationDAO;
    }

    /** The categories and the products for sale; {@code heldAgainstOrderId} is the order on screen, whose own lines are not held against it (TODO 3.1), or 0. */
    public Menu menu(int heldAgainstOrderId) {
        return new Menu(categoryDAO.findAll(), productDAO.findAllForSale(heldAgainstOrderId));
    }

    /** The products for sale only (the tiles after a payment or a change of order). */
    public List<Product> productsForSale(int heldAgainstOrderId) {
        return productDAO.findAllForSale(heldAgainstOrderId);
    }

    /** Units sold per product id over the last {@value #BEST_SELLER_DAYS} days, for the best-sellers order of the tiles. */
    public Map<Integer, Integer> recentUnitsSold() {
        return reportDAO.unitsSold(BusinessDay.today().minusDays(BEST_SELLER_DAYS), BusinessDay.today());
    }

    /** The tables and the open take-away orders the table map is drawn from. */
    public TableMap tableMap() {
        return new TableMap(tableDAO.findAll(), orderDAO.findOpenTakeAway());
    }

    /** Reads the stock of the cart's products again (TODO 3.1); empty means the payment box can open. */
    public List<StockCheck.Shortage> stockShortages(List<OrderItem> lines) {
        return StockCheck.check(lines, productDAO);
    }

    /** Closes a reservation as seated, once its guests have a table and an order. */
    public void markSeated(int reservationId) {
        reservationDAO.close(reservationId, Reservation.Status.SEATED);
    }
}
