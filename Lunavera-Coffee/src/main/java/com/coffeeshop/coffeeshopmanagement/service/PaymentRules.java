package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.PaymentLine;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.Voucher;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the payment dialog decides, with no screen in it (TODO 6.5). The dialog used to hold these rules inside the button's
 * click handler of {@code OrderController.openPaymentDialog}: the discount check, the points check, the "money arrived" tick of a
 * transfer or an e-wallet, the cash that was handed over, and the arithmetic of the preview (the total after points, the change,
 * which cash buttons are too small). They are plain functions of plain values here, so they are tested without JavaFX.
 *
 * <p>This class only decides whether the dialog may close and what the payment is; saving it is {@link CheckoutService}, which
 * checks the discount, the voucher and the points again against the database before anything is written. The split-payment box
 * ({@code SplitPaymentPanel}) and the discount box ({@code PromotionPanel}) still hold their own typed fields; the dialog asks
 * them and hands their answers in as {@link Entered}.
 */
public final class PaymentRules {

    private PaymentRules() {
    }

    /** Why the dialog stayed open. The tests read this; the cashier reads {@link Outcome#title()} and {@link Outcome#message()}. */
    public enum Problem {
        DISCOUNT_NOT_A_NUMBER, DISCOUNT_REFUSED, POINTS_NOT_A_NUMBER, POINTS_REFUSED, SPLIT_INVALID,
        TRANSFER_NOT_CONFIRMED, WALLET_NOT_CONFIRMED, CASH_NOT_A_NUMBER, CASH_TOO_LITTLE
    }

    /**
     * The answer to "may the dialog close?": either the payment to save ({@link #payment()}), or the reason it may not
     * ({@link #problem()}, with the box title and the sentence to show). Exactly one of the two is set.
     */
    public record Outcome(CheckoutService.Payment payment, Problem problem, String title, String message) {

        static Outcome accepted(CheckoutService.Payment payment) {
            return new Outcome(payment, null, null, null);
        }

        static Outcome refused(Problem problem, String title, String message) {
            return new Outcome(null, problem, title, message);
        }

        public boolean isAccepted() {
            return payment != null;
        }
    }

    /**
     * Everything the cashier has entered when "Thanh toán" is pressed, as plain values.
     *
     * @param role            the signed-in role (decides the discount limit)
     * @param subtotal        what the lines add up to
     * @param manualDiscount  the typed discount in dong, or null when what was typed is not a number
     * @param discountReason  the typed reason (may be blank)
     * @param remaining       the bill after the discount and an applied voucher
     * @param voucherCode     the applied voucher's code, or null
     * @param typedPoints     the text of the points box ("0" when untouched)
     * @param pointsBalance   the points the attached customer has, or null when no customer is attached; zero or less means none
     * @param method          the method the payment is saved under: the chosen one, or the gift card when it pays the whole bill
     * @param splitProblem    what the split / gift-card box says is wrong, or null when it is fine
     * @param splitDue        what the main method has to receive now (the total less the other parts)
     * @param splitLines      the lines of a split payment, or an empty list when the payment is not split
     * @param transferReceived the "money arrived" tick of a bank transfer
     * @param walletReceived   the "money arrived" tick of an e-wallet
     * @param cashReceivedText what is typed in "tiền khách đưa"
     * @param transferRefText  the bank reference typed for a transfer (optional)
     * @param walletRefText    the transaction code typed for an e-wallet (optional)
     */
    public record Entered(Role role, BigDecimal subtotal, BigDecimal manualDiscount, String discountReason, BigDecimal remaining,
                          String voucherCode, String typedPoints, Integer pointsBalance, PaymentMethod method, String splitProblem,
                          BigDecimal splitDue, List<PaymentLine> splitLines, boolean transferReceived, boolean walletReceived,
                          String cashReceivedText, String transferRefText, String walletRefText) {
    }

