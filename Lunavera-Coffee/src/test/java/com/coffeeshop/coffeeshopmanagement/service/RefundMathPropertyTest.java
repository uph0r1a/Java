package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.service.RefundMath.Line;
import com.coffeeshop.coffeeshopmanagement.service.RefundMath.LineRefund;
import com.coffeeshop.coffeeshopmanagement.service.RefundMath.Plan;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.76: random orders (1 to 4 lines, discounts from none to 90 %, points earned and spent) refunded in random pieces until
 * nothing is left, with the rules of {@link RefundMath} checked at every step. The same check was run once with 200,000 orders and
 * 672,077 steps and everything held; this keeps it in the repository with a fixed seed (see {@link RandomCases} to replay).
 */
public class RefundMathPropertyTest {

    private static final int ORDERS = 4000;

    /** An order as the refund screen would load it. */
    private record Order(List<Line> lines, long subtotal, long total, int earned, int spent) {
    }

    private static Order randomOrder(Random random) {
        int lineCount = RandomCases.between(random, 1, 4);
        List<Line> lines = new ArrayList<>();
        long subtotal = 0;
        for (int i = 0; i < lineCount; i++) {
            int quantity = RandomCases.between(random, 1, 6);
            // small and odd prices on purpose: that is where rounding shows; an extra cost of an option makes a line not a multiple of its quantity
            long unit = random.nextInt(4) == 0 ? RandomCases.between(random, 1, 9) : RandomCases.between(random, 1, 150) * 1000L - random.nextInt(2) * 500L; // never 0: a priced line
            long lineTotal = unit * quantity + (random.nextInt(5) == 0 ? RandomCases.between(random, 1, 9_999) : 0);
            lines.add(new Line(100 + i, quantity, lineTotal, 0));
            subtotal += lineTotal;
        }
        int percent = random.nextInt(3) == 0 ? 0 : RandomCases.between(random, 1, 90);
        long total = subtotal - subtotal * percent / 100; // whole dong; a 90 % discount of a positive bill never reaches 0
        int earned = (int) (total / 10_000);
        int spent = random.nextBoolean() || total == 0 ? 0 : RandomCases.between(random, 0, 300); // points are only spent on a bill that has something to pay
        return new Order(lines, subtotal, total, earned, spent);
    }

    /** A random request that {@link RefundMath#problem} accepts: some pieces of at least one line that still has pieces. */
    private static Map<Integer, Integer> randomRequest(Random random, List<Line> lines, boolean everything) {
        Map<Integer, Integer> request = new HashMap<>();
        for (Line line : lines) {
            if (line.remaining() == 0) {
                continue;
            }
            if (everything) {
                request.put(line.itemId(), line.remaining());
            } else if (random.nextInt(3) != 0) {
                request.put(line.itemId(), RandomCases.between(random, 1, line.remaining()));
            }
        }
        if (request.isEmpty()) { // nothing chosen is refused by problem(); take one piece of the first line that has one
            for (Line line : lines) {
                if (line.remaining() > 0) {
                    request.put(line.itemId(), 1);
                    break;
                }
            }
        }
        return request;
    }

    private static List<Line> after(List<Line> lines, Map<Integer, Integer> request) {
        List<Line> next = new ArrayList<>();
        for (Line line : lines) {
            next.add(new Line(line.itemId(), line.quantity(), line.lineTotal(), line.refundedQuantity() + request.getOrDefault(line.itemId(), 0)));
        }
        return next;
    }

