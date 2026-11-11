package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.ClockOffer;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;

/**
 * The sidebar every screen with a sidebar has, written once (TODO 6.54, TODO 6.4): the buttons that open the other screens, the
 * check that an employee may not open an administrator screen, and the sign-out. Before this the same 60 lines stood in
 * {@link AdminScreen} (and so in the five administrator screens), {@link OrderController}, {@link CustomerController} and
 * {@link EmployeeController}, and a change to the sign-out (the clock-out question of 5.38, the backup question of 5.18) had to be made
 * four times.
 *
 * <p>The handlers are public and marked {@code @FXML}, so an FXML file can name them on any subclass (the FXML loader finds inherited
 * public methods; this class is public for that reason). A screen that has no button for one of them simply never names it.
 *
 * <p>One thing differs between the screens and is a hook: {@link #confirmLeaving()}. The till overrides it to ask "leave this
 * order?" (and to save it), and the check runs BEFORE anything is opened or anyone is signed out; every other screen leaves at once.
 */
public abstract class ScreenNavigation {

    /**
     * Asked before this screen is left by a sidebar button or by the sign-out. Return false to stay (the person said no, or the
     * work could not be kept). The default leaves at once.
     */
    protected boolean confirmLeaving() {
        return true;
    }

    /** A screen for administrators only: anyone else is told so; otherwise the screen is left (after {@link #confirmLeaving()}) and the other opens. */
    private void openAdminScreen(ActionEvent event, String fxml) {
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("admin.only.message"));
            return;
        }
        if (confirmLeaving()) SceneNavigator.switchScene(event, fxml);
    }

    private void open(ActionEvent event, String fxml) {
        if (confirmLeaving()) SceneNavigator.switchScene(event, fxml);
    }

    @FXML
    public void openDashboard(ActionEvent event) {
        open(event, Session.isAdmin() ? "/fxml/admin-trangchu.fxml" : "/fxml/employee-trangchu.fxml");
    }

    @FXML
    public void openProductManagement(ActionEvent event) {
        openAdminScreen(event, "/fxml/quanlysanpham.fxml");
    }

    @FXML
    public void openOrderManagement(ActionEvent event) {
        open(event, "/fxml/quanlydonhang.fxml");
    }

    @FXML
    public void openCustomerManagement(ActionEvent event) {
        open(event, "/fxml/quanlykhachhang.fxml");
    }

    @FXML
    public void openCategoryManagement(ActionEvent event) {
        openAdminScreen(event, "/fxml/quanlydanhmuc.fxml");
    }

    @FXML
    public void openAccountManagement(ActionEvent event) {
        openAdminScreen(event, "/fxml/quanlytaikhoan.fxml");
    }

    @FXML
    public void openStaffManagement(ActionEvent event) {
        openAdminScreen(event, "/fxml/quanlynhanvien.fxml");
    }

    @FXML
    public void logout(ActionEvent event) {
        if (!confirmLeaving()) return;
        // First "clock out?" when the person is still clocked in (TODO 5.38), then, for an administrator who has not saved a backup by
        // hand for a week, the backup question (TODO 5.18; it also tries the daily automatic backup, TODO 2.62), then the sign-out.
        ClockOffer.beforeSignOut(() -> BackupDialogs.offerBeforeLeaving(BackupDialogs.windowOf(event), () -> {
            Session.clear();
            SceneNavigator.switchScene(event, "/fxml/dangnhap.fxml");
        }));
    }
}
