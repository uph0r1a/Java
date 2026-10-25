package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SessionGuard;
import com.coffeeshop.coffeeshopmanagement.util.SceneNavigator;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.HBox;
import javafx.scene.Node;
import javafx.stage.Stage;
import javafx.util.Callback;
import java.util.List;
import java.util.Map;

/**
 * What every administrator screen has in common (TODO 6.4): the sidebar navigation, the "is this still an administrator"
 * check, the edit/delete column of the tables, and the guard that sends an employee away from a management screen. The
 * four screens, each with its own FXML and its own controller, extend it: {@link AdminDashboardController},
 * {@link CategoryController}, {@link ProductController} and {@link AccountController}. The handlers are public and marked
 * {@code @FXML}, so an FXML file can name them on a subclass.
 */
public abstract class AdminScreen {

    // =================================================================== Navigation (shared by every screen)

    @FXML
    public void openDashboard(ActionEvent event) {
        String target = Session.isAdmin() ? "/fxml/admin-trangchu.fxml" : "/fxml/employee-trangchu.fxml";
        SceneNavigator.switchScene(event, target);
    }

    @FXML
    public void openProductManagement(ActionEvent event) {
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("admin.only.message"));
            return;
        }
        SceneNavigator.switchScene(event, "/fxml/quanlysanpham.fxml");
    }

    @FXML
    public void openOrderManagement(ActionEvent event) {
        SceneNavigator.switchScene(event, "/fxml/quanlydonhang.fxml");
    }

    @FXML
    public void openCustomerManagement(ActionEvent event) {
        SceneNavigator.switchScene(event, "/fxml/quanlykhachhang.fxml");
    }

    @FXML
    public void openCategoryManagement(ActionEvent event) {
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("admin.only.message"));
            return;
        }
        SceneNavigator.switchScene(event, "/fxml/quanlydanhmuc.fxml");
    }

    @FXML
    public void openAccountManagement(ActionEvent event) {
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("admin.only.message"));
            return;
        }
        SceneNavigator.switchScene(event, "/fxml/quanlytaikhoan.fxml");
    }

    @FXML
    public void logout(ActionEvent event) {
        // An administrator who has not saved a backup by hand for a week is asked once (TODO 5.18).
        BackupDialogs.offerBeforeLeaving(BackupDialogs.windowOf(event), () -> {
            Session.clear();
            SceneNavigator.switchScene(event, "/fxml/dangnhap.fxml");
        });
    }

    protected boolean requireAdmin() {
        // Re-read the account first: refreshes the role (an admin demoted since login stops
        // being one immediately) and ends the session if it was locked/removed meanwhile.
        if (!SessionGuard.validateNow()) {
            SessionGuard.forceLogout();
            return false;
        }
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("admin.only.operation"));
            return false;
        }
        return true;
    }

    /** Builds a table-cell factory rendering one or two small action buttons per row. */
    protected <T> Callback<TableColumn<T, Void>, TableCell<T, Void>> actionColumnFactory(
            java.util.function.Consumer<T> onEdit, java.util.function.Consumer<T> onDelete) {
        return column -> new TableCell<>() {
            private final Button editButton = new Button("✎");
            private final Button deleteButton = new Button("🗑");
            private final HBox box = new HBox(6, editButton, deleteButton);

            {
                // Icon-only buttons need words: a tooltip for the mouse, accessible text for screen readers (TODO 2.34).
                editButton.setTooltip(new javafx.scene.control.Tooltip(I18n.t("common.edit")));
                editButton.setAccessibleText(I18n.t("common.edit"));
                deleteButton.setTooltip(new javafx.scene.control.Tooltip(I18n.t("common.delete")));
                deleteButton.setAccessibleText(I18n.t("common.delete"));
                editButton.setOnAction(e -> onEdit.accept(getTableView().getItems().get(getIndex())));
                if (onDelete != null) {
                    deleteButton.setOnAction(e -> onDelete.accept(getTableView().getItems().get(getIndex())));
                }
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    box.getChildren().setAll(editButton);
                    if (onDelete != null) {
                        box.getChildren().add(deleteButton);
                    }
                    setGraphic(box);
                }
            }
        };
    }

    @SuppressWarnings("unchecked")
    protected static <T> List<T> castList(Object value) {
        return (List<T>) value;
    }

    @SuppressWarnings("unchecked")
    protected static Map<Integer, Integer> castCountMap(Object value) {
        return (Map<Integer, Integer>) value;
    }

    /**
     * For the three management screens (categories, products, accounts): an employee who somehow reaches one is told so and
     * sent back to the employee home screen. Returns true when the screen must stop initialising. {@code anchor} is any
     * control of the screen (it is how the window is found).
     */
    protected static boolean blockNonAdmin(Node anchor) {
        if (Session.isAdmin()) {
            return false;
        }
        javafx.application.Platform.runLater(() -> {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("admin.only.message"));
            if (anchor != null && anchor.getScene() != null && anchor.getScene().getWindow() instanceof Stage stage) {
                SceneNavigator.switchScene(stage, "/fxml/employee-trangchu.fxml");
            }
        });
        return true;
    }
}
