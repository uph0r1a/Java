package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.service.RepeatOrder.Left;
import com.coffeeshop.coffeeshopmanagement.service.RepeatOrder.Reason;
import com.coffeeshop.coffeeshopmanagement.service.RepeatOrder.Result;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO 4.56: "the same as last time" puts the last paid order into the cart at today's prices and says what was left out. The cases
 * the item lists: a product that was deleted, one that is hidden, one that is sold out, and a price that changed.
 */
public class RepeatOrderTest {

    private static Product product(int id, String name, String price, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setActive(true);
        return p;
    }

    private static OrderItem last(Integer productId, String name, int quantity, String note, String oldPrice) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setProductName(name);
        item.setQuantity(quantity);
        item.setNote(note);
        item.setUnitPrice(new BigDecimal(oldPrice));
        item.setLineTotal(new BigDecimal(oldPrice).multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private static Cart emptyCart(List<OrderItem> lines) {
        return new Cart(lines);
    }

    @Test
    public void everyLineIsRepeatedWithItsNoteAtTodaysPrice() {
        List<OrderItem> lines = new ArrayList<>();
        Result result = RepeatOrder.apply(emptyCart(lines),
                List.of(last(1, "Latte", 2, "it duong", "40000"), last(2, "Tra", 1, null, "30000")),
                List.of(product(1, "Latte", "45000", 10), product(2, "Tra", "30000", 10)));
        assertEquals(3, result.pieces());
        assertTrue(result.left().isEmpty());
        assertEquals(2, lines.size());
        assertEquals("the note came back", "it duong", lines.get(0).getNote());
        assertEquals("today's price, not last time's", 0, new BigDecimal("45000").compareTo(lines.get(0).getUnitPrice()));
        assertEquals(0, new BigDecimal("90000").compareTo(lines.get(0).getLineTotal()));
    }

    @Test
    public void aPriceThatChangedIsTheNewPrice() {
        List<OrderItem> lines = new ArrayList<>();
        RepeatOrder.apply(emptyCart(lines), List.of(last(1, "Latte", 1, null, "40000")), List.of(product(1, "Latte", "50000", 5)));
        assertEquals(0, new BigDecimal("50000").compareTo(lines.get(0).getUnitPrice()));
    }

    @Test
    public void aDeletedOrHiddenProductIsLeftOutAndNamed() {
        // a deleted product is no longer a product at all; a hidden one is not on the list the till may sell (SellableMenu.filter)
        List<OrderItem> lines = new ArrayList<>();
        Result result = RepeatOrder.apply(emptyCart(lines),
                List.of(last(1, "Latte", 1, null, "40000"), last(99, "Old special", 2, null, "35000"), last(null, "Custom", 1, null, "1000")),
                List.of(product(1, "Latte", "40000", 5)));
        assertEquals(1, result.pieces());
        assertEquals(List.of(new Left("Old special", Reason.NOT_SOLD, 2, 0), new Left("Custom", Reason.NOT_SOLD, 1, 0)), result.left());
        assertEquals(1, lines.size());
    }

    @Test
    public void aSoldOutProductIsLeftOut() {
        List<OrderItem> lines = new ArrayList<>();
        Result result = RepeatOrder.apply(emptyCart(lines), List.of(last(1, "Latte", 2, null, "40000")), List.of(product(1, "Latte", "40000", 0)));
        assertEquals(0, result.pieces());
        assertTrue(result.nothing());
        assertEquals(List.of(new Left("Latte", Reason.SOLD_OUT, 2, 0)), result.left());
        assertTrue(lines.isEmpty());
    }

    @Test
    public void aProductWithLessInStockIsAddedAsFarAsTheStockGoes() {
        List<OrderItem> lines = new ArrayList<>();
        Result result = RepeatOrder.apply(emptyCart(lines), List.of(last(1, "Latte", 4, null, "40000")), List.of(product(1, "Latte", "40000", 3)));
        assertEquals(3, result.pieces());
        assertEquals(List.of(new Left("Latte", Reason.LESS, 4, 3)), result.left());
        assertEquals(3, lines.get(0).getQuantity());
    }

    @Test
    public void whatIsAlreadyInTheCartCountsAgainstTheStock() {
        List<OrderItem> lines = new ArrayList<>();
        Cart cart = emptyCart(lines);
        Product latte = product(1, "Latte", "40000", 3);
        assertEquals(Cart.Outcome.ADDED, cart.add(latte).outcome()); // one already there
        Result result = RepeatOrder.apply(cart, List.of(last(1, "Latte", 3, null, "40000")), List.of(latte));
        assertEquals("only 2 more fit", 2, result.pieces());
        assertEquals(3, cart.quantityOf(1));
        assertEquals("they join the plain line", 1, lines.size());
    }

    @Test
    public void twoLinesOfTheSameDrinkWithDifferentNotesStayTwoLinesAndShareTheStock() {
        List<OrderItem> lines = new ArrayList<>();
        Result result = RepeatOrder.apply(emptyCart(lines),
                List.of(last(1, "Latte", 2, "it da", "40000"), last(1, "Latte", 2, null, "40000")), List.of(product(1, "Latte", "40000", 3)));
        assertEquals(3, result.pieces());
        assertEquals(2, lines.size());
        assertEquals(2, lines.get(0).getQuantity());
        assertEquals(1, lines.get(1).getQuantity());
        assertEquals(List.of(new Left("Latte", Reason.LESS, 2, 1)), result.left());
    }

    @Test
    public void repeatingTwiceDoesNotDuplicateLinesItJoinsThem() {
        List<OrderItem> lines = new ArrayList<>();
        Cart cart = emptyCart(lines);
        List<Product> menu = List.of(product(1, "Latte", "40000", 10));
        RepeatOrder.apply(cart, List.of(last(1, "Latte", 2, "it da", "40000")), menu);
        RepeatOrder.apply(cart, List.of(last(1, "Latte", 2, "it da", "40000")), menu);
        assertEquals(1, lines.size());
        assertEquals(4, lines.get(0).getQuantity());
    }

    @Test
    public void theCeilingPerLineIsKept() {
        List<OrderItem> lines = new ArrayList<>();
        Result result = RepeatOrder.apply(emptyCart(lines),
                List.of(last(1, "Latte", Limits.MAX_LINE_QUANTITY + 5, null, "40000")), List.of(product(1, "Latte", "40000", Limits.MAX_LINE_QUANTITY + 100)));
        assertEquals(Limits.MAX_LINE_QUANTITY, lines.get(0).getQuantity());
        assertEquals(Reason.LESS, result.left().get(0).reason());
    }

    @Test
    public void anEmptyLastOrderChangesNothing() {
        List<OrderItem> lines = new ArrayList<>();
        Result result = RepeatOrder.apply(emptyCart(lines), List.of(), List.of(product(1, "Latte", "40000", 5)));
        assertTrue(result.nothing());
        assertTrue(result.left().isEmpty());
        assertEquals("", RepeatOrder.notice(result));
    }

    @Test
    public void theNoticeHasOneSentencePerLineThatFellShort() {
        Result result = new Result(1, List.of(new Left("Old special", Reason.NOT_SOLD, 2, 0), new Left("Latte", Reason.SOLD_OUT, 1, 0),
                new Left("Tra", Reason.LESS, 4, 3)));
        assertEquals(I18n.t("pos.repeat.left.notsold", "Old special") + "\n" + I18n.t("pos.repeat.left.soldout", "Latte") + "\n"
                + I18n.t("pos.repeat.left.less", "Tra", 3, 4), RepeatOrder.notice(result));
    }

    // ------------------------------------------------------------------ Cart.addMany itself

    @Test
    public void addManyCleansTheNoteLikeTheNoteBoxDoes() {
        List<OrderItem> lines = new ArrayList<>();
        new Cart(lines).addMany(product(1, "Latte", "40000", 5), 1, "  it   duong \n");
        assertEquals("it duong", lines.get(0).getNote());
        List<OrderItem> plain = new ArrayList<>();
        new Cart(plain).addMany(product(1, "Latte", "40000", 5), 1, "   ");
        assertNull("an empty note is no note", plain.get(0).getNote());
    }

    @Test
    public void addManyNeverAddsPastTheStockOrAddsZero() {
        List<OrderItem> lines = new ArrayList<>();
        Cart cart = new Cart(lines);
        assertEquals(0, cart.addMany(product(1, "Latte", "40000", 0), 3, null));
        assertEquals(2, cart.addMany(product(1, "Latte", "40000", 2), 9, null));
        assertEquals(0, cart.addMany(product(1, "Latte", "40000", 2), 1, null));
        assertEquals(1, lines.size());
    }
}
