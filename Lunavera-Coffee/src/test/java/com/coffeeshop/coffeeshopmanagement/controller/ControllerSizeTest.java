package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 6.54: a size ceiling for EVERY class in {@code controller/}, not only the five admin screens
 * ({@code AdminScreensTest.noControllerIsAMonster}). Reads the sources as text, so it needs no JavaFX and no database.
 *
 * <p>The ceilings can only go down. A file may not grow past its ceiling (growth fails the build early, while the change is
 * small); a file that has shrunk by more than {@link #SLACK} lines must have its ceiling lowered (so a refactor keeps what it
 * gained); a file with no entry gets {@link #DEFAULT_CEILING}. To lower a number, edit {@link #CEILINGS}. Never raise one: split
 * the class instead.
 */
public class ControllerSizeTest {

    private static final Path CONTROLLERS = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller");

    /** Lines a file may be below its ceiling before the test asks for the ceiling to be lowered. */
    static final int SLACK = 30;

    /** The ceiling of a class that has no entry below (every small dialog and every new class). */
    static final int DEFAULT_CEILING = 400;

    /** Ceilings of the big classes, in lines, as of 2026-10-27 (the line count of the file that day). They only go down. */
    static final Map<String, Integer> CEILINGS = Map.of(
            "OrderController", 1738,
            "ProductController", 824,
            "AdminDashboardController", 651,
            "AccountController", 508,
            "ReportWindow", 472,
            "StaffController", 466,
            "OrderHistoryWindow", 435,
            "CustomerController", 421);

    /** The hard rule that no entry may be above, whatever the map says (the admin-screen rule of 6.4, now for all but one). */
    static final int NO_ENTRY_ABOVE = 900;

    /** The one class allowed above {@link #NO_ENTRY_ABOVE}, until the till screen is split (TODO 6.54). */
    static final String THE_BIGGEST = "OrderController";

    private static Map<String, Integer> lineCounts() throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        try (Stream<Path> files = Files.list(CONTROLLERS)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.getFileName().toString().endsWith(".java"))::iterator) {
                String name = file.getFileName().toString();
                counts.put(name.substring(0, name.length() - ".java".length()),
                        (int) Files.readString(file, StandardCharsets.UTF_8).lines().count());
            }
        }
        return counts;
    }

    static int ceilingOf(String className) {
        return CEILINGS.getOrDefault(className, DEFAULT_CEILING);
    }

    @Test
    public void everyControllerIsWithinItsCeiling() throws IOException {
        List<String> tooBig = new ArrayList<>();
        for (Map.Entry<String, Integer> e : lineCounts().entrySet()) {
            int ceiling = ceilingOf(e.getKey());
            if (e.getValue() > ceiling) {
                tooBig.add(e.getKey() + " has " + e.getValue() + " lines, ceiling " + ceiling
                        + (CEILINGS.containsKey(e.getKey()) ? "" : " (the default; a new or small class)")
                        + ": split it, do not raise the number");
            }
        }
        assertTrue(String.join("; ", tooBig), tooBig.isEmpty());
    }

    @Test
    public void aCeilingThatIsTooLooseMustBeLowered() throws IOException {
        Map<String, Integer> counts = lineCounts();
        List<String> loose = new ArrayList<>();
        for (Map.Entry<String, Integer> e : CEILINGS.entrySet()) {
            Integer lines = counts.get(e.getKey());
            if (lines != null && lines < e.getValue() - SLACK) {
                loose.add(e.getKey() + " is down to " + lines + " lines: lower its ceiling from " + e.getValue() + " to " + lines);
            }
        }
        assertTrue(String.join("; ", loose), loose.isEmpty());
    }

    @Test
    public void everyCeilingBelongsToAClassThatStillExists() throws IOException {
        Map<String, Integer> counts = lineCounts();
        for (String name : CEILINGS.keySet()) {
            assertTrue("CEILINGS names " + name + " but controller/" + name + ".java does not exist (renamed or deleted: remove the entry)",
                    counts.containsKey(name));
        }
    }

    @Test
    public void onlyTheTillScreenMayHaveACeilingOverNineHundred() {
        for (Map.Entry<String, Integer> e : CEILINGS.entrySet()) {
            if (!e.getKey().equals(THE_BIGGEST)) {
                assertTrue(e.getKey() + " has a ceiling of " + e.getValue() + ", over " + NO_ENTRY_ABOVE,
                        e.getValue() <= NO_ENTRY_ABOVE);
            }
        }
        assertTrue(DEFAULT_CEILING <= NO_ENTRY_ABOVE);
    }

    @Test
    public void everyControllerFileIsCounted() throws IOException {
        assertTrue("the controller folder is empty or the test runs from the wrong folder", lineCounts().size() > 10);
        assertTrue(lineCounts().containsKey("OrderController"));
    }
}
