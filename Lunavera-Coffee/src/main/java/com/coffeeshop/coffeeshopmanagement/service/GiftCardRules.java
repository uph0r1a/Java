package com.coffeeshop.coffeeshopmanagement.service;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Gift cards (TODO 7, rule D23: my choice, the owner has not answered). A card is sold for cash for a whole number of thousand dong,
 * has a printable code, and its balance can only be spent on a sale, in part or in full, never paid back as cash. Selling a card is
 * not a sale of goods: it is no revenue and no order; the cash goes into the drawer (the shift close counts it) and the value is
 * counted as revenue later, when the card is spent. An unused card does not expire. A refund of a whole order puts the part paid
 * with a card back on that card; the money of a refunded line is returned by the method the owner picks, never on a card.
 */
public final class GiftCardRules {

    public static final long MIN_AMOUNT = 10_000;
    public static final long MAX_AMOUNT = 5_000_000;
    public static final long STEP = 1_000;
    /** Letters and digits that cannot be confused when read aloud or typed from a printed card (no 0, 1, O, I, L). */
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int GROUP = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private GiftCardRules() {
    }

    /** A new code in the form {@code GC-ABCD-EFGH}. */
    public static String newCode() {
        StringBuilder code = new StringBuilder("GC");
        for (int g = 0; g < 2; g++) {
            code.append('-');
            for (int i = 0; i < GROUP; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * The code as it is kept, from what was typed: capitals, spaces ignored, the dashes put back when they were left out; null when it
     * cannot be a code of ours.
     */
    public static String normalise(String typed) {
        if (typed == null) return null;
        String compact = typed.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace("-", "");
        if (compact.length() != 2 + 2 * GROUP || !compact.startsWith("GC")) return null;
        String body = compact.substring(2);
        for (int i = 0; i < body.length(); i++) {
            if (ALPHABET.indexOf(body.charAt(i)) < 0) return null;
        }
        return "GC-" + body.substring(0, GROUP) + "-" + body.substring(GROUP);
    }

    /** True when {@code amount} (whole dong) may be put on a new card. */
    public static boolean validAmount(long amount) {
        return amount >= MIN_AMOUNT && amount <= MAX_AMOUNT && amount % STEP == 0;
    }

    /**
     * Shares {@code amount} over cards in proportion to what each still has spent on the order ({@code spent}, per card), whole dong,
     * never more on a card than it has spent and never more than {@code amount} in all; what the rounding leaves goes one dong at a time
     * to the first cards that still have room. With 40.000 spent on card A and 20.000 on card B, a give-back of 30.000 is 20.000 to A and
     * 10.000 to B. The result has the order of {@code spent}.
     */
    public static long[] shares(long amount, long[] spent) {
        long total = 0;
        for (long s : spent) total += Math.max(0, s);
        long[] share = new long[spent.length];
        long give = Math.max(0, Math.min(amount, total));
        if (total == 0 || give == 0) return share;
        long given = 0;
        for (int i = 0; i < spent.length; i++) {
            share[i] = java.math.BigInteger.valueOf(give).multiply(java.math.BigInteger.valueOf(Math.max(0, spent[i])))
                    .divide(java.math.BigInteger.valueOf(total)).longValueExact();
            given += share[i];
        }
        for (int i = 0; given < give && i < spent.length; i++) {
            long room = Math.max(0, spent[i]) - share[i];
            long add = Math.min(room, give - given);
            share[i] += add;
            given += add;
        }
        return share;
    }
}
