package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.util.I18n;
import org.junit.After;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** TODO 1.7: what a rebuild of the till screen loses (the guard of the in-app language switch). */
public class PosLeaveRulesTest {

    private final String languageAtStart = I18n.current();

    @After
    public void putTheLanguageBack() {
        I18n.setLanguage(languageAtStart);
    }

    private static PosLeaveRules.State state(boolean savesDone, boolean starting, boolean orderSaved, int lines, boolean typed) {
        return new PosLeaveRules.State(savesDone, starting, orderSaved, lines, typed);
    }

    @Test
    public void aSavedOrderWithAllSavesDoneLosesNothing() {
        assertTrue(PosLeaveRules.lost(state(true, false, true, 5, false)).isEmpty());
    }

    @Test
    public void anEmptyScreenLosesNothing() {
        assertTrue(PosLeaveRules.lost(state(true, false, false, 0, false)).isEmpty());
    }

    @Test
    public void cartLinesWithoutASavedOrderAreLost() {
        I18n.setLanguage(I18n.EN);
        List<String> lost = PosLeaveRules.lost(state(true, false, false, 3, false));
        assertEquals(List.of("Cart lines not saved to an order: 3."), lost);
    }

    @Test
    public void aSaveStillRunningOrFailedIsLostOnlyWhenThereIsACart() {
        assertEquals(1, PosLeaveRules.lost(state(false, false, true, 2, false)).size());
        assertTrue(PosLeaveRules.lost(state(false, false, true, 0, false)).isEmpty());
    }

    @Test
    public void anUnsavedOrderIsNotListedTwice() {
        assertEquals(1, PosLeaveRules.lost(state(false, false, false, 2, false)).size());
    }

    @Test
    public void anOrderBeingStartedAndATypedCustomerAreListed() {
        assertEquals(2, PosLeaveRules.lost(state(true, true, false, 0, true)).size());
    }

    @Test
    public void theLinesFollowTheLanguageInUse() {
        I18n.setLanguage(I18n.VI);
        String vi = PosLeaveRules.lost(state(true, false, false, 1, false)).get(0);
        I18n.setLanguage(I18n.EN);
        String en = PosLeaveRules.lost(state(true, false, false, 1, false)).get(0);
        assertNotEquals(vi, en);
    }

    @Test
    public void textTheScreenFillsInItselfIsNotATypedCustomer() {
        assertFalse(PosLeaveRules.customerTyped("Khách vãng lai", "", null, null, "Khách vãng lai"));
        assertFalse(PosLeaveRules.customerTyped("", "", null, null, "Khách vãng lai"));
        assertFalse(PosLeaveRules.customerTyped(null, null, null, null, "x"));
    }

    @Test
    public void theSavedCustomersOwnTextIsNotTyped() {
        assertFalse(PosLeaveRules.customerTyped("Nguyễn An ", "0901234567", "Nguyễn An", "0901234567", "x"));
    }

    @Test
    public void aNameOrPhoneNobodyIsAttachedToIsTyped() {
        assertTrue(PosLeaveRules.customerTyped("Lan", "", null, null, "x"));
        assertTrue(PosLeaveRules.customerTyped("", "0901234567", null, null, "x"));
        assertTrue(PosLeaveRules.customerTyped("Lan", "", "An", "0901", "x"));
    }
}
