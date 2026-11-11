package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** TODO 4.10, rule D10: the pure rules of merging tables and splitting a bill. */
public class TableMoveRulesTest {

    private static OrderItem line(Integer product, String name, int quantity, String price, String note) {
        OrderItem item = new OrderItem();
        item.setProductId(product);
        item.setProductName(name);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(price));
        item.setLineTotal(new BigDecimal(price).multiply(BigDecimal.valueOf(quantity)));
        item.setNote(note);
        return item;
    }

    @Test
    public void mergingAddsTheSameProductWithTheSameNoteAndPrice() {
        List<OrderItem> merged = TableMoveRules.mergeLines(
                List.of(line(1, "A", 2, "20000", null), line(2, "B", 1, "30000", "ít đường")),
                List.of(line(1, "A", 3, "20000", null), line(2, "B", 1, "30000", null), line(2, "B", 2, "30000", " ít đường ")));
        assertEquals(3, merged.size());
        assertEquals(5, merged.get(0).getQuantity());
        assertEquals(0, new BigDecimal("100000").compareTo(merged.get(0).getLineTotal()));
        assertEquals("the same note, with spaces around it, is the same line", 3, merged.get(1).getQuantity());
        assertEquals("a line with no note stays its own line", 1, merged.get(2).getQuantity());
        assertNull(merged.get(2).getNote());
    }

    @Test
    public void aDifferentPriceOrNoProductStaysItsOwnLine() {
        List<OrderItem> merged = TableMoveRules.mergeLines(List.of(line(1, "A", 1, "20000", null), line(null, "Custom", 1, "5000", null)),
                List.of(line(1, "A", 1, "25000", null), line(null, "Custom", 1, "5000", null)));
        assertEquals(4, merged.size());
    }

    @Test
    public void mergingDoesNotChangeTheLinesItWasGiven() {
        OrderItem a = line(1, "A", 2, "20000", null);
        TableMoveRules.mergeLines(List.of(a), List.of(line(1, "A", 3, "20000", null)));
        assertEquals(2, a.getQuantity());
    }

    @Test
    public void theKeptOrdersCustomerWins() {
        assertEquals(Integer.valueOf(5), TableMoveRules.customerAfterMerge(5, 7));
        assertEquals(Integer.valueOf(7), TableMoveRules.customerAfterMerge(null, 7));
        assertNull(TableMoveRules.customerAfterMerge(null, null));
    }

    @Test
    public void aSplitTakesSomePiecesAndLeavesTheRest() {
        List<OrderItem> lines = List.of(line(1, "A", 3, "20000", null), line(2, "B", 1, "30000", "x"));
        SplitBillRules.Split split = SplitBillRules.split(lines, Map.of(0, 1, 1, 1));
        assertEquals(2, split.part().size());
        assertEquals(1, split.part().get(0).getQuantity());
        assertEquals(1, split.remainder().size());
        assertEquals(2, split.remainder().get(0).getQuantity());
        assertEquals(0, new BigDecimal("40000").compareTo(SplitBillRules.sum(split.remainder())));
        assertEquals("the two parts add up to the order", 0,
                SplitBillRules.sum(lines).compareTo(SplitBillRules.sum(split.part()).add(SplitBillRules.sum(split.remainder()))));
        assertEquals("the note goes with the piece", "x", split.part().get(1).getNote());
        assertEquals("the cart's own line is untouched", 3, lines.get(0).getQuantity());
    }

    @Test
    public void aBadSplitIsRefused() {
        List<OrderItem> lines = List.of(line(1, "A", 2, "20000", null), line(2, "B", 1, "30000", null));
        assertNotNull("nothing taken", SplitBillRules.problem(lines, Map.of()));
        assertNotNull("zero pieces", SplitBillRules.problem(lines, Map.of(0, 0)));
        assertNotNull("everything taken is an ordinary payment", SplitBillRules.problem(lines, Map.of(0, 2, 1, 1)));
        assertNotNull("more than the line has", SplitBillRules.problem(lines, Map.of(1, 2)));
        assertNotNull("a line that is not there", SplitBillRules.problem(lines, Map.of(5, 1)));
        assertNotNull("negative", SplitBillRules.problem(lines, Map.of(0, -1)));
        assertNotNull("no lines", SplitBillRules.problem(List.of(), Map.of(0, 1)));
        assertNull(SplitBillRules.problem(lines, Map.of(0, 1)));
        try {
            SplitBillRules.split(lines, Map.of());
            fail("refused");
        } catch (IllegalArgumentException expected) {
            // fine
        }
    }
}
