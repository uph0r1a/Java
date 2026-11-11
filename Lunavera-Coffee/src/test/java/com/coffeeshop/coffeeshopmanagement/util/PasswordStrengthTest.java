package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.Assert.*;

/** TODO 2A.47: which passwords are too easy to guess. */
public class PasswordStrengthTest {

    @Test
    public void theListHoldsThousandsOfPasswordsAndLoadsFromTheResource() throws IOException {
        assertTrue("about 4,000 lines, not the 25 it had", PasswordStrength.listSize() >= 3000);
        try (InputStream in = PasswordStrength.class.getResourceAsStream(PasswordStrength.RESOURCE)) {
            assertNotNull("the resource is inside the program", in);
        }
    }

    @Test
    public void theCommonestPasswordsAreOnTheListInAnyLetterCase() {
        for (String easy : new String[]{"password123", "Password123", "PASSWORD1", "12345678a", "abc12345", "matkhau123", "1qaz2wsx", "letmein123"}) {
            assertTrue(easy, PasswordStrength.isEasy(easy));
        }
    }

    @Test
    public void oneRepeatedCharacterOrTooFewDifferentOnesIsEasy() {
        for (String easy : new String[]{"aaaaaaaa1", "11111111a", "abababab1", "a1a1a1a1a1", "abcabcabc"}) {
            assertTrue(easy, PasswordStrength.isEasy(easy));
        }
        assertEquals(2, PasswordStrength.distinctCharacters("aaaaaaaa1"));
        assertFalse("four different characters are enough for this rule", PasswordStrength.isEasy("Zq7x9mWp"));
    }

    @Test
    public void aKeyboardRunWithAFewExtraCharactersIsEasyEvenWhenItIsNotOnTheList() {
        // none of these is in the list file: the rule finds them
        for (String easy : new String[]{"Qwertyuiop9", "1q2w3e4r5t6", "asdfghjkl7", "Zxcvbnm123", "Poiuytrewq8", "qwerty4321", "azerty2027"}) {
            assertTrue(easy, PasswordStrength.isKeyboardRun(easy.toLowerCase(java.util.Locale.ROOT)));
            assertTrue(easy, PasswordStrength.isEasy(easy));
        }
    }

    @Test
    public void aKeyboardRunInsideALongerPasswordIsNotRefusedByThatRule() {
        assertFalse(PasswordStrength.isKeyboardRun("qwerty-and-then-some-words-9"));
        assertFalse(PasswordStrength.isKeyboardRun("qwe12345")); // only three letters
    }

    @Test
    public void aCommonWordWithAYearOrADigitBehindItIsEasy() {
        // again not on the list: the word rule finds them
        for (String easy : new String[]{"letmein7777", "Iloveyou2027", "admin2027!", "Caphe2030", "trustno1z", "Welcome@2027", "coffee-2028", "Matkhau#99"}) {
            assertTrue(easy, PasswordStrength.isCommonWordWithTail(easy.toLowerCase(java.util.Locale.ROOT)));
            assertTrue(easy, PasswordStrength.isEasy(easy));
        }
    }

    @Test
    public void aCommonWordInsideARealPassphraseIsLeftAlone() {
        assertFalse(PasswordStrength.isEasy("Tra-sua-9x"));
        assertFalse(PasswordStrength.isEasy("my-coffee-beans-are-roasted-7"));
        assertFalse(PasswordStrength.isEasy("Correct1"));
        assertFalse(PasswordStrength.isEasy("Horse-Battery-42-Staple"));
        assertFalse(PasswordStrength.isEasy("k7Rm2vQx9"));
    }

    @Test
    public void theShopNameIsStillRefused() {
        assertTrue(PasswordStrength.isEasy("Lunavera2026"));
        assertTrue(PasswordStrength.isEasy("my-LUNAVERA-shop-7"));
    }

    @Test
    public void nullIsNotEasyThePasswordRuleRefusesItAnyway() {
        assertFalse(PasswordStrength.isEasy(null));
        assertFalse(ValidationUtil.isValidPassword(null));
    }

    @Test
    public void theListFileIsReadOneLowerCasePasswordPerLineWithCommentsSkipped() throws IOException {
        Set<String> words = PasswordStrength.parse(new BufferedReader(new StringReader("# comment\n\nAbC12345\n  Qwerty99  \n#another\nsecond1line\n")));
        assertEquals(Set.of("abc12345", "qwerty99", "second1line"), words);
    }

    @Test
    public void everyLineOfTheListFileIsLowerCaseWithALetterAndADigitAndUsableLength() throws IOException {
        Path file = Path.of("src", "main", "resources", "passwords", "common-passwords.txt");
        int lines = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            lines++;
            assertEquals("lower case, no spaces around: " + line, line.strip().toLowerCase(java.util.Locale.ROOT), line);
            assertTrue("8 characters or more: " + line, line.length() >= 8);
            assertTrue("a letter: " + line, line.chars().anyMatch(Character::isLetter));
            assertTrue("a digit: " + line, line.chars().anyMatch(Character::isDigit));
        }
        assertTrue(lines >= 3000);
    }

    @Test
    public void theNoticesNameWhereTheListCameFrom() throws IOException {
        String notices = Files.readString(Path.of("THIRD-PARTY-NOTICES.md"));
        assertTrue(notices.contains("SecLists"));
        assertTrue(notices.contains("Open Government Licence"));
        String header = Files.readString(Path.of("src", "main", "resources", "passwords", "common-passwords.txt"));
        assertTrue(header.startsWith("#"));
        assertTrue(header.contains("Daniel Miessler"));
    }
}
