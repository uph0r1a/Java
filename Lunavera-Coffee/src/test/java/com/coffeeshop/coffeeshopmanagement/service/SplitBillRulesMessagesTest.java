package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/**
 * TODO 6.78, mutation sweep 2026-11-16: the first PIT run found that the tests of {@link SplitBillRules} only checked "there is a
 * problem", never WHICH one, and never the exact edges (a line taken whole, a line left whole, every field of a split-off copy).
 */
public class SplitBillRulesMessagesTest {

    private static OrderItem line(int productId, String name, int quantity, String unitPrice) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setProductName(name);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setLineTotal(new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private static Map<Integer, Integer> take(int... indexAndPieces) {
        Map<Integer, Integer> take = new HashMap<>();
        for (int i = 0; i < indexAndPieces.length; i += 2) take.put(indexAndPieces[i], indexAndPieces[i + 1]);
        return take;
    }

    @Test
    public void everyKindOfProblemHasItsOwnSentence() {
        String nolines = I18n.t("split.val.nolines");
        String pieces = I18n.t("split.val.pieces");
        String none = I18n.t("split.val.none");
        String all = I18n.t("split.val.all");
        assertNotEquals(nolines, pieces);
        assertNotEquals(pieces, none);
        assertNotEquals(none, all);
        assertNotEquals(all, nolines);
        for (String sentence : new String[]{nolines, pieces, none, all}) {
            assertNotEquals("an empty sentence tells the cashier nothing", "", sentence);
        }
    }

    @Test
    public void noLinesIsNotTheSameProblemAsNothingChosen() {
        assertEquals(I18n.t("split.val.nolines"), SplitBillRules.problem(null, take()));
        assertEquals(I18n.t("split.val.nolines"), SplitBillRules.problem(List.of(), take(0, 1)));
    }

    @Test
    public void aPieceCountThatDoesNotExistIsRefusedWithThePiecesSentence() {
        List<OrderItem> lines = List.of(line(1, "A", 2, "10000"), line(2, "B", 1, "20000"));
        String pieces = I18n.t("split.val.pieces");
        assertEquals("a line that is not there", pieces, SplitBillRules.problem(lines, take(2, 1)));
        assertEquals("a negative line", pieces, SplitBillRules.problem(lines, take(-1, 1)));
        assertEquals("a negative number of pieces", pieces, SplitBillRules.problem(lines, take(0, -1)));
        assertEquals("one piece more than the line has", pieces, SplitBillRules.problem(lines, take(0, 3)));
    }

    @Test
    public void takingExactlyTheWholeLineIsAllowedWhileSomethingElseStays() {
        List<OrderItem> lines = List.of(line(1, "A", 2, "10000"), line(2, "B", 1, "20000"));
        assertNull("the whole of the first line, the second stays", SplitBillRules.problem(lines, take(0, 2)));
        assertNull("one piece of the first line", SplitBillRules.problem(lines, take(0, 1)));
        assertNull("the whole of the second line", SplitBillRules.problem(lines, take(1, 1)));
    }

    @Test
    public void nothingChosenIsTheNoneSentenceEvenWithZeroPiecesOrANullCount() {
        List<OrderItem> lines = List.of(line(1, "A", 2, "10000"));
        String none = I18n.t("split.val.none");
        assertEquals(none, SplitBillRules.problem(lines, take()));
        assertEquals(none, SplitBillRules.problem(lines, take(0, 0)));
        Map<Integer, Integer> nullCount = new HashMap<>();
        nullCount.put(0, null);
        assertEquals(none, SplitBillRules.problem(lines, nullCount));
    }

    @Test
    public void takingEverythingIsTheAllSentenceAndOnePieceLessIsFine() {
        List<OrderItem> lines = List.of(line(1, "A", 2, "10000"), line(2, "B", 1, "20000"));
        assertEquals(I18n.t("split.val.all"), SplitBillRules.problem(lines, take(0, 2, 1, 1)));
        assertNull(SplitBillRules.problem(lines, take(0, 1, 1, 1)));
    }

    @Test
    public void aLineTakenWholeLeavesNoEmptyCopyBehindAndALineNotTakenIsNotInThePart() {
        List<OrderItem> lines = List.of(line(1, "A", 2, "10000"), line(2, "B", 3, "20000"));
        SplitBillRules.Split split = SplitBillRules.split(lines, take(0, 2, 1, 1));
        assertEquals(2, split.part().size());
        assertEquals(2, split.part().get(0).getQuantity());
        assertEquals(1, split.part().get(1).getQuantity());
        assertEquals("only what is left of the second line stays", 1, split.remainder().size());
        assertEquals(2, split.remainder().get(0).getQuantity());
        assertEquals("B", split.remainder().get(0).getProductName());

        SplitBillRules.Split other = SplitBillRules.split(lines, take(1, 1));
        assertEquals("the first line is not in the part, not even with 0 pieces", 1, other.part().size());
        assertEquals("B", other.part().get(0).getProductName());
        assertEquals("the first line stays whole, the second keeps two", 2, other.remainder().size());
        assertEquals(2, other.remainder().get(0).getQuantity());
        assertEquals(2, other.remainder().get(1).getQuantity());
    }

    @Test
    public void aSplitOffCopyKeepsEveryFieldOfItsLine() {
        OrderItem original = line(7, "Cà phê sữa", 4, "25000");
        original.setNote("ít đá");
        original.setSizeName("L");
        original.setSizePercent(120);
        original.setExtras("thêm trân châu");
        original.setOptionCost(new BigDecimal("5000"));

        OrderItem copy = SplitBillRules.copy(original, 3);
        assertEquals(Integer.valueOf(7), copy.getProductId());
        assertEquals("Cà phê sữa", copy.getProductName());
        assertEquals(3, copy.getQuantity());
        assertEquals(new BigDecimal("25000"), copy.getUnitPrice());
        assertEquals(new BigDecimal("75000"), copy.getLineTotal());
        assertEquals("ít đá", copy.getNote());
        assertEquals("L", copy.getSizeName());
        assertEquals(Integer.valueOf(120), copy.getSizePercent());
        assertEquals("thêm trân châu", copy.getExtras());
        assertEquals(new BigDecimal("5000"), copy.getOptionCost());
    }
}
