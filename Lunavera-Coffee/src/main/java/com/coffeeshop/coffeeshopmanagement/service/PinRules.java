package com.coffeeshop.coffeeshopmanagement.service;

/**
 * What a cashier's PIN may be (TODO 4.36, rule D7: the owner chose 6 digits): exactly 6 digits, and not a PIN anybody tries first: every digit the
 * same ("777777"), a run up or down ("123456", "654321"), one or two different digits ("121212", "000111"), a repeated block ("123123"),
 * paired digits ("112233"), a palindrome ("123321") or a pad pattern or famous number ("147258", "520131", TODO 2A.47).
 * A PIN already set is not checked again: only the sign-in by PIN uses {@link #hasShape}. Pure, so it is tested without a screen.
 */
public final class PinRules {

    public static final int MIN_LENGTH = 6;
    public static final int MAX_LENGTH = 6;

    /** Wrong PINs in a row after which the PIN is switched off and the person must set a new one after a password sign-in. */
    public static final int MAX_WRONG_IN_A_ROW = 5;

    private PinRules() {
    }

    /** True when {@code pin} is only the digits 0-9 and has 6 of them (the shape a PIN box accepts). */
    public static boolean hasShape(String pin) {
        if (pin == null || pin.length() < MIN_LENGTH || pin.length() > MAX_LENGTH) return false;
        for (int i = 0; i < pin.length(); i++) {
            char c = pin.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    /** Six-digit PINs of a pad pattern or a famous number, which are tried before anything else (TODO 2A.47). */
    private static final java.util.Set<String> COMMON_PINS = java.util.Set.of(
            "147258", "258369", "369258", "159753", "357951", "741852", "852963", "963852", "147369", "789456", "456789", "123654",
            "321654", "456123", "159357", "121314", "131415", "521314", "131420");

    /**
     * True when the PIN has the right shape and is not an easy one: every digit the same, a run up or down ({@code 123456}, {@code 654321}),
     * only one or two different digits ({@code 121212}, {@code 000111}, {@code 696969}), one block of three digits repeated
     * ({@code 123123}, {@code 520520}), three pairs of equal digits ({@code 112233}, {@code 998877}), a number that reads the same
     * backwards ({@code 123321}), or one of a short list of pad patterns and famous numbers.
     */
    public static boolean isAcceptable(String pin) {
        if (!hasShape(pin)) return false;
        boolean up = true;
        boolean down = true;
        for (int i = 1; i < pin.length(); i++) {
            int step = pin.charAt(i) - pin.charAt(i - 1);
            up &= step == 1;
            down &= step == -1;
        }
        if (up || down) return false;
        if (pin.chars().distinct().count() <= 2) return false; // all the same, or two digits only (121212, 000111, 696969)
        if (pin.substring(0, 3).equals(pin.substring(3))) return false; // 123123, 520520
        if (pin.charAt(0) == pin.charAt(1) && pin.charAt(2) == pin.charAt(3) && pin.charAt(4) == pin.charAt(5)) return false; // 112233
        if (new StringBuilder(pin).reverse().toString().equals(pin)) return false; // 123321
        return !COMMON_PINS.contains(pin);
    }
}
