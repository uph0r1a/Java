package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.CheckoutService;
import com.coffeeshop.coffeeshopmanagement.service.CustomerService;
import com.coffeeshop.coffeeshopmanagement.service.LoyaltyPolicy;
import com.coffeeshop.coffeeshopmanagement.service.PaymentRules;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionService;
import com.coffeeshop.coffeeshopmanagement.util.AlertUtil;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.OffScreenThread;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.Session;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * The payment box of the till (TODO 6.54): moved out of {@code OrderController} so the till screen gets smaller. It builds the window,
 * reads the boxes and hands back the confirmed payment; what is allowed and what the payment is are still decided in
 * {@link PaymentRules}, and saving it is still {@code OrderController.completeOrder}. Nothing here reads or changes the till's state: the
 * lines, the customer and the open order come in as arguments.
 */
final class PaymentDialog {

    private PaymentDialog() {
    }

    /** What the box is built from and that needs the database: the automatic promotions, the points balance and the moment it is priced at. */
    private record Preload(AutoPromotionService.Result auto, int points, java.time.LocalDateTime pricedAt) {
    }

    @OffScreenThread
    private static Preload preload(List<OrderItem> paying, Customer attachedCustomer, CustomerService customerService, java.time.LocalDateTime at) {
        return new Preload(PromotionPanel.autoPromotionsFor(paying, at), attachedCustomer == null ? 0 : customerService.currentPoints(attachedCustomer), at);
    }

    /**
     * Reads what the box needs in the background (TODO 6.14), then shows the payment box and hands the confirmed payment to {@code paid}; nothing
     * is called when the box is cancelled. The caller returns at once, so anything it must keep for {@code paid} has to be passed in it.
     *
     * @param subtotal         the sum of {@code lines} (the whole cart, or the pieces paid first in a split bill, TODO 4.10)
     * @param lines            the lines being paid
     * @param attachedCustomer the customer attached to this sale, or null (points can be spent only when there is one)
     * @param activeOpenOrder  the saved open order, or null (the transfer box names it in the bank note)
     * @param customerService  reads the customer's points balance again at the moment the box opens
     * @param unlock           called on the screen thread just before the box opens, or when it cannot (the caller locked the screen meanwhile)
     * @param paid             receives the payment the cashier confirmed
     */
    static void ask(BigDecimal subtotal, List<OrderItem> lines, Customer attachedCustomer, Order activeOpenOrder, CustomerService customerService,
                    Runnable unlock, java.util.function.Consumer<CheckoutService.Payment> paid) {
        final List<OrderItem> paying = List.copyOf(lines);
        final java.time.LocalDateTime pricedAt = AppClock.now(); // the moment the bill is priced at, even if a promotion ends while the box stays open
        Async.run(() -> preload(paying, attachedCustomer, customerService, pricedAt), pre -> {
            unlock.run();
            show(subtotal, attachedCustomer, activeOpenOrder, pre).ifPresent(paid);
        }, error -> {
            unlock.run();
            AlertUtil.error(I18n.t("pos.pay.title"), String.valueOf(error.getMessage()));
        });
    }

    private static Optional<CheckoutService.Payment> show(BigDecimal subtotal, Customer attachedCustomer, Order activeOpenOrder, Preload pre) {
        Dialog<CheckoutService.Payment> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("pos.pay.title"));
        AlertUtil.configure(dialog);
        ButtonType confirmButtonType = new ButtonType(I18n.t("pos.pay.confirm"), ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);
        AlertUtil.setCancelButton(dialog, ButtonType.CANCEL);

        ToggleGroup methodGroup = new ToggleGroup();
        RadioButton cashRadio = new RadioButton(PaymentMethod.CASH.label());
        cashRadio.setToggleGroup(methodGroup);
        cashRadio.setSelected(true);
        RadioButton cardRadio = new RadioButton(PaymentMethod.CARD.label());
        cardRadio.setToggleGroup(methodGroup);
        RadioButton transferRadio = new RadioButton(PaymentMethod.TRANSFER.label());
        transferRadio.setToggleGroup(methodGroup);
        // The e-wallets (TODO 4.34): one radio each, in a second row, so the report can tell them from a bank transfer.
        java.util.Map<PaymentMethod, RadioButton> walletRadios = new java.util.LinkedHashMap<>();
        for (PaymentMethod wallet : PaymentMethod.eWallets()) {
            RadioButton walletRadio = new RadioButton(wallet.label());
            walletRadio.setToggleGroup(methodGroup);
            walletRadios.put(wallet, walletRadio);
        }
        // What is chosen now; cash when nothing is (cannot happen: cash starts selected and a toggle group keeps one).
        java.util.function.Supplier<PaymentMethod> chosenMethod = () -> {
            for (java.util.Map.Entry<PaymentMethod, RadioButton> entry : walletRadios.entrySet()) {
                if (entry.getValue().isSelected()) return entry.getKey();
            }
            return cashRadio.isSelected() ? PaymentMethod.CASH : transferRadio.isSelected() ? PaymentMethod.TRANSFER : PaymentMethod.CARD;
        };

