package com.coffeeshop.coffeeshopmanagement.util;

/**
 * Counts how many slow actions are running at the same time in one window (TODO 5.16), so the "waiting" mouse pointer is
 * switched on by the first and off only when the last one has ended. Toolkit-free, so it can be tested; {@link BusyIndicator}
 * keeps one per window.
 */
public final class BusyCount {

    private int running;

    /** One more action started. True when it is the first, i.e. the pointer must now change. */
    public synchronized boolean enter() {
        running++;
        return running == 1;
    }

    /** One action ended. True when it was the last, i.e. the pointer must be put back. Ending more than were started does nothing. */
    public synchronized boolean exit() {
        if (running == 0) {
            return false;
        }
        running--;
        return running == 0;
    }

    public synchronized int running() {
        return running;
    }
}
