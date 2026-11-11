package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.dao.DataAccessException;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.Voucher;
import com.coffeeshop.coffeeshopmanagement.service.AutoPromotionService;
import com.coffeeshop.coffeeshopmanagement.model.Customer;
import com.coffeeshop.coffeeshopmanagement.service.BirthdayRules;
import com.coffeeshop.coffeeshopmanagement.service.DiscountPolicy;
import com.coffeeshop.coffeeshopmanagement.service.PaymentRules;
import com.coffeeshop.coffeeshopmanagement.service.VoucherRules;
import com.coffeeshop.coffeeshopmanagement.service.VoucherService;
import com.coffeeshop.coffeeshopmanagement.util.AppClock;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.OffScreenThread;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;

/**
 * The discount part of the payment dialog (TODO 4.11, rule D6): the typed discount with its reason, percentage buttons that only fill
 * it in, and a voucher code. It owns the three controls and answers the questions the dialog asks ({@link #remaining}, {@link #voucherCode}),
 * so the dialog does not have to repeat the arithmetic. The rules themselves are in {@link DiscountPolicy} and {@link VoucherRules}; the
 * checkout service checks everything again before anything is saved.
 *
 * <p>Automatic promotions (TODO 4.55) come off before all of this: the panel works out what they take off the bill being paid
 * ({@link AutoPromotionService}, the same call the checkout service makes) and everything it answers is on what is left. The cashier's
 * limit stays on the price before promotions ({@code subtotal}), but a discount, a voucher or points can never take more than is left to
 * pay ({@code payable}).
 * <p>For a customer whose birthday it is and who has not had the discount today ({@link BirthdayRules#isOffered}), there is also a
 * "Birthday" button (TODO 3.66, rule D25). It fills in the birthday percentage and its reason like the other buttons, but it is what
 * tells the payment that this discount is the birthday one, so it is given once a day. Typing another amount makes it an ordinary
 * discount again.
 */
final class PromotionPanel {

    private final BigDecimal subtotal;
    /** The subtotal less what the automatic promotions take off: the bill the discount, the voucher and the points work on. */
    private final BigDecimal payable;
    private final AutoPromotionService.Result auto;
    /** When this window was opened: the moment the bill is priced at, even if a promotion ends while the window stays open. */
    private final java.time.LocalDateTime pricedAt;
    private final TextField discountField = new TextField("0");
    private final TextField reasonField = new TextField();
    private final TextField voucherField = new TextField();
    private final Label voucherStatus = new Label();
    private final Node discountNode;
    private final Node voucherNode;
    private Voucher applied;
    private BigDecimal birthdayAmount; // what the birthday button filled in, while the typed discount is still exactly that
    private Runnable onChange = () -> { };

    /** @param customer the customer attached to the bill, or null; with their birthday available the Birthday button is shown */
    PromotionPanel(Role role, BigDecimal subtotal, AutoPromotionService.Result auto, java.time.LocalDateTime pricedAt, Customer customer) {
        this.subtotal = subtotal;
        this.auto = auto; // read in the background by PaymentDialog before the box is built (TODO 6.14)
        this.pricedAt = pricedAt;
        this.payable = subtotal.subtract(auto.value());
        reasonField.setPromptText(I18n.t("pos.discount.reason.prompt"));
        TextLengthLimiter.limit(reasonField, DiscountPolicy.REASON_MAX_LENGTH);
        reasonField.setDisable(true); // only worth asking for a reason when there is a discount
        TextLengthLimiter.limit(discountField, TextLengthLimiter.NUMBER_MAX);
        discountField.textProperty().addListener((obs, o, n) -> {
            BigDecimal typed = MoneyParser.parseOptionalDong(n);
            reasonField.setDisable(typed == null || typed.signum() == 0);
            if (birthdayAmount != null && (typed == null || typed.compareTo(birthdayAmount) != 0)) birthdayAmount = null; // no longer the birthday discount
            onChange.run();
        });
        BigDecimal max = DiscountPolicy.maxDiscount(role, subtotal).min(payable); // the limit is on the price before promotions
        Label hint = new Label(role == Role.ADMIN ? I18n.t("pos.discount.admin")
                : I18n.t("pos.discount.staff", DiscountPolicy.staffMaxPercent(), CurrencyUtil.format(max)));
        hint.setStyle(com.coffeeshop.coffeeshopmanagement.util.TextSize.size(11) + " -fx-text-fill: #5b7e45;");
        HBox percents = new HBox(6);
        for (int percent : VoucherRules.PERCENT_BUTTONS) {
            BigDecimal amount = VoucherRules.percentOf(subtotal, percent);
            Button button = new Button(percent + "%");
            button.setDisable(amount.compareTo(max) > 0); // the cashier's limit still holds: a button above it is off
            button.setOnAction(e -> {
                discountField.setText(amount.toBigInteger().toString());
                if (reasonField.getText() == null || reasonField.getText().isBlank()) reasonField.setText(I18n.t("pos.promo.percent.reason", percent));
            });
            percents.getChildren().add(button);
        }
        if (BirthdayRules.isOffered(customer, AppClock.today())) {
            BigDecimal amount = BirthdayRules.discountFor(subtotal);
            Button birthday = new Button(I18n.t("pos.promo.birthday", BirthdayRules.suggestedPercent()));
            birthday.setDisable(amount.compareTo(max) > 0 || amount.signum() == 0);
            birthday.setOnAction(e -> {
                discountField.setText(amount.toBigInteger().toString());
                reasonField.setText(I18n.t("pos.promo.birthday.reason", BirthdayRules.suggestedPercent()));
                birthdayAmount = amount; // after the text, which the listener above would otherwise clear
                onChange.run();
            });
            percents.getChildren().add(0, birthday);
        }
        discountNode = new VBox(4, discountField, percents, hint);

        voucherField.setPromptText(I18n.t("pos.voucher.prompt"));
        TextLengthLimiter.limit(voucherField, VoucherRules.CODE_MAX);
        Button apply = new Button(I18n.t("pos.voucher.apply"));
        voucherStatus.setWrapText(true);
        voucherStatus.setStyle(com.coffeeshop.coffeeshopmanagement.util.TextSize.size(11) + " -fx-text-fill: #5b7e45;");
        apply.setOnAction(e -> apply());
        voucherField.setOnAction(e -> apply());
        voucherField.textProperty().addListener((obs, o, n) -> { // a changed code is not the one that was checked
            if (applied != null && !applied.code().equals(VoucherRules.normalise(n))) {
                applied = null;
                voucherStatus.setText("");
                onChange.run();
            }
        });
        voucherNode = new VBox(2, new Label(I18n.t("pos.voucher.label")), new HBox(8, voucherField, apply), voucherStatus);
    }

