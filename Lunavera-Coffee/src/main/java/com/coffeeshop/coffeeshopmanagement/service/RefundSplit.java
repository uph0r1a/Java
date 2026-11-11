package com.coffeeshop.coffeeshopmanagement.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the money of a line refund goes when the order was paid, wholly or in part, with a gift card (TODO 2.93, rule D23 of
 * {@link GiftCardRules}). Prepaid value must never turn into cash, so a refund goes back to the card(s) the order spent FIRST, up to what
 * is still spent on them, and only the rest goes back by the method the cashier chose. This is the same outcome as refunding the whole
 * order (which puts everything the cards paid back on them) whatever the order of the line refunds, and it never gives a card more than
 * the order took from it.
 *
 * <p>No screen and no database. {@code RefundDAO} writes what {@link #rows} says, one {@code refunds} row per piece of money: a line
 * paid back wholly by one means is one row; a line that is split between the card and the chosen method is two rows, the card part
 * carrying the pieces and the line's share, the other part 0 of both, so the pieces, the line shares and the money still add up to
 * the plan and every report that reads the rows keeps working.
 */
public final class RefundSplit {

    /** One row to write: the line, its pieces and line share (0 on the second row of a split line), the money, and where it goes. */
    public record Row(int itemId, int quantity, long lineAmount, long amount, boolean toCard) {
    }

    private RefundSplit() {
    }

    /** How much of {@code amount} goes back onto the cards: all of it, but never more than the order still has spent on them. */
    public static long cardPart(long amount, long cardLeft) {
        return Math.max(0, Math.min(amount, cardLeft));
    }

    /**
     * The rows of a refund, the lines in the order given taking from the card money first. A line that is not touched by the card gives
     * one row for the chosen method; a line that the card pays whole gives one row for the card; a line the card pays in part gives two.
     */
    public static List<Row> rows(List<RefundMath.LineRefund> lines, long cardLeft) {
        List<Row> rows = new ArrayList<>();
        long left = Math.max(0, cardLeft);
        for (RefundMath.LineRefund line : lines) {
            long card = Math.min(line.amount(), left);
            left -= card;
            long rest = line.amount() - card;
            if (card == 0) {
                rows.add(new Row(line.itemId(), line.quantity(), line.lineAmount(), line.amount(), false));
            } else {
                rows.add(new Row(line.itemId(), line.quantity(), line.lineAmount(), card, true));
                if (rest > 0) rows.add(new Row(line.itemId(), 0, 0, rest, false));
            }
        }
        return rows;
    }

    /** The money of the rows that go back onto the cards. */
    public static long toCard(List<Row> rows) {
        long sum = 0;
        for (Row row : rows) if (row.toCard()) sum += row.amount();
        return sum;
    }
}
