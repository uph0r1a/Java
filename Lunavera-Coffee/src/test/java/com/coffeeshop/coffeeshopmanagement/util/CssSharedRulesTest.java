package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 6.29: a rule that two screens share lives once, in a {@code common*.css} sheet that the screens load before their own sheet
 * (the screen's own sheet can still override it). Reads the files as text, so it needs no JavaFX.
 * <ul>
 *   <li>no rule (same selectors, same declarations) is written in two sheets, apart from the ones listed below;</li>
 *   <li>a screen lists its {@code common*.css} sheets before its own, because a later sheet wins when two rules are equally specific;</li>
 *   <li>a shared sheet is loaded by at least two screens, and every sheet is loaded by some screen.</li>
 * </ul>
 */
public class CssSharedRulesTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path CSS = RESOURCES.resolve("css");
    private static final Path FXML = RESOURCES.resolve("fxml");

    /**
     * Rules that are the same in two sheets on purpose, by their selector. {@code .search-field:focused} is written in the category and
     * the account sheet: the two sheets order it differently against {@code .search-field:hover} (both set the border colour, and a search
     * box can be hovered and focused at once), so moving it to the shared sheet would change what one of the two screens shows.
     */
    static final Set<String> KNOWN_DUPLICATES = new TreeSet<>(Arrays.asList(".search-field:focused"));

    private static final Pattern RULE = Pattern.compile("([^{}]+)\\{([^{}]*)\\}");
    private static final Pattern SHEET = Pattern.compile("@\\.\\./css/([\\w.-]+\\.css)");

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static List<Path> files(Path dir, String extension) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> list = new ArrayList<>();
            s.filter(p -> p.toString().endsWith(extension)).sorted().forEach(list::add);
            return list;
        }
    }

    /** Every rule of a sheet as "selectors{declarations}", with spacing and declaration order made the same. */
    static List<String> rulesOf(String css) {
        List<String> rules = new ArrayList<>();
        Matcher m = RULE.matcher(css.replaceAll("(?s)/\\*.*?\\*/", ""));
        while (m.find()) {
            List<String> selectors = new ArrayList<>();
            for (String s : m.group(1).split(",")) {
                String clean = s.trim().replaceAll("\\s+", " ");
                if (!clean.isEmpty()) selectors.add(clean);
            }
            List<String> declarations = new ArrayList<>();
            for (String d : m.group(2).split(";")) {
                String clean = d.trim().replaceAll("\\s+", " ");
                if (!clean.isEmpty()) declarations.add(clean);
            }
            declarations.sort(null);
            rules.add(String.join(",", selectors) + "{" + String.join(";", declarations) + "}");
        }
        return rules;
    }

    private static String selectorsOf(String rule) {
        return rule.substring(0, rule.indexOf('{'));
    }

    @Test
    public void noRuleIsWrittenInTwoSheets() throws IOException {
        Map<String, Set<String>> where = new TreeMap<>();
        for (Path sheet : files(CSS, ".css")) {
            for (String rule : rulesOf(read(sheet))) {
                where.computeIfAbsent(rule, k -> new TreeSet<>()).add(sheet.getFileName().toString());
            }
        }
        Set<String> found = new TreeSet<>();
        where.forEach((rule, sheets) -> {
            if (sheets.size() > 1) found.add(selectorsOf(rule));
        });
        Set<String> added = new TreeSet<>(found);
        added.removeAll(KNOWN_DUPLICATES);
        assertTrue("These rules are written in more than one sheet: move them to a common*.css sheet (or, if the order against another "
                + "rule really matters, list them in KNOWN_DUPLICATES): " + added, added.isEmpty());
        Set<String> fixed = new TreeSet<>(KNOWN_DUPLICATES);
        fixed.removeAll(found);
        assertTrue("These are no longer duplicated, remove them from KNOWN_DUPLICATES: " + fixed, fixed.isEmpty());
    }

    /** The sheets a screen loads, in the order they are written in its FXML file. */
    private static Map<String, List<String>> sheetsPerScreen() throws IOException {
        Map<String, List<String>> perScreen = new TreeMap<>();
        for (Path fxml : files(FXML, ".fxml")) {
            List<String> sheets = new ArrayList<>();
            Matcher sheet = SHEET.matcher(read(fxml)); // both forms: stylesheets="@../css/a.css,@../css/b.css" and <URL value="@../css/a.css"/>
            while (sheet.find()) sheets.add(sheet.group(1));
            perScreen.put(fxml.getFileName().toString(), sheets);
        }
        return perScreen;
    }

    @Test
    public void aScreenLoadsTheSharedSheetsBeforeItsOwn() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, List<String>> screen : sheetsPerScreen().entrySet()) {
            boolean ownSeen = false;
            for (String sheet : screen.getValue()) {
                boolean shared = sheet.startsWith("common");
                if (shared && ownSeen) wrong.add(screen.getKey() + ": " + sheet + " comes after the screen's own sheet");
                if (!shared) ownSeen = true;
            }
        }
        assertTrue("A shared sheet has to be listed first, or it overrides the screen's own rules: " + wrong, wrong.isEmpty());
    }

    @Test
    public void everySharedSheetIsReallySharedAndNoSheetIsLeftOver() throws IOException {
        Map<String, Integer> users = new TreeMap<>();
        for (List<String> sheets : sheetsPerScreen().values()) {
            for (String sheet : new TreeSet<>(sheets)) users.merge(sheet, 1, Integer::sum);
        }
        List<String> problems = new ArrayList<>();
        for (Path sheet : files(CSS, ".css")) {
            String name = sheet.getFileName().toString();
            if (name.equals("app.css")) continue; // added to every scene by AppStyle, not by an FXML file
            int count = users.getOrDefault(name, 0);
            if (count == 0) problems.add(name + " is loaded by no screen");
            if (name.startsWith("common") && count < 2) problems.add(name + " is a shared sheet but only " + count + " screen loads it");
        }
        assertTrue(problems.toString(), problems.isEmpty());
    }

    @Test
    public void theSheetParserReadsRulesTheSameWhateverTheSpacing() {
        assertEquals(rulesOf(".a, .b { -fx-x: 1; -fx-y: 2; }"), rulesOf("/* note */ .a,.b{\n -fx-y:  2;\n -fx-x: 1 }"));
        assertNotEquals(rulesOf(".a { -fx-x: 1; }"), rulesOf(".a { -fx-x: 2; }"));
        assertEquals(2, rulesOf(".a { -fx-x: 1; } .b:hover { -fx-x: 2; }").size());
    }
}
