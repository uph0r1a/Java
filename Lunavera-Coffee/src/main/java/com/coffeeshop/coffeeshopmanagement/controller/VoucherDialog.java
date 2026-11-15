package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import com.coffeeshop.coffeeshopmanagement.model.Voucher;
import com.coffeeshop.coffeeshopmanagement.service.VoucherRules;
import com.coffeeshop.coffeeshopmanagement.service.VoucherService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.SessionGuard;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;

/**
 * "Mã giảm giá" (TODO 4.11, rule D6): the administrator's window for voucher codes. Make a code (percentage or an amount, optional
 * smallest bill, days, number of uses), see every code with how often it was used, and switch one off or on. A used voucher is never
 * deleted (past orders name it); switching it off stops it at the till. {@link VoucherService} and {@link VoucherRules} hold the rules.
 */
public final class VoucherDialog {

    private VoucherDialog() {
    }

    public static void show() {
        if (!SessionGuard.validateNow()) {
            SessionGuard.forceLogout();
            return;
        }
        if (!Session.isAdmin()) {
            AlertUtil.warning(I18n.t("admin.only.title"), I18n.t("voucher.adminonly"));
            return;
        }
        VoucherService dao = new VoucherService();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("voucher.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.setResizable(true);

        TextField codeField = new TextField();
        codeField.setPromptText(I18n.t("voucher.code.prompt"));
        ComboBox<String> kindBox = new ComboBox<>(FXCollections.observableArrayList(Voucher.PERCENT, Voucher.AMOUNT));
        kindBox.setValue(Voucher.PERCENT);
        kindBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String kind) {
                return kind == null ? "" : I18n.t(Voucher.PERCENT.equals(kind) ? "voucher.kind.PERCENT" : "voucher.kind.AMOUNT");
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        TextField valueField = new TextField();
        valueField.setPromptText(I18n.t("voucher.value.prompt"));
        TextField minField = new TextField();
        minField.setPromptText(I18n.t("voucher.min.prompt"));
        DatePicker fromField = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker();
        DatePicker toField = com.coffeeshop.coffeeshopmanagement.util.DateBoxes.picker();
        javafx.scene.control.CheckBox onceBox = new javafx.scene.control.CheckBox(I18n.t("voucher.label.once")); // TODO 4.54
        TextField usesField = new TextField();
        usesField.setPromptText(I18n.t("voucher.uses.prompt"));
        TextLengthLimiter.limit(codeField, VoucherRules.CODE_MAX); // TODO 5.50: stop typing at the limit the rules enforce
        TextLengthLimiter.limit(valueField, TextLengthLimiter.MONEY_MAX);
        TextLengthLimiter.limit(minField, TextLengthLimiter.MONEY_MAX);
        TextLengthLimiter.limit(usesField, TextLengthLimiter.NUMBER_MAX);
        Label status = new Label();
        status.setWrapText(true);
        Button addButton = new Button(I18n.t("voucher.add"));
        addButton.setDefaultButton(true);
        Label hint = new Label(I18n.t("voucher.hint"));
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #6d4c41;");

        TableView<Voucher> table = new TableView<>();
        table.setPrefHeight(260);
        table.setPlaceholder(new Label(I18n.t("voucher.empty")));
        table.getColumns().add(column(I18n.t("voucher.col.code"), 130, v -> v.code()));
        table.getColumns().add(column(I18n.t("voucher.col.worth"), 100, VoucherRules::worth));
        table.getColumns().add(column(I18n.t("voucher.col.min"), 110, v -> CurrencyUtil.format(v.minSubtotal())));
        table.getColumns().add(column(I18n.t("voucher.col.days"), 170,
                v -> (v.validFrom() == null ? "…" : v.validFrom().toString()) + " – " + (v.validTo() == null ? "…" : v.validTo().toString())));
        table.getColumns().add(column(I18n.t("voucher.col.used"), 90,
                v -> v.maxUses() == null ? String.valueOf(v.usedCount()) : v.usedCount() + " / " + v.maxUses()));
        table.getColumns().add(column(I18n.t("voucher.col.state"), 90, v -> I18n.t(v.active() ? "voucher.on" : "voucher.off")));
        table.getColumns().add(column(I18n.t("voucher.col.once"), 130, v -> v.oncePerCustomer() ? I18n.t("common.yes") : ""));
        Button toggleButton = new Button(I18n.t("voucher.toggle"));
        toggleButton.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> toggleButton.setDisable(now == null));

        // TODO 6.14: the database is read off the screen thread; the table is filled when the answer comes back
        Runnable reload = () -> Async.run(dao::findAll,
                found -> table.getItems().setAll(found),
                error -> AlertUtil.error(I18n.t("voucher.title"), error.getMessage()));
        reload.run();

        addButton.setOnAction(e -> {
            Long value = parseLong(valueField.getText());
            Integer uses = usesField.getText() == null || usesField.getText().isBlank() ? null : toInt(parseLong(usesField.getText()));
            boolean usesBad = usesField.getText() != null && !usesField.getText().isBlank() && uses == null;
            BigDecimal min = minField.getText() == null || minField.getText().isBlank() ? BigDecimal.ZERO
                    : MoneyParser.parseOptionalDong(minField.getText());
            String problem = usesBad || min == null ? I18n.t("voucher.val.number")
                    : VoucherRules.newVoucherProblem(codeField.getText(), kindBox.getValue(), value, min, fromField.getValue(), toField.getValue(), uses);
            if (problem != null) {
                status.setText(problem);
                return;
            }
            final String code = codeField.getText();
            final String kind = kindBox.getValue();
            final java.time.LocalDate from = fromField.getValue();
            final java.time.LocalDate to = toField.getValue();
            final boolean once = onceBox.isSelected();
            Async.run(() -> dao.add(code, kind, value, min, from, to, uses, once),
                    made -> {
                        status.setText(I18n.t("voucher.saved", made.code()));
                        codeField.clear();
                        valueField.clear();
                        minField.clear();
                        usesField.clear();
                        onceBox.setSelected(false);
                        fromField.setValue(null);
                        toField.setValue(null);
                        reload.run();
                        codeField.requestFocus();
                    },
                    error -> status.setText(error.getMessage()),
                    addButton);
        });
        toggleButton.setOnAction(e -> {
            Voucher selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            final int id = selected.id();
            final boolean on = !selected.active();
            Async.run(() -> {
                        dao.setActive(id, on);
                        return null;
                    },
                    done -> reload.run(),
                    error -> AlertUtil.error(I18n.t("voucher.title"), error.getMessage()),
                    toggleButton);
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        int row = 0;
        form.addRow(row++, new Label(I18n.t("voucher.label.code")), codeField);
        form.addRow(row++, new Label(I18n.t("voucher.label.kind")), kindBox);
        form.addRow(row++, new Label(I18n.t("voucher.label.value")), valueField);
        form.addRow(row++, new Label(I18n.t("voucher.label.min")), minField);
        form.addRow(row++, new Label(I18n.t("voucher.label.from")), fromField);
        form.addRow(row++, new Label(I18n.t("voucher.label.to")), toField);
        form.addRow(row++, new Label(I18n.t("voucher.label.uses")), usesField);
        form.add(onceBox, 1, row++);
        form.add(addButton, 1, row);
        GridPane.setHgrow(codeField, Priority.ALWAYS);

        VBox content = new VBox(12, hint, form, status, table, toggleButton);
        content.setPadding(new Insets(16));
        content.setPrefWidth(780);
        dialog.getDialogPane().setContent(content);
        javafx.application.Platform.runLater(codeField::requestFocus);
        dialog.showAndWait();
    }

    private static Long parseLong(String text) {
        BigDecimal dong = MoneyParser.parseOptionalDong(text);
        return dong == null ? null : dong.longValue();
    }

    private static Integer toInt(Long value) {
        return value == null || value > Integer.MAX_VALUE ? null : value.intValue();
    }

    private static TableColumn<Voucher, String> column(String title, double width, java.util.function.Function<Voucher, String> text) {
        TableColumn<Voucher, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellValueFactory(data -> new SimpleStringProperty(text.apply(data.getValue())));
        return column;
    }
}