    @Test
    public void everyStepOfEveryRandomRefundKeepsTheRules() {
        RandomCases.run("RefundMath.plan", ORDERS, random -> {
            Order order = randomOrder(random);
            List<Line> lines = order.lines();
            long refunded = 0;
            long earnedBack = 0;
            long spentBack = 0;
            int steps = 0;
            while (lines.stream().anyMatch(l -> l.remaining() > 0)) {
                Map<Integer, Integer> request = randomRequest(random, lines, random.nextInt(6) == 0);
                assertNull("a request made of pieces that are left must be accepted", RefundMath.problem(lines, request));
                Plan plan = RefundMath.plan(order.total(), order.subtotal(), refunded, order.earned(), order.spent(), lines, request);

                long sum = 0;
                for (LineRefund r : plan.lines()) {
                    assertTrue("no negative amount for a line: " + r, r.amount() >= 0 && r.lineAmount() >= 0);
                    assertEquals("the pieces refunded are the pieces asked for", (int) request.get(r.itemId()), r.quantity());
                    sum += r.amount();
                }
                assertEquals("the lines add up to the amount", sum, plan.amount());
                assertTrue("an amount is never negative", plan.amount() >= 0);
                assertTrue("never more than the order total in all: " + (refunded + plan.amount()) + " > " + order.total(),
                        refunded + plan.amount() <= order.total());
                assertTrue("points are never negative", plan.earnedBack() >= 0 && plan.spentBack() >= 0);

                refunded += plan.amount();
                earnedBack += plan.earnedBack();
                spentBack += plan.spentBack();
                assertTrue("points taken back never pass the points given", earnedBack <= order.earned());
                assertTrue("points given back never pass the points spent", spentBack <= order.spent());
                lines = after(lines, request);
                steps++;
                assertTrue("a refund of an order ends after at most one step per piece", steps <= 24);
            }
            assertEquals("the last piece brings the total to exactly what was paid", order.total(), refunded);
            assertEquals("the points taken back add up to the points given", order.earned(), earnedBack);
            assertEquals("the points given back add up to the points spent", order.spent(), spentBack);
        });
    }

    @Test
    public void refundingTheWholeOrderInOneStepGivesBackTheTotalAndAllThePoints() {
        RandomCases.run("RefundMath.plan, whole order", ORDERS, random -> {
            Order order = randomOrder(random);
            Map<Integer, Integer> request = randomRequest(random, order.lines(), true);
            Plan plan = RefundMath.plan(order.total(), order.subtotal(), 0, order.earned(), order.spent(), order.lines(), request);
            assertEquals(order.total(), plan.amount());
            assertEquals(order.earned(), plan.earnedBack());
            assertEquals(order.spent(), plan.spentBack());
        });
    }

    @Test
    public void twoStepsGiveBackWhatOneStepWould() {
        // "worked out on the pieces refunded so far": a refund made in two pieces moves exactly what one refund of both pieces moves,
        // as long as the order is not finished by it (the last pieces are brought to the total, which is a different rule)
        RandomCases.run("RefundMath.plan, two steps", ORDERS, random -> {
            Order order = randomOrder(random);
            Map<Integer, Integer> first = new HashMap<>();
            Map<Integer, Integer> second = new HashMap<>();
            Map<Integer, Integer> both = new HashMap<>();
            boolean leavesSomething = false;
            for (Line line : order.lines()) {
                int a = RandomCases.between(random, 0, line.quantity());
                int b = RandomCases.between(random, 0, line.quantity() - a);
                if (a > 0) {
                    first.put(line.itemId(), a);
                }
                if (b > 0) {
                    second.put(line.itemId(), b);
                }
                if (a + b > 0) {
                    both.put(line.itemId(), a + b);
                }
                leavesSomething |= a + b < line.quantity();
            }
            if (first.isEmpty() || second.isEmpty() || !leavesSomething) {
                return; // not a two-step refund of part of the order
            }
            Plan one = RefundMath.plan(order.total(), order.subtotal(), 0, order.earned(), order.spent(), order.lines(), both);
            Plan p1 = RefundMath.plan(order.total(), order.subtotal(), 0, order.earned(), order.spent(), order.lines(), first);
            Plan p2 = RefundMath.plan(order.total(), order.subtotal(), p1.amount(), order.earned(), order.spent(), after(order.lines(), first), second);
            assertEquals("money", one.amount(), p1.amount() + p2.amount());
            assertEquals("points taken back", one.earnedBack(), p1.earnedBack() + p2.earnedBack());
            assertEquals("points given back", one.spentBack(), p1.spentBack() + p2.spentBack());
        });
    }
}
