package com.coffeeshop.coffeeshopmanagement.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The stock history of one product or one ingredient (TODO 4.59): every movement of the number, whatever wrote it, in one list,
 * newest first, each with the stock it left behind. No JavaFX and no database: {@code StockHistoryDAO} reads the movements, this
 * class orders them and works out the running balance, so both can be tested on their own.
 *
 * <p><b>How the balance is worked out.</b> The stock of today is the one number that is certainly right (it is what the sales
 * screen uses), so the balance is counted <i>backwards</i> from it: the newest movement left today's stock, the one before it left
 * that stock minus what the newer one moved, and so on. A number that was changed without any trace (an ingredient created with a
 * starting stock, a line paid before sales were recorded) therefore never makes the newest lines wrong; it only shows up as the
 * number {@link #before} at the bottom of the list.
 */
public final class StockHistory {

    /** What moved the stock. */
    public enum Kind {
        /** Goods received (the stock log, TODO 4.5). */
        RECEIPT,
        /** A stock number typed over on the product form (the stock log). */
        ADJUST,
        /** Waste or breakage (the stock log, TODO 4.33). */
        WASTE,
        /** Taken off by a paid order (TODO 4.41, 2.96). */
        SALE,
        /** Put back on the shelf by a refund of lines or of a whole order (TODO 4.37, 2.66). */
        REFUND,
        /** A delivery or a count of an ingredient (its audit line, TODO 4.41, 4.51). */
        CHANGE
    }

    /**
     * One movement.
     *
     * @param quantity how many pieces (or units of an ingredient) were added; negative when the stock went down
     * @param orderId  the order behind a sale or a refund, null for the others
     * @param by       who did it, when the record says (a sale does not: the order has its cashier)
     * @param note     a reason, a supplier or a note, null when there is none
     * @param balance  the stock right after this movement (filled by {@link #withBalance})
     */
    public record Move(LocalDateTime at, Kind kind, long quantity, Integer orderId, String by, String note, long balance) {

        /** A movement whose balance is not known yet. */
        public static Move of(LocalDateTime at, Kind kind, long quantity, Integer orderId, String by, String note) {
            return new Move(at, kind, quantity, orderId, by, note, 0);
        }
    }

    /**
     * What the screen shows.
     *
     * @param moves  the newest first, at most the number asked for
     * @param now    the stock today
     * @param before the stock before the oldest movement shown
     * @param cut    true when older movements exist that are not in {@code moves}
     */
    public record Page(List<Move> moves, long now, long before, boolean cut) {
    }

    private StockHistory() {
    }

    /**
     * The movements newest first. Two movements at the same moment keep the order they were given in, which is how a reader would
     * expect a receipt and the sale that followed it a second later to read; the total is the same whichever comes first.
     */
    public static List<Move> newestFirst(List<Move> unsorted) {
        List<Move> sorted = new ArrayList<>(unsorted);
        sorted.sort(Comparator.comparing(Move::at, Comparator.nullsFirst(Comparator.naturalOrder())).reversed()); // stable
        return sorted;
    }

    /** The same movements (newest first) with the balance after each one, counted backwards from {@code stockNow}. */
    public static List<Move> withBalance(List<Move> newestFirst, long stockNow) {
        List<Move> result = new ArrayList<>(newestFirst.size());
        long balance = stockNow;
        for (Move move : newestFirst) {
            result.add(new Move(move.at(), move.kind(), move.quantity(), move.orderId(), move.by(), move.note(), balance));
            balance -= move.quantity();
        }
        return result;
    }

    /** The stock before the last (oldest) movement of {@code withBalance}; {@code stockNow} when the list is empty. */
    public static long before(List<Move> withBalance, long stockNow) {
        if (withBalance.isEmpty()) return stockNow;
        Move oldest = withBalance.get(withBalance.size() - 1);
        return oldest.balance() - oldest.quantity();
    }

    /**
     * Puts the movements of every source together: newest first, balance from today's stock, only the newest {@code limit} kept.
     * The balance is counted over ALL the movements given before any are dropped, so a line is never wrong because of the cut.
     * Each source may itself have been cut to its newest {@code limit} lines by the database: the newest {@code limit} of all
     * of them are always among those, so nothing that is shown is missing a newer movement.
     */
    public static Page page(List<Move> everything, long stockNow, int limit) {
        int keep = Math.max(1, limit);
        List<Move> counted = withBalance(newestFirst(everything), stockNow);
        boolean cut = counted.size() > keep;
        List<Move> shown = cut ? new ArrayList<>(counted.subList(0, keep)) : counted;
        return new Page(shown, stockNow, before(shown, stockNow), cut);
    }

    /**
     * The change an ingredient's audit line records: its two values are the stock before and after, written as plain numbers.
     * Null when either is missing or is not a whole number (a line of another shape is left out rather than guessed).
     */
    public static Long auditChange(String oldValue, String newValue) {
        Long before = number(oldValue);
        Long after = number(newValue);
        return before == null || after == null ? null : after - before;
    }

    private static Long number(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (!t.matches("-?[0-9]{1,18}")) return null;
        return Long.parseLong(t);
    }
}
