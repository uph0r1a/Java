package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 6.67: {@code docs/SCREEN_TOUR.md} is the list a person walks through to open every window once in both languages, the check
 * that a compiler and unit tests cannot make. Reads the sources as text (no JavaFX, no display). It fails when a screen (FXML) or a
 * window class is added and not put on the list, and when the list names one that is gone, so the tour stays complete.
 */
public class ScreenTourTest {

    private static final Path MAIN = Path.of("src", "main");
    private static final Path CONTROLLERS = MAIN.resolve("java/com/coffeeshop/coffeeshopmanagement/controller");
    private static final Path FXML = MAIN.resolve("resources/fxml");
    private static final Pattern WINDOW_CLASS = Pattern.compile(".*(Dialog|Dialogs|Window|Wizard|Panel|Pane)|LockScreen");
    private static final Pattern TOKEN = Pattern.compile("`([A-Za-z0-9_-]+(?:\\.fxml)?)`");

    private static String tour() throws IOException {
        return Files.readString(Path.of("docs", "SCREEN_TOUR.md"), StandardCharsets.UTF_8);
    }

    private static Set<String> names(Path dir, String extension) throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(extension))
                    .forEach(n -> names.add(n.substring(0, n.length() - extension.length())));
        }
        return names;
    }

    @Test
    public void everyScreenIsOnTheTour() throws IOException {
        String tour = tour();
        for (String screen : names(FXML, ".fxml")) {
            assertTrue("docs/SCREEN_TOUR.md does not list the screen " + screen + ".fxml", tour.contains("`" + screen + ".fxml`"));
        }
    }

    @Test
    public void everyWindowClassIsOnTheTour() throws IOException {
        String tour = tour();
        int count = 0;
        for (String name : names(CONTROLLERS, ".java")) {
            if (!WINDOW_CLASS.matcher(name).matches()) continue;
            count++;
            assertTrue("docs/SCREEN_TOUR.md does not list the window class " + name, tour.contains("`" + name + "`"));
        }
        assertTrue("the pattern that finds window classes found almost none", count >= 40);
    }

    @Test
    public void theTourNamesNothingThatIsGone() throws IOException {
        Set<String> screens = names(FXML, ".fxml");
        Set<String> classes = names(CONTROLLERS, ".java");
        Matcher token = TOKEN.matcher(tour());
        int checked = 0;
        while (token.find()) {
            String name = token.group(1);
            if (name.endsWith(".fxml")) {
                checked++;
                assertTrue("the tour lists a screen that does not exist: " + name, screens.contains(name.substring(0, name.length() - 5)));
            } else if (WINDOW_CLASS.matcher(name).matches() || name.endsWith("Controller")) {
                checked++;
                assertTrue("the tour lists a class that does not exist: " + name, classes.contains(name));
            }
        }
        assertTrue(checked >= 60);
    }

    @Test
    public void theTourAsksForBothLanguagesAndEveryRowCanBeTicked() throws IOException {
        String tour = tour();
        assertTrue(tour.contains("in Vietnamese, then switch the language"));
        long rows = tour.lines().filter(l -> l.startsWith("| [ ] |")).count();
        assertEquals("ten screens and forty-five windows", 55, rows);
    }
}
