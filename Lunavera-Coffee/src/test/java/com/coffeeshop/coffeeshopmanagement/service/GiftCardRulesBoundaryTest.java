package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** TODO 6.78: tests that came from a mutation run on {@link GiftCardRules}. The rules are pure: no database is needed to run them. */
public class GiftCardRulesBoundaryTest {

    private static final String LOOK_ALIKES = "01OIL";

    // ---- the code

    @Test
    public void aNewCodeIsGCAndTwoGroupsOfFourFromTheAlphabetWithoutLookAlikes() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            String code = GiftCardRules.newCode();
            assertTrue(code, code.matches("GC-[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}"));
            for (char c : LOOK_ALIKES.toCharArray()) assertEquals(code, -1, code.substring(2).indexOf(c));
            seen.add(code);
        }
        assertTrue("codes are random, not one fixed text", seen.size() > 250);
    }

    @Test
    public void everyNewCodeIsReadBackAsItself() {
        for (int i = 0; i < 50; i++) {
            String code = GiftCardRules.newCode();
            assertEquals(code, GiftCardRules.normalise(code));
        }
    }

    @Test
    public void aTypedCodeLosesItsSpacesAndCapitalsAndGetsItsDashesBack() {
        assertEquals("GC-ABCD-EFGH", GiftCardRules.normalise("gc-abcd-efgh"));
        assertEquals("GC-ABCD-EFGH", GiftCardRules.normalise("  GC ABCD EFGH "));
        assertEquals("GC-ABCD-EFGH", GiftCardRules.normalise("GCABCDEFGH"));
        assertEquals("GC-AAAA-AAAA", GiftCardRules.normalise("gc-aaaa-aaaa")); // A is the first letter of the alphabet
        assertEquals("GC-2345-6789", GiftCardRules.normalise("gc-2345-6789")); // 9 is the last
        assertEquals("GC-ZZZZ-ZZZZ", GiftCardRules.normalise("gc-zzzz-zzzz"));
    }

    @Test
    public void aTextThatCannotBeACodeIsNull() {
        assertNull(GiftCardRules.normalise(null));
        assertNull(GiftCardRules.normalise(""));
        assertNull("one character short", GiftCardRules.normalise("GC-ABCD-EFG"));
        assertNull("one character too many", GiftCardRules.normalise("GC-ABCD-EFGHJ"));
        assertNull("another prefix", GiftCardRules.normalise("XX-ABCD-EFGH"));
        assertNull("the prefix is not just two characters of any kind", GiftCardRules.normalise("ABABCDEFGH"));
        assertNull("the first character of the body counts", GiftCardRules.normalise("GC-0BCD-EFGH"));
        assertNull("the last character of the body counts", GiftCardRules.normalise("GC-ABCD-EFG1"));
        for (char c : LOOK_ALIKES.toCharArray()) {
            assertNull("the look-alike " + c, GiftCardRules.normalise("GC-ABCD-EF" + c + "H"));
        }
    }

    // ---- the amount

    @Test
    public void anAmountIsValidFromTheLeastToTheMostInWholeThousands() {
        assertEquals(10_000, constant("MIN_AMOUNT"));
        assertEquals(5_000_000, constant("MAX_AMOUNT"));
        assertEquals(1_000, constant("STEP"));
        assertTrue(GiftCardRules.validAmount(10_000));
        assertTrue(GiftCardRules.validAmount(11_000));
        assertTrue(GiftCardRules.validAmount(5_000_000));
        assertFalse(GiftCardRules.validAmount(9_000));
        assertFalse("not a whole thousand", GiftCardRules.validAmount(10_500));
        assertFalse(GiftCardRules.validAmount(5_001_000));
        assertFalse(GiftCardRules.validAmount(0));
        assertFalse(GiftCardRules.validAmount(-10_000));
    }

    // ---- sharing a give-back over the cards

    @Test
    public void aGiveBackIsSharedInProportionToWhatEachCardPaid() {
        assertArrayEquals(new long[]{20_000, 10_000}, GiftCardRules.shares(30_000, new long[]{40_000, 20_000}));
    }

    @Test
    public void neverMoreThanTheCardsPaidAndNothingForAnEmptyOrNegativeRequest() {
        assertArrayEquals(new long[]{40_000, 20_000}, GiftCardRules.shares(100_000, new long[]{40_000, 20_000}));
        assertArrayEquals(new long[]{0, 0}, GiftCardRules.shares(0, new long[]{40_000, 20_000}));
        assertArrayEquals(new long[]{0, 0}, GiftCardRules.shares(-5, new long[]{40_000, 20_000}));
        assertArrayEquals(new long[]{0, 0}, GiftCardRules.shares(10_000, new long[]{0, 0}));
        assertArrayEquals(new long[0], GiftCardRules.shares(10_000, new long[0]));
    }

    @Test
    public void aNegativeSpentCountsAsNothingAndDoesNotChangeTheOthersShares() {
        assertArrayEquals(new long[]{0, 10}, GiftCardRules.shares(10, new long[]{-5, 10}));
        // with a plain "count it as 1" the 10 would be shared over 21 instead of 20 and the shares of the others would move
        assertArrayEquals(new long[]{0, 4, 1, 5}, GiftCardRules.shares(10, new long[]{-3, 7, 3, 10}));
    }

    @Test
    public void aTotalOfOneOrAGiveBackOfOneIsStillGivenBack() {
        assertArrayEquals(new long[]{1}, GiftCardRules.shares(1, new long[]{1}));
        assertArrayEquals(new long[]{1}, GiftCardRules.shares(1, new long[]{5}));
        assertArrayEquals(new long[]{1, 0}, GiftCardRules.shares(1, new long[]{3, 3}));
    }

    @Test
    public void theRoundingLeftOverGoesOneDongAtATimeToTheFirstCardsThatStillHaveRoom() {
        assertArrayEquals(new long[]{34, 33, 33}, GiftCardRules.shares(100, new long[]{100, 100, 100}));
        assertArrayEquals("only two dong are left over: the first two cards get one each", new long[]{1, 1, 0}, GiftCardRules.shares(2, new long[]{1, 1, 1}));
        assertArrayEquals("a card with nothing spent gets none of the left-over", new long[]{0, 3, 2}, GiftCardRules.shares(5, new long[]{0, 3, 3}));
    }

    @Test
    public void theSharesAddUpToTheGiveBackWheneverTheCardsCanTakeIt() {
        java.util.Random random = new java.util.Random(78);
        for (int round = 0; round < 500; round++) {
            long[] spent = new long[1 + random.nextInt(4)];
            long total = 0;
            for (int i = 0; i < spent.length; i++) {
                spent[i] = random.nextInt(50_000);
                total += spent[i];
            }
            long amount = random.nextInt(60_000);
            long[] share = GiftCardRules.shares(amount, spent);
            long sum = 0;
            for (int i = 0; i < spent.length; i++) {
                assertTrue(share[i] >= 0 && share[i] <= spent[i]);
                sum += share[i];
            }
            assertEquals(Math.min(amount, total), sum);
        }
    }

    /** A static final long is copied into the test when it is compiled; read through reflection, the value is the class's own. */
    private static long constant(String name) {
        try {
            return GiftCardRules.class.getField(name).getLong(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