        Role currentRole = Session.getCurrentRole();
        PromotionPanel promo = new PromotionPanel(currentRole, subtotal, pre.auto(), pre.pricedAt(), attachedCustomer); // discount, voucher (4.11), automatic promotions (4.55), birthday (3.66)
        TextField cashReceivedField = new TextField();
        TextLengthLimiter.limit(cashReceivedField, TextLengthLimiter.NUMBER_MAX);
        cashReceivedField.disableProperty().bind(cashRadio.selectedProperty().not()); // only cash has "tiền khách đưa"
        Label totalPreviewLabel = new Label(CurrencyUtil.format(subtotal));
        Label changePreviewLabel = new Label(CurrencyUtil.format(BigDecimal.ZERO));
        SplitPaymentPanel split = new SplitPaymentPanel(subtotal); // part in cash, the rest with another method (TODO 4.32)

        // Spending loyalty points (TODO 4.16): only for a customer already attached to this sale, and only when they have some. The
        // balance is read again now (the attached object can be older than the last sale). The database checks it once more at payment.
        final Customer pointsCustomer = attachedCustomer;
        final int pointsBalance = pointsCustomer == null ? 0 : pre.points();
        final boolean canSpendPoints = pointsCustomer != null && pointsBalance > 0;
        TextField pointsField = new TextField("0");
        TextLengthLimiter.limit(pointsField, 9);
        Label pointsHint = new Label();
        pointsHint.setStyle(com.coffeeshop.coffeeshopmanagement.util.TextSize.size(11) + " -fx-text-fill: #5b7e45;");
        Button pointsMaxButton = new Button(I18n.t("pos.points.max"));
        // The points that count in the preview: what is typed when it is allowed, else none (the confirm button says why not).
        java.util.function.IntSupplier previewPoints = () ->
                PaymentRules.previewPoints(pointsField.getText(), canSpendPoints, pointsBalance, promo.manual() != null, promo.remaining());
        Runnable refreshPointsHint = () -> {
            pointsHint.setText(I18n.t("pos.points.hint", pointsBalance, CurrencyUtil.format(LoyaltyPolicy.vndPerPointSpent()),
                    LoyaltyPolicy.maxRedeemable(pointsBalance, promo.remaining())));
        };
        refreshPointsHint.run();
        pointsMaxButton.setOnAction(e -> pointsField.setText(String.valueOf(LoyaltyPolicy.maxRedeemable(pointsBalance, promo.remaining()))));
        pointsField.setDisable(!canSpendPoints);
        pointsMaxButton.setDisable(!canSpendPoints);

        // Quick cash buttons (TODO 5.1): "Đủ tiền" fills the exact total, the others a common note; a note below the total is disabled.
        BigDecimal[] currentTotal = {subtotal};
        Button exactButton = new Button(I18n.t("pos.pay.exact"));
        HBox quickCash = new HBox(6, exactButton);
        java.util.Map<Button, BigDecimal> noteButtons = new java.util.LinkedHashMap<>();
        for (long note : new long[]{50_000, 100_000, 200_000, 500_000}) {
            Button b = new Button(CurrencyUtil.format(BigDecimal.valueOf(note)));
            noteButtons.put(b, BigDecimal.valueOf(note));
            b.setOnAction(e -> cashReceivedField.setText(String.valueOf(note)));
            quickCash.getChildren().add(b);
        }
        exactButton.setOnAction(e -> cashReceivedField.setText(currentTotal[0].toBigInteger().toString()));
        quickCash.disableProperty().bind(cashRadio.selectedProperty().not());

        Runnable updatePreview = () -> {
            BigDecimal total = PaymentRules.totalAfterPoints(promo.remaining(), previewPoints.getAsInt());
            currentTotal[0] = total;
            split.totalChanged(total);
            noteButtons.forEach((button, value) -> button.setDisable(PaymentRules.noteTooSmall(value, total)));
            totalPreviewLabel.setText(CurrencyUtil.format(total));
            BigDecimal change = PaymentRules.changePreview(cashReceivedField.getText(), split.due());
            changePreviewLabel.setText(change != null ? CurrencyUtil.format(change) : "-");
        };
        promo.setOnChange(() -> {
            refreshPointsHint.run();
            updatePreview.run();
        });
        pointsField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        cashReceivedField.textProperty().addListener((obs, o, n) -> updatePreview.run());
        cardRadio.selectedProperty().addListener((obs, o, n) -> updatePreview.run());
        transferRadio.selectedProperty().addListener((obs, o, n) -> updatePreview.run());
        updatePreview.run();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        grid.addRow(0, new Label(I18n.t("pos.pay.subtotal")), promo.subtotalNode(subtotal));
        grid.addRow(1, new Label(I18n.t("pos.pay.discount")), promo.discountNode());
        grid.addRow(2, new Label(I18n.t("pos.pay.discount.reason")), new VBox(8, promo.reasonNode(), promo.voucherNode()));
        if (canSpendPoints) {
            grid.addRow(3, new Label(I18n.t("pos.points.label")), new VBox(2, new HBox(8, pointsField, pointsMaxButton), pointsHint));
        } else if (pointsCustomer != null) {
            grid.addRow(3, new Label(I18n.t("pos.points.label")), new Label(I18n.t("pos.points.none")));
        } else {
            grid.addRow(3, new Label(I18n.t("pos.points.label")), new Label(I18n.t("pos.points.attach")));
        }
        grid.addRow(4, new Label(I18n.t("pos.pay.total")), totalPreviewLabel);
        grid.addRow(5, new Label(I18n.t("pos.pay.method")), new VBox(6, new HBox(14, cashRadio, cardRadio, transferRadio),
                new HBox(14, walletRadios.values().toArray(new RadioButton[0]))));
        grid.addRow(6, new Label(I18n.t("pos.pay.cash")), new VBox(6, cashReceivedField, quickCash));
        grid.addRow(7, new Label(I18n.t("pos.pay.change")), changePreviewLabel);