    private void apply() {
        String typed = voucherField.getText();
        if (typed == null || typed.isBlank()) {
            applied = null;
            voucherStatus.setText("");
            onChange.run();
            return;
        }
        // TODO 6.14: the code is looked up in the background; the code box and its button wait, and an answer for a code that is no longer
        // the one typed is dropped (the listener above has already cleared the old one)
        Async.run(() -> VoucherRules.lookup(typed, new VoucherService().findByCode(typed).orElse(null), payable, AppClock.today()), // TODO 6.5
                result -> {
                    if (!VoucherRules.normalise(voucherField.getText()).equals(VoucherRules.normalise(typed))) return;
                    applied = result.voucher();
                    voucherStatus.setText(result.status());
                    onChange.run();
                }, error -> {
                    applied = null;
                    voucherStatus.setText(error.getMessage());
                    onChange.run();
                }, voucherNode);
    }

    /** What the promotions take off the bill, or none; when they cannot be read the till keeps working and the checkout says the rest. */
    @OffScreenThread
    static AutoPromotionService.Result autoPromotionsFor(List<OrderItem> paying, java.time.LocalDateTime at) {
        try {
            return AutoPromotionService.evaluate(paying == null ? List.of() : List.copyOf(paying), at);
        } catch (DataAccessException e) {
            java.util.logging.Logger.getLogger(PromotionPanel.class.getName()).log(java.util.logging.Level.WARNING,
                    "The automatic promotions could not be read; the payment window shows none", e);
            return AutoPromotionService.Result.NONE;
        }
    }

    /** The moment this window was opened; the payment carries it so the checkout prices the bill the way this window did. */
    java.time.LocalDateTime pricedAt() {
        return pricedAt;
    }

    /** The subtotal, with the automatic promotions that apply written under it (TODO 4.55). */
    Node subtotalNode(BigDecimal subtotal) {
        Label total = new Label(CurrencyUtil.format(subtotal));
        if (auto.isNone()) {
            return total;
        }
        Label note = new Label(I18n.t("pos.pay.promo", String.join(", ", auto.names()), CurrencyUtil.format(auto.value())));
        note.setWrapText(true);
        note.setStyle(com.coffeeshop.coffeeshopmanagement.util.TextSize.size(11) + " -fx-text-fill: #5b7e45;");
        return new VBox(2, total, note);
    }

    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    Node discountNode() { return discountNode; }

    Node reasonNode() { return reasonField; }

    Node voucherNode() { return voucherNode; }

    String reasonText() { return reasonField.getText(); }

    /** True while the typed discount is the one the Birthday button filled in (TODO 3.66): paying with it records the use. */
    boolean birthday() {
        BigDecimal typed = manual();
        return birthdayAmount != null && typed != null && typed.compareTo(birthdayAmount) == 0;
    }

    /** The typed discount in dong, or null when it is not a number. */
    BigDecimal manual() {
        return MoneyParser.parseOptionalDong(discountField.getText());
    }

    /** What the applied voucher takes off now (it follows the typed discount, never going below 0 in total); 0 with none. */
    BigDecimal voucherValue() {
        return PaymentRules.voucherTakes(applied, payable, manual());
    }

    /** The applied voucher's code, or null; the value is worked out again by the checkout service. */
    String voucherCode() {
        return voucherValue().signum() > 0 ? applied.code() : null;
    }

    /** What is left to pay after the typed discount and the voucher (a typed discount that is not usable counts as none). */
    BigDecimal remaining() {
        return PaymentRules.remaining(payable, manual(), applied);
    }
}
