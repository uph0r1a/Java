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
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Keeps {@code TODO.md} and {@code COMPLETE.md} telling the truth about each other (the audit "marked completed but not started", kept as a
 * test so a later edit cannot undo it): every item in COMPLETE is ticked and has a number of its own; TODO holds only open ({@code [ ]}) and
 * done-in-code ({@code [~]}) items, none of which is also in COMPLETE; and a ticked item that says "Still open" or "NOT done" about
 * its own feature must carry a dated check note or a DONE note after it, so the old sentence cannot be mistaken for the current state.
 * Three more checks keep the habits of TODO 6.8 and 6.32 honest: the counts in the first paragraph of {@code TODO.md} are the real
 * ones, a new session in {@code progress.md} is numbered higher than the one before it (except the numbers that were reused when
 * branches were merged), and every test that item 6.32 names exists. The pull request template carries the two boxes that ask for
 * the same habits.
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

    // ------------------------------------------------------------------ the counts in the first paragraph of TODO.md (TODO 6.8)

    /** The numbers the first paragraph of TODO.md claims, and the numbers the file really has. */
    @Test
    public void theCountsInTheFirstParagraphOfTodoAreTheRealOnes() throws IOException {
        List<String> lines = Files.readAllLines(Paths.get("TODO.md"), StandardCharsets.UTF_8);
        String paragraph = lines.stream().filter(l -> l.contains("boxes and items")).findFirst().orElse("");
        Matcher claim = Pattern.compile("\\*\\*(\\d+) boxes and items\\*\\*.*?(\\d+) human checks in section 0, (\\d+) boxes in section 1, "
                + "(\\d+) numbered items in sections 2A to 6 and (\\d+) ideas in section 7.*?\\*\\*(\\d+) items marked `\\[~\\]`\\*\\*").matcher(paragraph);
        assertTrue("the first paragraph of TODO.md must say \"N boxes and items (A human checks in section 0, B boxes in section 1, "
                + "C numbered items in sections 2A to 6 and D ideas in section 7 ...), plus N items marked `[~]`\"", claim.find());

        Pattern box = Pattern.compile("^\\s*- \\[ \\]");
        int section0 = 0, section1 = 0, section7 = 0, numbered = 0, doneInCode = 0;
        String section = "";
        for (String line : lines) {
            Matcher s = SECTION.matcher(line);
            if (s.find()) section = s.group(1);
            Matcher item = ITEM.matcher(line);
            if (item.matches()) {
                if (item.group(2).equals("~")) doneInCode++;
                else if (item.group(2).equals(" ")) numbered++;
            } else if (box.matcher(line).find()) {
                switch (section) {
                    case "0" -> section0++;
                    case "1" -> section1++;
                    case "7" -> section7++;
                    default -> { }
                }
            }
        }
        int total = Integer.parseInt(claim.group(1));
        assertEquals("human checks in section 0", Integer.parseInt(claim.group(2)), section0);
        assertEquals("boxes in section 1", Integer.parseInt(claim.group(3)), section1);
        assertEquals("numbered open items in sections 2A to 6", Integer.parseInt(claim.group(4)), numbered);
        assertEquals("ideas in section 7", Integer.parseInt(claim.group(5)), section7);
        assertEquals("the parts add up to the total the paragraph names", total, section0 + section1 + numbered + section7);
        assertEquals("items marked [~]", Integer.parseInt(claim.group(6)), doneInCode);
    }

    // ------------------------------------------------------------------ session numbers in progress.md only go up (TODO 6.8)

    /**
     * Session numbers repeat across the branches that were merged, and these were reused on purpose: 156, 157 and 158 (the three
     * sections kept before 176), 179 (three sections) and 242 and 243 (several sections after a merge). Nothing else may be out of
     * order: a new session takes a number higher than every session before it, or two notes will point at the same "Session N".
     */
    private static final Set<Integer> REUSED_SESSION_NUMBERS = Set.of(156, 157, 158, 179, 242, 243);

    @Test
    public void newSessionNumbersInProgressOnlyGoUp() throws IOException {
        Pattern heading = Pattern.compile("^##\\s+Session\\s+(\\d+)\\b");
        int highest = -1;
        int sessions = 0;
        List<String> outOfOrder = new ArrayList<>();
        for (String line : Files.readAllLines(Paths.get("progress.md"), StandardCharsets.UTF_8)) {
            Matcher m = heading.matcher(line);
            if (!m.find()) continue;
            sessions++;
            int number = Integer.parseInt(m.group(1));
            if (REUSED_SESSION_NUMBERS.contains(number)) continue;
            if (number <= highest) outOfOrder.add("Session " + number + " comes after Session " + highest);
            highest = Math.max(highest, number);
        }
        assertTrue("progress.md has its sessions", sessions > 10);
        assertTrue("session numbers must go up (the only numbers that may repeat or come late are "
                + REUSED_SESSION_NUMBERS + "): " + outOfOrder, outOfOrder.isEmpty());
    }

    // ------------------------------------------------------------------ the tests that item 6.32 names exist (TODO 6.32)

    @Test
    public void everyTestThatItem632NamesExists() throws IOException {
        List<String> text = new ArrayList<>();
        boolean inItem = false;
        String section = "";
        for (String line : Files.readAllLines(Paths.get("TODO.md"), StandardCharsets.UTF_8)) {
            Matcher s = SECTION.matcher(line);
            if (s.find()) {
                section = s.group(1);
                inItem = false;
            }
            Matcher m = ITEM.matcher(line);
            if (m.matches()) inItem = section.equals("6") && line.trim().startsWith("32.");
            if (inItem) text.add(line);
        }
        assertTrue("item 6.32 is in TODO.md", !text.isEmpty());

        Path testRoot = Path.of("src", "test", "java");
        Map<String, Path> classes = new HashMap<>();
        try (Stream<Path> walk = Files.walk(testRoot)) {
            walk.filter(p -> p.getFileName().toString().endsWith("Test.java"))
                    .forEach(p -> classes.put(p.getFileName().toString().replace(".java", ""), p));
        }
        Matcher named = Pattern.compile("`([A-Z]\\w*Test)(?:\\.(\\w+))?`").matcher(String.join("\n", text));
        List<String> missing = new ArrayList<>();
        int checked = 0;
        while (named.find()) {
            checked++;
            Path file = classes.get(named.group(1));
            if (file == null) {
                missing.add(named.group(1) + " (no such test class)");
            } else if (named.group(2) != null
                    && !Files.readString(file, StandardCharsets.UTF_8).contains("void " + named.group(2) + "(")) {
                missing.add(named.group(1) + "." + named.group(2) + " (no such test method)");
            }
        }
        assertTrue("item 6.32 names tests", checked >= 10);
        assertTrue("item 6.32 names tests that do not exist (rename them in the item, or restore the test): " + missing, missing.isEmpty());
    }

    // ------------------------------------------------------------------ the pull request template asks for the same habits

    @Test
    public void thePullRequestTemplateAsksForARegressionTestAndForUpdatedNotes() throws IOException {
        String template = Files.readString(Path.of(".github", "pull_request_template.md"), StandardCharsets.UTF_8);
        assertTrue("a box for the test that fails without the fix (TODO 6.32)", template.contains("fails without the fix"));
        assertTrue("a box for the notes (TODO 6.8)", template.contains("`progress.md`") && template.contains("notes are updated"));
    }
}
