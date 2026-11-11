package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * The clock line of the till screen: the time now, and, when an order is open, the time it was started. Split out of
 * {@code OrderController} (TODO 6.54: the till class may only get smaller).
 */
final class OrderClock {

    private final Label label;
    private final Supplier<Order> openOrder;
    private final Timeline timeline;

    OrderClock(Label label, Supplier<Order> openOrder) {
        this.label = label;
        this.openOrder = openOrder;
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> update()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        update();
        // Stop the timer when this screen is replaced (TODO 2.19). SceneNavigator swaps the root of the
        // same Scene, so the old screen's label loses its scene (the window stays); stop on that.
        if (label != null) {
            label.sceneProperty().addListener((o, oldScene, scene) -> {
                if (scene == null) timeline.stop();
                else if (timeline.getStatus() != Animation.Status.RUNNING) timeline.play();
            });
        }
    }

    void update() {
        if (label != null) {
            String nowStr = AppClock.now().format(DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy"));
            Order order = openOrder.get();
            if (order != null && order.getOrderDate() != null) {
                label.setText(I18n.t("pos.time.withorder", nowStr, order.getOrderDate().format(DateTimeFormatter.ofPattern("HH:mm"))));
            } else {
                label.setText(nowStr);
            }
        }
    }
}
