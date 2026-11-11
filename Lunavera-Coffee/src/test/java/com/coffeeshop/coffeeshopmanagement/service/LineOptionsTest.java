package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.LineChoice;
import com.coffeeshop.coffeeshopmanagement.model.OptionKind;
import com.coffeeshop.coffeeshopmanagement.model.OrderItem;
import com.coffeeshop.coffeeshopmanagement.model.ProductOption;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** TODO 4.47: the size and extras of a line: written into the two text columns, read back, compared, shown and added up. */
public class LineOptionsTest {

    private static ProductOption size(String name, long price, long cost, int percent) {
        return new ProductOption(0, 1, OptionKind.SIZE, name, BigDecimal.valueOf(price), BigDecimal.valueOf(cost), percent, false, true);
    }

    private static ProductOption extra(String name, long price, long cost) {
        return new ProductOption(0, 1, OptionKind.EXTRA, name, BigDecimal.valueOf(price), BigDecimal.valueOf(cost), 100, false, true);
    }

    private static OrderItem lineWith(LineChoice choice) {
        OrderItem item = new OrderItem();
        LineOptions.apply(item, choice);
        return item;
    }

    @Test
    public void aChoiceAddsUpPriceAndCostAndKeepsNamesAsTheyWere() {
        LineChoice choice = LineOptions.choice(size("L", 10000, 3000, 130), List.of(extra("Extra shot", 10000, 4000), extra("Cream", 5000, 1000)));
        assertEquals("L", choice.sizeName());
        assertEquals(Integer.valueOf(130), choice.sizePercent());
        assertEquals(0, new BigDecimal("25000").compareTo(choice.priceDelta()));
        assertEquals(0, new BigDecimal("8000").compareTo(choice.costDelta()));
        assertEquals(2, choice.extras().size());
        assertFalse(choice.isNone());
        assertTrue(LineChoice.NONE.isNone());
    }

    @Test
    public void extrasAreWrittenAsNameAndPriceAndReadBackInOrder() {
        LineChoice choice = LineOptions.choice(null, List.of(extra("Extra shot", 10000, 0), extra("Whipped cream", 5000, 0)));
        String text = LineOptions.encodeExtras(choice.extras());
        assertEquals("Extra shot=10000;Whipped cream=5000", text);
        List<LineChoice.Extra> back = LineOptions.decodeExtras(text);
        assertEquals(2, back.size());
        assertEquals("Extra shot", back.get(0).name());
        assertEquals(0, new BigDecimal("5000").compareTo(back.get(1).price()));
        assertNull("no extras is stored as nothing, never as an empty text", LineOptions.encodeExtras(List.of()));
    }

    @Test
    public void aDamagedColumnTextLosesOnlyTheBrokenPart() {
        List<LineChoice.Extra> read = LineOptions.decodeExtras("Extra shot=10000;;=5;Cream=abc;Syrup=2000");
        assertEquals(2, read.size());
        assertEquals("Extra shot", read.get(0).name());
        assertEquals("Syrup", read.get(1).name());
        assertTrue(LineOptions.decodeExtras(null).isEmpty());
        assertTrue(LineOptions.decodeExtras("   ").isEmpty());
    }

    @Test
    public void applyPutsTheChoiceOnTheLineAndAPlainChoiceLeavesItPlain() {
        OrderItem line = lineWith(LineOptions.choice(size("M", 5000, 0, 100), List.of(extra("Extra shot", 10000, 4000))));
        assertEquals("M", line.getSizeName());
        assertEquals(Integer.valueOf(100), line.getSizePercent());
        assertEquals("Extra shot=10000", line.getExtras());
        assertEquals(0, new BigDecimal("4000").compareTo(line.getOptionCost()));
        OrderItem plain = lineWith(LineChoice.NONE);
        assertNull(plain.getSizeName());
        assertNull(plain.getSizePercent());
        assertNull(plain.getExtras());
        assertNull("no cost to record is null, not zero", plain.getOptionCost());
    }

