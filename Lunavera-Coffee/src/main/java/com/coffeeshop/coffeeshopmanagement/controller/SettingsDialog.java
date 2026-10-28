package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.config.ShopSettings;
import com.coffeeshop.coffeeshopmanagement.dao.TableDAO;
import com.coffeeshop.coffeeshopmanagement.service.StockLevel;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * The settings window of the owner (TODO 4.7), opened from the admin dashboard: the shop's name, address and phone (printed on
 * the receipts), how many tables there are (the table list itself is {@link TableManagerDialog}), the shop-wide low-stock level
 * (a product with its own level keeps it, see {@link StockLevel}) and the bank details of the transfer QR (set in the existing
 * transfer-QR dialog, which also holds the picture and logs the change).
 *
 * <p>Everything typed here is saved together by {@link ShopSettings#saveAll}: all of it or none of it. Administrators only.
 */
final class SettingsDialog {

    /** What is read before the window opens: the number of tables and the account text kept next to the QR picture. */
    record Snapshot(int tableCount, String bankText) {}

    private SettingsDialog() {}

    /**
     * Reads what the window needs (off the UI thread), then shows it. {@code onTablesChanged} runs after the table list was
     * changed from here, {@code openBank} opens the transfer-QR dialog, {@code onSaved} runs on the UI thread after a save.
     */
    static void show(Runnable onTablesChanged, Runnable openBank, Runnable onSaved) {
        Async.run(SettingsDialog::readSnapshot,
                snapshot -> open(snapshot, onTablesChanged, openBank, onSaved),
                error -> AlertUtil.error(I18n.t("settings.title"), I18n.t("settings.load.fail")));
    }

    private static Snapshot readSnapshot() {
        int tables = new TableDAO().findAll().size();
        String bank = TransferQrGuard.dataPicture().map(TransferQrGuard::accountInfo).orElse("");
        return new Snapshot(tables, bank == null ? "" : bank.strip());
    }

    private static void open(Snapshot snapshot, Runnable onTablesChanged, Runnable openBank, Runnable onSaved) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("settings.title"));
        dialog.setHeaderText(null);
        AlertUtil.configure(dialog);
        ButtonType save = new ButtonType(I18n.t("settings.save"), ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        AlertUtil.setDefaultButton(dialog, save);
        AlertUtil.confirmBeforeDiscarding(dialog, ButtonType.CANCEL);

        // --- shop details
        TextField nameField = new TextField(ShopSettings.cachedName());
        TextLengthLimiter.limit(nameField, ShopSettings.NAME_MAX);
        TextField addressField = new TextField(ShopSettings.cachedAddress());
        TextLengthLimiter.limit(addressField, ShopSettings.ADDRESS_MAX);
        addressField.setPromptText(I18n.t("settings.address.prompt"));
        TextField phoneField = new TextField(ShopSettings.cachedPhone());
        TextLengthLimiter.limit(phoneField, ShopSettings.PHONE_MAX);
        phoneField.setPromptText(I18n.t("settings.phone.prompt"));
        TextField taxField = new TextField(ShopSettings.cachedTaxCode());
        TextLengthLimiter.limit(taxField, ShopSettings.TAX_CODE_MAX + 6); // room for spaces typed between the digits
        taxField.setPromptText(I18n.t("settings.tax.prompt"));
        nameField.setPrefColumnCount(26);

        // --- tables
        Label tableCount = new Label(I18n.t("settings.tables.count", snapshot.tableCount()));
        Button manageTables = new Button(I18n.t("settings.tables.manage"));
        manageTables.setOnAction(e -> TableManagerDialog.show(() -> {
            Async.run(() -> new TableDAO().findAll().size(),
                    count -> tableCount.setText(I18n.t("settings.tables.count", count)),
                    error -> { /* the number is only a hint; the list itself was saved */ });
            if (onTablesChanged != null) onTablesChanged.run();
        }));
        HBox tableRow = new HBox(12, tableCount, manageTables);
        tableRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        // --- low stock
        TextField lowStockField = new TextField(String.valueOf(StockLevel.defaultLevel()));
        TextLengthLimiter.limit(lowStockField, 7);
        lowStockField.setPrefColumnCount(7);

        // --- bank details (the transfer QR)
        Label bankText = wrapped(snapshot.bankText().isEmpty() ? I18n.t("settings.bank.none") : snapshot.bankText());
        Button editBank = new Button(I18n.t("settings.bank.edit"));
        editBank.setOnAction(e -> {
            if (openBank != null) openBank.run();
        });
        VBox bankBox = new VBox(8, bankText, editBank);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        grid.setPadding(new Insets(18));
        int row = 0;
        grid.add(heading(I18n.t("settings.section.shop")), 0, row++, 2, 1);
        grid.add(new Label(I18n.t("setup.name.label")), 0, row);
        grid.add(nameField, 1, row++);
        grid.add(new Label(I18n.t("settings.address.label")), 0, row);
        grid.add(addressField, 1, row++);
        grid.add(new Label(I18n.t("settings.phone.label")), 0, row);
        grid.add(phoneField, 1, row++);
        grid.add(new Label(I18n.t("settings.tax.label")), 0, row);
        grid.add(taxField, 1, row++);
        grid.add(wrapped(I18n.t("settings.shop.hint")), 0, row++, 2, 1);

        grid.add(heading(I18n.t("settings.section.tables")), 0, row++, 2, 1);
        grid.add(tableRow, 0, row++, 2, 1);

        grid.add(heading(I18n.t("settings.section.stock")), 0, row++, 2, 1);
        grid.add(new Label(I18n.t("settings.lowstock.label")), 0, row);
        grid.add(lowStockField, 1, row++);
        grid.add(wrapped(I18n.t("settings.lowstock.hint")), 0, row++, 2, 1);

        grid.add(heading(I18n.t("settings.section.bank")), 0, row++, 2, 1);
        grid.add(bankBox, 0, row++, 2, 1);
        grid.add(wrapped(I18n.t("settings.bank.hint")), 0, row, 2, 1);
        grid.getColumnConstraints().add(new javafx.scene.layout.ColumnConstraints());
        javafx.scene.layout.ColumnConstraints fill = new javafx.scene.layout.ColumnConstraints();
        fill.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().add(fill);
        dialog.getDialogPane().setContent(grid);
        nameField.requestFocus();

        dialog.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, event -> {
            String problem = ShopSettings.problemWith(nameField.getText(), addressField.getText(),
                    phoneField.getText(), taxField.getText(), lowStockField.getText());
            if (problem != null) {
                AlertUtil.warning(I18n.t("settings.title"), problem);
                event.consume();
            }
        });

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != save) return;
        String name = nameField.getText();
        String address = addressField.getText();
        String phone = phoneField.getText();
        String tax = taxField.getText();
        String level = lowStockField.getText();
        Async.run(() -> {
                    Permissions.requireAdmin("change the shop settings");
                    ShopSettings.saveAll(name, address, phone, tax, level);
                    SecurityLog.event(SecurityLog.actor() + " changed the shop settings");
                    return Boolean.TRUE;
                },
                saved -> {
                    AlertUtil.info(I18n.t("common.saved"), I18n.t("settings.saved"));
                    if (onSaved != null) onSaved.run();
                },
                error -> AlertUtil.error(I18n.t("settings.title"), I18n.t("settings.save.fail")));
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-padding: 8 0 0 0;");
        return label;
    }

    private static Label wrapped(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(440);
        return label;
    }
}
