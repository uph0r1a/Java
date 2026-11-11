package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 6.37: what an autosave of an open order has to write, worked out without a database. */
public class OrderItemDiffTest {

    private static OrderItem item(int rowId, Integer product, String name, int quantity, String unitPrice) {
        OrderItem item = new OrderItem();
        item.setId(rowId);
        item.setProductId(product);
        item.setProductName(name);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setLineTotal(new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    @Test
    public void sameLinesMeanNothingToWrite() {
        OrderItemDiff plan = OrderItemDiff.plan(
                List.of(item(1, 7, "Cà phê", 2, "25000"), item(2, 8, "Trà", 1, "30000")),
                List.of(item(0, 7, "Cà phê", 2, "25000"), item(0, 8, "Trà", 1, "30000")));
        assertTrue(plan.toString(), plan.isEmpty());
        assertEquals(2, plan.unchanged);
    }

    @Test
    public void aChangedQuantityIsOneUpdateOfThatRow() {
        OrderItemDiff plan = OrderItemDiff.plan(
                List.of(item(1, 7, "Cà phê", 2, "25000"), item(2, 8, "Trà", 1, "30000")),
                List.of(item(0, 7, "Cà phê", 2, "25000"), item(0, 8, "Trà", 4, "30000")));
        assertEquals(1, plan.updates.size());
        assertEquals(2, plan.updates.get(0).rowId);
        assertEquals(4, plan.updates.get(0).item.getQuantity());
        assertTrue(plan.inserts.isEmpty());
        assertTrue(plan.deletes.isEmpty());
        assertEquals(1, plan.unchanged);
    }

    @Test
    public void aNewProductIsInsertedAndARemovedOneDeleted() {
        OrderItemDiff plan = OrderItemDiff.plan(
                List.of(item(1, 7, "Cà phê", 2, "25000"), item(2, 8, "Trà", 1, "30000")),
                List.of(item(0, 8, "Trà", 1, "30000"), item(0, 9, "Bánh", 1, "20000")));
        assertEquals(1, plan.inserts.size());
        assertEquals(Integer.valueOf(9), plan.inserts.get(0).getProductId());
        assertEquals(List.of(1), plan.deletes);
        assertTrue(plan.updates.isEmpty());
    }

    @Test
    public void theSameProductOnTwoLinesIsMatchedInOrder() {
        OrderItemDiff plan = OrderItemDiff.plan(
                List.of(item(1, 7, "Cà phê", 1, "25000"), item(2, 7, "Cà phê", 2, "25000")),
                List.of(item(0, 7, "Cà phê", 1, "25000"), item(0, 7, "Cà phê", 3, "25000")));
        assertEquals(1, plan.updates.size());
        assertEquals(2, plan.updates.get(0).rowId);
        assertEquals(1, plan.unchanged);
    }

    @Test
    public void aNewPriceIsANewLineNotAnEditOfTheOldOne() {
        // the order keeps the price it was sold at (OrderItem snapshot), so a different price is a different line
        OrderItemDiff plan = OrderItemDiff.plan(
                List.of(item(1, 7, "Cà phê", 1, "25000")),
                List.of(item(0, 7, "Cà phê", 1, "27000")));
        assertEquals(1, plan.inserts.size());
        assertEquals(List.of(1), plan.deletes);
    }

    @Test
    public void numbersThatDifferOnlyInScaleAreTheSame() {
        OrderItemDiff plan = OrderItemDiff.plan(
                List.of(item(1, 7, "Cà phê", 1, "25000")),
                List.of(item(0, 7, "Cà phê", 1, "25000.00")));
        assertTrue(plan.toString(), plan.isEmpty());
    }

    @Test
    public void aLineWithoutAProductMatchesByNameAndPrice() {
        OrderItemDiff plan = OrderItemDiff.plan(
                List.of(item(1, null, "Món đã xóa", 1, "10000")),
                List.of(item(0, null, "Món đã xóa", 2, "10000")));
        assertEquals(1, plan.updates.size());
        assertTrue(plan.inserts.isEmpty());
    }

    @Test
    public void emptyOnEitherSide() {
        assertEquals(List.of(1, 2), OrderItemDiff.plan(
                List.of(item(1, 7, "a", 1, "1"), item(2, 8, "b", 1, "1")), List.of()).deletes);
        assertEquals(2, OrderItemDiff.plan(
                List.of(), List.of(item(0, 7, "a", 1, "1"), item(0, 8, "b", 1, "1"))).inserts.size());
        assertTrue(OrderItemDiff.plan(List.of(), List.of()).isEmpty());
    }

    private static OrderItem noted(OrderItem item, String note) {
        item.setNote(note);
        return item;
    }

    // ---- TODO 2.100: a changed note is an update in place, not a delete and an insert at the end

    @Test
    public void aChangedNoteIsAnUpdateOfTheSameRowNotADeleteAndAnInsert() {
        OrderItemDiff plan = OrderItemDiff.plan(
                List.of(noted(item(1, 7, "Cà phê", 1, "25000"), "no ice"), item(2, 8, "Trà", 1, "30000")),
                List.of(noted(item(0, 7, "Cà phê", 1, "25000"), "extra ice"), item(0, 8, "Trà", 1, "30000")));
        assertEquals(plan.toString(), 1, plan.updates.size());
        assertEquals("the row keeps its id, so it keeps its place", 1, plan.updates.get(0).rowId);
        assertEquals("extra ice", plan.updates.get(0).item.getNote());
        assertTrue(plan.updates.get(0).noteChanged);
        assertTrue(plan.inserts.isEmpty());
        assertTrue(plan.deletes.isEmpty());
        assertEquals(1, plan.unchanged);
    }

    @Test
    public void aNoteAddedOrRemovedIsAlsoInPlace() {
        OrderItemDiff added = OrderItemDiff.plan(List.of(item(1, 7, "Cà phê", 1, "25000")), List.of(noted(item(0, 7, "Cà phê", 1, "25000"), "hot")));
        assertEquals(1, added.updates.size());
        assertTrue(added.inserts.isEmpty() && added.deletes.isEmpty());
        OrderItemDiff removed = OrderItemDiff.plan(List.of(noted(item(1, 7, "Cà phê", 1, "25000"), "hot")), List.of(item(0, 7, "Cà phê", 1, "25000")));
        assertEquals(1, removed.updates.size());
        assertNull(removed.updates.get(0).item.getNote());
        assertTrue(removed.inserts.isEmpty() && removed.deletes.isEmpty());
    }

    @Test
    public void aNoteAndAQuantityChangedTogetherAreOneUpdate() {
        OrderItemDiff plan = OrderItemDiff.plan(List.of(noted(item(1, 7, "Cà phê", 1, "25000"), "a")), List.of(noted(item(0, 7, "Cà phê", 3, "25000"), "b")));
        assertEquals(1, plan.updates.size());
        assertEquals(3, plan.updates.get(0).item.getQuantity());
        assertEquals("b", plan.updates.get(0).item.getNote());
        assertTrue(plan.inserts.isEmpty() && plan.deletes.isEmpty());
    }

    @Test
    public void aNoteThatOnlyDiffersInSpacingIsNoChangeAtAll() {
        OrderItemDiff plan = OrderItemDiff.plan(List.of(noted(item(1, 7, "Cà phê", 1, "25000"), "no ice")), List.of(noted(item(0, 7, "Cà phê", 1, "25000"), "  no   ice ")));
        assertTrue(plan.toString(), plan.isEmpty());
        assertEquals(1, plan.unchanged);
    }

    @Test
    public void anotherProductPriceOrSizeIsStillADeleteAndAnInsertEvenWithANewNote() {
        OrderItemDiff product = OrderItemDiff.plan(List.of(noted(item(1, 7, "Cà phê", 1, "25000"), "a")), List.of(noted(item(0, 8, "Trà", 1, "25000"), "b")));
        assertEquals(1, product.inserts.size());
        assertEquals(List.of(1), product.deletes);
        OrderItemDiff price = OrderItemDiff.plan(List.of(noted(item(1, 7, "Cà phê", 1, "25000"), "a")), List.of(noted(item(0, 7, "Cà phê", 1, "27000"), "b")));
        assertEquals(1, price.inserts.size());
        assertEquals(List.of(1), price.deletes);
        OrderItem large = noted(item(0, 7, "Cà phê", 1, "25000"), "b");
        large.setSizeName("L");
        OrderItemDiff size = OrderItemDiff.plan(List.of(noted(item(1, 7, "Cà phê", 1, "25000"), "a")), List.of(large));
        assertEquals("a size is another line: its columns are not updated in place", 1, size.inserts.size());
        assertEquals(List.of(1), size.deletes);
    }

    @Test
    public void anExactMatchKeepsItsRowEvenWhenAnEarlierLineChangesItsNote() {
        // saved: "a" (row 1) and "b" (row 2); wanted: "b" then "c". "b" is row 2 as it was; "c" takes the free row 1 and its note becomes "c"
        OrderItemDiff plan = OrderItemDiff.plan(
                List.of(noted(item(1, 7, "Cà phê", 1, "25000"), "a"), noted(item(2, 7, "Cà phê", 1, "25000"), "b")),
                List.of(noted(item(0, 7, "Cà phê", 1, "25000"), "b"), noted(item(0, 7, "Cà phê", 1, "25000"), "c")));
        assertEquals(plan.toString(), 1, plan.updates.size());
        assertEquals(1, plan.updates.get(0).rowId);
        assertEquals("c", plan.updates.get(0).item.getNote());
        assertEquals("the exact match was left alone", 1, plan.unchanged);
        assertTrue(plan.inserts.isEmpty() && plan.deletes.isEmpty());
    }

    @Test
    public void twoLinesOfOneProductWithNewNotesPairInOrder() {
        OrderItemDiff plan = OrderItemDiff.plan(
                List.of(noted(item(1, 7, "Cà phê", 1, "25000"), "a"), noted(item(2, 7, "Cà phê", 1, "25000"), "b")),
                List.of(noted(item(0, 7, "Cà phê", 1, "25000"), "x"), noted(item(0, 7, "Cà phê", 1, "25000"), "y")));
        assertEquals(2, plan.updates.size());
        assertEquals(1, plan.updates.get(0).rowId);
        assertEquals("x", plan.updates.get(0).item.getNote());
        assertEquals(2, plan.updates.get(1).rowId);
        assertEquals("y", plan.updates.get(1).item.getNote());
        assertTrue(plan.inserts.isEmpty() && plan.deletes.isEmpty());
    }

    @Test
    public void aNewLineBesideAChangedNoteIsInsertedAndTheChangedOneStaysPut() {
        OrderItemDiff plan = OrderItemDiff.plan(
                List.of(noted(item(1, 7, "Cà phê", 1, "25000"), "a")),
                List.of(noted(item(0, 7, "Cà phê", 1, "25000"), "b"), item(0, 9, "Bánh", 1, "20000")));
        assertEquals(1, plan.updates.size());
        assertEquals(1, plan.updates.get(0).rowId);
        assertEquals(1, plan.inserts.size());
        assertTrue(plan.deletes.isEmpty());
    }
}
