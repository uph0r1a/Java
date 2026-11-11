package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.util.List;

/**
 * The open order was changed on another till after this screen loaded it (TODO 2.91): its revision is no longer the one the screen
 * holds, so the save or the payment was refused and nothing was written. {@link #saved()} is what the order holds now, so the screen
 * can show it again and say which of its own lines are missing from it.
 */
public class OrderChangedElsewhereException extends DataAccessException {
    private static final long serialVersionUID = 1L;

    private final transient List<OrderItem> saved;

    public OrderChangedElsewhereException(List<OrderItem> saved) {
        super(I18n.t("order.changed.elsewhere"));
        this.saved = saved == null ? List.of() : List.copyOf(saved);
    }

    /** The lines the order has in the database now. */
    public List<OrderItem> saved() {
        return saved;
    }
}
