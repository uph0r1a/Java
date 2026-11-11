package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * Keeps {@code TODO.md} and {@code COMPLETE.md} telling the truth about each other (the audit "marked completed but not started", kept as a
 * test so a later edit cannot undo it): every item in COMPLETE is ticked and has a number of its own; TODO holds only open ({@code [ ]}) and
 * done-in-code ({@code [~]}) items, none of which is also in COMPLETE; and a ticked item that says "Still open" or "NOT done" about
 * its own feature must carry a dated check note or a DONE note after it, so the old sentence cannot be mistaken for the current state.
 */
public class NotesConsistencyTest {

    private static final Pattern SECTION = Pattern.compile("^##\\s*(\\d+A?)\\.");
    private static final Pattern ITEM = Pattern.compile("^\\s*(\\d+)\\.\\s+\\[( |x|X|~)\\]\\s+(.*)$");

    private record Item(String key, String box, String text) {
    }

    private static List<Item> items(String file) throws IOException {
        List<Item> found = new ArrayList<>();
        String section = "";
        for (String line : Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)) {
            Matcher s = SECTION.matcher(line);
            if (s.find()) section = s.group(1);
            Matcher m = ITEM.matcher(line);
            if (m.matches()) found.add(new Item(section + "." + m.group(1), m.group(2), m.group(3)));
        }
        return found;
    }

    @Test
    public void everyItemInCompleteIsTickedAndNumberedOnce() throws IOException {
        Map<String, Integer> seen = new HashMap<>();
        List<String> unticked = new ArrayList<>();
        for (Item item : items("COMPLETE.md")) {
            seen.merge(item.key(), 1, Integer::sum);
            if (!item.box().equalsIgnoreCase("x")) unticked.add(item.key());
        }
        List<String> twice = new ArrayList<>();
        seen.forEach((k, n) -> { if (n > 1) twice.add(k); });
        assertTrue("items in COMPLETE.md that are not ticked: " + unticked, unticked.isEmpty());
        assertTrue("item numbers that appear twice in COMPLETE.md: " + twice, twice.isEmpty());
        assertTrue("COMPLETE.md has its items", seen.size() > 300);
    }

    @Test
    public void todoHoldsOnlyOpenAndDoneInCodeItemsAndNoneThatIsAlsoInComplete() throws IOException {
        Set<String> complete = new HashSet<>();
        for (Item item : items("COMPLETE.md")) complete.add(item.key());
        List<String> ticked = new ArrayList<>();
        List<String> both = new ArrayList<>();
        for (Item item : items("TODO.md")) {
            if (item.box().equalsIgnoreCase("x")) ticked.add(item.key());
            if (complete.contains(item.key())) both.add(item.key());
        }
        assertTrue("ticked items still in TODO.md (they belong in COMPLETE.md): " + ticked, ticked.isEmpty());
        assertTrue("items that are in both files: " + both, both.isEmpty());
    }

    @Test
    public void aTickedItemThatSaysStillOpenOrNotDoneAlsoSaysWhatIsTrueToday() throws IOException {
        List<String> unanswered = new ArrayList<>();
        for (Item item : items("COMPLETE.md")) {
            String text = item.text();
            if (!(text.contains("**Still open") || text.contains("NOT done") || text.contains("Not started"))) continue;
            boolean answered = text.contains("Check 20") || text.contains("DONE") || text.contains("CLOSED");
            if (!answered) unanswered.add(item.key());
        }
        assertTrue("these ticked items say \"Still open\", \"NOT done\" or \"Not started\" and have no dated check note or DONE note after it; "
                + "look in the code, then add \"(Check <date>: ...)\" saying what is true today: " + unanswered, unanswered.isEmpty());
    }

    @Test
    public void everyNumberedItemOfTodoHasABoxAndAShortTitle() throws IOException {
        for (Item item : items("TODO.md")) {
            assertTrue(item.key() + " starts with a bold title", item.text().startsWith("**"));
        }
        assertTrue(Files.exists(Path.of("TODO.md")) && Files.exists(Path.of("COMPLETE.md")));
    }
}
