package com.coffeeshop.coffeeshopmanagement.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Which passwords are too easy to guess (TODO 2A.47). Pure and without a screen, so it is tested on its own; {@link ValidationUtil#isValidPassword}
 * adds the length and the letter-and-digit rules. A password is easy when it is
 * <ul>
 *   <li>on the list of the commonest passwords ({@code passwords/common-passwords.txt}, about 4,000 lines, compared in lower case),</li>
 *   <li>made of fewer than {@value #MIN_DISTINCT} different characters ({@code aaaaaaaa1}, {@code abababab1}),</li>
 *   <li>a run along a keyboard row with a few extra characters ({@code qwertyuiop1}, {@code 1q2w3e4r5t}, {@code asdf1234}), or</li>
 *   <li>one of the words everybody tries with a year or a digit behind it ({@code letmein7777}, {@code admin2027!}, {@code caphe2030}).</li>
 * </ul>
 * It never throws: if the list cannot be read, the built-in short list is used and the structural rules still apply.
 */
public final class PasswordStrength {

    private static final Logger LOGGER = Logger.getLogger(PasswordStrength.class.getName());

    static final String RESOURCE = "/passwords/common-passwords.txt";
    static final int MIN_DISTINCT = 4;
    /** A keyboard run is only looked for in a password shorter than this; a long one with a run inside is a different matter. */
    static final int RUN_MAX_LENGTH = 12;
    static final int RUN_MIN_LETTERS = 4;
    /** After a common word is taken out, at most this many letters may be left for the password to count as that word with a tail. */
    static final int WORD_TAIL_LETTERS = 2;

    /** Used when the resource cannot be read (the list the program had before TODO 2A.47). */
    private static final Set<String> BUILT_IN = Set.of(
            "12345678", "123456789", "1234567890", "12345678a", "123456789a", "a12345678", "abc12345", "abcd1234",
            "password1", "password12", "password123", "matkhau1", "matkhau12", "matkhau123", "qwerty123", "qwerty12",
            "1q2w3e4r", "1qaz2wsx", "admin123", "admin1234", "iloveyou1", "lunavera1", "lunavera123",
            "caphe123", "coffee123");

    /** Rows of a keyboard (and their reverse is checked too); only the letters of a password are looked for in them. */
    private static final List<String> KEYBOARD_ROWS = List.of(
            "qwertyuiop", "asdfghjkl", "zxcvbnm", "azertyuiop", "qazwsxedcrfvtgbyhnujmik");

    /** Words everybody tries, alone or with a year or a digit after them. Compared in lower case, 5 letters or more. */
    private static final List<String> COMMON_WORDS = List.of(
            "password", "passw0rd", "letmein", "iloveyou", "trustno1", "welcome", "qwerty", "admin", "administrator", "master",
            "monkey", "dragon", "football", "baseball", "sunshine", "princess", "superman", "shadow", "michael", "freedom",
            "whatever", "lunavera", "caphe", "coffee", "matkhau", "vietnam", "hanoi", "saigon");

    private PasswordStrength() {
    }

    /** The commonest passwords, loaded once on first use. */
    private static final class Holder {
        static final Set<String> COMMON = load();
    }

    private static Set<String> load() {
        try (InputStream in = PasswordStrength.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                LOGGER.warning("The common-password list " + RESOURCE + " is missing; the short built-in list is used.");
                return BUILT_IN;
            }
            Set<String> words = parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            words.addAll(BUILT_IN);
            return words;
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "The common-password list could not be read; the short built-in list is used.", e);
            return BUILT_IN;
        }
    }

    /** One password per line, lower case; blank lines and lines starting with {@code #} are skipped. */
    static Set<String> parse(BufferedReader reader) throws IOException {
        Set<String> words = new HashSet<>();
        String line;
        while ((line = reader.readLine()) != null) {
            String word = line.strip().toLowerCase(Locale.ROOT);
            if (!word.isEmpty() && word.charAt(0) != '#') {
                words.add(word);
            }
        }
        return words;
    }

    /** How many passwords the list holds (for a test and the log). */
    static int listSize() {
        return Holder.COMMON.size();
    }

    /** True when the password is too easy to guess; see the class comment. {@code null} is not easy (the other rules refuse it). */
    public static boolean isEasy(String password) {
        if (password == null) {
            return false;
        }
        String lower = password.toLowerCase(Locale.ROOT);
        return Holder.COMMON.contains(lower)
                || lower.contains("lunavera")
                || distinctCharacters(lower) < MIN_DISTINCT
                || isKeyboardRun(lower)
                || isCommonWordWithTail(lower);
    }

    static int distinctCharacters(String text) {
        return (int) text.codePoints().distinct().count();
    }

    /** True for a short password whose letters, read in order, are a stretch of a keyboard row (either direction). */
    static boolean isKeyboardRun(String lower) {
        if (lower.length() >= RUN_MAX_LENGTH) {
            return false;
        }
        StringBuilder letters = new StringBuilder();
        lower.chars().filter(c -> c >= 'a' && c <= 'z').forEach(c -> letters.append((char) c));
        if (letters.length() < RUN_MIN_LETTERS) {
            return false;
        }
        String forward = letters.toString();
        String backward = letters.reverse().toString();
        for (String row : KEYBOARD_ROWS) {
            if (row.contains(forward) || row.contains(backward)) {
                return true;
            }
        }
        return false;
    }

    /** True when taking a common word out leaves no more than {@value #WORD_TAIL_LETTERS} letters ({@code letmein7777}, {@code admin2027!}). */
    static boolean isCommonWordWithTail(String lower) {
        for (String word : COMMON_WORDS) {
            if (lower.contains(word)) {
                String rest = lower.replace(word, "");
                if (rest.chars().filter(Character::isLetter).count() <= WORD_TAIL_LETTERS) {
                    return true;
                }
            }
        }
        return false;
    }
}
