package com.coffeeshop.coffeeshopmanagement.util;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Runs an action on a timer for as long as a screen is on show (TODO 5.3). The timer stops when the screen is
 * replaced (its node loses its scene, like the POS clock, TODO 2.19) and starts again if the screen is put back.
 */
public final class AutoRefresh {

    private AutoRefresh() {}

    public static Timeline every(Node anchor, Duration period, Runnable action) {
        Timeline timeline = new Timeline(new KeyFrame(period, e -> {
            if (anchor.getScene() != null) action.run();
        }));
        timeline.setCycleCount(Animation.INDEFINITE);
        anchor.sceneProperty().addListener((o, old, scene) -> {
            if (scene == null) timeline.stop();
            else if (timeline.getStatus() != Animation.Status.RUNNING) timeline.play();
        });
        if (anchor.getScene() != null) timeline.play();
        return timeline;
    }
}
