package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** TODO 2.62: one automatic backup at a time, however many places ask. */
public class SingleFlightTest {

    /** Starts a thread that holds the flight until released; returns once it is inside. */
    private static Thread holder(SingleFlight flight, CountDownLatch inside, CountDownLatch release) throws InterruptedException {
        Thread thread = new Thread(() -> flight.run(0, () -> {
            inside.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "done";
        }));
        thread.start();
        assertTrue(inside.await(5, TimeUnit.SECONDS));
        return thread;
    }

    @Test
    public void workRunsWhenNothingElseIsRunning() {
        SingleFlight flight = new SingleFlight();
        assertEquals(Optional.of("ok"), flight.run(0, () -> "ok"));
        assertFalse(flight.isRunning());
    }

    @Test
    public void aSecondCallerWhileOneRunsDoesNothing() throws Exception {
        SingleFlight flight = new SingleFlight();
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread first = holder(flight, inside, release);
        AtomicInteger ran = new AtomicInteger();
        assertTrue(flight.isRunning());
        assertEquals(Optional.empty(), flight.run(0, () -> {
            ran.incrementAndGet();
            return "second";
        }));
        assertEquals("the second one must not have started", 0, ran.get());
        release.countDown();
        first.join(5000);
        assertFalse(flight.isRunning());
    }

    @Test
    public void aCallerThatMayWaitRunsAfterTheFirstIsDone() throws Exception {
        SingleFlight flight = new SingleFlight();
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread first = holder(flight, inside, release);
        new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                // the release below is what matters
            }
            release.countDown();
        }).start();
        assertEquals(Optional.of("after"), flight.run(5000, () -> "after"));
        first.join(5000);
    }

    @Test
    public void aCallerThatWaitsTooLongGivesUp() throws Exception {
        SingleFlight flight = new SingleFlight();
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread first = holder(flight, inside, release);
        long started = System.nanoTime();
        assertEquals(Optional.empty(), flight.run(150, () -> "never"));
        long waitedMs = (System.nanoTime() - started) / 1_000_000;
        assertTrue("waited about the time it was given, not for ever: " + waitedMs, waitedMs >= 100 && waitedMs < 2000);
        release.countDown();
        first.join(5000);
    }

    @Test
    public void theLockIsGivenBackWhenTheWorkThrows() {
        SingleFlight flight = new SingleFlight();
        try {
            flight.run(0, () -> {
                throw new IllegalStateException("backup failed");
            });
        } catch (IllegalStateException expected) {
            // the caller of the work sees it
        }
        assertFalse(flight.isRunning());
        assertEquals(Optional.of(1), flight.run(0, () -> 1));
    }

    @Test
    public void workThatReturnsNothingIsEmptyNotAnError() {
        assertEquals(Optional.empty(), new SingleFlight().run(0, () -> null));
    }

    @Test
    public void manyThreadsAtOnceRunTheWorkOnlyOnceAtATime() throws Exception {
        SingleFlight flight = new SingleFlight();
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        AtomicInteger ran = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                flight.run(0, () -> {
                    maxInside.accumulateAndGet(inside.incrementAndGet(), Math::max);
                    ran.incrementAndGet();
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                        // finish anyway
                    }
                    inside.decrementAndGet();
                    return 1;
                });
            });
            threads[i].start();
        }
        go.countDown();
        for (Thread t : threads) t.join(5000);
        assertEquals("never two inside at once", 1, maxInside.get());
        assertTrue("at least one ran", ran.get() >= 1);
    }
}
