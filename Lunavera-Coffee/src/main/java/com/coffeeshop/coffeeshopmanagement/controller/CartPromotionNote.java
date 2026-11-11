package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionService;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.util.List;

/**
 * The cart's live line for automatic promotions (TODO 4.55): "Promotion: Giờ vàng (-12.000đ) - to pay 108.000đ", written under the
 * total's title in the total card while the cashier is still ordering, and empty when nothing applies. It follows the cart because the
 * total's text changes with every change of the cart, and it asks again every 30 seconds so a happy hour that starts or ends while
 * the cart sits there is noticed. The work is done off the screen thread with a few seconds of remembered promotions
 * ({@link AutoPromotionService#preview}); only the latest answer is shown. It is a preview: the payment window prices the bill again
 * when it opens, and that moment is what the checkout charges.
 */
final class CartPromotionNote {

    private CartPromotionNote() {
    }

    /**
     * Sets the total label's first text and starts following the cart; does nothing more when the card is not laid out as expected,
     * so a screen without the note still works.
     */
    static void attach(Label totalLabel, List<OrderItem> cart, String firstText) {
        totalLabel.setText(firstText);
        VBox titles = null;
        if (totalLabel.getParent() instanceof Pane card) {
            for (Node child : card.getChildren()) {
                if (child instanceof VBox box) {
                    titles = box;
                    break;
                }
            }
        }
        if (titles == null) {
            return;
        }
        Label note = new Label();
        note.setWrapText(true);
        note.setStyle(com.coffeeshop.coffeeshopmanagement.util.TextSize.size(11) + " -fx-text-fill: #5b7e45;");
        note.setVisible(false);
        note.setManaged(false);
        titles.getChildren().add(note);
        int[] latest = {0};
        Runnable refresh = () -> {
            List<OrderItem> snapshot = List.copyOf(cart);
            int mine = ++latest[0];
            Async.run(() -> AutoPromotionService.preview(snapshot), result -> {
                if (mine != latest[0]) return; // a newer answer is on its way
                boolean show = !result.isNone();
                note.setText(show ? I18n.t("pos.cart.promo", String.join(", ", result.names()), CurrencyUtil.format(result.value()),
                        CurrencyUtil.format(subtotalOf(snapshot).subtract(result.value()))) : "");
                note.setVisible(show);
                note.setManaged(show);
            }, error -> {
                if (mine != latest[0]) return;
                note.setVisible(false); // a preview that cannot be read is simply not shown; the payment window says the rest
                note.setManaged(false);
            });
        };
        totalLabel.textProperty().addListener((o, was, now) -> refresh.run());
        Timeline again = new Timeline(new KeyFrame(Duration.seconds(30), e -> refresh.run()));
        again.setCycleCount(Animation.INDEFINITE);
        totalLabel.sceneProperty().addListener((o, was, now) -> {
            if (now == null) again.stop(); else again.play();
        });
        if (totalLabel.getScene() != null) again.play();
    }

    private static BigDecimal subtotalOf(List<OrderItem> items) {
        BigDecimal sum = BigDecimal.ZERO;
        for (OrderItem item : items) {
            if (item.getUnitPrice() != null) sum = sum.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return sum;
    }
}
