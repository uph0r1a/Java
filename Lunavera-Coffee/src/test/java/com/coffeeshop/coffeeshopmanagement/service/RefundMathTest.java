package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.service.RefundMath.Line;
import com.coffeeshop.coffeeshopmanagement.service.RefundMath.Plan;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** TODO 4.37: the arithmetic of refunding some lines of a paid order. No database. */
public class RefundMathTest {

    // The order of the examples: A 2 x 20000, B 1 x 30000; subtotal 70000, discount 7000, so the customer paid 63000.
    private static final long TOTAL = 63000;
    private static final long SUBTOTAL = 70000;

    private static List<Line> fresh() {
        return List.of(new Line(1, 2, 40000, 0), new Line(2, 1, 30000, 0));
    }

    @Test
    public void oneOfTwoPiecesGivesBackItsShareOfWhatWasPaid() {
        Plan plan = RefundMath.plan(TOTAL, SUBTOTAL, 0, 63, 0, fresh(), Map.of(1, 1));
        assertEquals(1, plan.lines().size());
        assertEquals("the line's share before the discount", 20000, plan.lines().get(0).lineAmount());
        assertEquals("20000 x 63000 / 70000: the discount goes with it", 18000, plan.lines().get(0).amount());
        assertEquals(18000, plan.amount());
    }

    @Test
    public void refundingInTwoStepsGivesExactlyWhatOneStepWould() {
        Plan one = RefundMath.plan(TOTAL, SUBTOTAL, 0, 63, 0, fresh(), Map.of(1, 2));
        Plan first = RefundMath.plan(TOTAL, SUBTOTAL, 0, 63, 0, fresh(), Map.of(1, 1));
        List<Line> afterFirst = List.of(new Line(1, 2, 40000, 1), new Line(2, 1, 30000, 0));
        Plan second = RefundMath.plan(TOTAL, SUBTOTAL, first.amount(), 63, 0, afterFirst, Map.of(1, 1));
        assertEquals(one.amount(), first.amount() + second.amount());
        assertEquals(36000, one.amount());
    }

    @Test
    public void refundingEverythingReachesTheOrderTotalExactly() {
        Plan all = RefundMath.plan(TOTAL, SUBTOTAL, 0, 63, 40, fresh(), Map.of(1, 2, 2, 1));
        assertEquals(TOTAL, all.amount());
        assertEquals("every point earned is taken back", 63, all.earnedBack());
        assertEquals("every point spent comes back", 40, all.spentBack());
    }

    @Test
    public void roundingNeverGivesBackMoreThanWasPaid() {
        // three lines of 10000 and a 1 dong discount: each share rounds up to 10000 (30000 in all) but only 29999 was paid
        List<Line> lines = List.of(new Line(1, 1, 10000, 0), new Line(2, 1, 10000, 0), new Line(3, 1, 10000, 0));
        Plan all = RefundMath.plan(29999, 30000, 0, 0, 0, lines, Map.of(1, 1, 2, 1, 3, 1));
        assertEquals(29999, all.amount());
        long sum = 0;
        for (RefundMath.LineRefund r : all.lines()) sum += r.amount();
        assertEquals("the lines add up to the amount", 29999, sum);
    }

    @Test
    public void theLastRefundGivesBackWhatRoundingLeftBehind() {
        // 3 pieces of one line, 100 dong paid for 3 pieces: cumulative 33, 67, 100 give 33, 34, 33: the order is refunded in full
        List<Line> lines = List.of(new Line(1, 3, 100, 0));
        Plan first = RefundMath.plan(100, 100, 0, 0, 0, lines, Map.of(1, 1));
        Plan second = RefundMath.plan(100, 100, first.amount(), 0, 0, List.of(new Line(1, 3, 100, 1)), Map.of(1, 1));
        Plan third = RefundMath.plan(100, 100, first.amount() + second.amount(), 0, 0, List.of(new Line(1, 3, 100, 2)), Map.of(1, 1));
        assertEquals(33, first.amount());
        assertEquals(34, second.amount());
        assertEquals(33, third.amount());
        assertEquals(100, first.amount() + second.amount() + third.amount());
    }

    @Test
    public void pointsMoveInProportionAndSumToTheWholeRefundValues() {
        Plan first = RefundMath.plan(TOTAL, SUBTOTAL, 0, 63, 40, fresh(), Map.of(1, 1)); // 18000 of 63000
        assertEquals("floor(63 x 18000 / 63000)", 18, first.earnedBack());
        assertEquals("floor(40 x 18000 / 63000)", 11, first.spentBack());
        List<Line> rest = List.of(new Line(1, 2, 40000, 1), new Line(2, 1, 30000, 0));
        Plan last = RefundMath.plan(TOTAL, SUBTOTAL, first.amount(), 63, 40, rest, Map.of(1, 1, 2, 1));
        assertEquals(63, first.earnedBack() + last.earnedBack());
        assertEquals(40, first.spentBack() + last.spentBack());
    }

    @Test
    public void anOrderWithNothingPaidRefundsNothing() {
        Plan plan = RefundMath.plan(0, 0, 0, 0, 0, List.of(new Line(1, 1, 0, 0)), Map.of(1, 1));
        assertEquals(0, plan.amount());
        assertEquals(0, plan.earnedBack());
    }

    @Test
    public void theRequestIsCheckedAgainstWhatIsLeft() {
        List<Line> lines = List.of(new Line(1, 2, 40000, 1), new Line(2, 1, 30000, 0));
        assertNull(RefundMath.problem(lines, Map.of(1, 1)));
        assertNull(RefundMath.problem(lines, Map.of(1, 1, 2, 1)));
        assertEquals("only 1 piece of line 1 is left", "refund.val.qty", RefundMath.problem(lines, Map.of(1, 2)));
        assertEquals("refund.val.qty", RefundMath.problem(lines, Map.of(2, -1)));
        assertEquals("refund.val.none", RefundMath.problem(lines, Map.of()));
        assertEquals("a zero is ignored", "refund.val.none", RefundMath.problem(lines, Map.of(1, 0, 2, 0)));
        assertEquals("refund.val.line", RefundMath.problem(lines, Map.of(99, 1)));
    }

    @Test
    public void hugeAmountsDoNotOverflow() {
        List<Line> lines = List.of(new Line(1, 1000, 1_000_000_000L, 0));
        Plan plan = RefundMath.plan(1_000_000_000L, 1_000_000_000L, 0, 1_000_000, 1_000_000, lines, Map.of(1, 500));
        assertEquals(500_000_000L, plan.amount());
        assertEquals(500_000, plan.earnedBack());
    }
}
