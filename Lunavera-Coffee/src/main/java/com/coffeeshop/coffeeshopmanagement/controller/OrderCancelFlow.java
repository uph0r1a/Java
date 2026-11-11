package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.OrderCancelService;
import com.coffeeshop.coffeeshopmanagement.service.RefundService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.scene.control.Button;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Cancelling a paid order from the history as a refund (TODO 2.66, 4.2): one box asks whether the goods go back into stock and why, with
 * the reason already filled in when something made to order is in the order (TODO 6.90 d), then the cancel runs in the background.
 * Moved out of {@link OrderHistoryWindow}, which is at its size ceiling. The permission checks stay in the window and in the service.
 */
final class OrderCancelFlow {

    private OrderCancelFlow() {
    }

    static void run(int orderId, BigDecimal total, Button cancelButton, Runnable reload) {
        // the suggestion is read first, in the background; if it cannot be read the box simply opens with no reason filled in
        Async.run(() -> new RefundService().suggestedReasonKey(orderId), key -> ask(orderId, total, key, cancelButton, reload),
                error -> ask(orderId, total, null, cancelButton, reload));
    }

    private static void ask(int orderId, BigDecimal total, String suggestedReasonKey, Button cancelButton, Runnable reload) {
        Optional<RefundDialog.Choice> choice = RefundDialog.ask(orderId, total, suggestedReasonKey);
        if (choice.isEmpty()) return;
        boolean returnToStock = choice.get().returnToStock();
        cancelButton.setDisable(true);
        Async.run(
                () -> {
                    new OrderCancelService().cancelPaid(orderId, returnToStock, choice.get().reason());
                    return Boolean.TRUE;
                },
                done -> reload.run(),
                error -> {
                    AlertUtil.error(I18n.t("history.cancel.fail"), error.getMessage());
                    reload.run();
                });
    }
}
