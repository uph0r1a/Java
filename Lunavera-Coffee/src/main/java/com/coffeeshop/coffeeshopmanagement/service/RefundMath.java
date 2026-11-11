package com.coffeeshop.coffeeshopmanagement.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The arithmetic of refunding some lines of a paid order (TODO 4.37), apart from the database and the screen so it can be tested and
 * shown live in the refund box. Every figure is whole dong or whole points.
 *
 * <p><b>The rules</b> (decision made here, the TODO leaves them open):
 * <ul>
 *   <li>A refund is for a number of pieces of a line, never more than what is left of it (sold minus refunded before).</li>
 *   <li>What is given back for a line is its share of what the customer PAID for the order: the line's total times the order total
 *       divided by the subtotal, so the share of the order's discount (and of the loyalty points spent, which are inside the discount,
 *       TODO 4.16) goes with it. It is worked out on the pieces refunded so far ("cumulative"), so refunding a line in two steps gives
 *       exactly what refunding it in one step would, with no rounding drift.</li>
 *   <li>All refunds of an order together never exceed its total; when the last piece of the last line is refunded, the total is
 *       reached exactly (rounding left over is given back with it).</li>
 *   <li>The loyalty points the order earned are taken back, and the points the customer spent on it are given back, in proportion to
 *       the money refunded so far: the first refund moves its share, and the last one brings both to exactly what a whole-order
 *       refund moves.</li>
 * </ul>
 */
public final class RefundMath {

    /** One line of the order as it stands: what was sold, and how many pieces were refunded before. */
    public record Line(int itemId, int quantity, long lineTotal, int refundedQuantity) {
        public int remaining() {
            return quantity - refundedQuantity;
        }
    }

    /** What is given back for one line in this refund. {@code lineAmount} is the line's total share (before the order's discount), {@code amount} what is paid back. */
    public record LineRefund(int itemId, int quantity, long lineAmount, long amount) {
    }

    /**
     * The whole refund.
     *
     * @param amount     the money to give back (the sum of the lines)
     * @param earnedBack the loyalty points to take back from the customer
     * @param spentBack  the loyalty points to give back to the customer
     */
    public record Plan(List<LineRefund> lines, long amount, int earnedBack, int spentBack) {
    }

    private RefundMath() {
    }

    /** {@code round(value * numerator / denominator)}, half up, without overflow; 0 when the denominator is 0. */
    static long share(long value, long numerator, long denominator) {
        if (denominator == 0) {
            return 0;
        }
        BigInteger top = BigInteger.valueOf(value).multiply(BigInteger.valueOf(numerator));
        return new BigDecimal(top).divide(new BigDecimal(denominator), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** {@code floor(value * numerator / denominator)}; 0 when the denominator is 0. */
    static long floorShare(long value, long numerator, long denominator) {
        if (denominator == 0) {
            return 0;
        }
        return BigInteger.valueOf(value).multiply(BigInteger.valueOf(numerator)).divide(BigInteger.valueOf(denominator)).longValueExact();
    }

    /**
     * What is wrong with the request, as a message key, or null when it can be carried out: nothing chosen, a line that is not in the
     * order, or more pieces than are left.
     *
     * @param request item id to number of pieces; a line with 0 is ignored
     */
    public static String problem(List<Line> lines, Map<Integer, Integer> request) {
        boolean any = false;
        for (Map.Entry<Integer, Integer> entry : request.entrySet()) {
            int wanted = entry.getValue() == null ? 0 : entry.getValue();
            if (wanted < 0) {
                return "refund.val.qty";
            }
            if (wanted == 0) {
                continue;
            }
            Line line = find(lines, entry.getKey());
            if (line == null) {
                return "refund.val.line";
            }
            if (wanted > line.remaining()) {
                return "refund.val.qty";
            }
            any = true;
        }
        return any ? null : "refund.val.none";
    }

    private static Line find(List<Line> lines, int itemId) {
        for (Line line : lines) {
            if (line.itemId() == itemId) {
                return line;
            }
        }
        return null;
    }

    /**
     * Works out a valid request ({@link #problem} returned null).
     *
     * @param orderTotal     what the customer paid for the order (after the discount)
     * @param orderSubtotal  the lines' total before the discount
     * @param refundedSoFar  the money already given back on this order by earlier refunds
     * @param pointsEarned   the points the order gave (what {@code orders.points_earned} holds, or today's rule for an old order)
     * @param pointsSpent    the points the customer spent on the order
     */
    public static Plan plan(long orderTotal, long orderSubtotal, long refundedSoFar, int pointsEarned, int pointsSpent,
                            List<Line> lines, Map<Integer, Integer> request) {
        List<LineRefund> refunds = new ArrayList<>();
        long amount = 0;
        boolean everythingGoes = true;
        for (Line line : lines) {
            int wanted = Math.max(0, request.getOrDefault(line.itemId(), 0));
            if (wanted < line.remaining()) {
                everythingGoes = false;
            }
            if (wanted == 0) {
                continue;
            }
            int before = line.refundedQuantity();
            int after = before + wanted;
            long lineAmount = share(line.lineTotal(), after, line.quantity()) - share(line.lineTotal(), before, line.quantity());
            long net = netShare(line, after, orderTotal, orderSubtotal) - netShare(line, before, orderTotal, orderSubtotal);
            refunds.add(new LineRefund(line.itemId(), wanted, lineAmount, net));
            amount += net;
        }
        long room = Math.max(0, orderTotal - refundedSoFar);
        if (everythingGoes && amount != room) {
            // the last pieces of the order: bring the total to exactly what is left (rounding left over goes with them)
            amount = adjust(refunds, room);
        } else if (amount > room) {
            amount = adjust(refunds, room); // never more than the order total, whatever the rounding did
        }
        long after = refundedSoFar + amount;
        int earnedBack = (int) (floorShare(pointsEarned, after, orderTotal) - floorShare(pointsEarned, refundedSoFar, orderTotal));
        int spentBack = (int) (floorShare(pointsSpent, after, orderTotal) - floorShare(pointsSpent, refundedSoFar, orderTotal));
        return new Plan(List.copyOf(refunds), amount, Math.max(0, earnedBack), Math.max(0, spentBack));
    }

    /** What the customer paid for {@code pieces} pieces of the line: its total times the order total over the subtotal. */
    private static long netShare(Line line, int pieces, long orderTotal, long orderSubtotal) {
        if (orderSubtotal <= 0 || line.quantity() <= 0) {
            return 0;
        }
        BigInteger top = BigInteger.valueOf(line.lineTotal()).multiply(BigInteger.valueOf(pieces)).multiply(BigInteger.valueOf(orderTotal));
        BigInteger bottom = BigInteger.valueOf(line.quantity()).multiply(BigInteger.valueOf(orderSubtotal));
        return new BigDecimal(top).divide(new BigDecimal(bottom), 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** Changes the amount of the last refunded lines so that the sum is {@code target}; returns {@code target}. */
    private static long adjust(List<LineRefund> refunds, long target) {
        long sum = 0;
        for (LineRefund r : refunds) {
            sum += r.amount();
        }
        long diff = target - sum;
        for (int i = refunds.size() - 1; i >= 0 && diff != 0; i--) {
            LineRefund r = refunds.get(i);
            long changed = Math.max(0, r.amount() + diff);
            diff -= changed - r.amount();
            refunds.set(i, new LineRefund(r.itemId(), r.quantity(), r.lineAmount(), changed));
        }
        return target - diff;
    }
}
