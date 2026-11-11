package com.coffeeshop.coffeeshopmanagement.service;

import java.util.Random;

/**
 * TODO 6.76: the small frame of the random ("property") tests of the money rules. A test states a rule that must hold for every
 * input and this feeds it thousands of random inputs from a FIXED seed, so a run is the same on every computer and a failure is a
 * fact, not bad luck.
 *
 * <p><b>Replaying a failure.</b> The failure message names the seed and the number of the case, for example
 * {@code seed=20261107 case=1234}. Run that one case again with
 * {@code ./mvnw -B test -Dtest=RefundMathPropertyTest -Dlunavera.propertySeed=20261107 -Dlunavera.propertyCase=1234}. To look for new
 * failures with other numbers, give another seed: {@code -Dlunavera.propertySeed=<any number>} (the same seed gives the same cases).
 * When a random run finds a real mistake, copy the printed input into an ordinary test next to the rule so it stays fixed.
 *
 * <p>This class is not a test itself (its name does not end in "Test").
 */
public final class RandomCases {

    /** The seed of a normal run: fixed, so the build does not change from day to day. */
    public static final long DEFAULT_SEED = 20_261_107L;

    /** One random case; throw (an assertion, or anything) when the rule is broken. */
    @FunctionalInterface
    public interface Case {
        void run(Random random) throws Exception;
    }

    private RandomCases() {
    }

    public static long seed() {
        return Long.getLong("lunavera.propertySeed", DEFAULT_SEED);
    }

    /** The random numbers of one case: they depend on the seed and the case number only, so one case can be replayed alone. */
    public static Random randomOf(long seed, int caseNumber) {
        long mixed = seed * 0x9E3779B97F4A7C15L + caseNumber * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 31)) * 0x94D049BB133111EBL;
        return new Random(mixed ^ (mixed >>> 29));
    }

    /** Runs {@code count} cases (or only {@code -Dlunavera.propertyCase=N}); the first broken one fails with its seed and number. */
    public static void run(String rule, int count, Case body) {
        long seed = seed();
        Integer only = Integer.getInteger("lunavera.propertyCase");
        for (int i = only != null ? only : 0; i < (only != null ? only + 1 : count); i++) {
            try {
                body.run(randomOf(seed, i));
            } catch (Throwable broken) {
                AssertionError error = new AssertionError(rule + ": broken at seed=" + seed + " case=" + i
                        + " (replay: -Dlunavera.propertySeed=" + seed + " -Dlunavera.propertyCase=" + i + "): " + broken.getMessage(), broken);
                throw error;
            }
        }
    }

    /** A number from {@code low} to {@code high}, both included. */
    public static long between(Random random, long low, long high) {
        return low + (long) (random.nextDouble() * (high - low + 1));
    }

    public static int between(Random random, int low, int high) {
        return low + random.nextInt(high - low + 1);
    }
}
