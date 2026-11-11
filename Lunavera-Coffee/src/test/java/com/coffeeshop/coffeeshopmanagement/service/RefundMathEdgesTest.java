package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.service.RefundMath.Line;
import com.coffeeshop.coffeeshopmanagement.service.RefundMath.Plan;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * TODO 6.78: edges of {@link RefundMath} that a mutation run (tools/mutation-sweep.py) showed no test noticed: the divide-by-nothing
 * guards, a denominator of 1, the last-pieces rule when rounding comes out SHORT, and an adjustment that has to reach the first line or
 * take a line down to zero.
 */
public class RefundMathEdgesTest {

    @Test
    public void shareRoundsHalfUpAndHandlesOneAndZero() {
        assertEquals(0, RefundMath.share(5, 3, 0));
        assertEquals("a denominator of 1 is an ordinary denominator", 21, RefundMath.share(7, 3, 1));
        assertEquals("a half goes up", 1, RefundMath.share(1, 1, 2));
        assertEquals(0, RefundMath.share(1, 1, 3));
    }

    @Test
    public void floorShareRoundsDownAndHandlesOneAndZero() {
        assertEquals(0, RefundMath.floorShare(5, 3, 0));
        assertEquals(21, RefundMath.floorShare(7, 3, 1));
        assertEquals("a half goes down", 0, RefundMath.floorShare(1, 1, 2));
        assertEquals(2, RefundMath.floorShare(5, 2, 4));
    }

    @Test
    public void aNullPieceCountIsIgnoredLikeZero() {
        Map<Integer, Integer> request = new HashMap<>();
        request.put(1, null);
        assertEquals("refund.val.none", RefundMath.problem(List.of(new Line(1, 2, 100, 0)), request));
    }

    @Test
    public void theLastPiecesBringTheTotalUpWhenRoundingCameOutShort() {
        // three 1-dong lines, only 1 dong paid in all: every share rounds down to 0, yet refunding everything must give back the 1 paid
        List<Line> lines = List.of(new Line(1, 1, 1, 0), new Line(2, 1, 1, 0), new Line(3, 1, 1, 0));
        Plan all = RefundMath.plan(1, 3, 0, 0, 0, lines, Map.of(1, 1, 2, 1, 3, 1));
        assertEquals(1, all.amount());
        assertEquals("rounding left over goes with the last line", 1, all.lines().get(2).amount());
    }

    @Test
    public void aPartialRefundIsNotBroughtUpToTheRest() {
        List<Line> lines = List.of(new Line(1, 1, 1, 0), new Line(2, 1, 1, 0), new Line(3, 1, 1, 0));
        Plan some = RefundMath.plan(1, 3, 0, 0, 0, lines, Map.of(1, 1, 2, 1));
        assertEquals("two of three pieces, each share rounds down to 0: not topped up", 0, some.amount());
    }

    @Test
    public void theSecondStepOfALineKeepsTheLinesOwnShareNotTheSum() {
        List<Line> afterFirst = List.of(new Line(1, 2, 40000, 1), new Line(2, 1, 30000, 0));
        Plan second = RefundMath.plan(63000, 70000, 18000, 0, 0, afterFirst, Map.of(1, 1));
        assertEquals(20000, second.lines().get(0).lineAmount());
        assertEquals(18000, second.lines().get(0).amount());
    }

    @Test
    public void aSingleLineIsCutBackToWhatIsLeft() {
        // 60 of the 100 paid is already refunded, so the last line can only give back 40 although its share is 100
        List<Line> lines = List.of(new Line(1, 1, 100, 0));
        Plan plan = RefundMath.plan(100, 100, 60, 0, 0, lines, Map.of(1, 1));
        assertEquals(40, plan.amount());
        assertEquals(40, plan.lines().get(0).amount());
    }

    @Test
    public void theLastLineIsTakenDownToZeroAndTheNextOneAbsorbsTheRest() {
        List<Line> lines = List.of(new Line(1, 1, 10, 0), new Line(2, 1, 90, 0));
        Plan plan = RefundMath.plan(100, 100, 98, 0, 0, lines, Map.of(1, 1, 2, 1));
        assertEquals(2, plan.amount());
        assertEquals(2, plan.lines().get(0).amount());
        assertEquals("the last line cannot go below zero", 0, plan.lines().get(1).amount());
    }

    @Test
    public void theAdjustmentFallsOnTheLastLineFirst() {
        List<Line> lines = List.of(new Line(1, 1, 10000, 0), new Line(2, 1, 10000, 0), new Line(3, 1, 10000, 0));
        Plan all = RefundMath.plan(29999, 30000, 0, 0, 0, lines, Map.of(1, 1, 2, 1, 3, 1));
        assertEquals(10000, all.lines().get(0).amount());
        assertEquals(10000, all.lines().get(1).amount());
        assertEquals(9999, all.lines().get(2).amount());
    }

    @Test
    public void anOrderWithNoSubtotalOrALineWithNoPiecesDoesNotDivideByZero() {
        Plan noSubtotal = RefundMath.plan(0, 0, 0, 0, 0, List.of(new Line(1, 2, 100, 0)), Map.of(1, 1));
        assertEquals(0, noSubtotal.amount());
        // A line with no pieces cannot be asked for (RefundMath.problem refuses it); if it is, the share is 0 and does not divide by zero.
        // It is then the "last pieces" case, so the rest of what was paid goes with it.
        Plan noPieces = RefundMath.plan(100, 100, 0, 0, 0, List.of(new Line(1, 0, 100, 0)), Map.of(1, 1));
        assertEquals(100, noPieces.amount());
    }

    @Test
    public void noPointsEarnedMeansNoPointsTakenBack() {
        Plan plan = RefundMath.plan(100, 100, 0, 0, 0, List.of(new Line(1, 2, 100, 0)), Map.of(1, 1));
        assertEquals(0, plan.earnedBack());
        assertEquals(0, plan.spentBack());
    }

    @Test
    public void aSubtotalOfOneDongStillGivesAShare() {
        // one piece of two, 1 dong paid for the whole line: half a dong rounds up to 1 (a "subtotal of 1 or less" rule would give 0)
        Plan plan = RefundMath.plan(1, 1, 0, 0, 0, List.of(new Line(1, 2, 1, 0)), Map.of(1, 1));
        assertEquals(1, plan.amount());
    }
}
