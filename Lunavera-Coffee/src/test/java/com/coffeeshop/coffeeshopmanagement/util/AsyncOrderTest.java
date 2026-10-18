package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * TODO 3.24 / 6.35: what the autosave relies on from {@link Async#runOrdered}: jobs run one at a time in the order they were
 * submitted, a job that fails does not stop the ones after it, and the window can wait for the queue to empty. The tests use
 * the plain-job entry point because a JavaFX {@code Task} cannot run without a toolkit.
 */
public class AsyncOrderTest {

    @Test
    public void jobsRunInTheOrderTheyWereSubmitted() {
        List<Integer> ran = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 300; i++) {
            final int n = i;
            Async.runOrderedRaw(() -> ran.add(n));
        }
        assertTrue(Async.flushOrdered(10_000));
        assertEquals(300, ran.size());
        for (int i = 0; i < 300; i++) {
            assertEquals("job " + i + " ran out of turn", Integer.valueOf(i), ran.get(i));
        }
    }

    @Test
    public void aSlowJobIsNotOvertakenByALaterOne() {
        List<String> ran = Collections.synchronizedList(new ArrayList<>());
        Async.runOrderedRaw(() -> {
            sleep(150);
            ran.add("slow first");
        });
        Async.runOrderedRaw(() -> ran.add("quick second"));
        assertTrue(Async.flushOrdered(10_000));
        assertEquals(List.of("slow first", "quick second"), ran);
    }

    @Test
    public void jobsNeverOverlap() {
        AtomicBoolean busy = new AtomicBoolean();
        AtomicBoolean overlapped = new AtomicBoolean();
        for (int i = 0; i < 20; i++) {
            Async.runOrderedRaw(() -> {
                if (!busy.compareAndSet(false, true)) overlapped.set(true);
                sleep(5);
                busy.set(false);
            });
        }
        assertTrue(Async.flushOrdered(10_000));
        assertFalse("two ordered jobs ran at the same time", overlapped.get());
    }

    @Test
    public void aFailingJobDoesNotStopTheJobsAfterIt() {
        AtomicBoolean laterRan = new AtomicBoolean();
        Async.runOrderedRaw(() -> {
            throw new IllegalStateException("a failing autosave (expected in this test)");
        });
        Async.runOrderedRaw(() -> laterRan.set(true));
        assertTrue(Async.flushOrdered(10_000));
        assertTrue("the job after a failed one never ran", laterRan.get());
    }

    @Test
    public void flushGivesUpAfterTheTimeoutAndSucceedsOnceTheQueueIsEmpty() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        Async.runOrderedRaw(() -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertFalse("flush should time out while a job is still running", Async.flushOrdered(100));
        release.countDown();
        assertTrue(Async.flushOrdered(10_000));
    }

    @Test
    public void flushWithNothingQueuedReturnsAtOnce() {
        long start = System.nanoTime();
        assertTrue(Async.flushOrdered(5_000));
        assertTrue("an empty queue should not make the window wait", System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
