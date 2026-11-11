package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.dao.StockHistoryDAO;
import com.coffeeshop.coffeeshopmanagement.dao.StockLogDAO;
import com.coffeeshop.coffeeshopmanagement.model.StockLogEntry;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the product-stock screens ask for (TODO 6.21): the recent stock log, a delivery, a write-off, and the history of one product or
 * ingredient. {@link StockRules} and {@link ReceiptCost} hold the rules; {@link StockLogDAO} the permission, the arithmetic in one
 * transaction and the audit lines. {@code StockLogDialog} and {@code StockHistoryDialog} hold no DAO of their own.
 */
public class StockService {

    /** How many history lines a dialog shows. */
    public static final int HISTORY_LINES = StockHistoryDAO.SHOWN_LINES;

    private final StockLogDAO log;
    private final StockHistoryDAO history;

    public StockService() {
        this(new StockLogDAO(), new StockHistoryDAO());
    }

    StockService(StockLogDAO log, StockHistoryDAO history) {
        this.log = log;
        this.history = history;
    }

    public List<StockLogEntry> findRecent(Integer productId, int limit) {
        return log.findRecent(productId, limit);
    }

    /** Stock after the delivery. */
    public int receive(int productId, int quantity, BigDecimal unitCost, String supplier, String note, ReceiptCost.Mode costMode) {
        return log.receive(productId, quantity, unitCost, supplier, note, costMode);
    }

    /** Stock after the write-off. */
    public int writeOff(int productId, int quantity, String reason) {
        return log.writeOff(productId, quantity, reason);
    }

    public StockHistory.Page ofProduct(int productId, int limit) {
        return history.ofProduct(productId, limit);
    }

    public StockHistory.Page ofIngredient(int ingredientId, int limit) {
        return history.ofIngredient(ingredientId, limit);
    }
}
