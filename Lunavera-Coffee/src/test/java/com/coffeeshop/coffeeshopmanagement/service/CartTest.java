package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** TODO 6.5: the cart rules of the sales screen, tested with no screen and no database. */
public class CartTest {

    private static Product product(int id, String name, String price, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        return p;
    }

    private final List<OrderItem> lines = new ArrayList<>();
    private final Cart cart = new Cart(lines);

    @Test
    public void aNewProductBecomesALineOfOneAtItsPrice() {
        Cart.Result result = cart.add(product(1, "Latte", "45000", 5));
        assertEquals(Cart.Outcome.ADDED, result.outcome());
        assertEquals(1, lines.size());
        assertEquals(1, lines.get(0).getQuantity());
        assertEquals(0, new BigDecimal("45000").compareTo(lines.get(0).getLineTotal()));
        assertEquals("Latte", lines.get(0).getProductName());
        assertEquals(Integer.valueOf(1), lines.get(0).getProductId());
    }

    @Test
    public void theSameProductAgainAddsAPieceToItsLine() {
        Product latte = product(1, "Latte", "45000", 5);
        cart.add(latte);
        Cart.Result second = cart.add(latte);
        assertEquals(Cart.Outcome.INCREASED, second.outcome());
        assertEquals(1, lines.size());
        assertEquals(2, lines.get(0).getQuantity());
        assertEquals(0, new BigDecimal("90000").compareTo(lines.get(0).getLineTotal()));
    }

    @Test
    public void youCannotAddMoreThanTheStock() {
        Product last = product(2, "Bánh", "20000", 1);
        assertEquals(Cart.Outcome.ADDED, cart.add(last).outcome());
        Cart.Result refused = cart.add(last);
        assertEquals(Cart.Outcome.NOT_ENOUGH_STOCK, refused.outcome());
        assertEquals("the message says how many are left", 1, refused.stock());
        assertEquals("nothing changed", 1, lines.get(0).getQuantity());
    }

    @Test
    public void aProductWithNoStockCannotBeAddedAtAll() {
        assertEquals(Cart.Outcome.NOT_ENOUGH_STOCK, cart.add(product(3, "Hết", "10000", 0)).outcome());
        assertTrue(lines.isEmpty());
    }

    @Test
    public void settingAQuantityUpdatesTheLineTotalAndZeroRemovesTheLine() {
        cart.add(product(1, "Latte", "45000", 9));
        OrderItem line = lines.get(0);
        cart.setQuantity(line, 3);
        assertEquals(3, line.getQuantity());
        assertEquals(0, new BigDecimal("135000").compareTo(line.getLineTotal()));
        cart.setQuantity(line, 0);
        assertTrue(lines.isEmpty());
    }

    @Test
    public void aNegativeQuantityAlsoRemovesTheLine() {
        cart.add(product(1, "Latte", "45000", 9));
        cart.setQuantity(lines.get(0), -2);
        assertTrue(lines.isEmpty());
    }

    @Test
    public void exceedsStockMeansMoreThanTheStockAndAnUnknownProductHasNoLimit() {
        Product p = product(1, "Latte", "45000", 4);
        assertFalse(Cart.exceedsStock(p, 4));
        assertTrue(Cart.exceedsStock(p, 5));
        assertFalse(Cart.exceedsStock(null, 1000));
    }

    @Test
    public void subtotalAndTotalQuantityCoverEveryLine() {
        assertEquals(0, BigDecimal.ZERO.compareTo(cart.subtotal()));
        assertEquals(0, cart.totalQuantity());
        Product latte = product(1, "Latte", "45000", 9);
        cart.add(latte);
        cart.add(latte);
        cart.add(product(2, "Bánh", "20000", 9));
        assertEquals(0, new BigDecimal("110000").compareTo(cart.subtotal()));
        assertEquals(3, cart.totalQuantity());
    }

    @Test
    public void aLineWithoutAProductIdIsNeverMatched() {
        OrderItem manual = new OrderItem();
        manual.setProductName("Món nhập tay");
        manual.setQuantity(1);
        manual.setUnitPrice(new BigDecimal("5000"));
        manual.setLineTotal(new BigDecimal("5000"));
        lines.add(manual);
        assertTrue(cart.lineFor(1).isEmpty());
        assertEquals(Cart.Outcome.ADDED, cart.add(product(1, "Latte", "45000", 5)).outcome());
        assertEquals(2, lines.size());
    }

    @Test
    public void theCartWorksOnTheListItIsGivenSoTheScreenSeesTheChanges() {
        cart.add(product(1, "Latte", "45000", 5));
        assertEquals("the caller's own list was changed", 1, lines.size());
    }
}
