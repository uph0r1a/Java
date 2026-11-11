package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.model.Role;
import com.coffeeshop.coffeeshopmanagement.model.TimeClockEntry;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.*;

/** TODO 5.38: who is asked to clock in at sign-in and to clock out at sign-out. */
public class ClockOfferRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 5, 17, 0);

    private static TimeClockEntry openSince(LocalDateTime in) {
        return new TimeClockEntry(1, 7, "An", in, null);
    }

    @Test
    public void aCashierWhoIsNotClockedInIsAskedAtSignIn() {
        assertTrue(ClockOfferRules.offerClockIn(Role.EMPLOYEE, false));
    }

    @Test
    public void aCashierAlreadyClockedInIsNotAsked() {
        assertFalse(ClockOfferRules.offerClockIn(Role.EMPLOYEE, true));
    }

    @Test
    public void theAdministratorAndOtherRolesAreNotAskedToClockIn() {
        assertFalse(ClockOfferRules.offerClockIn(Role.ADMIN, false));
        assertFalse(ClockOfferRules.offerClockIn(Role.CUSTOMER, false));
        assertFalse(ClockOfferRules.offerClockIn(null, false));
    }

    @Test
    public void anyoneStillClockedInIsAskedAtSignOut() {
        assertTrue(ClockOfferRules.offerClockOut(openSince(NOW.minusHours(8)), NOW));
        assertTrue("the administrator who clocked in by hand is asked too",
                ClockOfferRules.offerClockOut(openSince(NOW.minusMinutes(5)), NOW));
    }

    @Test
    public void nobodyIsAskedToClockOutWhenNotClockedIn() {
        assertFalse(ClockOfferRules.offerClockOut(null, NOW));
    }

    @Test
    public void aStretchThatLooksForgottenIsLeftForTheAdministrator() {
        int limit = TimeClockRules.forgottenAfterHours();
        assertTrue(ClockOfferRules.offerClockOut(openSince(NOW.minusHours(limit).plusMinutes(1)), NOW));
        assertFalse("a stretch open for the whole limit would be written as worked time",
                ClockOfferRules.offerClockOut(openSince(NOW.minusHours(limit)), NOW));
        assertFalse(ClockOfferRules.offerClockOut(openSince(NOW.minusDays(2)), NOW));
    }
}
