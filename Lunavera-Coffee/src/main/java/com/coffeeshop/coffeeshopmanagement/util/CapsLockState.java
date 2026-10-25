package com.coffeeshop.coffeeshopmanagement.util;

/**
 * What the app can tell about the Caps Lock key (TODO 5.26). JavaFX 21 has no call for the key's state and the AWT call needs
 * the {@code java.desktop} module, which the app does not use, so the state is worked out from the keys typed: a letter comes out
 * capital when exactly one of Shift and Caps Lock is on, so "capital without Shift" or "small with Shift" means Caps Lock is on.
 * Until a letter has been typed the state is unknown and nothing is shown. The Caps Lock key itself flips a known state. No screen
 * code in here, so it can be tested.
 */
public final class CapsLockState {

    private Boolean on; // null = not known yet

    /** True only when Caps Lock is known to be on. */
    public boolean isOn() {
        return Boolean.TRUE.equals(on);
    }

    /** The Caps Lock key was pressed: a known state flips, an unknown one stays unknown. */
    public void capsKeyPressed() {
        if (on != null) on = !on;
    }

    /**
     * A key was pressed that produced {@code text}. Only a single plain letter A-Z / a-z tells anything, and not with Ctrl, Alt or
     * Meta held (shortcuts, and AltGr on Windows, give other letters).
     */
    public void keyTyped(String text, boolean shiftDown, boolean otherModifierDown) {
        if (otherModifierDown || text == null || text.length() != 1) return;
        char c = text.charAt(0);
        boolean upper = c >= 'A' && c <= 'Z';
        boolean lower = c >= 'a' && c <= 'z';
        if (!upper && !lower) return;
        on = upper != shiftDown;
    }

    /** The window lost the focus: Caps Lock may be changed elsewhere, so the state is unknown again. */
    public void forget() {
        on = null;
    }
}
