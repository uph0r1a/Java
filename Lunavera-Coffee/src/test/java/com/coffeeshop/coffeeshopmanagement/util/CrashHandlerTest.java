package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 2.24: at most one error dialog per interval. */
public class CrashHandlerTest {

    @Test
    public void dialogsAreSpacedOut() {
        CrashHandler.resetThrottle();
        long t = 1_000_000_000L;
        assertTrue(CrashHandler.shouldShowDialog(t));
        assertFalse(CrashHandler.shouldShowDialog(t + 1_000));
        assertFalse(CrashHandler.shouldShowDialog(t + 9_000));
        assertTrue(CrashHandler.shouldShowDialog(t + 10_001));
    }
}
