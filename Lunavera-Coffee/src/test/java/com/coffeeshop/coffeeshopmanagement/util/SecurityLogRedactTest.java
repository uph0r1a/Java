package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** TODO 2A.39: a text typed into the user-name box is logged by its length and at most its first two characters. */
public class SecurityLogRedactTest {

    @Test
    public void aLongTextShowsOnlyItsFirstTwoCharactersAndItsLength() {
        assertEquals("'Qu...' (14 characters)", SecurityLog.redactTyped("Quan-Cafe#2026"));
    }

    @Test
    public void aShortTextShowsOnlyItsLength() {
        assertEquals("(4 characters)", SecurityLog.redactTyped("abcd"));
        assertEquals("(0 characters)", SecurityLog.redactTyped("   "));
        assertEquals("(nothing) (0 characters)", SecurityLog.redactTyped(null));
    }

    @Test
    public void spacesAroundTheTextAreNotCounted() {
        assertEquals("'ab...' (5 characters)", SecurityLog.redactTyped("  abcde  "));
    }

    @Test
    public void accentedLettersAndEmojiCountAsOneCharacterEach() {
        assertEquals("'Ng...' (7 characters)", SecurityLog.redactTyped("Nguyễn1"));
        String emoji = "\uD83D\uDE00\uD83D\uDE00abc"; // two emoji (a surrogate pair each) and three letters
        assertEquals("'\uD83D\uDE00\uD83D\uDE00...' (5 characters)", SecurityLog.redactTyped(emoji));
    }

    @Test
    public void lineBreaksAndControlCharactersInTheShownPartAreReplaced() {
        assertEquals("'a ...' (7 characters)", SecurityLog.redactTyped("a\nbcdef"));
        assertEquals("'a ...' (6 characters)", SecurityLog.redactTyped("a\u2028bcde"));
    }

    @Test
    public void cleanReplacesEveryLineSeparatorAndControlCharacter() {
        // TODO 2A.41: U+2028, U+2029, U+0085, vertical tab, form feed and ESC must not survive
        assertEquals("a b c d e f g h", SecurityLog.clean("a\u2028b\u2029c\u0085d\u000Be\u000Cf\u001Bg\nh"));
        for (char c : "\r\n\t\u000B\u000C\u001B\u0085\u2028\u2029\u007F".toCharArray()) {
            String cleaned = SecurityLog.clean("a" + c + "b");
            assertEquals("a b", cleaned);
        }
        assertEquals("", SecurityLog.clean(null));
        assertEquals("kept at 40 characters", 43, SecurityLog.clean("x".repeat(100)).length());
        assertEquals("a normal Vietnamese name is untouched", "Nguyễn Văn A", SecurityLog.clean("Nguyễn Văn A"));
    }
}
