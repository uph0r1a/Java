package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.service.StockHistory.Kind;
import com.coffeeshop.coffeeshopmanagement.service.StockHistory.Move;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.59: the order and the running balance of the stock history, with no database. */
public class StockHistoryTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 1, 8, 0);

    private static Move move(int minutes, Kind kind, long quantity) {
        return Move.of(T0.plusMinutes(minutes), kind, quantity, null, null, null);
    }

    @Test
    public void theNewestMovementIsFirst() {
        List<Move> sorted = StockHistory.newestFirst(List.of(move(1, Kind.RECEIPT, 10), move(5, Kind.SALE, -2), move(3, Kind.WASTE, -1)));
        assertEquals(Kind.SALE, sorted.get(0).kind());
        assertEquals(Kind.WASTE, sorted.get(1).kind());
        assertEquals(Kind.RECEIPT, sorted.get(2).kind());
    }

    @Test
    public void theBalanceIsCountedBackwardsFromTodaysStock() {
        // 5 before, +10 = 15, -3 = 12, -2 = 10 today
        List<Move> list = StockHistory.withBalance(StockHistory.newestFirst(
                List.of(move(1, Kind.RECEIPT, 10), move(2, Kind.SALE, -3), move(3, Kind.SALE, -2))), 10);
        assertEquals(10, list.get(0).balance());
        assertEquals(12, list.get(1).balance());
        assertEquals(15, list.get(2).balance());
        assertEquals(5, StockHistory.before(list, 10));
    }

    @Test
    public void anEmptyHistoryIsTodaysStockBeforeAndAfter() {
        assertEquals(42, StockHistory.before(List.of(), 42));
        StockHistory.Page page = StockHistory.page(List.of(), 42, 10);
        assertTrue(page.moves().isEmpty());
        assertEquals(42, page.before());
        assertFalse(page.cut());
    }

    @Test
    public void cuttingTheListKeepsEveryShownBalanceRight() {
        List<Move> all = new ArrayList<>();
        for (int i = 1; i <= 10; i++) all.add(move(i, Kind.SALE, -1)); // ten sales of one piece, 20 left today
        StockHistory.Page page = StockHistory.page(all, 20, 3);
        assertEquals(3, page.moves().size());
        assertTrue(page.cut());
        assertEquals(20, page.moves().get(0).balance());
        assertEquals(22, page.moves().get(2).balance());
        assertEquals(23, page.before()); // before the oldest SHOWN line
    }

    @Test
    public void twoMovementsAtTheSameMomentKeepTheirOrder() {
        Move a = move(1, Kind.RECEIPT, 5);
        Move b = move(1, Kind.SALE, -1);
        assertEquals(List.of(a, b), StockHistory.newestFirst(List.of(a, b)));
    }

    @Test
    public void anAuditLineIsAChangeOnlyWhenBothNumbersAreWholeNumbers() {
        assertEquals(Long.valueOf(500), StockHistory.auditChange("1000", "1500"));
        assertEquals(Long.valueOf(-200), StockHistory.auditChange(" 300", "100 "));
        assertNull(StockHistory.auditChange(null, "5"));
        assertNull(StockHistory.auditChange("abc", "5"));
        assertNull(StockHistory.auditChange("1.5", "5"));
    }
}
