package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Session;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.Pane;

/**
 * Adds a "Đổi mật khẩu" button above the logout button of every screen that has one (TODO 2.2), so any signed-in
 * person can change their own password. Done in one place after a screen is loaded (SceneNavigator's screen
 * decorator) instead of editing the seven sidebars; it reuses the logout button's style so it matches.
 */
public final class SidebarExtras {

    static final String BUTTON_ID = "changePasswordButton";

    private SidebarExtras() {
    }

    public static void addChangePasswordButton(Parent root) {
        if (Session.getCurrentUser() == null) return; // login screen: nobody to change a password for
        Node logout = root.lookup(".logout-button");
        if (logout == null || !(logout.getParent() instanceof Pane pane) || pane.lookup("#" + BUTTON_ID) != null) {
            return;
        }
        Button change = new Button(I18n.t("password.title"));
        change.setId(BUTTON_ID);
        change.getStyleClass().addAll(logout.getStyleClass());
        change.setPrefHeight(42.0);
        change.setMaxWidth(Double.MAX_VALUE);
        change.setOnAction(e -> PasswordChangeDialog.showVoluntary(Session.getCurrentUser()));
        pane.getChildren().add(pane.getChildren().indexOf(logout), change);
    }
}
