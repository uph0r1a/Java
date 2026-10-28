package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.config.AppFlags;
import com.coffeeshop.coffeeshopmanagement.config.ShopSettings;
import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import com.coffeeshop.coffeeshopmanagement.service.SetupPlan;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.function.Consumer;

/**
 * The short setup window the owner sees once, on the first administrator login of a new shop (TODO 4.15): the name of the shop,
 * how many tables there are, and a reminder that the starter menu's prices and stock are placeholders. The password of the first
 * administrator was already chosen by the owner in {@link FirstRunDialog}, so that part of the item needs no step here.
 * "Để sau" skips it for good (everything can still be done from the dashboard: "Quản lý bàn", the product screen).
 */
public final class SetupWizard {

    private SetupWizard() {}

    /** What happened: {@code saved} when the owner pressed the save button and everything was applied. */
    public record Result(boolean saved, boolean openProducts) {}

    /**
     * Shows the window. {@code tableNumbers} are the tables that exist now; {@code done} runs on the UI thread when the window is
     * over (and, after saving, when the changes are written). The "setup pending" mark is cleared when the owner skips and
     * after a successful save; after a failed save it stays, so the window comes back at the next login.
     */
    public static void show(Window owner, List<Integer> tableNumbers, Consumer<Result> done) {
        Dialog<Boolean> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle(I18n.t("setup.title"));
        dialog.setHeaderText(null);
        AlertUtil.configure(dialog);
        ButtonType save = new ButtonType(I18n.t("setup.save"), ButtonData.OK_DONE);
        // OTHER, not CANCEL_CLOSE: Esc and the window's X must not skip the setup by accident.
        ButtonType later = new ButtonType(I18n.t("setup.later"), ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(save, later);
        AlertUtil.setDefaultButton(dialog, save);

        Label intro = wrapped(I18n.t("setup.intro"));
        TextField nameField = new TextField(ShopSettings.cachedName());
        TextLengthLimiter.limit(nameField, ShopSettings.NAME_MAX);
        TextField tablesField = new TextField(String.valueOf(tableNumbers.size()));
        TextLengthLimiter.limit(tablesField, 3);
        tablesField.setPrefColumnCount(5);
        CheckBox openProducts = new CheckBox(I18n.t("setup.menu.open"));
        openProducts.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(18));
        int row = 0;
        grid.add(intro, 0, row++, 2, 1);
        grid.add(new Label(I18n.t("setup.name.label")), 0, row);
        grid.add(nameField, 1, row++);
        grid.add(new Label(I18n.t("setup.tables.label")), 0, row);
        grid.add(tablesField, 1, row++);
        grid.add(wrapped(I18n.t("setup.tables.hint")), 0, row++, 2, 1);
        grid.add(new VBox(6, wrapped(I18n.t("setup.menu.text")), openProducts), 0, row++, 2, 1);
        grid.add(wrapped(I18n.t("setup.password.done")), 0, row, 2, 1);
        dialog.getDialogPane().setContent(grid);
        nameField.requestFocus();

        dialog.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, event -> {
            String problem = problemWith(nameField.getText(), tablesField.getText());
            if (problem != null) {
                AlertUtil.warning(I18n.t("setup.title"), problem);
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == save);
        boolean saved = Boolean.TRUE.equals(dialog.showAndWait().orElse(false));

        if (!saved) {
            Async.run(() -> {
                AppFlags.clear(AppFlags.SETUP_PENDING);
                return Boolean.TRUE;
            }, ok -> done.accept(new Result(false, false)), error -> done.accept(new Result(false, false)));
            return;
        }
        String name = ShopSettings.clean(nameField.getText());
        int wanted = SetupPlan.parseTableCount(tablesField.getText());
        boolean open = openProducts.isSelected();
        Async.run(() -> apply(name, wanted, tableNumbers),
                kept -> {
                    if (kept > 0) {
                        AlertUtil.warning(I18n.t("setup.title"), I18n.t("setup.tables.kept", kept));
                    }
                    done.accept(new Result(true, open));
                },
                error -> {
                    AlertUtil.error(I18n.t("setup.title"), I18n.t("setup.fail", String.valueOf(error.getMessage())));
                    done.accept(new Result(false, false)); // the mark stays: the window comes back at the next login
                });
    }

    /** Writes the shop name and brings the tables to the wanted number; returns how many tables could not be removed (in use). Off the UI thread. */
    static int apply(String shopName, int wantedTables, List<Integer> tableNumbers) {
        ShopSettings.setName(shopName);
        SetupPlan.TablePlan plan = SetupPlan.planTables(tableNumbers, wantedTables);
        TableDAO tables = new TableDAO();
        int kept = 0;
        for (int i = 0; i < plan.toAdd(); i++) {
            tables.add("", SetupPlan.DEFAULT_SEATS);
        }
        for (int number : plan.toRemove()) {
            if (!tables.remove(number)) kept++;
        }
        AppFlags.clear(AppFlags.SETUP_PENDING);
        AppFlags.clear(AppFlags.REVIEW_MENU_NOTICE); // the window already told the owner to review the starter menu
        return kept;
    }

    /** The first thing wrong with what was typed, or null. Separate from the window so it can be tested. */
    static String problemWith(String shopName, String tableCount) {
        String problem = ShopSettings.problem(ShopSettings.clean(shopName));
        if (problem != null) return problem;
        if (SetupPlan.parseTableCount(tableCount) == null) {
            return I18n.t("setup.tables.invalid", com.coffeeshop.coffeeshopmanagement.service.TableRules.MAX_TABLES);
        }
        return null;
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(430);
        return label;
    }
}
