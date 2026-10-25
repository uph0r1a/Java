package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.dao.CustomerDAO;
import com.coffeeshop.coffeeshopmanagement.dao.EmployeeDAO;
import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ProductDAO;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderStatus;
import com.coffeeshop.coffeeshopmanagement.model.Product;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Pulls together the numbers shown on the admin and employee dashboards. All figures come
 * from the database - none are hardcoded - so once the POS/order-creation screen starts
 * writing real orders, these numbers will move on their own.
 */
public class DashboardStatsService {

    public record DashboardStats(
            int todayOrders,
            BigDecimal todayRevenue,
            int todayCustomers,
            int paidOrders,
            int openOrders,
            int cancelledOrders,
            int totalProducts,
            int totalEmployees,
            int totalCustomers,
            Map<LocalDate, BigDecimal> revenueLast7Days,
            int lowStockCount,
            List<Product> lowStockProducts,
            List<Order> todayOrdersList,
            List<Order> staleOpenOrders,
            List<Integer> heldTablesWithoutOrder,
            int sharedCustomerPhones
    ) {
    }

    private static final int LOW_STOCK_ROWS = 5;
    /** An order still OPEN after this long was most likely forgotten (TODO 2.11). */
    public static final java.time.Duration STALE_OPEN_AFTER = java.time.Duration.ofHours(12);

    private final OrderDAO orderDAO;
    private final ProductDAO productDAO;
    private final EmployeeDAO employeeDAO;
    private final CustomerDAO customerDAO;

    public DashboardStatsService() {
        this(new OrderDAO(), new ProductDAO(), new EmployeeDAO(), new CustomerDAO());
    }

    public DashboardStatsService(OrderDAO orderDAO, ProductDAO productDAO, EmployeeDAO employeeDAO,
                                  CustomerDAO customerDAO) {
        this.orderDAO = orderDAO;
        this.productDAO = productDAO;
        this.employeeDAO = employeeDAO;
        this.customerDAO = customerDAO;
    }

    public DashboardStats loadStats() {
        return new DashboardStats(
                orderDAO.countToday(),
                orderDAO.sumRevenueToday(),
                orderDAO.countDistinctCustomersToday(),
                orderDAO.countByStatus(OrderStatus.PAID),
                orderDAO.countByStatus(OrderStatus.OPEN),
                orderDAO.countRefunded(),
                productDAO.countAll(),
                employeeDAO.countActive(),
                customerDAO.countAll(),
                orderDAO.revenueForLast7Days(),
                productDAO.countLowStock(ProductDAO.LOW_STOCK_THRESHOLD),
                productDAO.findLowStock(ProductDAO.LOW_STOCK_THRESHOLD, LOW_STOCK_ROWS),
                orderDAO.findTodayOrders(),
                orderDAO.findStaleOpenOrders(STALE_OPEN_AFTER),
                new com.coffeeshop.coffeeshopmanagement.dao.TableDAO().holdsWithoutOrder(),
                customerDAO.countSharedPhones()
        );
    }

    /** Tables marked "in use" by hand with no order (TODO 2.53), or null when there are none. */
    public static String heldTablesWarning(List<Integer> held) {
        if (held == null || held.isEmpty()) return null;
        StringBuilder names = new StringBuilder();
        for (int number : held) names.append(names.length() == 0 ? "" : ", ").append(I18n.t("stats.table.lower") + " ").append(number);
        return I18n.t("stats.warn.occupied", names);
    }

    /** Customers who share a phone number (TODO 3.41), or null when none do. */
    public static String sharedPhonesWarning(int sharedPhones) {
        if (sharedPhones <= 0) return null;
        return I18n.t("stats.warn.sharedphones", sharedPhones);
    }

    /** The dashboard warnings as one text, or null when there is nothing to warn about. */
    public static String combinedWarning(List<Order> stale, List<Integer> held) {
        return combinedWarning(stale, held, 0);
    }

    public static String combinedWarning(List<Order> stale, List<Integer> held, int sharedPhones) {
        StringBuilder text = new StringBuilder();
        for (String line : new String[]{staleOrdersWarning(stale), heldTablesWarning(held), sharedPhonesWarning(sharedPhones)}) {
            if (line == null) continue;
            if (text.length() > 0) text.append('\n');
            text.append(line);
        }
        return text.length() == 0 ? null : text.toString();
    }

    /** The one-line warning for the dashboard, or null when nothing is stale: "2 đơn mở quá 12 giờ (bàn 3, bàn 5)". */
    public static String staleOrdersWarning(List<Order> stale) {
        if (stale == null || stale.isEmpty()) return null;
        java.util.LinkedHashSet<String> tables = new java.util.LinkedHashSet<>();
        for (Order order : stale) {
            tables.add(order.getTableNumber() == null ? I18n.t("stats.takeaway.lower") : I18n.t("stats.table.lower.n", order.getTableNumber()));
        }
        return I18n.t("stats.warn.stale", stale.size(), STALE_OPEN_AFTER.toHours(), String.join(", ", tables));
    }
}
