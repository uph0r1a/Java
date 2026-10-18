package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 6.10: the text bundle. The static checks read the sources, so a key that is misspelled in code, or left unused in the
 * file, fails the build here and not when somebody opens the screen. The first group needs no database or screen.
 */
public class MessagesTest {

    private static final Path BUNDLE = Path.of("src", "main", "resources", "messages_vi.properties");
    private static final Path JAVA = Path.of("src", "main", "java");
    private static final Pattern CALL = Pattern.compile("Messages\\.(?:get|format)\\(\\s*(?:[^\"(),]*\\?\\s*)?\"([\\w.]+)\"(?:\\s*:\\s*\"([\\w.]+)\")?");

    private static List<String> lines() throws IOException {
        return Files.readAllLines(BUNDLE, StandardCharsets.UTF_8);
    }

    private static Set<String> keysInFile() throws IOException {
        Set<String> keys = new TreeSet<>();
        for (String line : lines()) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            int eq = t.indexOf('=');
            assertTrue("not a key=value line: " + line, eq > 0);
            keys.add(t.substring(0, eq).trim());
        }
        return keys;
    }

    private static Set<String> keysUsedInCode() throws IOException {
        Set<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(JAVA)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java") && !p.endsWith("Messages.java"))::iterator) {
                Matcher m = CALL.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    used.add(m.group(1));
                    if (m.group(2) != null) used.add(m.group(2));
                }
            }
        }
        return used;
    }

    @Test
    public void everyKeyUsedInCodeIsInTheFile() throws IOException {
        Set<String> missing = new TreeSet<>(keysUsedInCode());
        missing.removeAll(keysInFile());
        assertTrue("code asks for keys that messages_vi.properties does not have: " + missing, missing.isEmpty());
    }

    @Test
    public void everyKeyInTheFileIsUsedInCode() throws IOException {
        Set<String> unused = new TreeSet<>(keysInFile());
        unused.removeAll(keysUsedInCode());
        assertTrue("these keys are in the file but no code uses them (delete them or use them): " + unused, unused.isEmpty());
    }

    @Test
    public void noKeyIsDefinedTwiceBecauseTheSecondSilentlyWins() throws IOException {
        Set<String> seen = new HashSet<>();
        for (String line : lines()) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            String key = t.substring(0, t.indexOf('=')).trim();
            assertTrue("defined twice: " + key, seen.add(key));
        }
        assertFalse(seen.isEmpty());
    }

    @Test
    public void theFileIsPlainUtf8WithVietnameseLettersWrittenAsTheyAre() throws IOException {
        String text = Files.readString(BUNDLE, StandardCharsets.UTF_8);
        assertFalse("escaped letters make the file hard to read and to review", text.contains("\\u"));
        assertTrue(text.contains("Thêm danh mục"));
    }

    @Test
    public void aKnownKeyGivesItsTextAndAnUnknownKeyGivesTheKeyItself() {
        assertEquals("Thêm danh mục", Messages.get("category.dialog.title.add"));
        assertEquals("no.such.key", Messages.get("no.such.key"));
    }

    @Test
    public void formatFillsInTheArguments() {
        // Uses the real lookup path with a key that does not exist: the key is the pattern, so the filling-in is still tested.
        assertEquals("no.such.key", Messages.format("no.such.key", "x"));
    }
}
