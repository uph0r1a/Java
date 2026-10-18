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
}
