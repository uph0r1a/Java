package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.PaymentLine;
import com.coffeeshop.coffeeshopmanagement.model.PaymentMethod;
import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.service.PaymentRules.Entered;
import com.coffeeshop.coffeeshopmanagement.service.PaymentRules.Outcome;
import com.coffeeshop.coffeeshopmanagement.service.PaymentRules.Problem;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.5: what the payment dialog decides, tested with no screen and no database. Every case builds the plain values the dialog
 * reads from its boxes and asks {@link PaymentRules}, the way the confirm button does.
 */
public class PaymentRulesTest {

    private static BigDecimal dong(long amount) {
        return BigDecimal.valueOf(amount);
    }

    /** A plain cash sale of 100.000 with nothing special: the base every test changes one thing of. */
    private static Entered cash() {
        return new Entered(Role.EMPLOYEE, dong(100_000), dong(0), "", dong(100_000), null, "0", null,
                PaymentMethod.CASH, null, dong(100_000), List.of(), false, false, "100000", "", "");
    }

    /** {@code base} with some fields changed, so a test shows only what it is about. */
    private static Entered with(Entered base, java.util.function.UnaryOperator<Builder> change) {
        return change.apply(new Builder(base)).build();
    }

    private static final class Builder {
        Role role; BigDecimal subtotal, manualDiscount, remaining, splitDue; String discountReason, voucherCode, typedPoints, splitProblem;
        String cashText, transferRef, walletRef; Integer pointsBalance; PaymentMethod method; List<PaymentLine> lines;
        boolean transferReceived, walletReceived;

        Builder(Entered e) {
            role = e.role(); subtotal = e.subtotal(); manualDiscount = e.manualDiscount(); discountReason = e.discountReason();
            remaining = e.remaining(); voucherCode = e.voucherCode(); typedPoints = e.typedPoints(); pointsBalance = e.pointsBalance();
            method = e.method(); splitProblem = e.splitProblem(); splitDue = e.splitDue(); lines = e.splitLines();
            transferReceived = e.transferReceived(); walletReceived = e.walletReceived(); cashText = e.cashReceivedText();
            transferRef = e.transferRefText(); walletRef = e.walletRefText();
        }

        Entered build() {
            return new Entered(role, subtotal, manualDiscount, discountReason, remaining, voucherCode, typedPoints, pointsBalance, method,
                    splitProblem, splitDue, lines, transferReceived, walletReceived, cashText, transferRef, walletRef);
        }
    }

    private static void assertRefused(Outcome outcome, Problem expected) {
        assertFalse("the dialog must stay open", outcome.isAccepted());
        assertNull(outcome.payment());
        assertEquals(expected, outcome.problem());
        assertTrue("there is a title to show", outcome.title() != null && !outcome.title().isBlank());
        assertTrue("there is a sentence to show", outcome.message() != null && !outcome.message().isBlank());
    }

    // ------------------------------------------------------------ the happy paths

    @Test
    public void aCashSaleThatCoversTheBillIsAccepted() {
        Outcome outcome = PaymentRules.confirm(cash());
        assertTrue(outcome.isAccepted());
        CheckoutService.Payment payment = outcome.payment();
        assertEquals(PaymentMethod.CASH, payment.method());
        assertEquals(0, dong(100_000).compareTo(payment.total()));
        assertEquals("what the customer handed over is kept", 0, dong(100_000).compareTo(payment.cashReceived()));
        assertNull("a cash sale has no bank reference", payment.transferRef());
        assertEquals(0, payment.pointsRedeemed());
        assertNull(payment.cashPart());
        assertNull(payment.voucherCode());
    }

