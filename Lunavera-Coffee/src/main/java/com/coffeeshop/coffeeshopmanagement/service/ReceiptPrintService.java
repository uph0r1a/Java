package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.ReceiptPrintDAO;

import java.util.List;

/**
 * What the bill window asks for when a receipt is printed (TODO 6.21): the print is counted and written to the audit log by {@code ReceiptPrintDAO}.
 */
public class ReceiptPrintService {

    private final ReceiptPrintDAO dao;

    public ReceiptPrintService() {
        this(new ReceiptPrintDAO());
    }

    ReceiptPrintService(ReceiptPrintDAO dao) {
        this.dao = dao;
    }

    public int recordPrint(int orderId) {
        return dao.recordPrint(orderId);
    }
}
