package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.Product;
import com.coffeeshop.coffeeshopmanagement.service.StockHolds.Shortage;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 3.1: what an autosave may keep when other open orders hold stock, and the sentences the cashier reads. No screen, no database;
 * the SQL that works out the free stock is in {@code StockHoldsDbTest}.
 */
public class StockHoldsTest {

    private static OrderItem line(Integer productId, String name, int quantity) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setProductName(name);
        item.setQuantity(quantity);
        return item;
    }

    /** A product as the till reads it for an order: {@code free} pieces left for this order, {@code held} in other open orders. */
    private static Product product(int id, String name, int free, int held) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setStock(free);
        product.setHeldElsewhere(held);
        return product;
    }

    private static Map<Integer, Product> available(Product... products) {
        Map<Integer, Product> map = new HashMap<>();
        for (Product p : products) map.put(p.getId(), p);
        return map;
    }

    @Test
    public void aLineThatGrowsPastWhatIsFreeIsRefusedWithTheFigures() {
        List<Shortage> result = StockHolds.shortages(List.of(line(1, "Latte", 1)), List.of(line(1, "Latte", 3)),
                available(product(1, "Latte", 2, 4)));
        assertEquals(1, result.size());
        Shortage s = result.get(0);
        assertEquals(1, s.productId());
        assertEquals("Latte", s.name());
        assertEquals(3, s.wanted());
        assertEquals(2, s.available());
        assertEquals(4, s.held());
    }

    @Test
    public void takingTheLastFreePieceIsFine() {
        assertTrue(StockHolds.shortages(List.of(), List.of(line(1, "Latte", 2)), available(product(1, "Latte", 2, 1))).isEmpty());
    }

    @Test
    public void aBrandNewLineWhenNothingIsFreeIsRefused() {
        List<Shortage> result = StockHolds.shortages(List.of(), List.of(line(1, "Latte", 1)), available(product(1, "Latte", 0, 3)));
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).available());
    }

    @Test
    public void takingPiecesOutIsNeverBlockedEvenWhenTheOrderIsAlreadyOver() {
        // a stock count lowered the shelf after the order was saved: 5 saved, 1 free; going down to 3 or to 0 must work
        Map<Integer, Product> free = available(product(1, "Latte", 1, 0));
        assertTrue(StockHolds.shortages(List.of(line(1, "Latte", 5)), List.of(line(1, "Latte", 3)), free).isEmpty());
        assertTrue(StockHolds.shortages(List.of(line(1, "Latte", 5)), List.of(), free).isEmpty());
        assertTrue("the same quantity is not a growth", StockHolds.shortages(List.of(line(1, "Latte", 5)), List.of(line(1, "Latte", 5)), free).isEmpty());
    }

    @Test
    public void aChangeToAnotherProductIsNotBlockedByAnOverHeldOne() {
        Map<Integer, Product> free = available(product(1, "Latte", 0, 3), product(2, "Tea", 5, 0));
        assertTrue(StockHolds.shortages(List.of(line(1, "Latte", 1)), List.of(line(1, "Latte", 1), line(2, "Tea", 2)), free).isEmpty());
    }

    @Test
    public void theLinesOfOneProductAreAddedUp() {
        // two lines of the same drink with different notes: 2 + 2 = 4 against 3 free
        List<Shortage> result = StockHolds.shortages(List.of(line(1, "Latte", 2)),
                List.of(line(1, "Latte", 2), line(1, "Latte", 2)), available(product(1, "Latte", 3, 0)));
        assertEquals(1, result.size());
        assertEquals(4, result.get(0).wanted());
    }

    @Test
    public void severalShortagesComeInTheOrderOfTheLines() {
        List<Shortage> result = StockHolds.shortages(List.of(),
                List.of(line(2, "Tea", 9), line(1, "Latte", 9)), available(product(1, "Latte", 1, 0), product(2, "Tea", 1, 0)));
        assertEquals(List.of(2, 1), result.stream().map(Shortage::productId).toList());
    }

    @Test
    public void aProductThatIsNotInTheAnswerIsLeftToThePaymentCheck() {
        assertTrue(StockHolds.shortages(List.of(), List.of(line(7, "Gone", 1)), available()).isEmpty());
    }

    @Test
    public void aLineWithNoProductIsNeverAShortage() {
        assertTrue(StockHolds.shortages(List.of(), List.of(line(null, "Custom", 99)), available()).isEmpty());
        assertEquals(List.of(), StockHolds.productIds(List.of(line(null, "Custom", 1))));
    }

    @Test
    public void theProductIdsAreEachOnceInTheOrderTheyAppear() {
        assertEquals(List.of(3, 1), StockHolds.productIds(List.of(line(3, "a", 1), line(1, "b", 1), line(3, "a", 2))));
    }

    @Test
    public void theNoticeNamesEachProductWithTheFigures() {
        String text = StockHolds.message(List.of(new Shortage(1, "Latte", 3, 2, 4), new Shortage(2, "Tea", 5, 0, 1)));
        assertTrue(text.contains("Latte"));
        assertTrue(text.contains("Tea"));
        assertEquals("the same two lines the product list shows", I18n.t("pos.hold.conflict",
                I18n.t("pos.hold.line", "Latte", 3, 2, 4) + "\n" + I18n.t("pos.hold.line", "Tea", 5, 0, 1)), text);
    }

    @Test
    public void theRefusalSaysWhatOtherOrdersHoldOnlyWhenTheyHoldSomething() {
        assertEquals(I18n.t("pos.stock.short.message", "Latte", 2), StockHolds.refusal(product(1, "Latte", 2, 0)));
        assertEquals(I18n.t("pos.stock.short.held", "Latte", 1, 3), StockHolds.refusal(product(1, "Latte", 1, 3)));
    }

    @Test
    public void aNegativeHoldIsKeptAtZero() {
        Product product = new Product();
        product.setHeldElsewhere(-4);
        assertEquals(0, product.getHeldElsewhere());
    }

    // ------------------------------------------------------------------ the box before the payment (moved out of the screen)

    @Test
    public void thePaymentBoxNoticeHasALinePerShortageAndTheFooter() {
        String text = StockCheck.message(List.of(
                new StockCheck.Shortage(1, "Latte", 4, 3, StockCheck.Kind.SHORT),
                new StockCheck.Shortage(2, "Tea", 1, 0, StockCheck.Kind.UNAVAILABLE)));
        assertEquals(I18n.t("pos.stock.recheck.short", "Latte", 4, 3) + "\n" + I18n.t("pos.stock.recheck.off", "Tea") + "\n\n"
                + I18n.t("pos.stock.recheck.footer"), text);
    }
}
