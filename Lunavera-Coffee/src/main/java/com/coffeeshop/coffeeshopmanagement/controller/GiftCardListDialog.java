package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.GiftCardDAO;
import com.coffeeshop.coffeeshopmanagement.dao.GiftCardDAO.Row;
import com.coffeeshop.coffeeshopmanagement.service.GiftCardService;
import com.coffeeshop.coffeeshopmanagement.model.GiftCard;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CopyText;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.GiftCardText;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Money;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * The administrator's list of gift cards (TODO 4.45): code, first amount, balance, who sold it and when, when it was last used and its
 * state, the money still on cards, and "void" for a card sold by mistake (only a card nothing was spent from; the cash goes back to
 * the customer and the shift close takes it out of the drawer). The rules are in {@link GiftCardService}; the calls run off the screen thread.
 */
final class GiftCardListDialog {

    private static final int LIMIT = 500;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private GiftCardListDialog() {
    }

    static String state(Row row) {
        return I18n.t(row.voided() ? "giftcard.list.state.void" : row.card().balance() > 0 ? "giftcard.list.state.open" : "giftcard.list.state.spent");
    }

    static void show() {
        GiftCardService dao = new GiftCardService();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("giftcard.list.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TableView<Row> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getColumns().add(column(I18n.t("giftcard.list.col.code"), 130, r -> r.card().code()));
        table.getColumns().add(column(I18n.t("giftcard.list.col.first"), 100, r -> CurrencyUtil.format(Money.fromDong(r.card().initialAmount()))));
        table.getColumns().add(column(I18n.t("giftcard.list.col.balance"), 100, r -> CurrencyUtil.format(Money.fromDong(r.card().balance()))));
        table.getColumns().add(column(I18n.t("giftcard.list.col.soldby"), 100, r -> r.card().soldBy()));
        table.getColumns().add(column(I18n.t("giftcard.list.col.date"), 120, r -> r.card().createdAt() == null ? "" : TIME.format(r.card().createdAt())));
        table.getColumns().add(column(I18n.t("giftcard.list.col.last"), 120, r -> r.lastUse() == null ? I18n.t("giftcard.list.never") : TIME.format(r.lastUse())));
        table.getColumns().add(column(I18n.t("giftcard.list.col.state"), 90, GiftCardListDialog::state));
        table.setPlaceholder(new Label(I18n.t("giftcard.list.empty")));
        Label total = new Label();
        total.setStyle("-fx-font-weight: bold;");
        Label cap = new Label(I18n.t("giftcard.list.cap", LIMIT));
        cap.setVisible(false);
        cap.setManaged(false);
        Label message = new Label();
        message.setWrapText(true);
        Button voidButton = new Button(I18n.t("giftcard.void.button"));
        voidButton.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((o, was, now) ->
                voidButton.setDisable(now == null || now.voided() || now.card().balance() <= 0));

        Runnable reload = () -> Async.run(() -> java.util.List.of(dao.findAll(LIMIT), dao.openBalance()), loaded -> {
            @SuppressWarnings("unchecked") java.util.List<Row> rows = (java.util.List<Row>) loaded.get(0);
            table.getItems().setAll(rows);
            GiftCardDAO.OpenBalance open = (GiftCardDAO.OpenBalance) loaded.get(1);
            total.setText(I18n.t("giftcard.list.total", CurrencyUtil.format(Money.fromDong(open.amount()))) + (open.cards() == 0 ? "" : " (" + open.cards() + ")"));
            cap.setVisible(rows.size() >= LIMIT);
            cap.setManaged(rows.size() >= LIMIT);
        }, ex -> message.setText(ex.getMessage()));
        voidButton.setOnAction(e -> {
            Row row = table.getSelectionModel().getSelectedItem();
            if (row == null) return;
            GiftCard card = row.card();
            String amount = CurrencyUtil.format(Money.fromDong(card.balance()));
            if (!AlertUtil.confirm(I18n.t("giftcard.void.button"), I18n.t("giftcard.void.confirm", card.code(), amount))) return;
            voidButton.setDisable(true);
            Async.run(() -> dao.voidCard(card.code()), done -> {
                message.setText(I18n.t("giftcard.void.done", card.code(), amount));
                reload.run();
            }, ex -> {
                message.setText(ex.getMessage());
                reload.run();
            });
        });
        CopyMenus.install(table, CopyMenus.copy("copy.code", (Row r) -> CopyText.giftCardCode(r.card().code())), CopyMenus.button(voidButton)); // TODO 5.46
        reload.run();

        VBox box = new VBox(8, table, total, cap, voidButton, message);
        VBox.setVgrow(table, Priority.ALWAYS);
        box.setPadding(new Insets(16));
        box.setPrefSize(760, 460);
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait();
    }

    private static TableColumn<Row, String> column(String title, double width, Function<Row, String> text) {
        TableColumn<Row, String> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellValueFactory(cell -> new SimpleStringProperty(text.apply(cell.getValue())));
        return column;
    }
}