    /**
     * The checks of the confirm button, in the order the cashier meets them (the first that fails is the one shown):
     * a discount that is a number and allowed for the role, the points, the split box, the "money arrived" tick, and for cash an
     * amount that covers what is due. When all pass, the payment as {@link CheckoutService} takes it.
     */
    public static Outcome confirm(Entered in) {
        BigDecimal discount = in.manualDiscount();
        if (discount == null) {
            return Outcome.refused(Problem.DISCOUNT_NOT_A_NUMBER, I18n.t("pos.discount.invalid.title"), I18n.t("pos.discount.invalid.number"));
        }
        DiscountPolicy.Result discountResult = DiscountPolicy.check(in.role(), in.subtotal(), discount, in.discountReason());
        if (discountResult != DiscountPolicy.Result.OK) {
            return Outcome.refused(Problem.DISCOUNT_REFUSED, I18n.t("pos.discount.invalid.title"),
                    DiscountPolicy.messageFor(discountResult, in.role(), in.subtotal()));
        }
        // Points spent (TODO 4.16): a typed number that is not allowed stops the payment with the reason in plain words.
        Integer points = LoyaltyPolicy.parsePoints(in.typedPoints());
        if (points == null) {
            return Outcome.refused(Problem.POINTS_NOT_A_NUMBER, I18n.t("pos.points.label"), I18n.t("pos.points.invalid"));
        }
        Integer balance = in.pointsBalance() == null ? null : Math.max(0, in.pointsBalance());
        String pointsProblem = LoyaltyPolicy.redemptionProblem(points, balance, in.remaining());
        if (pointsProblem != null) {
            return Outcome.refused(Problem.POINTS_REFUSED, I18n.t("pos.points.label"), pointsProblem);
        }
        BigDecimal total = totalAfterPoints(in.remaining(), points);
        PaymentMethod method = in.method();
        if (in.splitProblem() != null) {
            return Outcome.refused(Problem.SPLIT_INVALID, I18n.t("pos.split.title"), in.splitProblem());
        }
        if (method == PaymentMethod.TRANSFER && !in.transferReceived()) {
            return Outcome.refused(Problem.TRANSFER_NOT_CONFIRMED, I18n.t("pos.unconfirmed.title"), I18n.t("pos.unconfirmed.message"));
        }
        if (method.isEWallet() && !in.walletReceived()) {
            return Outcome.refused(Problem.WALLET_NOT_CONFIRMED, I18n.t("pos.unconfirmed.title"), I18n.t("pos.unconfirmed.wallet", method.label()));
        }
        BigDecimal cashHandedOver = null; // what the customer gave, saved with the order (TODO 3.17)
        if (method == PaymentMethod.CASH) {
            BigDecimal received = parseAmount(in.cashReceivedText());
            if (received == null) {
                return Outcome.refused(Problem.CASH_NOT_A_NUMBER, I18n.t("pos.amount.invalid.title"), I18n.t("pos.amount.invalid.message"));
            }
            if (received.compareTo(in.splitDue()) < 0) {
                return Outcome.refused(Problem.CASH_TOO_LITTLE, I18n.t("pos.amount.short.title"), I18n.t("pos.amount.short.message"));
            }
            cashHandedOver = received;
        }
        String reference = method == PaymentMethod.TRANSFER ? PaymentDetails.cleanReference(in.transferRefText())
                : method.isEWallet() ? PaymentDetails.cleanReference(in.walletRefText()) : null;
        return Outcome.accepted(new CheckoutService.Payment(method, discount,
                DiscountPolicy.reasonToStore(discount, in.discountReason()), total, cashHandedOver, reference, points, null,
                in.voucherCode(), in.splitLines()));
    }

    // ---------------------------------------------------------------- the preview, recalculated on every keystroke

    /** A typed amount in dong: blank is 0, anything that is not an amount is null. */
    public static BigDecimal parseAmount(String text) {
        return MoneyParser.parseOptionalDong(text);
    }

    /**
     * The typed discount as it counts in the preview (it was {@code PromotionPanel.manualOrZero}): a discount that is not a number, or is
     * more than the bill still to pay, counts as none; the confirm button says why.
     *
     * @param typed   the typed discount in dong, or {@code null} when it is not a number
     * @param payable the bill after the automatic promotions
     */
    public static BigDecimal usableDiscount(BigDecimal typed, BigDecimal payable) {
        return typed == null || typed.compareTo(payable) > 0 ? BigDecimal.ZERO : typed;
    }

    /** What the applied voucher takes off now: it follows the typed discount and never takes the bill below 0 in all; 0 with no voucher. */
    public static BigDecimal voucherTakes(Voucher applied, BigDecimal payable, BigDecimal typedDiscount) {
        return applied == null ? BigDecimal.ZERO : VoucherRules.discountFor(applied, payable, usableDiscount(typedDiscount, payable));
    }

    /** What is left to pay after the typed discount and the voucher (before points). */
    public static BigDecimal remaining(BigDecimal payable, BigDecimal typedDiscount, Voucher applied) {
        return payable.subtract(usableDiscount(typedDiscount, payable)).subtract(voucherTakes(applied, payable, typedDiscount));
    }

    /** The bill once {@code points} are spent on it. */
    public static BigDecimal totalAfterPoints(BigDecimal remaining, int points) {
        return remaining.subtract(LoyaltyPolicy.redemptionOf(points).value());
    }

    /**
     * The points that count in the preview: what is typed when it is allowed, else none (the confirm button says why not).
     *
     * @param canSpend      a customer is attached and has points
     * @param discountValid the typed discount is a number (the bill is not known otherwise)
     */
    public static int previewPoints(String typedPoints, boolean canSpend, int balance, boolean discountValid, BigDecimal remaining) {
        Integer typed = LoyaltyPolicy.parsePoints(typedPoints);
        if (!canSpend || typed == null || !discountValid) {
            return 0;
        }
        return LoyaltyPolicy.redemptionProblem(typed, balance, remaining) == null ? typed : 0;
    }

    /** What goes back to the customer, or null when the amount is not a number or is less than what is due ("-" on screen). */
    public static BigDecimal changePreview(String cashReceivedText, BigDecimal due) {
        BigDecimal received = parseAmount(cashReceivedText);
        if (received == null) {
            return null;
        }
        BigDecimal change = received.subtract(due);
        return change.signum() >= 0 ? change : null;
    }

    /** True when a quick-cash note is worth less than the total, so its button is off. */
    public static boolean noteTooSmall(BigDecimal note, BigDecimal total) {
        return note.compareTo(total) < 0;
    }
}
