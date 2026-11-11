package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.service.PaymentFlowRules.ScreenAfter;
import com.coffeeshop.coffeeshopmanagement.service.PaymentFlowRules.Start;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 6.5: what {@code OrderController} decided inside its payment code, now testable without JavaFX. No database. */
public class PaymentFlowRulesTest {

    private static OrderItem line(int qty, long unit) {
        OrderItem item = new OrderItem();
        item.setProductId(1);
        item.setProductName("Cà phê");
        item.setQuantity(qty);
        item.setUnitPrice(BigDecimal.valueOf(unit));
        item.setLineTotal(BigDecimal.valueOf(unit * qty));
        return item;
    }

    // ---- may a payment start

    @Test
    public void aPaymentInFlightWinsOverEverythingElseAndSaysNothing() {
        assertEquals(Start.ALREADY_PAYING, PaymentFlowRules.canStart(true, false, true));
        assertEquals(Start.ALREADY_PAYING, PaymentFlowRules.canStart(true, true, false));
    }

    @Test
    public void noOrderIsCheckedBeforeAnEmptyCart() {
        assertEquals(Start.NO_ORDER, PaymentFlowRules.canStart(false, false, true));
        assertEquals(Start.NO_ORDER, PaymentFlowRules.canStart(false, false, false));
        assertEquals(Start.EMPTY_CART, PaymentFlowRules.canStart(false, true, true));
        assertEquals(Start.OK, PaymentFlowRules.canStart(false, true, false));
    }

    // ---- the lines of the receipt

    @Test
    public void aSplitShowsOnlyThePiecesPaid() {
        List<OrderItem> part = List.of(line(1, 30_000));
        List<OrderItem> cart = List.of(line(1, 30_000), line(2, 20_000));
        assertSame(part, PaymentFlowRules.receiptLines(part, false, cart));
        assertSame("even a free-drink flag cannot show the whole cart in a split", part, PaymentFlowRules.receiptLines(part, true, cart));
    }

    @Test
    public void aFreeDrinkShowsTheLinesAtNoChargeAndAnOrdinarySaleTheCart() {
        List<OrderItem> cart = List.of(line(2, 20_000));
        List<OrderItem> free = PaymentFlowRules.receiptLines(null, true, cart);
        assertNotSame(cart, free);
        assertEquals(1, free.size());
        assertEquals(2, free.get(0).getQuantity());
        assertEquals(0, free.get(0).getLineTotal().signum());
        assertEquals("the cart itself is not changed", 40_000, cart.get(0).getLineTotal().intValue());
        assertSame(cart, PaymentFlowRules.receiptLines(null, false, cart));
    }

    // ---- what the screen does afterwards

    @Test
    public void anOrdinarySaleOfTheTableOnScreenClearsTheOrder() {
        assertEquals(ScreenAfter.CLEAR_ORDER, PaymentFlowRules.screenAfter(false, 5, 5, false, false));
    }

    @Test
    public void anOrdinarySaleOfAnotherTableLeavesTheScreenAlone() {
        assertEquals(ScreenAfter.KEEP, PaymentFlowRules.screenAfter(false, 5, 6, false, false));
        assertEquals(ScreenAfter.KEEP, PaymentFlowRules.screenAfter(false, 5, null, true, true));
    }

    @Test
    public void aTakeAwaySaleClearsTheScreenOnlyWhenItStillShowsThatOrder() {
        assertEquals(ScreenAfter.CLEAR_ORDER, PaymentFlowRules.screenAfter(false, null, null, true, true));
        assertEquals("another take-away order is on screen", ScreenAfter.KEEP, PaymentFlowRules.screenAfter(false, null, null, true, false));
        assertEquals("a table is on screen now", ScreenAfter.KEEP, PaymentFlowRules.screenAfter(false, null, 3, false, true));
        assertEquals("the take-away screen is not showing", ScreenAfter.KEEP, PaymentFlowRules.screenAfter(false, null, null, false, true));
    }

    @Test
    public void aSplitShowsTheRestAgainOnlyWhenThatTableIsStillOnScreen() {
        assertEquals(ScreenAfter.RELOAD_REST, PaymentFlowRules.screenAfter(true, 5, 5, false, false));
        assertEquals(ScreenAfter.KEEP, PaymentFlowRules.screenAfter(true, 5, 6, false, false));
        assertEquals(ScreenAfter.KEEP, PaymentFlowRules.screenAfter(true, 5, null, true, true));
    }

    @Test
    public void aSplitNeverClearsTheOrderBecauseTheRestStaysOpen() {
        for (Integer selected : new Integer[]{null, 1, 5}) {
            for (boolean away : new boolean[]{false, true}) {
                for (boolean same : new boolean[]{false, true}) {
                    assertNotEquals(ScreenAfter.CLEAR_ORDER, PaymentFlowRules.screenAfter(true, 5, selected, away, same));
                }
            }
        }
    }
}
