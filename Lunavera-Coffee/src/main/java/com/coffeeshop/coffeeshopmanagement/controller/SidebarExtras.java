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

    static final String SHIFT_BUTTON_ID = "closeShiftButton";

    static final String EXPENSES_BUTTON_ID = "expensesButton";

    /** The extra sidebar buttons: "Chi phí" for the owner (TODO 4.38), "Chốt ca" (TODO 4.6) and the password one. */
    public static void addButtons(Parent root) {
        addExpensesButton(root);
        addCloseShiftButton(root);
        addPinButtons(root);
        addChangePasswordButton(root);
    }

    static final String LOCK_BUTTON_ID = "lockTillButton";
    static final String PIN_BUTTON_ID = "pinButton";

    /** "Khóa" for everybody signed in, and "Mã PIN" for staff (TODO 4.36), above the logout button like the other extras. */
    public static void addPinButtons(Parent root) {
        if (Session.getCurrentUser() == null) return;
        Node logout = root.lookup(".logout-button");
        if (logout == null || !(logout.getParent() instanceof Pane pane) || pane.lookup("#" + LOCK_BUTTON_ID) != null) {
            return;
        }
        Button lock = new Button(I18n.t("pin.lock.button"));
        lock.setId(LOCK_BUTTON_ID);
        lock.getStyleClass().addAll(logout.getStyleClass());
        lock.setPrefHeight(42.0);
        lock.setMaxWidth(Double.MAX_VALUE);
        lock.setOnAction(e -> LockScreen.lockNow());
        pane.getChildren().add(pane.getChildren().indexOf(logout), lock);
        if (com.coffeeshop.coffeeshopmanagement.service.PinService.mayHavePin(Session.getCurrentUser())) {
            Button pin = new Button(I18n.t("pin.set.title"));
            pin.setId(PIN_BUTTON_ID);
            pin.getStyleClass().addAll(logout.getStyleClass());
            pin.setPrefHeight(42.0);
            pin.setMaxWidth(Double.MAX_VALUE);
            pin.setOnAction(e -> PinSetupDialog.show());
            pane.getChildren().add(pane.getChildren().indexOf(logout), pin);
        }
    }

    /** "Chi phí" above the logout button, for administrators only: expenses and cash taken out of the drawer (TODO 4.38). */
    public static void addExpensesButton(Parent root) {
        if (Session.getCurrentUser() == null || !Session.isAdmin()) return;
        Node logout = root.lookup(".logout-button");
        if (logout == null || !(logout.getParent() instanceof Pane pane) || pane.lookup("#" + EXPENSES_BUTTON_ID) != null) {
            return;
        }
        Button expenses = new Button(I18n.t("expense.open"));
        expenses.setId(EXPENSES_BUTTON_ID);
        expenses.getStyleClass().addAll(logout.getStyleClass());
        expenses.setPrefHeight(42.0);
        expenses.setMaxWidth(Double.MAX_VALUE);
        expenses.setOnAction(e -> ExpenseDialog.show());
        pane.getChildren().add(pane.getChildren().indexOf(logout), expenses);
    }

    /** "Chốt ca" above the logout button, for every signed-in person: counting the drawer is the cashier's job (TODO 4.6). */
    public static void addCloseShiftButton(Parent root) {
        if (Session.getCurrentUser() == null) return;
        Node logout = root.lookup(".logout-button");
        if (logout == null || !(logout.getParent() instanceof Pane pane) || pane.lookup("#" + SHIFT_BUTTON_ID) != null) {
            return;
        }
        Button close = new Button(I18n.t("shift.open"));
        close.setId(SHIFT_BUTTON_ID);
        close.getStyleClass().addAll(logout.getStyleClass());
        close.setPrefHeight(42.0);
        close.setMaxWidth(Double.MAX_VALUE);
        close.setOnAction(e -> ShiftCloseDialog.show());
        pane.getChildren().add(pane.getChildren().indexOf(logout), close);
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
