package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.78, mutation sweep 2026-11-16 (first batch, security classes): the exact edges of the password rules. The tests so far
 * checked a clearly easy and a clearly good password; a change of {@code <} to {@code <=} at an edge went unnoticed.
 */
public class PasswordRulesEdgesTest {

    @Test
    public void theFullCommonPasswordListIsLoadedNotJustTheBuiltInOne() {
        assertTrue("the resource holds thousands of passwords, the built-in list a few dozen", PasswordStrength.listSize() > 1000);
    }

    @Test
    public void aKeyboardRunIsLookedForOnlyInAPasswordShorterThanTwelveCharacters() {
        assertTrue("eleven characters, a run of ten letters", PasswordStrength.isKeyboardRun("qwertyuiop1"));
        assertFalse("twelve characters: no longer looked at", PasswordStrength.isKeyboardRun("qwertyuiop12"));
    }

    @Test
    public void aKeyboardRunNeedsFourLetters() {
        assertTrue("exactly four letters", PasswordStrength.isKeyboardRun("qwer1"));
        assertFalse("three letters", PasswordStrength.isKeyboardRun("qwe12"));
    }

    @Test
    public void theLettersAAndZAreLettersOfARun() {
        assertTrue("a is the first letter of the home row", PasswordStrength.isKeyboardRun("asdf1"));
        assertTrue("z is the first letter of the bottom row", PasswordStrength.isKeyboardRun("zxcv1"));
    }

    @Test
    public void aPasswordNeedsFourDifferentCharacters() {
        assertFalse("four different characters are enough for that rule", PasswordStrength.isEasy("a1b2a1b2"));
        assertTrue("three different characters are not", PasswordStrength.isEasy("a1ba1ba1b"));
        assertTrue("two", PasswordStrength.isEasy("a1a1a1a1"));
        assertEquals(4, PasswordStrength.distinctCharacters("a1b2a1b2"));
    }

    @Test
    public void aCommonWordMayHaveAtMostTwoLettersBehindIt() {
        assertTrue("two letters and a digit are still that word", PasswordStrength.isCommonWordWithTail("adminab1"));
        assertFalse("three letters are a different password", PasswordStrength.isCommonWordWithTail("adminabc1"));
        assertFalse(PasswordStrength.isEasy("Zq7mKxP2"));
    }

    @Test
    public void nullIsNotEasyBecauseTheOtherRulesRefuseIt() {
        assertFalse(PasswordStrength.isEasy(null));
    }

    @Test
    public void aUsernameIsLookedForInThePasswordOnlyFromThreeCharacters() {
        assertTrue(ValidationUtil.containsUsername("xxabcxx", "abc"));
        assertFalse("two characters are too short to count", ValidationUtil.containsUsername("xxabxx", "ab"));
        assertTrue("trimmed and in any letter case", ValidationUtil.containsUsername("xxABCxx", "  abc "));
        assertFalse(ValidationUtil.containsUsername(null, "abc"));
        assertFalse(ValidationUtil.containsUsername("abc", null));
    }

    private static String ofLength(int length) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length) sb.append("Zq7mKxP2");
        return sb.substring(0, length);
    }

    @Test
    public void aPasswordIsAcceptedFromEightToOneHundredTwentyEightCharacters() {
        assertFalse("null", ValidationUtil.isValidPassword(null));
        assertFalse("seven", ValidationUtil.isValidPassword(ofLength(7)));
        assertTrue("eight", ValidationUtil.isValidPassword(ofLength(8)));
        assertTrue("the longest", ValidationUtil.isValidPassword(ofLength(ValidationUtil.MAX_PASSWORD_LENGTH)));
        assertFalse("one more", ValidationUtil.isValidPassword(ofLength(ValidationUtil.MAX_PASSWORD_LENGTH + 1)));
    }

    @Test
    public void aPasswordNeedsALetterAndADigit() {
        assertFalse("only letters", ValidationUtil.isValidPassword("ZqmKxPrT"));
        assertFalse("only digits", ValidationUtil.isValidPassword("73985241"));
        assertTrue(ValidationUtil.isValidPassword("ZqmKxP27"));
    }

    @Test
    public void aPhoneIsCleanedOfSpacesDotsDashesAndBrackets() {
        assertNull(ValidationUtil.phoneCompact(null));
        assertEquals("0901111222", ValidationUtil.phoneCompact("(0901) 111-222"));
        assertEquals("+84901111222", ValidationUtil.phoneCompact("+84 (0) 901.111.222"));
        assertEquals("84901111222", ValidationUtil.phoneCompact("84(0)901111222"));
        assertEquals("0901111222", ValidationUtil.phoneCompact("0(901)111222"));
    }
}
