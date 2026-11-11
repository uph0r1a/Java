package com.coffeeshop.coffeeshopmanagement.util;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Lets one piece of work run at a time (TODO 2.62). The automatic backup is started from five places now (start-up, the hourly
 * timer, a logout, the idle logout and closing the window); two of them at once would write two zips of the same second and read the
 * database twice. A caller that finds the work already running either leaves at once or waits a bounded time, and the lock is
 * always given back, even when the work throws.
 */
public final class SingleFlight {

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Runs {@code work} when nothing else is running, or when it finishes within {@code waitMillis}; otherwise returns empty and
     * does nothing. {@code waitMillis} 0 means "only if nobody is running now". Empty also when the thread was interrupted while waiting.
     */
    public <T> Optional<T> run(long waitMillis, Supplier<T> work) {
        try {
            if (!lock.tryLock(Math.max(0, waitMillis), TimeUnit.MILLISECONDS)) return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(work.get());
        } finally {
            lock.unlock();
        }
    }

    /** True while some thread is inside {@link #run}. */
    public boolean isRunning() {
        return lock.isLocked();
    }
}
