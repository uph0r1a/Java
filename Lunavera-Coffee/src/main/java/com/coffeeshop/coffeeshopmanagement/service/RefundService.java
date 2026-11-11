package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.RefundDAO;

import java.util.List;

/**
 * What the refund box asks for (TODO 6.21): the refundable lines of an order, making the refund, and the refunds already made. The rules ({@code RefundMath}, {@code RefundSplit}) and the transaction stay in {@code RefundDAO}.
 */
public class RefundService {

    private final RefundDAO dao;

    public RefundService() {
        this(new RefundDAO());
    }

    RefundService(RefundDAO dao) {
        this.dao = dao;
    }

    public RefundDAO.State load(int orderId) {
        return dao.load(orderId);
    }

    public RefundDAO.Result refund(int orderId, java.util.Map<Integer, Integer> quantities, boolean returnToStock, com.coffeeshop.coffeeshopmanagement.model.PaymentMethod method, String reason) {
        return dao.refund(orderId, quantities, returnToStock, method, reason);
    }

    /** The key of the reason to suggest when the whole order is refunded, or null (TODO 6.90 d): a read, to be called off the screen thread. */
    public String suggestedReasonKey(int orderId) {
        return RefundDefaults.reasonKey(RefundDefaults.anyMade(dao.load(orderId).lines()));
    }

    public List<RefundDAO.RefundRow> forOrder(int orderId) {
        return dao.forOrder(orderId);
    }
}
