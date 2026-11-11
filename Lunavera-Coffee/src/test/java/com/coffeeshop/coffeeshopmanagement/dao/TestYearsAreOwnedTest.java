package com.coffeeshop.coffeeshopmanagement.dao;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 6.77: every database test class runs against the one database of the run, so a test that counts rows over a range of days
 * must be the only one that wrote on them. Several classes say so in a comment ("in 2037 (no other test writes there)"); this test
 * holds them to it. Reads the test sources as text (no database): a year that a comment gives to one class must not appear as a
 * date (a {@code LocalDate.of}, a {@code "2037-03-05"} or a {@code "03/2037"}) in any other class. Two classes claiming 2032 at
 * the same time, and a third writing in it, is what this found; {@link TestDatabaseSupport#freshDays} now hands out days that are
 * nobody else's.
 */
public class TestYearsAreOwnedTest {

    private static final Path TESTS = Path.of("src", "test", "java");
    private static final Pattern YEAR = Pattern.compile("\\b(?:19|20|21|22|23|24|25|26|27|28|29)\\d\\d\\b");
    /** A year used as a date: {@code LocalDate.of(2037,}, {@code LocalDateTime.of(2037,}, {@code 2037-03-05}, {@code 03/2037}. */
    private static final String DATE_CONTEXT = "(?:Local(?:Date|DateTime)\\.of\\(\\s*%1$s\\b|\\b%1$s-\\d{2}|\\b\\d{1,2}/%1$s\\b)";

    private static List<Path> tests() throws IOException {
        try (Stream<Path> walk = Files.walk(TESTS)) {
            return walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    /** Years a class gives to itself: the years in a sentence that says no other test uses or writes there. */
    static List<String> claimedYears(String source) {
        List<String> years = new ArrayList<>();
        // comment text only, in one line, so a sentence that was wrapped over two comment lines is one sentence
        String comments = source.lines().map(String::strip)
                .filter(l -> l.startsWith("*") || l.startsWith("//") || l.startsWith("/*"))
                .map(l -> l.replaceFirst("^(/\\*+|\\*+/?|//)\\s?", ""))
                .reduce("", (a, b) -> a + " " + b);
        for (String sentence : comments.split("(?<=[.;])\\s+")) {
            String lower = sentence.toLowerCase();
            int claim = lower.indexOf("no other test");
            // "no other test in this class" is about the days inside one class, not a year of its own; the years that come AFTER the
            // phrase are history ("EdgeCasesTest used March 20 2031"), not part of the claim
            if (claim < 0 || lower.contains("in this class")) continue;
            Matcher y = YEAR.matcher(sentence.substring(0, claim));
            while (y.find()) years.add(y.group());
        }
        return years;
    }

    @Test
    public void aYearThatOneClaimsAsItsOwnIsUsedByNoOtherTestClass() throws IOException {
        Map<String, String> owner = new TreeMap<>();   // year -> the class that claims it
        List<String> problems = new ArrayList<>();
        Map<String, String> source = new TreeMap<>();  // class -> text
        for (Path file : tests()) {
            source.put(file.getFileName().toString().replace(".java", ""), Files.readString(file, StandardCharsets.UTF_8));
        }
        for (Map.Entry<String, String> entry : source.entrySet()) {
            if (entry.getKey().equals("TestYearsAreOwnedTest")) continue; // its examples are not claims
            for (String year : new TreeSet<>(claimedYears(entry.getValue()))) {
                String other = owner.putIfAbsent(year, entry.getKey());
                if (other != null && !other.equals(entry.getKey())) {
                    problems.add(year + " is claimed as private by both " + other + " and " + entry.getKey());
                }
            }
        }
        for (Map.Entry<String, String> claim : owner.entrySet()) {
            Pattern date = Pattern.compile(String.format(java.util.Locale.ROOT, DATE_CONTEXT, claim.getKey()));
            for (Map.Entry<String, String> entry : source.entrySet()) {
                if (entry.getKey().equals(claim.getValue()) || entry.getKey().equals("TestYearsAreOwnedTest")) continue;
                if (date.matcher(entry.getValue()).find()) {
                    problems.add(entry.getKey() + " uses a date in " + claim.getKey() + ", which " + claim.getValue() + " claims as its own");
                }
            }
        }
        assertTrue("Tests that share days of the one test database (use TestDatabaseSupport.freshDays, or pick a year no test uses):\n  "
                + String.join("\n  ", problems), problems.isEmpty());
    }

    @Test
    public void theClaimsAreFoundInTheCommentsTheProjectHas() throws IOException {
        // the sentences as they are written in the sources today, so the finder cannot quietly stop finding them
        assertEquals(List.of("2037"), claimedYears("/**\n * Rows are made by hand with dates in 2037 (no other test writes there: EdgeCasesTest used March 20).\n */"));
        assertEquals(List.of("2043"), claimedYears("/**\n * <p>The year 2043 is used by no other test, so\n * the counts here are not disturbed.\n */"));
        assertEquals("a year in a sentence without the claim is not a claim", List.of(), claimedYears("// the sale is on 2031-03-03.\n"));
        assertEquals("code is not read", List.of(), claimedYears("int x = 2031; // no other test\n"));
        int found = 0;
        for (Path file : tests()) {
            if (!claimedYears(Files.readString(file, StandardCharsets.UTF_8)).isEmpty()) found++;
        }
        assertTrue("at least the five classes that say so (2032, 2037, 2038, 2043 and the one for 2035), found " + found, found >= 5);
    }

    @Test
    public void theDaysHandedOutAreNobodysElse() throws IOException {
        int latest = 0;
        for (Path file : tests()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher year = Pattern.compile("(?:Local(?:Date|DateTime)\\.of\\(\\s*|\")(\\d{4})\\b").matcher(text);
            while (year.find()) {
                int y = Integer.parseInt(year.group(1));
                if (y < 2900 && y > latest) latest = y;
            }
        }
        assertTrue("fresh days start in the year 3000, after the latest year a test writes by hand (" + latest + ")", latest < 3000);
    }
}
