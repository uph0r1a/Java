package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.service.Cart.QuantityMove;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** TODO 6.5: the decision that was the middle of {@code OrderController.changeQuantity}, for "+", "-" and a typed number. */
public class CartQuantityMoveTest {

    @Test
    public void theSameNumberChangesNothingAndReadsNoStock() {
        AtomicInteger asked = new AtomicInteger();
        assertEquals(QuantityMove.UNCHANGED, Cart.decideQuantity(3, 3, () -> { asked.incrementAndGet(); return true; }));
        assertEquals(0, asked.get());
    }

    @Test
    public void goingUpIsStoppedByTheLineCeilingBeforeTheStockIsRead() {
        AtomicInteger asked = new AtomicInteger();
        assertEquals(QuantityMove.LINE_LIMIT, Cart.decideQuantity(1, Limits.MAX_LINE_QUANTITY + 1, () -> { asked.incrementAndGet(); return false; }));
        assertEquals("the stock is not read when the ceiling already says no", 0, asked.get());
        assertEquals(QuantityMove.SET, Cart.decideQuantity(1, Limits.MAX_LINE_QUANTITY, () -> false));
    }

    @Test
    public void goingUpIsStoppedWhenTheStockCannotCoverIt() {
        assertEquals(QuantityMove.NOT_ENOUGH_STOCK, Cart.decideQuantity(2, 3, () -> true));
        assertEquals(QuantityMove.SET, Cart.decideQuantity(2, 3, () -> false));
    }

    @Test
    public void goingDownDoesNotReadTheStockAndOnlyTheLastCupAsksFirst() {
        AtomicInteger asked = new AtomicInteger();
        java.util.function.BooleanSupplier stock = () -> { asked.incrementAndGet(); return true; };
        assertEquals(QuantityMove.SET, Cart.decideQuantity(5, 4, stock));
        assertEquals(QuantityMove.SET, Cart.decideQuantity(5, 1, stock));
        assertEquals(QuantityMove.ASK_REMOVE, Cart.decideQuantity(1, 0, stock));
        assertEquals(QuantityMove.ASK_REMOVE, Cart.decideQuantity(5, 0, stock));
        assertEquals("a typed negative number is a removal too", QuantityMove.ASK_REMOVE, Cart.decideQuantity(2, -3, stock));
        assertEquals(0, asked.get());
    }

    @Test
    public void everyWayOfChangingAQuantityEndsInExactlyOneOfTheFiveMoves() {
        RandomCases.run("Cart.decideQuantity", 4000, random -> {
            int current = RandomCases.between(random, 0, Limits.MAX_LINE_QUANTITY + 3);
            int wanted = RandomCases.between(random, -5, Limits.MAX_LINE_QUANTITY + 5);
            boolean over = random.nextBoolean();
            QuantityMove move = Cart.decideQuantity(current, wanted, () -> over);
            if (wanted == current) assertEquals(QuantityMove.UNCHANGED, move);
            else if (wanted > current && wanted > Limits.MAX_LINE_QUANTITY) assertEquals(QuantityMove.LINE_LIMIT, move);
            else if (wanted > current && over) assertEquals(QuantityMove.NOT_ENOUGH_STOCK, move);
            else if (wanted < current && wanted <= 0) assertEquals(QuantityMove.ASK_REMOVE, move);
            else assertEquals(QuantityMove.SET, move);
            if (wanted < current) assertTrue("going down is never refused for stock or ceiling", move == QuantityMove.SET || move == QuantityMove.ASK_REMOVE);
        });
    }
}
