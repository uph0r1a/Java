package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 4.36: what a PIN may be. */
public class PinRulesTest {

    @Test
    public void exactlySixDigitsAreTheRightShape() {
        for (String ok : new String[]{"482139", "047213"}) assertTrue(ok, PinRules.hasShape(ok));
        for (String bad : new String[]{null, "", "123", "4821", "48213", "1234567", "12a4", "12 4", "١٢٣٤", "12.4"}) assertFalse(String.valueOf(bad), PinRules.hasShape(bad));
    }

    @Test
    public void easyPinsAreRefused() {
        for (String easy : new String[]{"000000", "777777", "111111", "123456", "654321", "234567", "012345"}) {
            assertFalse(easy, PinRules.isAcceptable(easy));
        }
        for (String fine : new String[]{"135790", "482139", "908172"}) assertTrue(fine, PinRules.isAcceptable(fine));
    }

    /** TODO 2A.47: the six examples of the item pass the old rule and are refused now, with the other shapes of an easy PIN. */
    @Test
    public void repeatedPairsBlocksAndPairedDigitsAreRefused() {
        for (String easy : new String[]{"121212", "123123", "112233", "000111", "696969", "520520", // the examples of the item
                "100000", "010101", "455455", "998877", "778899", "223344"}) {
            assertFalse(easy, PinRules.isAcceptable(easy));
        }
    }

    @Test
    public void aNumberThatReadsTheSameBackwardsIsRefused() {
        for (String easy : new String[]{"123321", "456654", "907709"}) assertFalse(easy, PinRules.isAcceptable(easy));
    }

    @Test
    public void padPatternsAndFamousNumbersAreRefused() {
        for (String easy : new String[]{"147258", "258369", "159753", "789456", "123654", "521314"}) assertFalse(easy, PinRules.isAcceptable(easy));
    }

    @Test
    public void ordinaryPinsStillPass() {
        for (String fine : new String[]{"190384", "270815", "406318", "851247", "031974", "612945"}) assertTrue(fine, PinRules.isAcceptable(fine));
    }

    @Test
    public void aWrongShapeIsStillRefused() {
        for (String bad : new String[]{null, "", "48213", "4821390", "48213a"}) assertFalse(String.valueOf(bad), PinRules.isAcceptable(bad));
    }
}
