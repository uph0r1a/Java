package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.PaymentLine;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.util.Money;
import com.coffeeshop.coffeeshopmanagement.util.MoneyParser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The arithmetic of paying one order with several methods and gift cards (TODO 4.32 and 7, the owner's rules D4 and D23), apart from the
 * screen so it can be tested without JavaFX (TODO 6.5). It is what {@code SplitPaymentPanel} used to work out next to its text fields; the
 * panel now only reads its fields into an {@link Input} and shows the answers. The rule checked when the order is saved is still
 * {@code OrderDAO.checkLines}; this only helps to type it and refuses the obvious mistakes first.
 *
 * <p><b>The rules:</b> the cashier chooses a <i>main</i> method; the split box (ticked only while a method other than cash is the main
 * one) takes an amount for each <i>other</i> method; gift cards come off first, as many as the customer has, each checked against the
 * database, none used twice, none for more than it holds, all together not more than the bill; the main method receives what is left. A
 * ticked split needs at least one other amount and leaves the main method something; cards alone may pay the whole bill.
 */
public final class SplitPaymentRules {

    private SplitPaymentRules() {
    }

    /** What is wrong, as the panel words it: nothing, a gift-card problem, or amounts that do not fit the bill. */
    public enum Problem { NONE, GIFT, SPLIT }

    /**
     * One gift card of the bill as typed.
     *
     * @param amountText what is typed to take from the card (empty is zero)
     * @param balance    what the card holds, or {@code null} while the typed code has not been checked or was not found
     * @param code       the code of the card that was found, or {@code null}
     */
    public record Card(String amountText, Long balance, String code) {

        /** The amount typed for this card: zero when empty, {@code null} when it is not a number or is negative. */
        public BigDecimal value() {
            if (amountText == null || amountText.isBlank()) return BigDecimal.ZERO;
            BigDecimal parsed = MoneyParser.parseOptionalDong(amountText);
            return parsed == null || parsed.signum() < 0 ? null : parsed;
        }
    }

    /**
     * Everything the rules need from the box.
     *
     * @param total      what is to be paid in all (after discounts and points)
     * @param main       the method the cashier chose
     * @param splitOn    whether the split box is ticked
     * @param typedTexts the text typed for each other method (a method missing or empty has no amount)
     * @param cards      the gift cards, in the order shown
     */
    public record Input(BigDecimal total, PaymentMethod main, boolean splitOn, Map<PaymentMethod, String> typedTexts, List<Card> cards) {
        public Input {
            typedTexts = typedTexts == null ? Map.of() : typedTexts;
            cards = cards == null ? List.of() : List.copyOf(cards);
        }
    }

    /** Every method the cashier types an amount for in the split box: not the gift card (it has its own part) and never a free drink. */
    public static List<PaymentMethod> otherMethods() {
        List<PaymentMethod> result = new ArrayList<>(List.of(PaymentMethod.values()));
        result.remove(PaymentMethod.GIFT_CARD);
        result.remove(PaymentMethod.COMPLIMENTARY); // never a way to pay (TODO 4.61)
        return result;
    }

    /** The amounts typed for the other methods, in the order of the method list; a field left empty or zero is not a line. */
    public static Map<PaymentMethod, BigDecimal> typed(Input in) {
        Map<PaymentMethod, BigDecimal> result = new EnumMap<>(PaymentMethod.class);
        for (PaymentMethod method : otherMethods()) {
            if (method == in.main()) continue;
            BigDecimal amount = MoneyParser.parseOptionalDong(in.typedTexts().get(method));
            if (amount != null && amount.signum() > 0) result.put(method, amount);
        }
        return result;
    }

    private static BigDecimal typedSum(Input in) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal amount : typed(in).values()) sum = sum.add(amount);
        return sum;
    }

    /** What is typed to take from all the cards together: zero when none, {@code null} when an amount is not a number. */
    public static BigDecimal gift(Input in) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Card card : in.cards()) {
            BigDecimal value = card.value();
            if (value == null) return null;
            sum = sum.add(value);
        }
        return sum;
    }

    /**
     * True when every card with an amount was checked and holds at least that, no card is used twice, and the cards together are not
     * more than the bill.
     */
    public static boolean giftOk(Input in, BigDecimal sum) {
        if (sum.compareTo(in.total()) > 0) return false;
        Set<String> codes = new HashSet<>();
        for (Card card : in.cards()) {
            BigDecimal value = card.value();
            if (value == null || value.signum() == 0) continue;
            if (card.balance() == null || card.code() == null || value.compareTo(Money.fromDong(card.balance())) > 0 || !codes.add(card.code())) {
                return false;
            }
        }
        return true;
    }

    /** True when every typed amount is a number, a ticked split has at least one other amount, and the main method keeps something. */
    public static boolean valid(Input in) {
        BigDecimal gift = gift(in);
        if (gift == null || (gift.signum() > 0 && !giftOk(in, gift))) return false;
        if (!in.splitOn()) return true;
        for (PaymentMethod method : otherMethods()) {
            String text = in.typedTexts().get(method);
            if (method != in.main() && text != null && !text.isBlank() && MoneyParser.parseOptionalDong(text) == null) return false;
        }
        BigDecimal sum = typedSum(in);
        if (gift.signum() > 0) return gift.add(sum).compareTo(in.total()) <= 0; // the card may pay for the rest
        return sum.signum() > 0 && sum.compareTo(in.total()) < 0;
    }

    /** What the main method has to receive now (the whole total while the typed amounts are not usable). */
    public static BigDecimal due(Input in) {
        if (!valid(in)) return in.total();
        BigDecimal gift = gift(in);
        return in.total().subtract(gift == null ? BigDecimal.ZERO : gift).subtract(in.splitOn() ? typedSum(in) : BigDecimal.ZERO);
    }

    /** The method the payment is saved under: only cards that pay the whole bill make it a gift-card payment (TODO 2.102), else the one chosen. */
    public static PaymentMethod effectiveMethod(Input in, PaymentMethod chosen) {
        return MainPaymentMethod.effective(chosen, valid(in), gift(in), in.total());
    }

    /**
     * The lines to save, or an empty list when the split is not ticked and no card is used: one line per card, then the other methods as
     * typed, then the main method with what is left. Call {@link #problem} first; with a problem the lines are not worked out.
     */
    public static List<PaymentLine> lines(Input in) {
        List<PaymentLine> result = new ArrayList<>();
        if (!valid(in)) return result;
        BigDecimal gift = gift(in);
        if (!in.splitOn() && gift.signum() == 0) return result;
        for (Card card : in.cards()) { // one line per card; OrderDAO adds them into the one GIFT_CARD row
            BigDecimal value = card.value();
            if (value.signum() > 0) result.add(new PaymentLine(PaymentMethod.GIFT_CARD, value, card.code()));
        }
        if (in.splitOn()) typed(in).forEach((method, amount) -> result.add(new PaymentLine(method, amount)));
        BigDecimal rest = in.total().subtract(gift).subtract(in.splitOn() ? typedSum(in) : BigDecimal.ZERO);
        if (rest.signum() > 0) result.add(new PaymentLine(in.main(), rest));
        return result;
    }

    /** What is wrong with the typed amounts; {@link Problem#NONE} when they are fine or not used. */
    public static Problem problem(Input in) {
        if (valid(in)) return Problem.NONE;
        BigDecimal gift = gift(in);
        return gift != null && gift.signum() > 0 && !giftOk(in, gift) ? Problem.GIFT : Problem.SPLIT;
    }

    /** Whether the box shows the line "the main method receives ...": the amounts are usable and the split or a card is in use. */
    public static boolean showsRest(Input in) {
        BigDecimal gift = gift(in);
        return valid(in) && (in.splitOn() || (gift != null && gift.signum() > 0));
    }

    /**
     * The amount to fill in for a card that was just found, or {@code null} to leave the field alone: the most this card can pay of what
     * the other cards leave of the bill, offered only while this card's own amount is still empty (zero) and the card holds something.
     *
     * @param ownValue    what is typed for this card now (zero when empty, {@code null} when not a number)
     * @param otherValues what is typed for the other cards (an entry that is not a number is {@code null} and counts as nothing)
     */
    public static Long offerFor(BigDecimal total, long balance, BigDecimal ownValue, List<BigDecimal> otherValues) {
        if (ownValue == null || ownValue.signum() != 0 || balance <= 0) return null;
        BigDecimal others = BigDecimal.ZERO;
        for (BigDecimal other : otherValues) if (other != null) others = others.add(other);
        long left = Math.max(0, total.longValue() - others.longValue());
        return Math.min(balance, left);
    }
}
