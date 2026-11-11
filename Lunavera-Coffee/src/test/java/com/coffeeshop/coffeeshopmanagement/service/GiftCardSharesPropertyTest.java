package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.76 (the same idea for the next money rule): a refund that goes back onto gift cards is shared over the cards that paid the
 * bill. Whatever the amounts, no card gets more than it paid, nothing is given that was not asked for, and the shares add up to
 * what can be given. See {@link RandomCases} to replay a failure.
 */
public class GiftCardSharesPropertyTest {

    @Test
    public void theSharesAddUpAndNoCardGetsMoreThanItSpent() {
        RandomCases.run("GiftCardRules.shares", 8000, random -> {
            int cards = RandomCases.between(random, 1, 5);
            long[] spent = new long[cards];
            long total = 0;
            for (int i = 0; i < cards; i++) {
                spent[i] = random.nextInt(8) == 0 ? 0 : RandomCases.between(random, 1, 5_000) * 1000L - random.nextInt(2);
                total += spent[i];
            }
            long amount = switch (random.nextInt(4)) {
                case 0 -> RandomCases.between(random, 0, Math.max(total, 1));
                case 1 -> total + RandomCases.between(random, 0, 10_000);
                case 2 -> RandomCases.between(random, 0, 20);
                default -> RandomCases.between(random, -50, total);
            };
            long[] share = GiftCardRules.shares(amount, spent);
            assertEquals("one share per card", cards, share.length);
            long given = 0;
            for (int i = 0; i < cards; i++) {
                assertTrue("never negative", share[i] >= 0);
                assertTrue("a card gets back at most what it paid: " + share[i] + " > " + spent[i], share[i] <= spent[i]);
                given += share[i];
            }
            assertEquals("all that can be given is given, and no more", Math.max(0, Math.min(amount, total)), given);
        });
    }

    @Test
    public void aCardThatPaidMoreNeverGetsBackLessThanOneThatPaidLessBeyondOneDong() {
        RandomCases.run("GiftCardRules.shares, in proportion", 5000, random -> {
            long a = RandomCases.between(random, 1, 5_000) * 1000L;
            long b = RandomCases.between(random, 1, 5_000) * 1000L;
            long amount = RandomCases.between(random, 0, a + b);
            long[] share = GiftCardRules.shares(amount, new long[]{a, b});
            if (a >= b) {
                assertTrue("the card that paid more gets at least as much, give or take one dong of rounding: " + share[0] + " vs " + share[1],
                        share[0] + 1 >= share[1]);
            } else {
                assertTrue(share[1] + 1 >= share[0]);
            }
        });
    }
}
