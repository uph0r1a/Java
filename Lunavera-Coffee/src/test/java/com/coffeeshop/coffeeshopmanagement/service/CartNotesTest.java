package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.9: notes on cart lines: a noted line is its own line, and the stock counts every line of a product. */
public class CartNotesTest {

    private static Product product(int id, String name, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal("45000"));
        p.setStock(stock);
        return p;
    }

    private final List<OrderItem> lines = new ArrayList<>();
    private final Cart cart = new Cart(lines);
    private final Product latte = product(1, "Latte", 10);

    @Test
    public void aProductTappedAgainDoesNotJoinAnAlreadyNotedLine() {
        cart.add(latte);
        cart.setNote(lines.get(0), "less sugar");
        Cart.Result result = cart.add(latte);
        assertEquals(Cart.Outcome.ADDED, result.outcome());
        assertEquals(2, lines.size());
        assertEquals("less sugar", lines.get(0).getNote());
        assertNull(lines.get(1).getNote());
    }

    @Test
    public void aPlainLineStillGrowsWhenANotedOneExists() {
        cart.add(latte);
        cart.add(latte);
        cart.setNote(lines.get(0), "no ice");
        cart.add(latte); // a new plain line
        Cart.Result again = cart.add(latte);
        assertEquals(Cart.Outcome.INCREASED, again.outcome());
        assertEquals(2, lines.size());
        assertEquals(2, lines.get(1).getQuantity());
    }

    @Test
    public void theNoteIsCleanedAndAnEmptyOneRemovesIt() {
        cart.add(latte);
        cart.setNote(lines.get(0), "  less \n sugar ");
        assertEquals("less sugar", lines.get(0).getNote());
        cart.setNote(lines.get(0), "   ");
        assertNull(lines.get(0).getNote());
    }

    @Test
    public void twoLinesWithTheSameNoteAreJoined() {
        cart.add(latte);
        cart.setNote(lines.get(0), "no ice");
        cart.add(latte);
        assertEquals(2, lines.size());
        Cart.NoteOutcome outcome = cart.setNote(lines.get(1), "No ice".toLowerCase());
        assertEquals(Cart.NoteOutcome.MERGED, outcome);
        assertEquals(1, lines.size());
        assertEquals(2, lines.get(0).getQuantity());
        assertEquals(0, new BigDecimal("90000").compareTo(lines.get(0).getLineTotal()));
    }

    @Test
    public void removingTheNoteJoinsThePlainLine() {
        cart.add(latte);
        cart.add(latte);
        cart.setNote(lines.get(0), "no ice");
        cart.add(latte); // plain line of 1 (the first line holds the noted one)
        assertEquals(2, lines.size());
        assertEquals(Cart.NoteOutcome.MERGED, cart.setNote(lines.get(0), null));
        assertEquals(1, lines.size());
        assertEquals(3, lines.get(0).getQuantity());
    }

    @Test
    public void joiningIsSkippedWhenItWouldPassTheLineCeiling() {
        Product plenty = product(2, "Water", 5000);
        cart.add(plenty);
        OrderItem first = lines.get(0);
        cart.setQuantity(first, Limits.MAX_LINE_QUANTITY);
        cart.setNote(first, "cold");
        cart.add(plenty);
        OrderItem second = lines.get(1);
        cart.setQuantity(second, 2);
        assertEquals(Cart.NoteOutcome.SET, cart.setNote(second, "cold"));
        assertEquals(2, lines.size());
    }

    @Test
    public void stockCountsEveryLineOfTheProduct() {
        Product two = product(3, "Tea", 2);
        cart.add(two);
        cart.setNote(lines.get(0), "less sugar");
        assertEquals(Cart.Outcome.ADDED, cart.add(two).outcome());   // 1 noted + 1 plain = 2
        assertEquals(Cart.Outcome.NOT_ENOUGH_STOCK, cart.add(two).outcome());
        assertEquals(2, cart.quantityOf(3));
        OrderItem plain = lines.get(1);
        assertTrue(cart.exceedsStock(two, plain, 2));   // 1 on the noted line + 2 here
        assertFalse(cart.exceedsStock(two, plain, 1));
    }
}