    @Test
    public void moreCashThanDueIsAcceptedAndKeptForTheChange() {
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> { b.cashText = "200.000"; return b; }));
        assertTrue(outcome.isAccepted());
        assertEquals(0, dong(200_000).compareTo(outcome.payment().cashReceived()));
    }

    @Test
    public void aCardSaleNeedsNoCashAndKeepsNone() {
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> { b.method = PaymentMethod.CARD; b.cashText = "abc"; return b; }));
        assertTrue("the cash box is not read for a card", outcome.isAccepted());
        assertNull(outcome.payment().cashReceived());
        assertNull(outcome.payment().transferRef());
    }

    // ------------------------------------------------------------ the discount

    @Test
    public void aDiscountThatIsNotANumberIsRefusedFirst() {
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> { b.manualDiscount = null; return b; }));
        assertRefused(outcome, Problem.DISCOUNT_NOT_A_NUMBER);
        assertEquals(I18n.t("pos.discount.invalid.number"), outcome.message());
    }

    @Test
    public void anEmployeeCannotGiveMoreThanTheirLimit() {
        long over = 100_000L * (DiscountPolicy.staffMaxPercent() + 1) / 100;
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> {
            b.manualDiscount = dong(over); b.discountReason = "regular"; b.remaining = dong(100_000 - over); return b;
        }));
        assertRefused(outcome, Problem.DISCOUNT_REFUSED);
        assertEquals(DiscountPolicy.messageFor(DiscountPolicy.Result.ABOVE_STAFF_LIMIT, Role.EMPLOYEE, dong(100_000)), outcome.message());
    }

    @Test
    public void aDiscountWithoutAReasonIsRefused() {
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> { b.manualDiscount = dong(5_000); b.remaining = dong(95_000); return b; }));
        assertRefused(outcome, Problem.DISCOUNT_REFUSED);
        assertEquals(DiscountPolicy.messageFor(DiscountPolicy.Result.REASON_REQUIRED, Role.EMPLOYEE, dong(100_000)), outcome.message());
    }

    @Test
    public void anAllowedDiscountIsSavedWithItsTrimmedReason() {
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> {
            b.manualDiscount = dong(5_000); b.discountReason = "  loyal customer  "; b.remaining = dong(95_000); b.cashText = "95000"; b.splitDue = dong(95_000); return b;
        }));
        assertTrue(outcome.isAccepted());
        assertEquals(0, dong(5_000).compareTo(outcome.payment().discount()));
        assertEquals("loyal customer", outcome.payment().discountReason());
        assertEquals(0, dong(95_000).compareTo(outcome.payment().total()));
    }

    @Test
    public void anAdminMayDiscountAboveTheEmployeeLimit() {
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> {
            b.role = Role.ADMIN; b.manualDiscount = dong(50_000); b.discountReason = "owner's guest"; b.remaining = dong(50_000);
            b.cashText = "50000"; b.splitDue = dong(50_000); return b;
        }));
        assertTrue(outcome.isAccepted());
    }

    // ------------------------------------------------------------ points

    @Test
    public void pointsThatAreNotANumberAreRefused() {
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> { b.typedPoints = "-5"; b.pointsBalance = 500; return b; }));
        assertRefused(outcome, Problem.POINTS_NOT_A_NUMBER);
        assertEquals(I18n.t("pos.points.invalid"), outcome.message());
    }

    @Test
    public void pointsWithNoCustomerAttachedAreRefused() {
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> { b.typedPoints = "30"; b.pointsBalance = null; return b; }));
        assertRefused(outcome, Problem.POINTS_REFUSED);
        assertEquals(I18n.t("pos.points.nocustomer"), outcome.message());
    }

    @Test
    public void moreThanTheBalanceIsRefusedWithTheBalanceInTheSentence() {
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> { b.typedPoints = "50"; b.pointsBalance = 40; return b; }));
        assertRefused(outcome, Problem.POINTS_REFUSED);
        assertEquals(I18n.t("pos.points.notenough", 40), outcome.message());
    }

    @Test
    public void aCustomerWithNoPointsCannotSpendAny() {
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> { b.typedPoints = "20"; b.pointsBalance = 0; return b; }));
        assertRefused(outcome, Problem.POINTS_REFUSED);
    }

    @Test
    public void spentPointsLowerTheTotalAndTravelWithThePayment() {
        // 40 points are worth 40.000 on a 100.000 bill (the limit is half the bill).
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> {
            b.typedPoints = "40"; b.pointsBalance = 500; b.cashText = "60000"; b.splitDue = dong(60_000); return b;
        }));
        assertTrue(outcome.isAccepted());
        assertEquals(40, outcome.payment().pointsRedeemed());
        assertEquals(0, dong(60_000).compareTo(outcome.payment().total()));
    }

    // ------------------------------------------------------------ the split box

    @Test
    public void aProblemInTheSplitBoxKeepsTheDialogOpenWithItsOwnSentence() {
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> { b.method = PaymentMethod.CARD; b.splitProblem = "the parts do not add up"; return b; }));
        assertRefused(outcome, Problem.SPLIT_INVALID);
        assertEquals("the parts do not add up", outcome.message());
        assertEquals(I18n.t("pos.split.title"), outcome.title());
    }

    @Test
    public void theLinesOfASplitPaymentAreHandedOn() {
        List<PaymentLine> lines = List.of(new PaymentLine(PaymentMethod.CASH, dong(40_000)), new PaymentLine(PaymentMethod.CARD, dong(60_000)));
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> { b.method = PaymentMethod.CARD; b.lines = lines; return b; }));
        assertTrue(outcome.isAccepted());
        assertEquals(lines, outcome.payment().lines());
    }

    // ------------------------------------------------------------ "the money arrived"

    @Test
    public void aTransferIsRefusedUntilTheMoneyIsTicked() {
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> { b.method = PaymentMethod.TRANSFER; return b; }));
        assertRefused(outcome, Problem.TRANSFER_NOT_CONFIRMED);
    }

    @Test
    public void aTickedTransferKeepsItsCleanedReference() {
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> {
            b.method = PaymentMethod.TRANSFER; b.transferReceived = true; b.transferRef = "  FT26\n0001   ABC "; b.walletRef = "ignored"; return b;
        }));
        assertTrue(outcome.isAccepted());
        assertEquals("FT26 0001 ABC", outcome.payment().transferRef());
        assertNull("a transfer has no cash handed over", outcome.payment().cashReceived());
    }

    @Test
    public void aTransferWithNoReferenceStillWorks() {
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> { b.method = PaymentMethod.TRANSFER; b.transferReceived = true; b.transferRef = "   "; return b; }));
        assertTrue(outcome.isAccepted());
        assertNull(outcome.payment().transferRef());
    }

    @Test
    public void everyEWalletIsRefusedUntilTheMoneyIsTicked() {
        for (PaymentMethod wallet : PaymentMethod.eWallets()) {
            Outcome outcome = PaymentRules.confirm(with(cash(), b -> { b.method = wallet; return b; }));
            assertRefused(outcome, Problem.WALLET_NOT_CONFIRMED);
            assertEquals(I18n.t("pos.unconfirmed.wallet", wallet.label()), outcome.message());
        }
    }

    @Test
    public void aTickedEWalletKeepsTheWalletReferenceNotTheTransferOne() {
        PaymentMethod wallet = PaymentMethod.eWallets().get(0);
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> {
            b.method = wallet; b.walletReceived = true; b.walletRef = "TX 99"; b.transferRef = "bank-ref"; return b;
        }));
        assertTrue(outcome.isAccepted());
        assertEquals(wallet, outcome.payment().method());
        assertEquals("TX 99", outcome.payment().transferRef());
    }

    // ------------------------------------------------------------ cash handed over

    @Test
    public void cashThatIsNotANumberIsRefused() {
        assertRefused(PaymentRules.confirm(with(cash(), b -> { b.cashText = "12x"; return b; })), Problem.CASH_NOT_A_NUMBER);
    }

    @Test
    public void cashBelowWhatIsDueIsRefused() {
        assertRefused(PaymentRules.confirm(with(cash(), b -> { b.cashText = "99999"; return b; })), Problem.CASH_TOO_LITTLE);
    }

    @Test
    public void anEmptyCashBoxCountsAsNothingHandedOver() {
        assertRefused(PaymentRules.confirm(with(cash(), b -> { b.cashText = ""; return b; })), Problem.CASH_TOO_LITTLE);
    }

    @Test
    public void cashIsComparedWithWhatTheMainMethodMustReceiveNotWithTheWholeBill() {
        // 40.000 of the bill is paid by gift card, so only 60.000 is due in cash.
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> { b.cashText = "60000"; b.splitDue = dong(60_000); return b; }));
        assertTrue(outcome.isAccepted());
    }

    @Test
    public void theFirstProblemInTheOrderOfTheDialogIsTheOneShown() {
        // a bad discount, bad points and too little cash at once: the discount is reported
        Outcome outcome = PaymentRules.confirm(with(cash(), b -> {
            b.manualDiscount = null; b.typedPoints = "x"; b.cashText = "1"; return b;
        }));
        assertRefused(outcome, Problem.DISCOUNT_NOT_A_NUMBER);
    }

    // ------------------------------------------------------------ the preview

    @Test
    public void theTotalAfterPointsTakesOneThousandPerPoint() {
        assertEquals(0, dong(60_000).compareTo(PaymentRules.totalAfterPoints(dong(100_000), 40)));
        assertEquals(0, dong(100_000).compareTo(PaymentRules.totalAfterPoints(dong(100_000), 0)));
    }

    @Test
    public void previewPointsCountOnlyWhatIsAllowed() {
        assertEquals(40, PaymentRules.previewPoints("40", true, 500, true, dong(100_000)));
        assertEquals("above the half-bill limit: none", 0, PaymentRules.previewPoints("80", true, 500, true, dong(100_000)));
        assertEquals("below the least that may be spent: none", 0, PaymentRules.previewPoints("10", true, 500, true, dong(100_000)));
        assertEquals("not a number: none", 0, PaymentRules.previewPoints("4o", true, 500, true, dong(100_000)));
        assertEquals("nobody to spend them: none", 0, PaymentRules.previewPoints("40", false, 0, true, dong(100_000)));
        assertEquals("the discount is not a number, so the bill is unknown: none", 0, PaymentRules.previewPoints("40", true, 500, false, dong(100_000)));
    }

    @Test
    public void theChangePreviewShowsWhatGoesBackOrNothing() {
        assertEquals(0, dong(15_000).compareTo(PaymentRules.changePreview("100000", dong(85_000))));
        assertEquals("exactly the amount: zero change", 0, BigDecimal.ZERO.compareTo(PaymentRules.changePreview("85000", dong(85_000))));
        assertNull("less than due", PaymentRules.changePreview("50000", dong(85_000)));
        assertNull("not a number", PaymentRules.changePreview("abc", dong(85_000)));
    }

    @Test
    public void aQuickCashNoteBelowTheTotalIsOff() {
        assertTrue(PaymentRules.noteTooSmall(dong(50_000), dong(60_000)));
        assertFalse(PaymentRules.noteTooSmall(dong(60_000), dong(60_000)));
        assertFalse(PaymentRules.noteTooSmall(dong(100_000), dong(60_000)));
    }
}
