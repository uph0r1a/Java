package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.OrderChangedElsewhereException;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.service.StaleOrderRules;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import java.util.List;

/**
 * What the till says when a save or a payment was refused because another till changed the open order first (TODO 2.91): that this
 * till's changes were not saved, which of its lines are missing from the order, and the screen shows the saved order again.
 */
final class StaleOrderAlert {

    private StaleOrderAlert() {
    }

    /** True when {@code error} was that refusal (the cashier has been told and {@code reload} has been run); false for any other failure. */
    static boolean show(Throwable error, List<OrderItem> onScreen, Runnable reload) {
        if (!(error instanceof OrderChangedElsewhereException stale)) return false;
        List<String> missing = StaleOrderRules.missingHere(onScreen, stale.saved());
        String more = missing.isEmpty() ? "" : I18n.t("pos.stale.missing", String.join("\n", missing));
        AlertUtil.warning(I18n.t("pos.stale.title"), I18n.t("pos.stale.message", more));
        reload.run();
        return true;
    }
}
