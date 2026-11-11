package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.GiftCard;
import com.coffeeshop.coffeeshopmanagement.service.GiftCardRules;
import com.coffeeshop.coffeeshopmanagement.service.GiftCardService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.CopyText;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Money;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * "Thẻ quà tặng" (TODO 7, rule D23): sell a card for cash (the code to write on it is shown) and look at the balance of a card from its
 * code. Paying with a card is done in the payment box ({@link SplitPaymentPanel}). The rules are in {@link GiftCardRules} and
 * {@link GiftCardService}; database calls run off the screen thread.
 */
public final class GiftCardDialog {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private GiftCardDialog() {
    }

    public static void show() {
        GiftCardService dao = new GiftCardService();
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("giftcard.title"));
        AlertUtil.configure(dialog);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TextField amountField = new TextField();
        amountField.setPromptText(I18n.t("giftcard.amount.prompt"));
        TextLengthLimiter.limit(amountField, TextLengthLimiter.NUMBER_MAX);
        Button sell = new Button(I18n.t("giftcard.sell"));
        Label sold = new Label();
        sold.setWrapText(true);
        sold.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        String[] lastCode = new String[1]; // the code just sold, for the "Sao chép" button under it (TODO 5.46)
        Button copyCode = CopyMenus.button(() -> CopyText.giftCardCode(lastCode[0]));
        copyCode.setVisible(false);
        copyCode.setManaged(false);
        sell.setOnAction(e -> {
            copyCode.setVisible(false);
            copyCode.setManaged(false);
            BigDecimal typed = MoneyParser.parseOptionalDong(amountField.getText());
            if (typed == null || !GiftCardRules.validAmount(typed.longValue())) {
                sold.setText(I18n.t("giftcard.val.amount", CurrencyUtil.format(Money.fromDong(GiftCardRules.MIN_AMOUNT)),
                        CurrencyUtil.format(Money.fromDong(GiftCardRules.MAX_AMOUNT)), CurrencyUtil.format(Money.fromDong(GiftCardRules.STEP))));
                return;
            }
            if (!AlertUtil.confirm(I18n.t("giftcard.sell"), I18n.t("giftcard.sell.confirm", CurrencyUtil.format(typed)))) return;
            sell.setDisable(true);
            Async.run(() -> dao.sell(typed.longValue()), card -> {
                sell.setDisable(false);
                amountField.clear();
                sold.setText(I18n.t("giftcard.sold", card.code(), CurrencyUtil.format(Money.fromDong(card.balance()))));
                lastCode[0] = card.code();
                copyCode.setVisible(true);
                copyCode.setManaged(true);
            }, ex -> {
                sell.setDisable(false);
                sold.setText(ex.getMessage());
            });
        });

        TextField codeField = new TextField();
        codeField.setPromptText(I18n.t("pos.gift.code.prompt"));
        TextLengthLimiter.limit(codeField, 20);
        Button check = new Button(I18n.t("pos.gift.check"));
        Label balance = new Label();
        balance.setWrapText(true);
        Runnable look = () -> Async.run(() -> dao.findByCode(codeField.getText()), found -> {
            GiftCard card = found.orElse(null);
            balance.setText(card == null ? I18n.t("giftcard.unknown")
                    : I18n.t("giftcard.detail", card.code(), CurrencyUtil.format(Money.fromDong(card.balance())),
                            CurrencyUtil.format(Money.fromDong(card.initialAmount())), card.createdAt() == null ? "" : DAY.format(card.createdAt())));
        }, ex -> balance.setText(ex.getMessage()));
        check.setOnAction(e -> look.run());
        codeField.setOnAction(e -> look.run());

        Label owed = new Label(); // TODO 4.45: what the shop still owes on cards
        owed.setWrapText(true);
        Async.run(dao::openBalance, open -> owed.setText(com.coffeeshop.coffeeshopmanagement.util.GiftCardText.line(open)), ex -> { });
        VBox box = new VBox(10, new Label(I18n.t("giftcard.sell.title")), new HBox(8, amountField, sell), sold, copyCode,
                new Label(I18n.t("giftcard.balance.title")), new HBox(8, codeField, check), balance, owed);
        if (com.coffeeshop.coffeeshopmanagement.util.Session.isAdmin()) {
            Button list = new Button(I18n.t("giftcard.list.button"));
            list.setOnAction(e -> GiftCardListDialog.show());
            box.getChildren().add(list);
        }
        box.setPadding(new Insets(16));
        box.setPrefWidth(480);
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait();
    }
}
