package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** TODO 5.16: the waiting pointer goes on with the first slow action and off with the last. */
public class BusyCountTest {

    @Test
    public void onlyTheFirstStartAndTheLastEndChangeThePointer() {
        BusyCount count = new BusyCount();
        assertTrue(count.enter());
        assertFalse("a second action in the same window", count.enter());
        assertFalse("the first one ended, the second is still running", count.exit());
        assertTrue(count.exit());
        assertEquals(0, count.running());
    }

    @Test
    public void endingMoreThanWereStartedChangesNothing() {
        BusyCount count = new BusyCount();
        assertFalse(count.exit());
        assertTrue(count.enter());
        assertTrue(count.exit());
        assertFalse(count.exit());
        assertTrue("and a later action still counts from zero", count.enter());
    }
}
