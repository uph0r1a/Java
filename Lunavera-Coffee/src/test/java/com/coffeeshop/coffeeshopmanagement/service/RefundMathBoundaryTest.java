package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.service.RefundMath.Line;
import com.coffeeshop.coffeeshopmanagement.service.RefundMath.Plan;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * TODO 6.78: tests that came from a mutation run on {@link RefundMath}. Each one fails when a specific change is made to the class
 * (a boundary moved, a sign flipped, a guard removed) that the earlier tests did not notice. No database.
 */
public class RefundMathBoundaryTest {

    @Test
    public void aDenominatorOfOneIsAnOrdinaryDivisionAndOnlyZeroGivesNothing() {
        assertEquals(21, RefundMath.share(7, 3, 1));
        assertEquals(21, RefundMath.floorShare(7, 3, 1));
        assertEquals(0, RefundMath.share(7, 3, 0));
        assertEquals(0, RefundMath.floorShare(7, 3, 0));
    }

    @Test
    public void aMissingCountIsNothingChosenNotOnePiece() {
        Map<Integer, Integer> request = new HashMap<>();
        request.put(1, null);
        assertEquals("refund.val.none", RefundMath.problem(List.of(new Line(1, 2, 40000, 0)), request));
    }

    @Test
    public void theLastPiecesTakeWhatTheRoundingLeftOverEvenWhenEveryLineRoundedDown() {
        // three lines of 1000, the customer paid 1000 of 3000: each share is 333.33, so the lines add up to 999 and 1 is missing
        List<Line> lines = List.of(new Line(1, 1, 1000, 0), new Line(2, 1, 1000, 0), new Line(3, 1, 1000, 0));
        Plan plan = RefundMath.plan(1000, 3000, 0, 0, 0, lines, Map.of(1, 1, 2, 1, 3, 1));
        assertEquals("a refund of the whole order gives back exactly what was paid", 1000, plan.amount());
        assertEquals(333, plan.lines().get(0).amount());
        assertEquals(333, plan.lines().get(1).amount());
        assertEquals("the missing dong goes with the last line", 334, plan.lines().get(2).amount());
        assertEquals(1000, plan.lines().stream().mapToLong(RefundMath.LineRefund::amount).sum());
    }

    @Test
    public void aSecondRefundOfALineReportsOnlyItsOwnShareOfTheLine() {
        // 3 pieces for 3000, one already given back: refunding one more is 1000 of the line, not 3000
        Plan plan = RefundMath.plan(3000, 3000, 1000, 0, 0, List.of(new Line(1, 3, 3000, 1)), Map.of(1, 1));
        assertEquals(1000, plan.lines().get(0).lineAmount());
        assertEquals(1000, plan.lines().get(0).amount());
        assertEquals(1000, plan.amount());
    }

    @Test
    public void noPointsAreTakenBackOrGivenBackWhenNoneWereEarnedOrSpent() {
        Plan plan = RefundMath.plan(1000, 1000, 0, 0, 0, List.of(new Line(1, 2, 1000, 0)), Map.of(1, 1));
        assertEquals(0, plan.earnedBack());
        assertEquals(0, plan.spentBack());
    }

    @Test
    public void anOrderWithNoSubtotalGivesNothingBackAndDoesNotDivideByZero() {
        Plan plan = RefundMath.plan(1000, 0, 0, 0, 0, List.of(new Line(1, 2, 1000, 0)), Map.of(1, 1));
        assertEquals(0, plan.amount());
        assertEquals(0, plan.lines().get(0).amount());
    }

    @Test
    public void aLineOfZeroPiecesDoesNotDivideByZeroEither() {
        Plan plan = RefundMath.plan(1000, 1000, 0, 0, 0, List.of(new Line(1, 0, 500, 0)), Map.of(1, 1));
        assertTrue(plan.amount() <= 1000);
    }

    @Test
    public void aSinglePieceLineIsRefundedForItsShareWhileOtherLinesRemain() {
        List<Line> lines = List.of(new Line(1, 1, 1000, 0), new Line(2, 2, 2000, 0));
        Plan plan = RefundMath.plan(3000, 3000, 0, 0, 0, lines, Map.of(1, 1));
        assertEquals(1000, plan.amount());
    }

    @Test
    public void anOrderOfOneDongStillSharesItsMoney() {
        // half of a 1-dong line, rounded half up, is 1 dong: a guard that treats "1 dong" as "no money" gives nothing
        Plan plan = RefundMath.plan(1, 1, 0, 0, 0, List.of(new Line(1, 2, 1, 0)), Map.of(1, 1));
        assertEquals(1, plan.amount());
    }

    @Test
    public void whenTheLinesRoundUpPastWhatIsLeftEveryLineIsTrimmedUntilTheSumFits() {
        // each of the three lines shares 0.67 of a dong, which rounds to 1, so the lines claim 3 dong against 0 left
        List<Line> lines = List.of(new Line(1, 1, 1000, 0), new Line(2, 1, 1000, 0), new Line(3, 1, 1000, 0));
        Plan plan = RefundMath.plan(2, 3000, 2, 0, 0, lines, Map.of(1, 1, 2, 1, 3, 1));
        assertEquals(0, plan.amount());
        for (RefundMath.LineRefund line : plan.lines()) {
            assertEquals("line " + line.itemId() + " must be trimmed too, the first one included", 0, line.amount());
        }
    }

    @Test
    public void moneyLeftOnAnOrderWithNothingMoreToRefundIsNotInventedAsARefund() {
        // the one line is already fully refunded, but 600 of the order total was never given back (rounding of old refunds)
        Plan plan = RefundMath.plan(1000, 1000, 400, 0, 0, List.of(new Line(1, 1, 1000, 1)), Map.of());
        assertEquals(0, plan.amount());
        assertTrue(plan.lines().isEmpty());
    }
}