    @Test
    public void twoLinesAreTheSameOnlyWithTheSameSizeAndExtras() {
        OrderItem l1 = lineWith(LineOptions.choice(size("L", 0, 0, 100), List.of(extra("Extra shot", 10000, 0))));
        OrderItem l2 = lineWith(LineOptions.choice(size("L", 0, 0, 100), List.of(extra("Extra shot", 10000, 0))));
        OrderItem small = lineWith(LineOptions.choice(size("S", 0, 0, 100), List.of(extra("Extra shot", 10000, 0))));
        OrderItem noExtra = lineWith(LineOptions.choice(size("L", 0, 0, 100), List.of()));
        assertTrue(LineOptions.same(l1, l2));
        assertFalse(LineOptions.same(l1, small));
        assertFalse(LineOptions.same(l1, noExtra));
        assertTrue(LineOptions.same(lineWith(LineChoice.NONE), new OrderItem()));
        assertNull(LineOptions.key(new OrderItem()));
        assertEquals(LineOptions.key(l1), LineOptions.key(LineOptions.choice(size("L", 0, 0, 100), List.of(extra("Extra shot", 10000, 0)))));
    }

    @Test
    public void aLineIsDescribedForTheCartAndTheReceipt() {
        OrderItem line = lineWith(LineOptions.choice(size("L", 0, 0, 100), List.of(extra("Extra shot", 1, 0), extra("Cream", 1, 0))));
        assertEquals("L, +Extra shot, +Cream", LineOptions.describe(line));
        assertNull(LineOptions.describe(new OrderItem()));
        line.setNote("less sugar");
        assertEquals("Latte (L, +Extra shot, +Cream, less sugar)", LineOptions.withOptions("Latte", line));
        assertEquals("Latte (L, +Extra shot, +Cream)", LineOptions.nameWithOptions("Latte", "L", "Extra shot=1;Cream=1"));
        assertEquals("Latte", LineOptions.nameWithOptions("Latte", null, null));
        OrderItem onlyNote = new OrderItem();
        onlyNote.setNote("no ice");
        assertEquals("Latte (no ice)", LineOptions.withOptions("Latte", onlyNote));
    }

    @Test
    public void aNameCannotCarryTheSeparatorsOrControlCharacters() {
        assertEquals("Extra shot", LineOptions.cleanName("  Extra \n  shot  "));
        assertEquals("Extrashot", LineOptions.cleanName("Extra;sh=ot".replace("sh=ot", "shot")));
        assertEquals("ab", LineOptions.cleanName("a;=b"));
        assertEquals("", LineOptions.cleanName(null));
        assertTrue(LineOptions.hasReservedCharacter("a;b"));
        assertTrue(LineOptions.hasReservedCharacter("a=b"));
        assertFalse(LineOptions.hasReservedCharacter("Extra shot"));
    }

    @Test
    public void theReportAddsUpEachExtraByNameOverAllLines() {
        Map<String, Integer> lines = new LinkedHashMap<>();
        lines.put("Extra shot=10000", 1);                 // 1 piece left after a refund
        lines.put("Extra shot=10000;Cream=5000", 3);
        List<LineOptions.ExtraTally> tally = LineOptions.tallyExtras(lines);
        assertEquals(2, tally.size());
        assertEquals("Extra shot", tally.get(0).name());
        assertEquals(4, tally.get(0).pieces());
        assertEquals(40000, tally.get(0).revenue());
        assertEquals("Cream", tally.get(1).name());
        assertEquals(3, tally.get(1).pieces());
        assertEquals(15000, tally.get(1).revenue());
    }

    @Test
    public void anExtraRefundedDownToNothingIsLeftOut() {
        Map<String, Integer> lines = new LinkedHashMap<>();
        lines.put("Extra shot=10000", 2);
        lines.put("Extra shot=10000;Cream=5000", 0);
        lines.put("Syrup=2000", 0);
        List<LineOptions.ExtraTally> tally = LineOptions.tallyExtras(lines);
        assertEquals(1, tally.size());
        assertEquals(20000, tally.get(0).revenue());
    }
}
