package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.service.CustomerMergeService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.I18n;

import javafx.scene.control.Button;
import javafx.scene.control.TableView;

/**
 * The two administrator buttons of the customer screen, "merge duplicate" and "make anonymous" (TODO 4.8 and 3.4, rule D21), with the
 * confirmations and the calls to {@link CustomerMergeService}. They were part of {@code CustomerController} until TODO 2.86 (that class
 * was 485 lines against a ceiling of 421); the code is the same, it only works on the table it is given and calls {@code reload} when
 * the list has changed.
 */
final class CustomerMergeButtons {

    private final TableView<Customer> table;
    private final Runnable reload;
    private final CustomerMergeService mergeService = new CustomerMergeService();

    /** @param table the customer table (the buttons work on its selected row) @param reload what to run after a merge or an anonymising */
    CustomerMergeButtons(TableView<Customer> table, Runnable reload) {
        this.table = table;
        this.reload = reload;
    }

    /**
     * "Gộp khách trùng" and "Ẩn danh hóa" for the selected customer (TODO 4.8 and 3.4, rule D21; administrators only, the rules are
     * checked again in {@code CustomerMergeService}). There is no delete: the owner said never delete. An anonymous customer can only
     * be looked at.
     */
    void addTo(javafx.scene.layout.HBox bar, int at) {
        Button merge = new Button(I18n.t("customers.button.merge"));
        Button anonymise = new Button(I18n.t("customers.button.anonymise"));
        for (Button b : new Button[]{merge, anonymise}) {
            b.setPrefHeight(42);
            b.setDisable(true);
        }
        table.getSelectionModel().selectedItemProperty().addListener((o, old, selected) -> {
            boolean none = selected == null || selected.isAnonymised();
            merge.setDisable(none);
            anonymise.setDisable(none);
        });
        merge.setOnAction(e -> handleMerge());
        anonymise.setOnAction(e -> handleAnonymise());
        bar.getChildren().addAll(at, java.util.List.of(merge, anonymise));
    }

    private void handleMerge() {
        Customer duplicate = table.getSelectionModel().getSelectedItem();
        if (duplicate == null) {
            AlertUtil.info(I18n.t("customers.button.merge"), I18n.t("customers.none.selected"));
            return;
        }
        java.util.Optional<Customer> chosen = CustomerPickerDialog.choose(I18n.t("customers.merge.pick.title"),
                I18n.t("customers.merge.pick.intro", duplicate));
        if (chosen.isEmpty()) return;
        Customer keep = chosen.get();
        if (keep.getId() == duplicate.getId()) {
            AlertUtil.warning(I18n.t("customers.merge.pick.title"), I18n.t("customers.merge.same"));
            return;
        }
        Async.run(
                () -> mergeService.impact(duplicate.getId()),
                impact -> {
                    if (!AlertUtil.confirm(I18n.t("customers.merge.confirm.title"), I18n.t("customers.merge.confirm",
                            duplicate, keep, impact.orders(), impact.points()))) {
                        return;
                    }
                    Async.run(
                            () -> mergeService.merge(keep.getId(), duplicate.getId()),
                            result -> {
                                AlertUtil.info(I18n.t("customers.merge.pick.title"), I18n.t("customers.merge.done",
                                        result.ordersMoved(), result.pointsMoved(), result.keptName()));
                                reload.run();
                            },
                            error -> AlertUtil.error(I18n.t("customers.merge.fail"), error.getMessage()),
                            table);
                },
                error -> AlertUtil.error(I18n.t("customers.merge.fail"), error.getMessage()),
                table);
    }

    private void handleAnonymise() {
        Customer target = table.getSelectionModel().getSelectedItem();
        if (target == null) {
            AlertUtil.info(I18n.t("customers.anon.title"), I18n.t("customers.none.selected"));
            return;
        }
        Async.run(
                () -> mergeService.impact(target.getId()),
                impact -> {
                    if (!AlertUtil.confirm(I18n.t("customers.anon.title"),
                            I18n.t("customers.anon.confirm", target.getFullName(), impact.orders()))) {
                        return;
                    }
                    Async.run(
                            () -> {
                                mergeService.anonymise(target.getId());
                                return Boolean.TRUE;
                            },
                            done -> reload.run(),
                            error -> AlertUtil.error(I18n.t("customers.anon.fail"), error.getMessage()),
                            table);
                },
                error -> AlertUtil.error(I18n.t("customers.anon.fail"), error.getMessage()),
                table);
    }
}
