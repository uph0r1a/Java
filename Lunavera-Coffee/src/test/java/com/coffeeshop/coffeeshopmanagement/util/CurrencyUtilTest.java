package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;

public class CurrencyUtilTest {

    @Test
    public void formatsWithDotThousandsSeparatorAndDongSuffix() {
        assertEquals("12.000đ", CurrencyUtil.format(new BigDecimal("12000")));
        assertEquals("1.500.000đ", CurrencyUtil.format(new BigDecimal("1500000")));
    }

    @Test
    public void noThousandsSeparatorBelowOneThousand() {
        assertEquals("500đ", CurrencyUtil.format(new BigDecimal("500")));
    }

    @Test
    public void zeroAndNullBothRenderAsZero() {
        // A null total (e.g. a field not yet loaded) must render as an honest 0đ, not throw
        // and not silently show a blank label.
        assertEquals("0đ", CurrencyUtil.format(BigDecimal.ZERO));
        assertEquals("0đ", CurrencyUtil.format(null));
    }

    @Test
    public void noDecimalPlacesEvenWithFractionalInput() {
        // Money in this app is whole VND; a stray fractional value from a calculation must
        // still display as a clean integer amount.
        assertEquals("100đ", CurrencyUtil.format(new BigDecimal("99.6")));
    }

    /** TODO 3.60: the screen rounds half-up like Money.toDong, so a shown amount equals the stored one. */
    @Test
    public void roundsHalfUpLikeMoneyToDong() {
        for (String v : new String[]{"0.5", "1.5", "2.5", "999.5", "-0.5", "-2.5"}) {
            BigDecimal value = new BigDecimal(v);
            assertEquals(CurrencyUtil.format(Money.fromDong(Money.toDong(value))), CurrencyUtil.format(value));
        }
        assertEquals("1đ", CurrencyUtil.format(new BigDecimal("0.5")));
        assertEquals("3đ", CurrencyUtil.format(new BigDecimal("2.5")));
    }

    /** TODO 3.60: a value that rounds to zero is "0đ", never "-0đ". */
    @Test
    public void tinyNegativeAmountShowsAsPlainZero() {
        assertEquals("0đ", CurrencyUtil.format(new BigDecimal("-0.0000001")));
        assertEquals("0đ", CurrencyUtil.format(new BigDecimal("-0.4")));
        assertEquals("-1đ", CurrencyUtil.format(new BigDecimal("-0.5")));
    }

    /** TODO 6.19: a shared DecimalFormat mixes up digits when two threads format at once; one per thread does not. */
    @Test
    public void formattingFromManyThreadsAtOnceStaysCorrect() throws Exception {
        int threads = 8;
        int rounds = 20_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final long amount = (t + 1) * 1_111_000L; // every thread formats its own, different, amount
                final String expected = CurrencyUtil.format(new BigDecimal(amount));
                Callable<String> task = () -> {
                    start.await();
                    for (int i = 0; i < rounds; i++) {
                        String got = CurrencyUtil.format(new BigDecimal(amount));
                        if (!expected.equals(got)) {
                            return "expected " + expected + " but got " + got;
                        }
                    }
                    return null;
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            for (Future<String> result : results) {
                assertEquals(null, result.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
