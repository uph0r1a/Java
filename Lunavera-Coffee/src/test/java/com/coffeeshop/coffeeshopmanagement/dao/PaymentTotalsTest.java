package com.coffeeshop.coffeeshopmanagement.dao;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 2.25: the sale's figures must add up before anything is written. */
public class PaymentTotalsTest {

    private static OrderItem line(String name, int qty, long price, long lineTotal) {
        OrderItem item = new OrderItem();
        item.setProductName(name);
        item.setQuantity(qty);
        item.setUnitPrice(BigDecimal.valueOf(price));
        item.setLineTotal(BigDecimal.valueOf(lineTotal));
        return item;
    }

    private static BigDecimal d(long v) {
        return BigDecimal.valueOf(v);
    }

    @Test
    public void goodFiguresReturnTheSubtotal() {
        assertEquals(0, d(70_000).compareTo(OrderDAO.checkTotals(d(5_000), d(65_000),
                List.of(line("A", 2, 20_000, 40_000), line("B", 1, 30_000, 30_000)))));
    }

    @Test(expected = DataAccessException.class)
    public void emptyOrderIsRefused() {
        OrderDAO.checkTotals(d(0), d(0), List.of());
    }

    @Test(expected = DataAccessException.class)
    public void negativeDiscountIsRefused() {
        OrderDAO.checkTotals(d(-5_000), d(35_000), List.of(line("A", 1, 30_000, 30_000)));
    }

    @Test(expected = DataAccessException.class)
    public void discountLargerThanTheSubtotalIsRefused() {
        OrderDAO.checkTotals(d(40_000), d(0), List.of(line("A", 1, 30_000, 30_000)));
    }

    @Test(expected = DataAccessException.class)
    public void totalThatDoesNotMatchIsRefused() {
        OrderDAO.checkTotals(d(0), d(1), List.of(line("A", 1, 30_000, 30_000)));
    }

    @Test(expected = DataAccessException.class)
    public void lineTotalThatIsNotPriceTimesQuantityIsRefused() {
        OrderDAO.checkTotals(d(0), d(10_000), List.of(line("A", 2, 30_000, 10_000)));
    }

    @Test(expected = DataAccessException.class)
    public void zeroQuantityIsRefused() {
        OrderDAO.checkTotals(d(0), d(0), List.of(line("A", 0, 30_000, 0)));
    }
}
