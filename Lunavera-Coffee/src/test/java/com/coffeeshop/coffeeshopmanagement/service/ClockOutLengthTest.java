package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** TODO 3.76: closing a forgotten stretch cannot make a shift longer than a stretch written by hand may be. */
public class ClockOutLengthTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 12, 0);

    @Test
    public void aFortyFiveHourShiftIsRefused() {
        LocalDateTime in = NOW.minusHours(45);
        assertEquals("LONG", TimeClockRules.correctionProblem(in, NOW.minusHours(2), NOW));
    }

    @Test
    public void theLimitIsTheSameAsForAStretchWrittenByHand() {
        LocalDateTime in = NOW.minusHours(40);
        assertNull(TimeClockRules.correctionProblem(in, in.plusHours(TimeClockRules.MAX_STRETCH_HOURS), NOW));
        assertEquals("LONG", TimeClockRules.correctionProblem(in, in.plusHours(TimeClockRules.MAX_STRETCH_HOURS).plusMinutes(1), NOW));
        assertEquals(TimeClockRules.stretchProblem(in, in.plusHours(25), NOW), TimeClockRules.correctionProblem(in, in.plusHours(25), NOW));
    }

    @Test
    public void theOtherChecksStillComeFirst() {
        LocalDateTime in = NOW.minusHours(5);
        assertEquals("TIME", TimeClockRules.correctionProblem(in, null, NOW));
        assertEquals("BEFORE", TimeClockRules.correctionProblem(in, in, NOW));
        assertEquals("FUTURE", TimeClockRules.correctionProblem(in, NOW.plusMinutes(1), NOW));
        assertNull(TimeClockRules.correctionProblem(in, NOW.minusHours(1), NOW));
    }
}
