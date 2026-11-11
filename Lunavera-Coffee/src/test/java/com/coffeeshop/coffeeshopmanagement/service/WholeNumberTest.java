package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

import static org.junit.Assert.*;

/**
 * TODO 2.95: the one reader of whole numbers behind the stock receipt and waste box, every ingredient box and the recipe amount.
 * Before it, {@code 7.5} was stored as 75, so a recipe of 7.5 g took ten times too much from the stock at every sale.
 */
public class WholeNumberTest {

    private static void assertRefused(String typed) {
        assertTrue("must be refused, not read as another number: \"" + typed + "\"", WholeNumber.parse(typed).isEmpty());
    }

    private static void assertRead(long expected, String typed) {
        OptionalLong value = WholeNumber.parse(typed);
        assertTrue("must be read: \"" + typed + "\"", value.isPresent());
        assertEquals(typed, expected, value.getAsLong());
    }

    @Test
    public void aFractionIsRefusedNeverReadAsAnotherNumber() {
        for (String typed : new String[]{"1.5", "0.5", "2,5", "7.5", "12.5", "12.5.1", "5.5", "2.50", "1.2.3", "1,5", "0,5", "7,5"}) {
            assertRefused(typed);
        }
    }

    @Test
    public void aDotIsAThousandsMarkOnlyInsideGroupsOfThreeDigits() {
        assertRead(1000, "1.000");
        assertRead(12_500, "12.500");
        assertRead(12_500_000, "12.500.000");
        assertRead(999_999, "999.999");
        assertRefused("1.00");        // two digits after the dot
        assertRefused("1.0000");      // four
        assertRefused("1000.000");    // the first group is longer than three
        assertRefused("1..000");
        assertRefused(".500");
        assertRefused("500.");
        assertRefused("1.000.00");
    }

    @Test
    public void spacesGroupDigitsTheSameWayAndAnyOtherSpaceIsRefused() {
        assertRead(1500, "1 500");
        assertRead(1500, "1\u00A0500");
        assertRead(1500, "1\u202F500");
        assertRead(1_500_000, "1 500 000");
        assertRefused("1 2");
        assertRefused("1 5000");
        assertRefused("1  500");
        assertRefused("15 00");
    }

    @Test
    public void aCommaIsNeverAccepted() {
        assertRefused("1,000");
        assertRefused("1,000,000");
        assertRefused("2,5");
        assertRefused(",5");
    }

    @Test
    public void plainDigitsAndASignAreRead() {
        assertRead(0, "0");
        assertRead(7, "7");
        assertRead(75, " 75 ");
        assertRead(7, "007");
        assertRead(-3, "-3");
        assertRead(-1000, "-1.000");
        assertRead(999_999_999_999L, "999999999999");
    }

    @Test
    public void emptyTextAndLettersAndTooManyDigitsAreRefused() {
        assertRefused(null);
        assertRefused("");
        assertRefused("   ");
        assertRefused("abc");
        assertRefused("12a");
        assertRefused("-");
        assertRefused("--3");
        assertRefused("+3");
        assertRefused("- 3");
        assertRefused("7 5g");
        assertRefused("1e3");
        assertRefused("9999999999999");        // 13 digits
        assertRefused("99999999999999999999"); // more than a long holds
    }

    @Test
    public void theStockBoxReadsItTheSameWay() {
        assertEquals(1500, StockRules.parseQuantity("1.500"));
        assertEquals(75, StockRules.parseQuantity("75"));
        for (String typed : new String[]{"7.5", "1.5", "0.5", "2,5", "12.5.1", "5.5", "2.50", "1.2.3", "1 2", "-3", "0", ""}) {
            assertEquals("the stock box must refuse " + typed, -1, StockRules.parseQuantity(typed));
        }
    }

    @Test
    public void theProductCsvReadsItTheSameWayAndKeepsItsIntegerRange() {
        assertEquals(1000, ProductCsv.parseStock("1.000"));
        for (String typed : new String[]{"1.5", "0.5", "2,5", "7.5", "12.5.1", "", "3000000000"}) {
            try {
                ProductCsv.parseStock(typed);
                fail("should be refused: " + typed);
            } catch (NumberFormatException expected) {
                // refused
            }
        }
    }

    @Test
    public void noScreenDeletesTheDotsOfWhatWasTypedAnyMore() throws IOException {
        Path root = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement");
        String dialog = Files.readString(root.resolve("controller/IngredientDialog.java"), StandardCharsets.UTF_8);
        assertTrue("the ingredient boxes read through WholeNumber", dialog.contains("WholeNumber.parse(text)"));
        String stock = Files.readString(root.resolve("service/StockRules.java"), StandardCharsets.UTF_8);
        assertTrue(stock.contains("WholeNumber.parse(typed)"));
        assertFalse("the habit that read 7.5 as 75", stock.contains("replace(\".\", \"\")"));
        assertFalse(dialog.contains("replace(\".\", \"\")"));
        String csv = Files.readString(root.resolve("service/ProductCsv.java"), StandardCharsets.UTF_8);
        assertTrue(csv.contains("WholeNumber.parse(text)"));
        String recipe = Files.readString(root.resolve("controller/RecipePane.java"), StandardCharsets.UTF_8);
        assertTrue("the recipe amount uses the ingredient reader", recipe.contains("IngredientDialog.number(typed.get())"));
    }

    @Test
    public void aDeliveryOfZeroOrLessIsRefusedInTheDialogWithItsOwnSentence() throws IOException {
        String dialog = Files.readString(Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller", "IngredientDialog.java"),
                StandardCharsets.UTF_8);
        assertTrue(dialog.contains("arrived.getAsLong() <= 0"));
        assertTrue(dialog.contains("I18n.t(\"ingredient.val.receive\")"));
    }
}
