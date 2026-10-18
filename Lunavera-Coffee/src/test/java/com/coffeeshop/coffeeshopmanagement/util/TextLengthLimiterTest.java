package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 6.35: the length rule that {@link TextLengthLimiter#limit} puts on a field (the field itself needs a JavaFX toolkit). */
public class TextLengthLimiterTest {

    @Test
    public void textUpToTheLimitIsAcceptedAndOneMoreIsNot() {
        assertTrue(TextLengthLimiter.accepts("", 5));
        assertTrue(TextLengthLimiter.accepts("abcde", 5));
        assertFalse(TextLengthLimiter.accepts("abcdef", 5));
    }

    @Test
    public void aPastedParagraphIsRefusedAsAWhole() {
        String paragraph = "x".repeat(TextLengthLimiter.NAME_MAX + 1);
        assertFalse(TextLengthLimiter.accepts(paragraph, TextLengthLimiter.NAME_MAX));
        assertTrue(TextLengthLimiter.accepts(paragraph.substring(1), TextLengthLimiter.NAME_MAX));
    }

    @Test
    public void nullTextIsNotTooLong() {
        assertTrue(TextLengthLimiter.accepts(null, 3));
    }

    @Test
    public void theLimitsAreSaneForWhatTheyGuard() {
        assertTrue(TextLengthLimiter.PHONE_MAX >= 13);               // +84 and ten digits
        assertTrue(TextLengthLimiter.PASSWORD_MAX >= 20);
        assertTrue(TextLengthLimiter.USERNAME_MAX < TextLengthLimiter.NAME_MAX);
        assertTrue(TextLengthLimiter.NAME_MAX < TextLengthLimiter.DESCRIPTION_MAX);
    }
}
