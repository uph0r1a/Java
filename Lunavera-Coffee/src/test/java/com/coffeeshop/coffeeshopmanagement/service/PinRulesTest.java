package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 4.36: what a PIN may be. */
public class PinRulesTest {

    @Test
    public void fourToSixDigitsAreTheRightShape() {
        for (String ok : new String[]{"4821", "48213", "482139", "0472"}) assertTrue(ok, PinRules.hasShape(ok));
        for (String bad : new String[]{null, "", "123", "1234567", "12a4", "12 4", "١٢٣٤", "12.4"}) assertFalse(String.valueOf(bad), PinRules.hasShape(bad));
    }

    @Test
    public void easyPinsAreRefused() {
        for (String easy : new String[]{"0000", "7777", "111111", "1234", "4321", "123456", "654321", "2345", "0123"}) {
            assertFalse(easy, PinRules.isAcceptable(easy));
        }
        for (String fine : new String[]{"1357", "4821", "1233", "135790", "9081"}) assertTrue(fine, PinRules.isAcceptable(fine));
    }
}
