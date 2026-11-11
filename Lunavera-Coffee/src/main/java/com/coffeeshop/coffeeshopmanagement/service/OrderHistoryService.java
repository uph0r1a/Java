package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;

import java.util.List;

/**
 * What the order-history window and its CSV export ask for (TODO 6.21): a page of orders, the count, the lines of one order, and the export. {@code OrderDAO} holds the queries and the permission.
 */
public class OrderHistoryService {

    private final OrderDAO dao;

    public OrderHistoryService() {
        this(new OrderDAO());
    }

    OrderHistoryService(OrderDAO dao) {
        this.dao = dao;
    }

    public List<OrderDAO.OrderSummary> findHistoryPage(OrderDAO.HistoryFilter filter, OrderDAO.HistorySort sort, int pageSize, int offset) {
        return dao.findHistoryPage(filter, sort, pageSize, offset);
    }

    public int countHistory(OrderDAO.HistoryFilter filter) {
        return dao.countHistory(filter);
    }

    public List<com.coffeeshop.coffeeshopmanagement.model.OrderItem> findItemsByOrderId(int orderId) {
        return dao.findItemsByOrderId(orderId);
    }

    public int exportOrders(OrderDAO.HistoryFilter filter, OrderDAO.HistorySort sort, java.util.function.Consumer<OrderDAO.OrderSummary> sink) {
        return dao.exportOrders(filter, sort, sink);
    }
}
