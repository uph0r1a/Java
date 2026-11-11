package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.OrderDAO;
import com.coffeeshop.coffeeshopmanagement.service.StockHolds;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

/**
 * Tells the cashier that another open order holds what the last change asked for (TODO 3.1), once per refused change: the autosave
 * refused it and wrote nothing, and the saves already queued behind it fail the same way, so the box would otherwise open again for
 * each. The notice is armed again by the next save that works.
 */
final class HoldNotice {

    private boolean shown;

    /**
     * Looks at what an autosave did. A save that worked arms the notice again; one that was refused for a held product says which,
     * then runs {@code reload} (show the order as saved, read the product list again) so the screen and the database agree. An order
     * that was closed meanwhile is the caller's business, not this notice's.
     */
    void after(OrderDAO.SaveResult result, Runnable reload) {
        if (result.saved()) {
            shown = false;
        } else if (result.stillOpen() && !shown) {
            shown = true;
            AlertUtil.warning(I18n.t("pos.hold.title"), StockHolds.message(result.shortages()));
            reload.run();
        }
    }
}
