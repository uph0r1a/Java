package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Order;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import com.coffeeshop.coffeeshopmanagement.util.ReceiptText;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

/** TODO 4.16, rule D5: what spending loyalty points is worth and when it is allowed. No screen, no database. */
public class LoyaltyRedemptionRulesTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    public void onePointTakesOneThousandOffAndTheValueIsWholeDong() {
        assertEquals(0, d("1000").compareTo(LoyaltyPolicy.redemptionOf(1).value()));
        assertEquals(0, d("25000").compareTo(LoyaltyPolicy.redemptionOf(25).value()));
        assertTrue(LoyaltyPolicy.redemptionOf(0).isNone());
        assertTrue(LoyaltyPolicy.redemptionOf(-3).isNone());
        assertSame(LoyaltyPolicy.Redemption.NONE, LoyaltyPolicy.redemptionOf(0));
    }

    @Test
    public void atMostHalfTheBillInWholePointsAndNeverMoreThanTheBalance() {
        assertEquals("half of 40.000 is 20.000 = 20 points", 20, LoyaltyPolicy.maxRedeemable(1000, d("40000")));
        assertEquals("the balance is the limit when it is smaller", 7, LoyaltyPolicy.maxRedeemable(7, d("40000")));
        assertEquals("half of 45.500 is 22.750, whole points: 22", 22, LoyaltyPolicy.maxRedeemable(1000, d("45500")));
        assertEquals("a bill under 2.000 cannot pay even one point", 0, LoyaltyPolicy.maxRedeemable(1000, d("1999")));
        assertEquals(1, LoyaltyPolicy.maxRedeemable(1000, d("2000")));
        assertEquals(0, LoyaltyPolicy.maxRedeemable(0, d("40000")));
        assertEquals(0, LoyaltyPolicy.maxRedeemable(100, BigDecimal.ZERO));
        assertEquals(0, LoyaltyPolicy.maxRedeemable(100, null));
        assertEquals("a huge bill does not overflow", 100, LoyaltyPolicy.maxRedeemable(100, d("99999999999999")));
    }

    @Test
    public void theProblemIsSaidInPlainWordsOrIsNull() {
        BigDecimal bill = d("40000");
        assertNull("no points typed is fine, with or without a customer", LoyaltyPolicy.redemptionProblem(0, null, bill));
        assertNull(LoyaltyPolicy.redemptionProblem(20, 100, bill));
        assertEquals(I18n.t("pos.points.invalid"), LoyaltyPolicy.redemptionProblem(-1, 100, bill));
        assertEquals(I18n.t("pos.points.nocustomer"), LoyaltyPolicy.redemptionProblem(5, null, bill));
        assertEquals(I18n.t("pos.points.notenough", 4), LoyaltyPolicy.redemptionProblem(5, 4, bill));
        assertEquals(I18n.t("pos.points.toomany", 20, 50), LoyaltyPolicy.redemptionProblem(21, 100, bill));
    }

    @Test
    public void typedPointsAreWholeNumbersOnly() {
        assertEquals(Integer.valueOf(0), LoyaltyPolicy.parsePoints(null));
        assertEquals(Integer.valueOf(0), LoyaltyPolicy.parsePoints("   "));
        assertEquals(Integer.valueOf(25), LoyaltyPolicy.parsePoints(" 25 "));
        assertEquals(Integer.valueOf(7), LoyaltyPolicy.parsePoints("007"));
        for (String bad : new String[] {"-1", "1.5", "1,5", "12a", "1 000", "1234567890", "+3"}) {
            assertNull("'" + bad + "' is not a number of points", LoyaltyPolicy.parsePoints(bad));
        }
    }

    @Test
    public void theReasonKeepsTheCashiersWordsAndNotesThePoints() {
        assertEquals("kept as it is when no points were spent", "Khách quen", LoyaltyPolicy.reasonWithPoints("Khách quen", 0));
        assertNull(LoyaltyPolicy.reasonWithPoints(null, 0));
        assertEquals(I18n.t("order.discount.reason.points", 10), LoyaltyPolicy.reasonWithPoints(null, 10));
        assertEquals(I18n.t("order.discount.reason.points", 10), LoyaltyPolicy.reasonWithPoints("   ", 10));
        assertEquals("Khách quen · " + I18n.t("order.discount.reason.points", 10), LoyaltyPolicy.reasonWithPoints("Khách quen", 10));
    }

    @Test
    public void theReceiptSaysHowManyPointsWereSpentOnlyWhenSome() {
        Order none = new Order();
        assertNull(ReceiptText.pointsSpent(none));
        none.setPointsRedeemed(0);
        assertNull(ReceiptText.pointsSpent(none));
        Order some = new Order();
        some.setPointsRedeemed(12);
        assertEquals("in the language of the receipt, which is its own setting", ReceiptText.t("receipt.points.spent", "12"), ReceiptText.pointsSpent(some));
    }
}
