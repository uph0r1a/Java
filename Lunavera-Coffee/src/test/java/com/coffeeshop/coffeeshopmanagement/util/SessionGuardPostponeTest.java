package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** TODO 3.14: an open history/report window no longer pauses the session check; a modal dialog delays a logout only briefly. */
public class SessionGuardPostponeTest {
    @Test
    public void aModalDialogDelaysTheLogoutOnlyTwice() {
        assertTrue(SessionGuard.shouldPostpone(true, 0));
        assertTrue(SessionGuard.shouldPostpone(true, SessionGuard.MAX_POSTPONED_TICKS - 1));
        assertFalse(SessionGuard.shouldPostpone(true, SessionGuard.MAX_POSTPONED_TICKS));
    }

    @Test
    public void withoutAModalDialogTheLogoutIsImmediate() {
        assertFalse(SessionGuard.shouldPostpone(false, 0));
    }
}