        // Bank transfer: the shop's own QR picture and the amount, shown only for this method.
        // The cashier must tick that the money arrived before the order can be paid.
        CheckBox transferReceivedBox = new CheckBox(I18n.t("pos.pay.transfer.received"));
        // The bank's reference of this transfer, so a disputed one can be traced later; optional (TODO 4.31).
        TextField transferRefField = new TextField();
        transferRefField.setPromptText(I18n.t("pos.pay.transfer.ref.prompt"));
        TextLengthLimiter.limit(transferRefField, com.coffeeshop.coffeeshopmanagement.service.PaymentDetails.REFERENCE_MAX);
        VBox transferBox = PaymentBoxes.transfer(split.dueLabel(), transferReceivedBox, transferRefField, activeOpenOrder);
        transferBox.visibleProperty().bind(transferRadio.selectedProperty());
        transferBox.managedProperty().bind(transferRadio.selectedProperty());
        grid.add(split.node(), 0, 8, 2, 1);
        grid.add(transferBox, 0, 9, 2, 1);
        // An e-wallet: the amount, the transaction code (optional) and the same "money arrived" tick as a bank transfer (TODO 4.34).
        CheckBox walletReceivedBox = new CheckBox(I18n.t("pos.pay.wallet.received"));
        TextField walletRefField = new TextField();
        walletRefField.setPromptText(I18n.t("pos.pay.wallet.ref.prompt"));
        TextLengthLimiter.limit(walletRefField, com.coffeeshop.coffeeshopmanagement.service.PaymentDetails.REFERENCE_MAX);
        VBox walletBox = PaymentBoxes.wallet(split.dueLabel(), walletReceivedBox, walletRefField);
        javafx.beans.binding.BooleanBinding walletChosen = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> chosenMethod.get().isEWallet(), methodGroup.selectedToggleProperty());
        walletBox.visibleProperty().bind(walletChosen);
        walletBox.managedProperty().bind(walletChosen);
        grid.add(walletBox, 0, 10, 2, 1);
        dialog.getDialogPane().setContent(grid);

        CheckoutService.Payment[] pendingResult = {null};
        Button confirmButton = (Button) dialog.getDialogPane().lookupButton(confirmButtonType);
        methodGroup.selectedToggleProperty().addListener((obs, was, isNow) -> {
            split.methodChanged(chosenMethod.get());
            confirmButton.setText(I18n.t(chosenMethod.get().needsReceivedConfirmation() ? "pos.pay.received" : "pos.pay.confirm"));
            // The dialog was sized for the short layout; grow or shrink it when the QR appears or goes.
            javafx.application.Platform.runLater(() -> {
                if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
                    dialog.getDialogPane().getScene().getWindow().sizeToScene();
                }
            });
        });
        confirmButton.addEventFilter(ActionEvent.ACTION, event -> {
            // What is allowed and what the payment is are decided in PaymentRules (TODO 6.5); this only reads the boxes and shows the answer.
            String splitProblem = split.problem();
            PaymentRules.Outcome outcome = PaymentRules.confirm(new PaymentRules.Entered(currentRole, subtotal, promo.manual(),
                    promo.reasonText(), promo.remaining(), promo.voucherCode(), pointsField.getText(), pointsCustomer == null ? null : pointsBalance,
                    split.effectiveMethod(chosenMethod.get()), splitProblem,
                    split.due(), splitProblem == null ? split.lines() : List.of(), // the lines of an unchecked gift card cannot be read yet
                    transferReceivedBox.isSelected(), walletReceivedBox.isSelected(), cashReceivedField.getText(),
                    transferRefField.getText(), walletRefField.getText()));
            if (!outcome.isAccepted()) {
                AlertUtil.warning(outcome.title(), outcome.message());
                event.consume();
                return;
            }
            var priced = outcome.payment().withPricedAt(promo.pricedAt()); // priced at the moment this window opened (TODO 4.55)
            pendingResult[0] = promo.birthday() ? priced.withBirthday() : priced; // TODO 3.66
        });

        dialog.showAndWait();
        return Optional.ofNullable(pendingResult[0]);
    }
}
