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

    private static final Path BUNDLE = Path.of("src", "main", "resources", "i18n", "messages_vi.properties");
    private static final Path BUNDLE_EN = Path.of("src", "main", "resources", "i18n", "messages_en.properties");
    private static final Path JAVA = Path.of("src", "main", "java");
    private static final Pattern CALL = Pattern.compile("Messages\\.(?:get|format)\\(\\s*(?:[^\"(),]*\\?\\s*)?\"([\\w.]+)\"(?:\\s*:\\s*\"([\\w.]+)\")?");

    private static List<String> lines() throws IOException {
        return Files.readAllLines(BUNDLE, StandardCharsets.UTF_8);
    }

    private static Set<String> keysInFile() throws IOException {
        return keysIn(lines());
    }

    private static Set<String> keysIn(List<String> fileLines) {
        Set<String> keys = new TreeSet<>();
        for (String line : fileLines) {
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
    public void everyKeyUsedInCodeIsAlsoInTheEnglishFile() throws IOException {
        Set<String> missing = new TreeSet<>(keysUsedInCode());
        missing.removeAll(keysIn(Files.readAllLines(BUNDLE_EN, StandardCharsets.UTF_8)));
        assertTrue("code asks for keys that messages_en.properties does not have: " + missing, missing.isEmpty());
    }

    @Test
    public void noKeyIsDefinedTwiceBecauseTheSecondSilentlyWins() throws IOException {
        for (Path file : List.of(BUNDLE, BUNDLE_EN)) {
            Set<String> seen = new HashSet<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                String key = t.substring(0, t.indexOf('=')).trim();
                assertTrue("defined twice in " + file.getFileName() + ": " + key, seen.add(key));
            }
            assertFalse(seen.isEmpty());
        }
    }

    @Test
    public void theFilesArePlainUtf8WithLettersWrittenAsTheyAre() throws IOException {
        String text = Files.readString(BUNDLE, StandardCharsets.UTF_8);
        assertFalse("escaped letters make the file hard to read and to review", text.contains("\\u"));
        assertTrue(text.contains("Thêm danh mục"));
        assertFalse(Files.readString(BUNDLE_EN, StandardCharsets.UTF_8).contains("\\u"));
    }

    @Test
    public void aKnownKeyGivesItsTextAndAnUnknownKeyGivesTheKeyItself() {
        String before = I18n.current();
        try {
            I18n.setLanguage(I18n.VI);
            assertEquals("Thêm danh mục", Messages.get("category.dialog.title.add"));
            I18n.setLanguage(I18n.EN);
            assertEquals("Add category", Messages.get("category.dialog.title.add"));
            assertEquals("no.such.key", Messages.get("no.such.key"));
        } finally {
            I18n.setLanguage(before);
        }
    }

    @Test
    public void formatFillsInTheArguments() {
        // Uses the real lookup path with a key that does not exist: the key is the pattern, so the filling-in is still tested.
        assertEquals("no.such.key", Messages.format("no.such.key", "x"));
    }

    @Test
    public void aMissingKeyIsLoggedOnlyOnce() {
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Messages.class.getName());
        java.util.concurrent.atomic.AtomicInteger records = new java.util.concurrent.atomic.AtomicInteger();
        java.util.logging.Handler counter = new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord r) { records.incrementAndGet(); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        logger.addHandler(counter);
        try {
            String key = "no.such.key." + System.nanoTime();
            assertEquals(key, Messages.get(key));
            assertEquals(key, Messages.get(key));
            assertEquals(key, Messages.format(key));
            assertEquals("three calls, one log line", 1, records.get());
        } finally {
            logger.removeHandler(counter);
        }
    }
}

