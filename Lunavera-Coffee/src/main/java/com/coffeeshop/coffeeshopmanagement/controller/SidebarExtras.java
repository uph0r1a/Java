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
    static final String HELP_BUTTON_ID = "helpButton";

    static final String AUDIT_BUTTON_ID = "auditLogButton";
    static final String EXPENSES_BUTTON_ID = "expensesButton";
    static final String VOUCHER_BUTTON_ID = "voucherButton";

    /** The extra sidebar buttons: the audit log (TODO 4.2), "Chi phí" for the owner (TODO 4.38), "Chốt ca" (TODO 4.6) and the password one. */
    public static void addButtons(Parent root) {
        LanguagePill.add(root); // TODO 1.7
        addAuditLogButton(root);
        addExpensesButton(root);
        addVoucherButton(root);
        addPromotionsButton(root);
        addCloseShiftButton(root);
        addHelpButton(root);
        addTimeClockButton(root);
        addGiftCardButton(root);
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

    /** "Mã giảm giá" above the logout button, for administrators only: voucher codes (TODO 4.11). */
    public static void addVoucherButton(Parent root) {
        if (Session.getCurrentUser() == null || !Session.isAdmin()) return;
        Node logout = root.lookup(".logout-button");
        if (logout == null || !(logout.getParent() instanceof Pane pane) || pane.lookup("#" + VOUCHER_BUTTON_ID) != null) {
            return;
        }
        Button vouchers = new Button(I18n.t("voucher.open"));
        vouchers.setId(VOUCHER_BUTTON_ID);
        vouchers.getStyleClass().addAll(logout.getStyleClass());
        vouchers.setPrefHeight(42.0);
        vouchers.setMaxWidth(Double.MAX_VALUE);
        vouchers.setOnAction(e -> VoucherDialog.show());
        pane.getChildren().add(pane.getChildren().indexOf(logout), vouchers);
    }

    static final String PROMO_BUTTON_ID = "promotionsButton";

    /** "Khuyến mãi tự động" above the logout button, for administrators only: happy hour, buy-X-get-Y and combo prices (TODO 4.55). */
    public static void addPromotionsButton(Parent root) {
        if (Session.getCurrentUser() == null || !Session.isAdmin()) return;
        Node logout = root.lookup(".logout-button");
        if (logout == null || !(logout.getParent() instanceof Pane pane) || pane.lookup("#" + PROMO_BUTTON_ID) != null) {
            return;
        }
        Button promotions = new Button(I18n.t("promo.open"));
        promotions.setId(PROMO_BUTTON_ID);
        promotions.getStyleClass().addAll(logout.getStyleClass());
        promotions.setPrefHeight(42.0);
        promotions.setMaxWidth(Double.MAX_VALUE);
        promotions.setOnAction(e -> PromotionDialog.show());
        pane.getChildren().add(pane.getChildren().indexOf(logout), promotions);
    }

    /** "Nhật ký thao tác" above the other extra buttons, for administrators only: the log is theirs to read (TODO 4.2). */
    public static void addAuditLogButton(Parent root) {
        if (!Session.isAdmin()) return;
        Node logout = root.lookup(".logout-button");
        if (logout == null || !(logout.getParent() instanceof Pane pane) || pane.lookup("#" + AUDIT_BUTTON_ID) != null) {
            return;
        }
        Button audit = new Button(I18n.t("audit.open"));
        audit.setId(AUDIT_BUTTON_ID);
        audit.getStyleClass().addAll(logout.getStyleClass());
        audit.setPrefHeight(42.0);
        audit.setMaxWidth(Double.MAX_VALUE);
        audit.setOnAction(e -> AuditLogWindow.show());
        pane.getChildren().add(pane.getChildren().indexOf(logout), audit);
    }

    /**
     * "Hướng dẫn" above the logout button, for every signed-in person (TODO 6.71): the owner gets the owner's guide, everybody else the
     * cashier's, in the screen language. Settings, where the two guides were first put, is for the owner only.
     */
    public static void addHelpButton(Parent root) {
        if (Session.getCurrentUser() == null) return;
        Node logout = root.lookup(".logout-button");
        if (logout == null || !(logout.getParent() instanceof Pane pane) || pane.lookup("#" + HELP_BUTTON_ID) != null) {
            return;
        }
        Button help = new Button(I18n.t("help.open"));
        help.setId(HELP_BUTTON_ID);
        help.getStyleClass().addAll(logout.getStyleClass());
        help.setPrefHeight(42.0);
        help.setMaxWidth(Double.MAX_VALUE);
        help.setOnAction(e -> HelpDialog.show(Session.isAdmin()
                ? com.coffeeshop.coffeeshopmanagement.util.HelpPages.OWNER : com.coffeeshop.coffeeshopmanagement.util.HelpPages.CASHIER));
        pane.getChildren().add(pane.getChildren().indexOf(logout), help);
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

    static final String GIFT_BUTTON_ID = "giftCardButton";

    /** "Thẻ quà tặng" above the logout button, for every signed-in person: sell a card, look at a balance (TODO 7). */
    public static void addGiftCardButton(Parent root) {
        if (Session.getCurrentUser() == null) return;
        Node logout = root.lookup(".logout-button");
        if (logout == null || !(logout.getParent() instanceof Pane pane) || pane.lookup("#" + GIFT_BUTTON_ID) != null) {
            return;
        }
        Button gift = new Button(I18n.t("giftcard.open"));
        gift.setId(GIFT_BUTTON_ID);
        gift.getStyleClass().addAll(logout.getStyleClass());
        gift.setPrefHeight(42.0);
        gift.setMaxWidth(Double.MAX_VALUE);
        gift.setOnAction(e -> GiftCardDialog.show());
        pane.getChildren().add(pane.getChildren().indexOf(logout), gift);
    }

    static final String CLOCK_BUTTON_ID = "timeClockButton";

    /** "Chấm công" above the logout button, for every signed-in person: clock in and out, and for an administrator everybody's hours (TODO 7). */
    public static void addTimeClockButton(Parent root) {
        if (Session.getCurrentUser() == null) return;
        Node logout = root.lookup(".logout-button");
        if (logout == null || !(logout.getParent() instanceof Pane pane) || pane.lookup("#" + CLOCK_BUTTON_ID) != null) {
            return;
        }
        Button clock = new Button(I18n.t("time.clock.open"));
        clock.setId(CLOCK_BUTTON_ID);
        clock.getStyleClass().addAll(logout.getStyleClass());
        clock.setPrefHeight(42.0);
        clock.setMaxWidth(Double.MAX_VALUE);
        clock.setOnAction(e -> TimeClockDialog.show());
        pane.getChildren().add(pane.getChildren().indexOf(logout), clock);
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
