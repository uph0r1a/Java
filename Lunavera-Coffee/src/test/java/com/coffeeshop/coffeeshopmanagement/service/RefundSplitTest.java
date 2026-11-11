package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** TODO 2.93: a line refund of an order paid with a gift card goes back onto the card first, never as cash, never more than the card paid. */
public class RefundSplitTest {

    private static RefundMath.LineRefund line(int item, int quantity, long lineAmount, long amount) {
        return new RefundMath.LineRefund(item, quantity, lineAmount, amount);
    }

    private static long sum(List<RefundSplit.Row> rows) {
        long total = 0;
        for (RefundSplit.Row r : rows) total += r.amount();
        return total;
    }

    @Test
    public void anOrderWithNoCardRefundsExactlyAsBefore() {
        List<RefundSplit.Row> rows = RefundSplit.rows(List.of(line(1, 2, 20000, 18000), line(2, 1, 5000, 4500)), 0);
        assertEquals(2, rows.size());
        assertFalse(rows.get(0).toCard());
        assertEquals(18000, rows.get(0).amount());
        assertEquals(2, rows.get(0).quantity());
        assertEquals(20000, rows.get(0).lineAmount());
        assertEquals(0, RefundSplit.toCard(rows));
    }

    @Test
    public void aCardThatPaidEverythingTakesTheWholeRefund() {
        List<RefundSplit.Row> rows = RefundSplit.rows(List.of(line(1, 1, 60000, 60000)), 60000);
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).toCard());
        assertEquals(60000, rows.get(0).amount());
        assertEquals("the row keeps the pieces and the line's share", 1, rows.get(0).quantity());
        assertEquals(60000, rows.get(0).lineAmount());
    }

    @Test
    public void aRefundIsNeverMoreOnTheCardThanTheCardPaid() {
        List<RefundSplit.Row> rows = RefundSplit.rows(List.of(line(1, 1, 50000, 50000)), 30000);
        assertEquals(30000, RefundSplit.toCard(rows));
        assertEquals(50000, sum(rows));
        assertEquals("a split line is two rows", 2, rows.size());
        assertTrue(rows.get(0).toCard());
        assertEquals(30000, rows.get(0).amount());
        assertEquals("the card row carries the pieces and the share", 1, rows.get(0).quantity());
        assertEquals(50000, rows.get(0).lineAmount());
        assertFalse(rows.get(1).toCard());
        assertEquals(20000, rows.get(1).amount());
        assertEquals("the other row carries none, so they are not counted twice", 0, rows.get(1).quantity());
        assertEquals(0, rows.get(1).lineAmount());
    }

    @Test
    public void theCardMoneyIsTakenLineByLineInOrder() {
        List<RefundSplit.Row> rows = RefundSplit.rows(List.of(line(1, 1, 20000, 20000), line(2, 1, 30000, 30000), line(3, 1, 10000, 10000)), 35000);
        assertEquals(35000, RefundSplit.toCard(rows));
        assertEquals(60000, sum(rows));
        // line 1 wholly by card, line 2 half and half, line 3 wholly by the chosen method
        assertEquals(4, rows.size());
        assertTrue(rows.get(0).toCard());
        assertEquals(15000, rows.get(1).amount());
        assertTrue(rows.get(1).toCard());
        assertEquals(15000, rows.get(2).amount());
        assertFalse(rows.get(2).toCard());
        assertFalse(rows.get(3).toCard());
        assertEquals(3, rows.get(3).itemId());
    }

    @Test
    public void thePiecesAndTheLineSharesStillAddUpToThePlan() {
        List<RefundMath.LineRefund> plan = List.of(line(1, 3, 45000, 40500), line(2, 2, 20000, 18000));
        List<RefundSplit.Row> rows = RefundSplit.rows(plan, 50000);
        int pieces = 0;
        long shares = 0;
        for (RefundSplit.Row r : rows) {
            pieces += r.quantity();
            shares += r.lineAmount();
        }
        assertEquals(5, pieces);
        assertEquals(65000, shares);
        assertEquals(58500, sum(rows));
    }

    @Test
    public void aFreeLineAndAnEmptyCardAreHandled() {
        List<RefundSplit.Row> rows = RefundSplit.rows(List.of(line(1, 1, 5000, 0)), 20000);
        assertEquals(1, rows.size());
        assertFalse("nothing to put on a card", rows.get(0).toCard());
        assertEquals(0, RefundSplit.toCard(rows));
        assertEquals(0, RefundSplit.toCard(RefundSplit.rows(List.of(), 1000)));
        assertEquals("a negative card figure is no card", 0, RefundSplit.toCard(RefundSplit.rows(List.of(line(1, 1, 5000, 5000)), -1)));
    }

    @Test
    public void theCardPartIsTheSmallerOfTheRefundAndWhatIsLeft() {
        assertEquals(30000, RefundSplit.cardPart(50000, 30000));
        assertEquals(10000, RefundSplit.cardPart(10000, 30000));
        assertEquals(0, RefundSplit.cardPart(10000, 0));
        assertEquals(0, RefundSplit.cardPart(0, 30000));
        assertEquals(0, RefundSplit.cardPart(10000, -5));
    }

    @Test
    public void severalCardsShareAGiveBackInProportionToWhatEachPaid() {
        long[] shares = GiftCardRules.shares(30000, new long[]{40000, 20000});
        assertEquals(20000, shares[0]);
        assertEquals(10000, shares[1]);
        assertArrayEquals(new long[]{40000, 20000}, GiftCardRules.shares(60000, new long[]{40000, 20000}));
        assertArrayEquals("never more than was spent", new long[]{40000, 20000}, GiftCardRules.shares(999999, new long[]{40000, 20000}));
        assertArrayEquals(new long[]{0, 0}, GiftCardRules.shares(0, new long[]{40000, 20000}));
        assertArrayEquals(new long[]{0, 0}, GiftCardRules.shares(1000, new long[]{0, 0}));
        assertArrayEquals(new long[0], GiftCardRules.shares(1000, new long[0]));
    }

    @Test
    public void roundingNeverLosesOrInventsADong() {
        for (long amount = 0; amount <= 100; amount++) {
            long[] spent = {33, 33, 34};
            long[] shares = GiftCardRules.shares(amount, spent);
            long total = 0;
            for (int i = 0; i < shares.length; i++) {
                assertTrue("a card gets no more than it paid", shares[i] >= 0 && shares[i] <= spent[i]);
                total += shares[i];
            }
            assertEquals("amount " + amount, Math.min(amount, 100), total);
        }
    }
}
