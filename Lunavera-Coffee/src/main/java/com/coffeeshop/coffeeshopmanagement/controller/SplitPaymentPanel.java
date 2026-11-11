package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.model.GiftCard;
import com.coffeeshop.coffeeshopmanagement.model.PaymentLine;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.service.GiftCardRules;
import com.coffeeshop.coffeeshopmanagement.service.GiftCardService;
import com.coffeeshop.coffeeshopmanagement.service.SplitPaymentRules;
import com.coffeeshop.coffeeshopmanagement.util.Async;
import com.coffeeshop.coffeeshopmanagement.util.Money;
import com.coffeeshop.coffeeshopmanagement.util.CurrencyUtil;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.TextLengthLimiter;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Paying one order with any number of methods (TODO 4.32, the owner's rule D4). The cashier chooses the <b>main</b> method with the
 * usual buttons (a method other than cash: its QR, transaction code and "money arrived" tick are for it), ticks this box, and types
 * the amount paid with each of the <b>other</b> methods (cash, card, transfer, e-wallets); the main method receives what is left.
 * Shown only while a method other than cash is chosen, so every split has at least one method that is not cash. The rule that is
 * checked when the order is saved is {@code OrderDAO.checkLines}; this panel only helps to type it and refuses the obvious mistakes
 * first. The shift close counts the cash line only (rule D4).
 *
 * <p>It also holds the gift-card part (TODO 7, rule D23): a card code, checked against the database, and how much to take from it. That
 * is always available (also with cash as the main method), for as many cards as the customer has (TODO 3.87: a button adds a card); it comes off first and the main method receives what is left. A card that
 * covers the whole bill makes the payment a gift-card payment ({@link #effectiveMethod}).
 */
final class SplitPaymentPanel {

    private final CheckBox on = new CheckBox(I18n.t("pos.split.check"));
    private final Map<PaymentMethod, TextField> fields = new EnumMap<>(PaymentMethod.class);
    private final Map<PaymentMethod, HBox> rows = new EnumMap<>(PaymentMethod.class);
    private final VBox fieldBox = new VBox(4);
    private final Label restLabel = new Label();
    private final Label due = new Label();
    private final VBox box = new VBox(6);
    private final VBox splitPart = new VBox(6);
    private final List<CardEntry> cards = new ArrayList<>(); // TODO 3.87: one entry per gift card; the first is always there
    private final VBox cardBox = new VBox(4);
    private BigDecimal total = BigDecimal.ZERO;
    private PaymentMethod main = PaymentMethod.CASH;

    SplitPaymentPanel(BigDecimal total) {
        this.total = total;
        for (PaymentMethod method : SplitPaymentRules.otherMethods()) {
            TextField field = new TextField();
            field.setPromptText(I18n.t("pos.split.amount.prompt"));
            TextLengthLimiter.limit(field, TextLengthLimiter.NUMBER_MAX);
            field.textProperty().addListener((o, was, now) -> refresh());
            fields.put(method, field);
            Label name = new Label(method.label());
            name.setMinWidth(110);
            HBox row = new HBox(8, name, field);
            rows.put(method, row);
            fieldBox.getChildren().add(row);
        }
        fieldBox.visibleProperty().bind(on.selectedProperty());
        fieldBox.managedProperty().bind(on.selectedProperty());
        restLabel.visibleProperty().bind(on.selectedProperty());
        restLabel.managedProperty().bind(on.selectedProperty());
        splitPart.getChildren().addAll(on, fieldBox, restLabel);
        splitPart.setVisible(false);
        splitPart.setManaged(false);
        Button addCard = new Button(I18n.t("pos.gift.add"));
        addCard.setOnAction(e -> {
            addEntry(true);
            refresh();
            resizeWindow();
        });
        addEntry(false);
        box.getChildren().addAll(new Label(I18n.t("pos.gift.label")), cardBox, addCard, splitPart);
        on.selectedProperty().addListener((o, was, now) -> {
            refresh();
            resizeWindow();
        });
        showRowsForMain();
        refresh();
    }

    /** One gift card of the bill: its code (checked against the database) and how much to take from it. */
    private final class CardEntry {
        final TextField code = new TextField();
        final TextField amount = new TextField();
        final Label status = new Label();
        GiftCard found; // the card the typed code was checked against; null = not checked or not found

        CardEntry() {
            code.setPromptText(I18n.t("pos.gift.code.prompt"));
            TextLengthLimiter.limit(code, 20);
            amount.setPromptText(I18n.t("pos.split.amount.prompt"));
            TextLengthLimiter.limit(amount, TextLengthLimiter.NUMBER_MAX);
            status.setWrapText(true);
            status.setStyle(com.coffeeshop.coffeeshopmanagement.util.TextSize.size(11) + " -fx-text-fill: #5b7e45;");
            code.setOnAction(e -> check());
            code.textProperty().addListener((o, was, now) -> { // a changed code is not the one that was checked
                found = null;
                status.setText("");
                refresh();
            });
            amount.textProperty().addListener((o, was, now) -> refresh());
        }

        /** What this card is as typed, for {@link SplitPaymentRules} (TODO 6.5): the amount text and what the checked card holds. */
        SplitPaymentRules.Card asCard() {
            return new SplitPaymentRules.Card(amount.getText(), found == null ? null : found.balance(), found == null ? null : found.code());
        }

        /** The amount typed for this card: zero when empty, null when it is not a number. */
        BigDecimal value() {
            return asCard().value();
        }

        void check() {
            String typed = code.getText();
            if (GiftCardRules.normalise(typed) == null) {
                found = null;
                status.setText(I18n.t("giftcard.unknown"));
                refresh();
                return;
            }
            Async.run(() -> new GiftCardService().findByCode(typed), result -> {
                found = result.orElse(null);
                if (found == null) {
                    status.setText(I18n.t("giftcard.unknown"));
                } else {
                    status.setText(I18n.t("giftcard.balance", CurrencyUtil.format(Money.fromDong(found.balance()))));
                    List<BigDecimal> others = new ArrayList<>(); // offer the most this card can pay of what the other cards leave
                    for (CardEntry other : cards) if (other != this) others.add(other.value());
                    Long offer = SplitPaymentRules.offerFor(total, found.balance(), value(), others);
                    if (offer != null) amount.setText(String.valueOf(offer));
                }
                refresh();
            }, e -> {
                found = null;
                status.setText(e.getMessage());
                refresh();
            });
        }
    }

    private void addEntry(boolean removable) {
        CardEntry entry = new CardEntry();
        Button check = new Button(I18n.t("pos.gift.check"));
        check.setOnAction(e -> entry.check());
        HBox top = new HBox(8, entry.code, check);
        VBox part = new VBox(4, top, new HBox(8, new Label(I18n.t("pos.gift.amount")), entry.amount), entry.status);
        if (removable) {
            Button remove = new Button(I18n.t("pos.gift.remove"));
            remove.setOnAction(e -> {
                cards.remove(entry);
                cardBox.getChildren().remove(part);
                refresh();
                resizeWindow();
            });
            top.getChildren().add(remove);
        }
        cards.add(entry);
        cardBox.getChildren().add(part);
    }

    private void resizeWindow() {
        Platform.runLater(() -> {
            if (box.getScene() != null && box.getScene().getWindow() != null) box.getScene().getWindow().sizeToScene();
        });
    }

    /** The block to put in the payment box. */
    Node node() {
        return box;
    }

    /**
     * What the main method has to receive: the whole total, or the total less the other amounts while they are valid. The QR of a
     * transfer and the e-wallet block show this label, so the customer is never asked to transfer the part paid another way.
     */
    Label dueLabel() {
        return due;
    }

    /** Call when the chosen main method changes: the box is for the non-cash methods only, and cash alone is never a split. */
    void methodChanged(PaymentMethod method) {
        main = method;
        boolean show = method != PaymentMethod.CASH;
        if (!show) on.setSelected(false);
        showRowsForMain();
        refresh();
        if (splitPart.isVisible() != show) {
            splitPart.setVisible(show);
            splitPart.setManaged(show);
            Platform.runLater(() -> {
                if (box.getScene() != null && box.getScene().getWindow() != null) box.getScene().getWindow().sizeToScene();
            });
        }
    }

    /** Call when the total changes (a discount, a voucher or points were applied). */
    void totalChanged(BigDecimal newTotal) {
        total = newTotal;
        refresh();
    }

    /** The row of the main method is hidden: it is not typed, it is the rest. */
    private void showRowsForMain() {
        rows.forEach((method, row) -> {
            row.setVisible(method != main);
            row.setManaged(method != main);
        });
    }

    /** The box as it is now, for {@link SplitPaymentRules}: the total, the main method, the tick, the typed texts and the cards (TODO 6.5). */
    private SplitPaymentRules.Input input() {
        Map<PaymentMethod, String> texts = new EnumMap<>(PaymentMethod.class);
        fields.forEach((method, field) -> texts.put(method, field.getText()));
        List<SplitPaymentRules.Card> list = new ArrayList<>();
        for (CardEntry entry : cards) list.add(entry.asCard());
        return new SplitPaymentRules.Input(total, main, on.isSelected(), texts, list);
    }

    /** What the main method has to receive now, as a number (the whole total while the typed amounts are not usable). */
    BigDecimal due() {
        return SplitPaymentRules.due(input());
    }

    /** The method the payment is saved under: only cards that pay the whole bill make it a gift-card payment (TODO 2.102), else the one chosen. */
    PaymentMethod effectiveMethod(PaymentMethod chosen) {
        return SplitPaymentRules.effectiveMethod(input(), chosen);
    }

    /**
     * The lines to save, or an empty list when the box is not ticked. The other methods as typed, then the main method with what is
     * left. Call {@link #problem()} first.
     */
    List<PaymentLine> lines() {
        return SplitPaymentRules.lines(input());
    }

    /** What is wrong with the typed amounts, in words for the cashier; null when they are fine or not used. */
    String problem() {
        return switch (SplitPaymentRules.problem(input())) {
            case NONE -> null;
            case GIFT -> I18n.t("pos.gift.invalid");
            case SPLIT -> I18n.t("pos.split.invalid", CurrencyUtil.format(total));
        };
    }

    private void refresh() {
        SplitPaymentRules.Input in = input();
        boolean ok = SplitPaymentRules.showsRest(in);
        BigDecimal rest = ok ? SplitPaymentRules.due(in) : total;
        restLabel.setText(ok ? I18n.t("pos.split.rest", main.label(), CurrencyUtil.format(rest)) : "");
        due.setText(CurrencyUtil.format(rest));
    }
}
