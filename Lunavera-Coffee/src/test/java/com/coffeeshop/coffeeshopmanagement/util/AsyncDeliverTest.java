package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** TODO 6.46: a success callback that throws is logged and the person is told once; one that works is left alone. */
public class AsyncDeliverTest {

    @Test
    public void aWorkingCallbackGetsTheValueAndNothingIsShown() {
        AtomicInteger got = new AtomicInteger();
        AtomicInteger told = new AtomicInteger();
        Async.deliver(v -> got.set(v), 7, told::incrementAndGet);
        assertEquals(7, got.get());
        assertEquals(0, told.get());
    }

    @Test
    public void aCallbackThatThrowsIsLoggedAndTheNoticeIsShownOnce() {
        AtomicInteger told = new AtomicInteger();
        Async.<Integer>deliver(v -> { throw new IllegalStateException("label is null"); }, 1, told::incrementAndGet);
        assertEquals(1, told.get());
    }

    @Test
    public void aNoticeThatFailsToShowDoesNotEscape() {
        Async.<Integer>deliver(v -> { throw new IllegalStateException("x"); }, 1,
                () -> { throw new IllegalStateException("no window"); });
    }
}
