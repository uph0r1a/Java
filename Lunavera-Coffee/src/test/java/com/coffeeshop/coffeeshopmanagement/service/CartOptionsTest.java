package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.LineChoice;
import com.coffeeshop.coffeeshopmanagement.model.OptionKind;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.model.ProductOption;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 4.47: a size and extras on cart lines: the price, which lines join, and what the stock counts. */
public class CartOptionsTest {

    private final List<OrderItem> lines = new ArrayList<>();
    private final Cart cart = new Cart(lines);
    private final Product latte = product(1, 5);

    private static Product product(int id, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName("Latte");
        p.setPrice(new BigDecimal("40000"));
        p.setStock(stock);
        return p;
    }

    private static LineChoice large(boolean shot) {
        ProductOption l = new ProductOption(0, 1, OptionKind.SIZE, "L", new BigDecimal("10000"), new BigDecimal("3000"), 130, true, true);
        ProductOption s = new ProductOption(0, 1, OptionKind.EXTRA, "Extra shot", new BigDecimal("10000"), new BigDecimal("4000"), 100, false, true);
        return LineOptions.choice(l, shot ? List.of(s) : List.of());
    }

    private static LineChoice small() {
        return LineOptions.choice(new ProductOption(0, 1, OptionKind.SIZE, "S", new BigDecimal("-5000"), BigDecimal.ZERO, 80, false, true), List.of());
    }

    @Test
    public void theLineIsPricedAtTheProductPlusWhatTheChoiceAdds() {
        cart.add(latte, large(true));
        OrderItem line = lines.get(0);
        assertEquals(0, new BigDecimal("60000").compareTo(line.getUnitPrice()));
        assertEquals(0, new BigDecimal("60000").compareTo(line.getLineTotal()));
        assertEquals("L", line.getSizeName());
        assertEquals(Integer.valueOf(130), line.getSizePercent());
        assertEquals("Extra shot=10000", line.getExtras());
        assertEquals(0, new BigDecimal("7000").compareTo(line.getOptionCost()));
        cart.setQuantity(line, 2);
        assertEquals(0, new BigDecimal("120000").compareTo(line.getLineTotal()));
    }

    @Test
    public void aSmallerSizeIsCheaperAndAPlainTapIsUnchanged() {
        cart.add(latte, small());
        cart.add(latte);
        assertEquals(0, new BigDecimal("35000").compareTo(lines.get(0).getUnitPrice()));
        assertEquals(0, new BigDecimal("40000").compareTo(lines.get(1).getUnitPrice()));
        assertNull(lines.get(1).getSizeName());
    }

    @Test
    public void theSameChoiceTopsUpItsLineAndAnotherChoiceIsAnotherLine() {
        assertEquals(Cart.Outcome.ADDED, cart.add(latte, large(true)).outcome());
        assertEquals(Cart.Outcome.INCREASED, cart.add(latte, large(true)).outcome());
        assertEquals(Cart.Outcome.ADDED, cart.add(latte, large(false)).outcome());
        assertEquals(Cart.Outcome.ADDED, cart.add(latte, small()).outcome());
        assertEquals(Cart.Outcome.ADDED, cart.add(latte).outcome());
        assertEquals(4, lines.size());
        assertEquals(2, lines.get(0).getQuantity());
    }

    @Test
    public void thePlainLineIsNeverToppedUpByASizedTap() {
        cart.add(latte);
        cart.add(latte, small());
        assertEquals(2, lines.size());
        assertTrue(cart.lineFor(1).isPresent());
        assertNull(cart.lineFor(1).get().getSizeName());
    }

    @Test
    public void theStockCountsEveryLineOfTheProduct() {
        cart.add(latte, large(true));
        cart.add(latte, small());
        cart.add(latte);
        cart.add(latte, large(false));
        cart.add(latte, large(false));
        assertEquals(5, cart.quantityOf(1));
        assertEquals(Cart.Outcome.NOT_ENOUGH_STOCK, cart.add(latte, small()).outcome());
        assertEquals(Cart.Outcome.NOT_ENOUGH_STOCK, cart.add(latte, large(true)).outcome());
    }

    @Test
    public void aNoteOnASizedLineJoinsOnlyALineWithTheSameSizeAndNote() {
        cart.add(latte, large(false));
        cart.add(latte, small());
        cart.setNote(lines.get(0), "less sugar");
        // the small line gets the same note: it must NOT join the large one
        Cart.NoteOutcome outcome = cart.setNote(lines.get(1), "less sugar");
        assertEquals(Cart.NoteOutcome.SET, outcome);
        assertEquals(2, lines.size());
    }

    @Test
    public void aNoteJoinsTheSameSizedLineWhenEverythingMatches() {
        cart.add(latte, large(false));
        cart.setNote(lines.get(0), "no ice");
        cart.add(latte, large(false));
        assertEquals(2, lines.size());
        assertEquals(Cart.NoteOutcome.MERGED, cart.setNote(lines.get(1), "no ice"));
        assertEquals(1, lines.size());
        assertEquals(2, lines.get(0).getQuantity());
    }

    @Test
    public void aChoiceCannotTakeThePriceBelowZero() {
        Product cheap = product(2, 5);
        cheap.setPrice(new BigDecimal("3000"));
        cart.add(cheap, small());
        assertEquals(0, BigDecimal.ZERO.compareTo(lines.get(0).getUnitPrice()));
    }

    @Test
    public void splittingALineKeepsItsSizeAndExtras() {
        cart.add(latte, large(true));
        OrderItem copy = SplitBillRules.copy(lines.get(0), 1);
        assertEquals("L", copy.getSizeName());
        assertEquals(Integer.valueOf(130), copy.getSizePercent());
        assertEquals("Extra shot=10000", copy.getExtras());
        assertEquals(0, new BigDecimal("7000").compareTo(copy.getOptionCost()));
    }
}
