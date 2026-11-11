package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.5: the decisions of the payment are in services, not in the screen classes. The till asks {@code PaymentFlowRules} (may a payment
 * start, which lines the receipt shows, what the screen does after the sale); the split-payment box asks {@code SplitPaymentRules} for every
 * amount and problem and only reads its fields and shows the answers. Reads the sources because both classes build JavaFX nodes. A rule that
 * creeps back into the screen shows up here; the rules themselves are tested in {@code PaymentFlowRulesTest} and {@code SplitPaymentRulesTest}.
 */
public class PaymentLogicInServicesSourceTest {

    private static final Path DIR = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller");

    private static String read(String file) throws IOException {
        return Files.readString(DIR.resolve(file), StandardCharsets.UTF_8);
    }

    @Test
    public void theTillAsksPaymentFlowRulesInsteadOfDecidingItself() throws IOException {
        String till = read("OrderController.java");
        for (String call : new String[]{"PaymentFlowRules.canStart(", "PaymentFlowRules.receiptLines(", "PaymentFlowRules.screenAfter("}) {
            assertTrue(call + " is missing from OrderController", till.contains(call));
        }
        for (String gone : new String[]{"ComplimentaryRules.atNoCharge", "shown != null", "part != null ? part"}) {
            assertFalse(gone + " came back into OrderController: that decision is PaymentFlowRules", till.contains(gone));
        }
    }

    @Test
    public void theSplitBoxHoldsNoPaymentArithmetic() throws IOException {
        String panel = read("SplitPaymentPanel.java");
        for (String call : new String[]{"SplitPaymentRules.otherMethods()", "SplitPaymentRules.due(", "SplitPaymentRules.lines(",
                "SplitPaymentRules.problem(", "SplitPaymentRules.effectiveMethod(", "SplitPaymentRules.offerFor("}) {
            assertTrue(call + " is missing from SplitPaymentPanel", panel.contains(call));
        }
        for (String gone : new String[]{".subtract(", ".compareTo(", ".signum()", "MainPaymentMethod", "MoneyParser", "new HashSet"}) {
            assertFalse(gone + " came back into SplitPaymentPanel: money arithmetic belongs in SplitPaymentRules", panel.contains(gone));
        }
    }

    @Test
    public void theDiscountBoxAsksTheServicesWhatIsLeftAndWhatAVoucherDoes() throws IOException {
        String panel = read("PromotionPanel.java");
        for (String call : new String[]{"VoucherRules.lookup(", "PaymentRules.remaining(", "PaymentRules.voucherTakes("}) {
            assertTrue(call + " is missing from PromotionPanel", panel.contains(call));
        }
        for (String gone : new String[]{"manualOrZero", "VoucherRules.check(", "VoucherRules.discountFor("}) {
            assertFalse(gone + " came back into PromotionPanel: that arithmetic is PaymentRules / VoucherRules", panel.contains(gone));
        }
    }

    @Test
    public void theTillAsksTheServicesWhatItsOrderScreenDecides() throws IOException {
        String till = read("OrderController.java");
        for (String call : new String[]{"OrderScreenRules.isStale(", "OrderScreenRules.shown(", "OrderScreenRules.canSeat(", "OrderScreenRules.isSaved(",
                "OrderScreenRules.somethingSelected(", "Cart.decideQuantity("}) {
            assertTrue(call + " is missing from OrderController", till.contains(call));
        }
        for (String gone : new String[]{"Cart.exceedsLineLimit(", "table == null || table.isOccupied()", "activeOpenOrder.getId() > 0",
                "selectedTableNumber != null || takeAway", "serving ? I18n.t(\"pos.walkin\")"}) {
            assertFalse(gone + " came back into OrderController: that decision is in a service (TODO 6.5)", till.contains(gone));
        }
    }
}
