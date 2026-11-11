package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * TODO 6.78: the gift card rules with no database. Their only tests used to be inside {@code GiftCardTest}, which needs SQLite, so a
 * mutation run here (tools/mutation-sweep.py) found 34 changes nobody noticed.
 */
public class GiftCardRulesTest {

    @Test
    public void aNewCodeHasTwoGroupsOfFourFromTheClearAlphabet() {
        for (int i = 0; i < 200; i++) {
            String code = GiftCardRules.newCode();
            assertTrue(code, code.matches("GC-[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{4}-[ABCDEFGHJKMNPQRSTUVWXYZ2-9]{4}"));
            assertEquals(code, GiftCardRules.normalise(code));
        }
    }

    @Test
    public void typedCodesAreTidiedOrRefused() {
        assertNull(GiftCardRules.normalise(null));
        assertEquals("GC-ABCD-EFGH", GiftCardRules.normalise("  gc-abcd-efgh "));
        assertEquals("GC-ABCD-EFGH", GiftCardRules.normalise("gc abcd efgh"));
        assertEquals("GC-ABCD-EFGH", GiftCardRules.normalise("GCABCDEFGH"));
        assertEquals("the last letter counts", "GC-2345-6789", GiftCardRules.normalise("GC-2345-6789"));
        assertNull("one short", GiftCardRules.normalise("GC-ABCD-EFG"));
        assertNull("one long", GiftCardRules.normalise("GC-ABCD-EFGHJ"));
        assertNull("empty", GiftCardRules.normalise(""));
        assertNull("not our prefix", GiftCardRules.normalise("XC-ABCD-EFGH"));
        assertNull("not our prefix", GiftCardRules.normalise("GX-ABCD-EFGH"));
        assertNull("a letter that is not in the alphabet, first place", GiftCardRules.normalise("GC-0BCD-EFGH"));
        assertNull("last place", GiftCardRules.normalise("GC-ABCD-EFG0"));
        assertNull("O is not in the alphabet", GiftCardRules.normalise("GC-ABCD-EFGO"));
        assertNull("a symbol", GiftCardRules.normalise("GC-AB.D-EFGH"));
    }

    @Test
    public void anAmountIsWithinTheLimitsAndInWholeThousands() {
        assertTrue(GiftCardRules.validAmount(10_000));
        assertTrue(GiftCardRules.validAmount(11_000));
        assertTrue(GiftCardRules.validAmount(5_000_000));
        assertFalse("below the least", GiftCardRules.validAmount(9_000));
        assertFalse(GiftCardRules.validAmount(0));
        assertFalse("above the most", GiftCardRules.validAmount(5_001_000));
        assertFalse("not a whole thousand", GiftCardRules.validAmount(10_500));
        assertFalse(GiftCardRules.validAmount(10_001));
    }

    @Test
    public void theGiveBackIsSharedInProportionToWhatEachCardPaid() {
        assertArrayEquals(new long[]{20_000, 10_000}, GiftCardRules.shares(30_000, new long[]{40_000, 20_000}));
    }

    @Test
    public void whatRoundingLeavesGoesToTheFirstCardsWithRoom() {
        long[] shares = GiftCardRules.shares(10, new long[]{5, 5, 5});
        assertArrayEquals(Arrays.toString(shares), new long[]{4, 3, 3}, shares);
        assertArrayEquals("room on the first is used up, so the next one gets it",
                new long[]{1, 4}, GiftCardRules.shares(5, new long[]{1, 10}));
    }

    @Test
    public void nothingIsGivenBackBeyondWhatWasSpentOrWhenThereIsNothing() {
        assertArrayEquals(new long[]{0, 0}, GiftCardRules.shares(0, new long[]{10, 10}));
        assertArrayEquals(new long[]{0, 0}, GiftCardRules.shares(50, new long[]{0, 0}));
        assertArrayEquals(new long[0], GiftCardRules.shares(50, new long[0]));
        assertArrayEquals("more than was spent: only what was spent", new long[]{10, 5}, GiftCardRules.shares(1_000, new long[]{10, 5}));
        assertArrayEquals("a negative figure counts as nothing", new long[]{0, 100}, GiftCardRules.shares(100, new long[]{-50, 100}));
        assertArrayEquals(new long[]{0, 0}, GiftCardRules.shares(-5, new long[]{10, 10}));
    }

    @Test
    public void aCardThatSpentNothingNeverGetsTheRoundingLeftOver() {
        assertArrayEquals(new long[]{0, 4, 3, 3}, GiftCardRules.shares(10, new long[]{-5, 5, 5, 5}));
        assertArrayEquals("a give-back of one dong is still given", new long[]{1}, GiftCardRules.shares(1, new long[]{10}));
        assertArrayEquals("one dong spent, one dong given", new long[]{1}, GiftCardRules.shares(1, new long[]{1}));
    }
}
