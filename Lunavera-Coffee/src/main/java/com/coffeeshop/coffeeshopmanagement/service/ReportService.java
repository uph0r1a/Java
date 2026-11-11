package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.ReportDAO;
import com.coffeeshop.coffeeshopmanagement.dao.ShiftDAO;

import java.util.List;

/**
 * What the report window and the revenue-book export ask for (TODO 6.21): thin doors to the figures in {@code ReportDAO}, which holds the permission and the queries.
 */
public class ReportService {

    private final ReportDAO dao;

    public ReportService() {
        this(new ReportDAO());
    }

    ReportService(ReportDAO dao) {
        this.dao = dao;
    }

    public ReportDAO.Summary summary(java.time.LocalDate from, java.time.LocalDate to) {
        return dao.summary(from, to);
    }

    public List<ReportDAO.ProductSales> allProducts(java.time.LocalDate from, java.time.LocalDate to) {
        return dao.allProducts(from, to);
    }

    public List<ReportDAO.DailySales> dailySales(java.time.LocalDate from, java.time.LocalDate to) {
        return dao.dailySales(from, to);
    }

    public List<ReportDAO.ChannelSales> channelSales(java.time.LocalDate from, java.time.LocalDate to) {
        return dao.channelSales(from, to);
    }

    public List<ReportDAO.BookLine> revenueBook(java.time.LocalDate from, java.time.LocalDate to) {
        return dao.revenueBook(from, to);
    }

    public List<ReportDAO.DiscountLine> discountsByCashier(java.time.LocalDate from, java.time.LocalDate to) {
        return dao.discountsByCashier(from, to);
    }

    public List<ReportDAO.PaymentSplit> paymentSplit(java.time.LocalDate from, java.time.LocalDate to) {
        return dao.paymentSplit(from, to);
    }

    public List<ShiftDAO.EmployeeShare> employeeSales(java.time.LocalDate from, java.time.LocalDate to) {
        return dao.employeeSales(from, to);
    }

    public ReportDAO.Profit profit(java.time.LocalDate from, java.time.LocalDate to) {
        return dao.profit(from, to);
    }

    public ReportDAO.Waste waste(java.time.LocalDate from, java.time.LocalDate to) {
        return dao.waste(from, to);
    }

    public ReportDAO.Expenses expenses(java.time.LocalDate from, java.time.LocalDate to) {
        return dao.expenses(from, to);
    }

    public List<ReportDAO.CategorySales> categorySales(java.time.LocalDate from, java.time.LocalDate to) {
        return dao.categorySales(from, to);
    }

    public List<ReportDAO.OptionSales> optionSales(java.time.LocalDate from, java.time.LocalDate to) {
        return dao.optionSales(from, to);
    }

    public List<SalesPatterns.PaidOrder> paidOrders(java.time.LocalDate from, java.time.LocalDate to) {
        return dao.paidOrders(from, to);
    }
}
