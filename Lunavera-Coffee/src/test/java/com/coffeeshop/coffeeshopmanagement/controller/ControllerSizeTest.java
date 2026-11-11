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
 * the class instead. Since TODO 6.54 is finished no class, the till screen included, may be over {@link #NO_ENTRY_ABOVE} lines.
 */
public class ControllerSizeTest {

    private static final Path CONTROLLERS = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller");

    /** Lines a file may be below its ceiling before the test asks for the ceiling to be lowered. */
    static final int SLACK = 30;

    /** The ceiling of a class that has no entry below (every small dialog and every new class). */
    static final int DEFAULT_CEILING = 400;

    /**
     * Ceilings of the big classes, in lines, as of 2026-10-27 (the line count of the file that day). They only go down. TODO 2.86: the
     * ceilings of {@code CustomerController} (421 then, 485 for a while) and {@code ReportWindow} (472) were once set up to the size the
     * files had grown to; the code was then moved out (CustomerMergeButtons, ReportCsv, ReportTexts) and both are lowered to the new size.
     */
    static final Map<String, Integer> CEILINGS = Map.of(
            "OrderController", 877,
            "ProductController", 720,
            "AdminDashboardController", 569,
            "AccountController", 507,
            "ReportWindow", 406,
            "StaffController", 466,
            "OrderHistoryWindow", 415,
            "CustomerController", 339);

    /** The hard rule that no entry may be above, whatever the map says (the admin-screen rule of 6.4, now for every controller; TODO 6.54). */
    static final int NO_ENTRY_ABOVE = 900;

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
    public void noCeilingIsOverNineHundredNotEvenTheTillScreens() {
        for (Map.Entry<String, Integer> e : CEILINGS.entrySet()) {
            assertTrue(e.getKey() + " has a ceiling of " + e.getValue() + ", over " + NO_ENTRY_ABOVE, e.getValue() <= NO_ENTRY_ABOVE);
        }
        assertTrue(DEFAULT_CEILING <= NO_ENTRY_ABOVE);
    }

    @Test
    public void everyControllerFileIsCounted() throws IOException {
        assertTrue("the controller folder is empty or the test runs from the wrong folder", lineCounts().size() > 10);
        assertTrue(lineCounts().containsKey("OrderController"));
    }
}
