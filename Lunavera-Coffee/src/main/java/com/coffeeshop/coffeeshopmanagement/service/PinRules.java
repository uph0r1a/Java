package com.coffeeshop.coffeeshopmanagement.service;

/**
 * What a cashier's PIN may be (TODO 4.36, rule D7 as chosen): 4 to 6 digits, and not a PIN anybody tries first: every digit the
 * same ("0000", "777777") or a run up or down ("1234", "654321"). Pure, so it is tested without a screen.
 */
public final class PinRules {

    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 6;

    /** Wrong PINs in a row after which the PIN is switched off and the person must set a new one after a password sign-in. */
    public static final int MAX_WRONG_IN_A_ROW = 5;

    private PinRules() {
    }

    /** True when {@code pin} is only the digits 0-9 and has 4 to 6 of them (the shape a PIN box accepts). */
    public static boolean hasShape(String pin) {
        if (pin == null || pin.length() < MIN_LENGTH || pin.length() > MAX_LENGTH) return false;
        for (int i = 0; i < pin.length(); i++) {
            char c = pin.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /** True when the PIN has the right shape and is not one of the easy ones. */
    public static boolean isAcceptable(String pin) {
        if (!hasShape(pin)) return false;
        boolean same = true;
        boolean up = true;
        boolean down = true;
        for (int i = 1; i < pin.length(); i++) {
            int step = pin.charAt(i) - pin.charAt(i - 1);
            same &= step == 0;
            up &= step == 1;
            down &= step == -1;
        }
        return !(same || up || down);
    }
}
