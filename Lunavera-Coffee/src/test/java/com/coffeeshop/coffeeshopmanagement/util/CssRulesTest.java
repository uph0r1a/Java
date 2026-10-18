package com.coffeeshop.coffeeshopmanagement.util;

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
 * TODO 6.29 / 6.27: no style sheet keeps a rule for a class or id that nothing uses. A CSS class that no FXML file names
 * and no Java string mentions can never match a node, so its rules are dead weight that people then edit by mistake (the
 * login and dashboard sheets had 52 such rules). Reads the sources as text, so it needs no JavaFX. A class that is built by
 * joining a prefix to something else in code would be flagged wrongly: name it in a literal, or add the prefix here.
 */
public class CssRulesTest {

    private static final Path MAIN = Path.of("src", "main");
    private static final Path CSS = MAIN.resolve("resources").resolve("css");
    private static final Path FXML = MAIN.resolve("resources").resolve("fxml");
    private static final Path JAVA = MAIN.resolve("java");

    /** Style classes JavaFX itself puts on its controls: the sheets restyle them, no file of ours names them. */
    private static final Set<String> JAVAFX_OWN = Set.of("axis", "axis-label", "button", "chart", "chart-horizontal-grid-lines",
            "chart-line-symbol", "chart-plot-background", "chart-series-line", "chart-vertical-grid-lines", "column-header",
            "column-header-background", "combo-box", "combo-box-popup", "decrement-button", "filler", "hyperlink", "increment-button",
            "label", "list-cell", "list-view", "password-field", "placeholder", "scroll-bar", "table-cell", "table-row-cell",
            "table-view", "text-field", "thumb", "tooltip", "track", "viewport", "virtual-flow");

    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z_][\\w-]*");

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static Set<String> referenced() throws IOException {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.list(FXML)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".fxml"))::iterator) {
                String text = read(f);
                Matcher m = Pattern.compile("styleClass=\"([^\"]*)\"").matcher(text);
                while (m.find()) for (String c : m.group(1).split("[\\s,]+")) if (!c.isEmpty()) names.add(c);
                m = Pattern.compile("<styleClass>(.*?)</styleClass>", Pattern.DOTALL).matcher(text);
                while (m.find()) {
                    Matcher v = Pattern.compile("value=\"([^\"]+)\"").matcher(m.group(1));
                    while (v.find()) names.add(v.group(1));
                }
                m = Pattern.compile("\\b(?:fx:id|id)=\"([^\"]*)\"").matcher(text);
                while (m.find()) names.add("#" + m.group(1));
            }
        }
        try (Stream<Path> files = Files.walk(JAVA)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Matcher s = STRING_LITERAL.matcher(read(f));
                while (s.find()) {
                    Matcher t = TOKEN.matcher(s.group(1));
                    while (t.find()) {
                        names.add(t.group());
                        names.add("#" + t.group());
                    }
                }
            }
        }
        return names;
    }

    @Test
    public void everyClassAndIdInTheStyleSheetsIsUsedBySomething() throws IOException {
        Set<String> used = referenced();
        Pattern token = Pattern.compile("(?<![\\w-])([.#])([A-Za-z_][\\w-]*)");
        StringBuilder dead = new StringBuilder();
        int rules = 0;
        try (Stream<Path> files = Files.list(CSS)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".css"))::iterator) {
                String css = read(f).replaceAll("(?s)/\\*.*?\\*/", "");
                for (String block : css.split("\\}")) {
                    int brace = block.indexOf('{');
                    if (brace < 0) continue;
                    rules++;
                    Matcher m = token.matcher(block.substring(0, brace));
                    while (m.find()) {
                        String name = m.group(2);
                        // "#5b7e45"-like colours are not selectors: a selector id is followed by a name, never by a value
                        if (m.group(1).equals(".") && JAVAFX_OWN.contains(name)) continue;
                        if (!used.contains(m.group(1).equals("#") ? "#" + name : name)) {
                            dead.append(f.getFileName()).append(": ").append(m.group()).append('\n');
                        }
                    }
                }
            }
        }
        assertTrue("the check read no rules; is the path right?", rules > 300);
        assertEquals("these selectors name a class or id that no FXML file or Java string uses; delete the rule or use the name:\n" + dead,
                0, dead.length());
    }

    @Test
    public void everyStyleSheetStillHasBalancedBraces() throws IOException {
        try (Stream<Path> files = Files.list(CSS)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".css"))::iterator) {
                String css = read(f).replaceAll("(?s)/\\*.*?\\*/", "");
                assertEquals(f.getFileName().toString(), css.chars().filter(c -> c == '{').count(), css.chars().filter(c -> c == '}').count());
            }
        }
    }
}
