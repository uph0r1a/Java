package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 5.26: what the app concludes about Caps Lock from the keys typed. */
public class CapsLockStateTest {

    @Test
    public void nothingIsShownBeforeALetterIsTyped() {
        CapsLockState state = new CapsLockState();
        assertFalse(state.isOn());
        state.capsKeyPressed(); // the key alone says nothing when the old state is unknown
        assertFalse(state.isOn());
        state.keyTyped("1", false, false);
        state.keyTyped(" ", false, false);
        assertFalse("digits and spaces tell nothing", state.isOn());
    }

    @Test
    public void aCapitalWithoutShiftMeansCapsLockIsOn() {
        CapsLockState state = new CapsLockState();
        state.keyTyped("A", false, false);
        assertTrue(state.isOn());
        state.keyTyped("b", false, false);
        assertFalse("a small letter without Shift: off again", state.isOn());
    }

    @Test
    public void shiftTurnsTheMeaningAround() {
        CapsLockState state = new CapsLockState();
        state.keyTyped("A", true, false);
        assertFalse("a capital with Shift is normal typing", state.isOn());
        state.keyTyped("a", true, false);
        assertTrue("a small letter with Shift can only be Caps Lock", state.isOn());
    }

    @Test
    public void theCapsKeyFlipsAKnownState() {
        CapsLockState state = new CapsLockState();
        state.keyTyped("X", false, false);
        assertTrue(state.isOn());
        state.capsKeyPressed();
        assertFalse(state.isOn());
        state.capsKeyPressed();
        assertTrue(state.isOn());
    }

    @Test
    public void shortcutsAndOtherCharactersAreIgnored() {
        CapsLockState state = new CapsLockState();
        state.keyTyped("a", false, false);
        assertFalse(state.isOn());
        state.keyTyped("V", false, true); // Ctrl+V, or AltGr on Windows
        assertFalse("a modified key is not evidence", state.isOn());
        state.keyTyped("AB", false, false);
        state.keyTyped(null, false, false);
        state.keyTyped("\u0110", false, false); // not A-Z: ignored
        state.keyTyped("\u00e0", false, false);
        assertFalse(state.isOn());
    }

    @Test
    public void leavingTheWindowForgetsTheState() {
        CapsLockState state = new CapsLockState();
        state.keyTyped("Q", false, false);
        assertTrue(state.isOn());
        state.forget();
        assertFalse(state.isOn());
        state.capsKeyPressed();
        assertFalse("still unknown after the forget", state.isOn());
    }
}
