package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.TableService;
import com.coffeeshop.coffeeshopmanagement.config.ShopSettings;
import com.coffeeshop.coffeeshopmanagement.service.ShopRules;
import com.coffeeshop.coffeeshopmanagement.service.SupportBundle;
import com.coffeeshop.coffeeshopmanagement.service.StockLevel;
import com.coffeeshop.coffeeshopmanagement.util.BusinessDay;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Permissions;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptPrinting;
import com.coffeeshop.coffeeshopmanagement.util.SecurityLog;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.util.TransferQrGuard;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
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

    @com.coffeeshop.coffeeshopmanagement.util.OffScreenThread
    private static Snapshot readSnapshot() {
        int tables = new TableService().findAll().size();
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
        TextField priceNoteField = new TextField(com.coffeeshop.coffeeshopmanagement.config.PriceNote.cached()); // off until written (TODO 2.103)
        TextLengthLimiter.limit(priceNoteField, com.coffeeshop.coffeeshopmanagement.config.PriceNote.MAX);
        priceNoteField.setPromptText(I18n.t("pos.total.note"));
        nameField.setPrefColumnCount(26);

        // --- tables
        Label tableCount = new Label(I18n.t("settings.tables.count", snapshot.tableCount()));
        Button manageTables = new Button(I18n.t("settings.tables.manage"));
        manageTables.setOnAction(e -> TableManagerDialog.show(() -> {
            Async.run(() -> new TableService().findAll().size(),
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

        // --- the shop's rules (TODO 4.49): the cashier's discount limit and the loyalty points numbers
        ShopRules.Values rules = ShopRules.current();
        TextField staffField = ruleField(rules.staffDiscountPercent());
        TextField earnField = ruleField(rules.earnDong());
        TextField spendField = ruleField(rules.spendDong());
        TextField billField = ruleField(rules.maxPercentOfBill());
        TextField minField = ruleField(rules.minPoints());
        TextField silverField = ruleField(rules.silverFrom());
        TextField goldField = ruleField(rules.goldFrom());
        TextField birthdayField = ruleField(rules.birthdayPercent());
        TextField slotField = ruleField(rules.slotMinutes());
        TextField idleField = ruleField(rules.idleMinutes());
        TextField forgottenField = ruleField(rules.forgottenHours());
        // --- when the shop's day starts (TODO 4.50): every 30 minutes up to 08:00, plus the stored time if it is another one
        ComboBox<String> dayStartBox = new ComboBox<>();
        for (int minutes = 0; minutes <= 8 * 60; minutes += 30) dayStartBox.getItems().add(BusinessDay.format(minutes));
        String currentDayStart = BusinessDay.format(BusinessDay.startMinutes());
        if (!dayStartBox.getItems().contains(currentDayStart)) dayStartBox.getItems().add(currentDayStart);
        dayStartBox.setValue(currentDayStart);
        javafx.scene.control.ComboBox<String> salaryBox = new javafx.scene.control.ComboBox<>(javafx.collections.FXCollections.observableArrayList(
                I18n.t("settings.rules.salary.monthly"), I18n.t("settings.rules.salary.hourly"))); // what the salary of the staff table is (TODO 4.60)
        salaryBox.getSelectionModel().select(rules.salaryHourly());
        TextField monthlyHoursField = ruleField(rules.monthlyHours());
        TextField freeField = ruleField(rules.freeDrinksPerDay()); // free cups a cashier may give one person a day (TODO 4.61)

        // --- text size (TODO 5.44): "Vừa" or "Lớn", per computer
        ComboBox<String> textSizeBox = new ComboBox<>();
        textSizeBox.getItems().addAll(I18n.t("settings.textsize.normal"), I18n.t("settings.textsize.large"));
        textSizeBox.getSelectionModel().select(com.coffeeshop.coffeeshopmanagement.util.TextSize.isLarge() ? 1 : 0);

        // --- alert sounds (TODO 5.47): per computer, off until switched on; the two buttons let the owner hear them and check the speaker
        javafx.scene.control.CheckBox soundBox = new javafx.scene.control.CheckBox(I18n.t("settings.sounds.label"));
        soundBox.setSelected(com.coffeeshop.coffeeshopmanagement.util.AlertSounds.isOn());
        Button tryLow = new Button(I18n.t("settings.sounds.try.low"));
        tryLow.setOnAction(e -> com.coffeeshop.coffeeshopmanagement.util.AlertPlayer.preview(com.coffeeshop.coffeeshopmanagement.util.AlertSounds.Cue.CODE_NOT_FOUND));
        Button tryChime = new Button(I18n.t("settings.sounds.try.chime"));
        tryChime.setOnAction(e -> com.coffeeshop.coffeeshopmanagement.util.AlertPlayer.preview(com.coffeeshop.coffeeshopmanagement.util.AlertSounds.Cue.BOOKING_DUE));

        // --- printing the receipt on THIS computer (TODO 5.39): where it goes, the paper, what follows a payment, the copies
        ReceiptPrinting.Choice print = ReceiptPrinting.load();
        ComboBox<String> printWhereBox = new ComboBox<>();
        printWhereBox.getItems().addAll(I18n.t("settings.print.where.ask"), I18n.t("settings.print.where.default"), I18n.t("settings.print.where.named"));
        printWhereBox.getSelectionModel().select(print.where().ordinal());
        java.util.List<String> installed = new java.util.ArrayList<>();
        try {
            for (javafx.print.Printer one : javafx.print.Printer.getAllPrinters()) installed.add(one.getName());
        } catch (RuntimeException e) {
            // no list: the saved name is still shown below
        }
        ComboBox<String> printerBox = new ComboBox<>();
        printerBox.getItems().addAll(installed);
        if (!print.printer().isEmpty() && !installed.contains(print.printer())) printerBox.getItems().add(print.printer()); // one that is switched off today
        printerBox.setValue(print.printer().isEmpty() ? null : print.printer());
        ComboBox<String> printPaperBox = new ComboBox<>();
        printPaperBox.getItems().addAll(I18n.t("settings.print.paper.auto"), I18n.t("settings.print.paper.58"), I18n.t("settings.print.paper.80"));
        printPaperBox.getSelectionModel().select(print.paper().ordinal());
        ComboBox<String> printAfterBox = new ComboBox<>();
        printAfterBox.getItems().addAll(I18n.t("settings.print.after.ask"), I18n.t("settings.print.after.auto"));
        printAfterBox.getSelectionModel().select(print.after().ordinal());
        ComboBox<Integer> printCopiesBox = new ComboBox<>();
        for (int n = 1; n <= ReceiptPrinting.MAX_COPIES; n++) printCopiesBox.getItems().add(n);
        printCopiesBox.setValue(print.copies());
        Runnable syncPrint = () -> {
            int where = Math.max(0, printWhereBox.getSelectionModel().getSelectedIndex());
            printerBox.setDisable(where != ReceiptPrinting.Where.NAMED.ordinal());
            printAfterBox.setDisable(where == ReceiptPrinting.Where.ASK.ordinal()); // printing at once needs a printer that is not asked for each time
            if (where == ReceiptPrinting.Where.ASK.ordinal()) printAfterBox.getSelectionModel().select(0);
        };
        printWhereBox.setOnAction(e -> syncPrint.run());
        syncPrint.run();

        // --- the details for support (TODO 5.42): one zip with the facts and the filtered log, made on request
        Button supportButton = new Button(I18n.t("settings.support.button"));
        supportButton.setOnAction(e -> saveSupportDetails(dialog));

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
        grid.add(new Label(I18n.t("settings.pricenote.label")), 0, row);
        grid.add(priceNoteField, 1, row++);
        grid.add(wrapped(I18n.t("settings.pricenote.hint")), 0, row++, 2, 1);
        grid.add(wrapped(I18n.t("settings.shop.hint")), 0, row++, 2, 1);

        grid.add(heading(I18n.t("settings.section.tables")), 0, row++, 2, 1);
        grid.add(tableRow, 0, row++, 2, 1);

        grid.add(heading(I18n.t("settings.section.day")), 0, row++, 2, 1);
        grid.add(new Label(I18n.t("settings.daystart.label")), 0, row);
        grid.add(dayStartBox, 1, row++);
        grid.add(wrapped(I18n.t("settings.daystart.hint")), 0, row++, 2, 1);

        grid.add(heading(I18n.t("settings.section.stock")), 0, row++, 2, 1);
        grid.add(new Label(I18n.t("settings.lowstock.label")), 0, row);
        grid.add(lowStockField, 1, row++);
        grid.add(wrapped(I18n.t("settings.lowstock.hint")), 0, row++, 2, 1);

        grid.add(heading(I18n.t("settings.section.rules")), 0, row++, 2, 1);
        grid.add(new Label(I18n.t("settings.rules.staff.label")), 0, row);
        grid.add(staffField, 1, row++);
        grid.add(new Label(I18n.t("settings.rules.earn.label")), 0, row);
        grid.add(earnField, 1, row++);
        grid.add(new Label(I18n.t("settings.rules.spend.label")), 0, row);
        grid.add(spendField, 1, row++);
        grid.add(new Label(I18n.t("settings.rules.bill.label")), 0, row);
        grid.add(billField, 1, row++);
        grid.add(new Label(I18n.t("settings.rules.min.label")), 0, row);
        grid.add(minField, 1, row++);
        grid.add(new Label(I18n.t("settings.rules.silver.label")), 0, row);
        grid.add(silverField, 1, row++);
        grid.add(new Label(I18n.t("settings.rules.gold.label")), 0, row);
        grid.add(goldField, 1, row++);
        grid.add(new Label(I18n.t("settings.rules.birthday.label")), 0, row);
        grid.add(birthdayField, 1, row++);
        grid.add(new Label(I18n.t("settings.rules.slot.label")), 0, row);
        grid.add(slotField, 1, row++);
        grid.add(new Label(I18n.t("settings.rules.idle.label")), 0, row);
        grid.add(idleField, 1, row++);
        grid.add(new Label(I18n.t("settings.rules.forgotten.label")), 0, row);
        grid.add(forgottenField, 1, row++);
        grid.add(new Label(I18n.t("settings.rules.salary.label")), 0, row);
        grid.add(salaryBox, 1, row++);
        grid.add(new Label(I18n.t("settings.rules.hours.label")), 0, row);
        grid.add(monthlyHoursField, 1, row++);
        grid.add(new Label(I18n.t("settings.rules.free.label")), 0, row);
        grid.add(freeField, 1, row++);
        grid.add(wrapped(I18n.t("settings.rules.hint")), 0, row++, 2, 1);

        grid.add(heading(I18n.t("settings.section.bank")), 0, row++, 2, 1);
        grid.add(bankBox, 0, row++, 2, 1);
        grid.add(wrapped(I18n.t("settings.bank.hint")), 0, row++, 2, 1);

        // --- the size of the text on THIS computer (TODO 5.44): not a shop rule, so it is not saved to the database
        grid.add(heading(I18n.t("settings.section.screen")), 0, row++, 2, 1);
        grid.add(new Label(I18n.t("settings.textsize.label")), 0, row);
        grid.add(textSizeBox, 1, row++);
        grid.add(wrapped(I18n.t("settings.textsize.hint")), 0, row++, 2, 1);
        grid.add(soundBox, 0, row++, 2, 1); // TODO 5.47
        grid.add(new javafx.scene.layout.HBox(8, tryLow, tryChime), 0, row++, 2, 1);
        grid.add(wrapped(I18n.t("settings.sounds.hint")), 0, row++, 2, 1);

        // --- printing the receipt on this computer (TODO 5.39): not a shop rule either, so not in the database
        grid.add(heading(I18n.t("settings.section.print")), 0, row++, 2, 1);
        grid.add(new Label(I18n.t("settings.print.where")), 0, row);
        grid.add(printWhereBox, 1, row++);
        grid.add(new Label(I18n.t("settings.print.printer")), 0, row);
        grid.add(printerBox, 1, row++);
        grid.add(new Label(I18n.t("settings.print.paper")), 0, row);
        grid.add(printPaperBox, 1, row++);
        grid.add(new Label(I18n.t("settings.print.after")), 0, row);
        grid.add(printAfterBox, 1, row++);
        grid.add(new Label(I18n.t("settings.print.copies")), 0, row);
        grid.add(printCopiesBox, 1, row++);
        grid.add(wrapped(I18n.t("settings.print.hint")), 0, row++, 2, 1);

        // --- for whoever looks after the program (TODO 5.42)
        grid.add(heading(I18n.t("settings.section.support")), 0, row++, 2, 1);
        grid.add(supportButton, 0, row++, 2, 1);
        grid.add(wrapped(I18n.t("settings.support.hint")), 0, row++, 2, 1);

        // --- before opening day (TODO 4.57): clears the test sales; its own window asks for a backup and the shop's name first
        grid.add(heading(I18n.t("settings.section.opening")), 0, row++, 2, 1);
        Button startSelling = new Button(I18n.t("settings.opening.button"));
        startSelling.setOnAction(e -> StartSellingDialog.show(() -> {
            if (onTablesChanged != null) onTablesChanged.run();
            if (onSaved != null) onSaved.run();
        }));
        grid.add(startSelling, 0, row++, 2, 1);
        grid.add(wrapped(I18n.t("settings.opening.hint")), 0, row++, 2, 1);
        // The staff guides (TODO 6.71) and which program this is, for "which version do you have?" (TODO 5.41)
        Button ownerHelp = new Button(I18n.t("settings.help.owner"));
        ownerHelp.setOnAction(e -> HelpDialog.show(com.coffeeshop.coffeeshopmanagement.util.HelpPages.OWNER));
        Button cashierHelp = new Button(I18n.t("settings.help.cashier"));
        cashierHelp.setOnAction(e -> HelpDialog.show(com.coffeeshop.coffeeshopmanagement.util.HelpPages.CASHIER));
        grid.add(new javafx.scene.layout.HBox(8, ownerHelp, cashierHelp), 0, row++, 2, 1);
        grid.add(wrapped(com.coffeeshop.coffeeshopmanagement.util.AppVersion.label()), 0, row, 2, 1);
        grid.getColumnConstraints().add(new javafx.scene.layout.ColumnConstraints());
        javafx.scene.layout.ColumnConstraints fill = new javafx.scene.layout.ColumnConstraints();
        fill.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().add(fill);
        // The window is long (shop, tables, stock, ten rules, bank): it scrolls instead of growing past a small screen.
        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(560);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        com.coffeeshop.coffeeshopmanagement.util.FitFix.wrapLabels(grid); // the longer English sentences wrap instead of being cut
        dialog.getDialogPane().setContent(scroll);
        nameField.requestFocus();

        dialog.getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, event -> {
            String problem = ShopSettings.problemWith(nameField.getText(), addressField.getText(),
                    phoneField.getText(), taxField.getText(), lowStockField.getText(), dayStartBox.getValue());
            if (problem == null && printWhereBox.getSelectionModel().getSelectedIndex() == ReceiptPrinting.Where.NAMED.ordinal()
                    && (printerBox.getValue() == null || printerBox.getValue().isBlank())) {
                problem = I18n.t("settings.print.choose"); // "this printer" with no printer chosen
            }
            if (problem == null) {
                problem = ShopRules.problemWith(staffField.getText(), earnField.getText(), spendField.getText(), billField.getText(),
                        minField.getText(), silverField.getText(), goldField.getText(), birthdayField.getText(), slotField.getText(),
                        idleField.getText(), forgottenField.getText(), Integer.toString(salaryBox.getSelectionModel().getSelectedIndex()),
                        monthlyHoursField.getText(), freeField.getText());
            }
            if (problem != null) {
                AlertUtil.warning(I18n.t("settings.title"), problem);
                event.consume();
            }
        });

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != save) return;
        com.coffeeshop.coffeeshopmanagement.util.TextSize.choose(textSizeBox.getSelectionModel().getSelectedIndex() == 1); // at once, in every open window
        com.coffeeshop.coffeeshopmanagement.util.AlertSounds.choose(soundBox.isSelected()); // TODO 5.47: this computer only
        ReceiptPrinting.save(new ReceiptPrinting.Choice(ReceiptPrinting.Where.values()[Math.max(0, printWhereBox.getSelectionModel().getSelectedIndex())],
                printerBox.getValue(), ReceiptPrinting.Paper.values()[Math.max(0, printPaperBox.getSelectionModel().getSelectedIndex())],
                ReceiptPrinting.AfterPayment.values()[Math.max(0, printAfterBox.getSelectionModel().getSelectedIndex())],
                printCopiesBox.getValue() == null ? 1 : printCopiesBox.getValue())); // TODO 5.39: this computer only
        String name = nameField.getText();
        String address = addressField.getText();
        String phone = phoneField.getText();
        String tax = taxField.getText();
        String level = lowStockField.getText();
        String priceNote = priceNoteField.getText();
        ShopRules.Values newRules = ShopRules.parse(staffField.getText(), earnField.getText(), spendField.getText(), billField.getText(),
                minField.getText(), silverField.getText(), goldField.getText(), birthdayField.getText(), slotField.getText(),
                idleField.getText(), forgottenField.getText(), Integer.toString(salaryBox.getSelectionModel().getSelectedIndex()),
                        monthlyHoursField.getText(), freeField.getText());
        boolean rulesChanged = !rules.equals(newRules);
        String dayStart = dayStartBox.getValue();
        Async.run(() -> {
                    Permissions.requireAdmin("change the shop settings");
                    ShopSettings.saveAll(name, address, phone, tax, level, newRules, dayStart);
                    com.coffeeshop.coffeeshopmanagement.config.PriceNote.save(priceNote);
                    SecurityLog.event(SecurityLog.actor() + " changed the shop settings"
                            + (rulesChanged ? " (rules now: " + newRules + ")" : "")); // numbers only, TODO 4.49
                    return Boolean.TRUE;
                },
                saved -> {
                    AlertUtil.info(I18n.t("common.saved"), I18n.t("settings.saved"));
                    if (onSaved != null) onSaved.run();
                },
                error -> AlertUtil.error(I18n.t("settings.title"), I18n.t("settings.save.fail")));
    }

    /** Asks where to put the zip, makes it off the screen thread and says where it went (TODO 5.42). */
    private static void saveSupportDetails(Dialog<ButtonType> owner) {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle(I18n.t("settings.support.button"));
        chooser.setInitialFileName(SupportBundle.suggestedName());
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(I18n.t("settings.support.filter"), "*.zip"));
        java.io.File file = chooser.showSaveDialog(owner.getDialogPane().getScene() != null ? owner.getDialogPane().getScene().getWindow() : null);
        if (file == null) return; // cancelled
        java.nio.file.Path target = file.toPath();
        Async.run(() -> SupportBundle.create(target),
                made -> AlertUtil.info(I18n.t("settings.support.button"), I18n.t("settings.support.done", made.getFileName())),
                error -> AlertUtil.error(I18n.t("settings.support.button"), I18n.t("settings.support.fail")));
    }

    private static TextField ruleField(long value) {
        TextField field = new TextField(String.valueOf(value));
        TextLengthLimiter.limit(field, 9);
        field.setPrefColumnCount(9);
        return field;
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
