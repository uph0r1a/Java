package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.TimeClockEntry;

import java.time.LocalDateTime;

/**
 * When the till offers to clock somebody in or out (TODO 5.38). The pure decisions only: no database and no screen here, so they
 * are tested on their own; {@code util.ClockOffer} asks the question and does the clocking.
 */
public final class ClockOfferRules {

    private ClockOfferRules() {
    }

    /**
     * At sign-in: a member of the staff who is not clocked in is asked once. The owner's account (administrator) is not asked: it
     * does not work a counter shift by default and clocks in with the sidebar button if it wants to. Nobody is asked twice, because
     * the question is only put at the moment of signing in (or of another cashier taking over a locked till).
     */
    public static boolean offerClockIn(Role role, boolean clockedIn) {
        return role == Role.EMPLOYEE && !clockedIn;
    }

    /**
     * At sign-out and at the idle sign-out: anybody who is still clocked in is asked. A stretch that is already so long that it
     * looks forgotten ({@link TimeClockRules#looksForgotten}) is not offered: clocking out "now" would write a stretch of a day or
     * more as worked time, and the administrator closes it with the time the person really left.
     */
    public static boolean offerClockOut(TimeClockEntry open, LocalDateTime now) {
        return open != null && !TimeClockRules.looksForgotten(open.clockIn(), now);
    }
}
