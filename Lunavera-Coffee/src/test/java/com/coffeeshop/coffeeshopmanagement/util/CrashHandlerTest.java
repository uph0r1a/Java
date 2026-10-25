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

    @Test
    public void aNegativeClockReadingStillShowsTheFirstDialogAndSpacesTheNext() {
        // The monotonic clock has an arbitrary zero and can be negative (TODO 6.65).
        CrashHandler.resetThrottle();
        long t = -5_000_000L;
        assertTrue(CrashHandler.shouldShowDialog(t));
        assertFalse(CrashHandler.shouldShowDialog(t + 1_000));
        assertTrue(CrashHandler.shouldShowDialog(t + 10_001));
    }

    @Test
    public void aReadingOfZeroIsNotMistakenForNeverShown() {
        CrashHandler.resetThrottle();
        assertTrue(CrashHandler.shouldShowDialog(0));
        assertFalse("the next error one second later is held back", CrashHandler.shouldShowDialog(1_000));
    }
}

