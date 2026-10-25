package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class BusyTest {

    private static final class Fake implements Busy.Switch {
        boolean disabled;
        Fake(boolean disabled) { this.disabled = disabled; }
        @Override public boolean isDisabled() { return disabled; }
        @Override public void setDisabled(boolean value) { disabled = value; }
    }

    @Test
    public void holdSwitchesEverythingOffAndReleaseBringsItBack() {
        Fake a = new Fake(false), b = new Fake(false);
        Runnable release = Busy.hold(List.of(a, b));
        assertTrue(a.disabled && b.disabled);
        release.run();
        assertFalse(a.disabled || b.disabled);
    }

    @Test
    public void somethingAlreadyDisabledStaysDisabled() {
        Fake already = new Fake(true), fresh = new Fake(false);
        Runnable release = Busy.hold(List.of(already, fresh));
        release.run();
        assertTrue("it was off before, so it is still off", already.disabled);
        assertFalse(fresh.disabled);
    }

    @Test
    public void releasingTwiceDoesNotReEnableWhatSomeoneElseDisabledMeanwhile() {
        Fake a = new Fake(false);
        Runnable release = Busy.hold(List.of(a));
        release.run();
        a.disabled = true; // the callback disabled it on purpose
        release.run();
        assertTrue(a.disabled);
    }

    @Test
    public void nothingToHoldIsFine() {
        Busy.hold(List.of()).run();
        Busy.hold(java.util.Arrays.asList((Busy.Switch) null)).run();
    }
}
