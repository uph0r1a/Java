package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.*;

/**
 * TODO 5.44, the large text. The sheets in {@code css/large/} are generated; these tests fail the build when one is out of date
 * (a rule was added or changed in a screen's sheet and the generator was not run), and check what the large sizes must never do.
 */
public class LargeTextSheetTest {

    private static final Path CSS = Paths.get("src/main/resources/css");
    private static final Path LARGE = CSS.resolve(LargeTextSheet.DIR_NAME);
    private static final Pattern SIZE = Pattern.compile("-fx-font-size\\s*:\\s*([0-9]+)px");

    private static Map<String, String> readDir(Path dir) throws IOException {
        Map<String, String> files = new TreeMap<>();
        try (Stream<Path> list = Files.list(dir)) {
            for (Path file : (Iterable<Path>) list.filter(p -> p.toString().endsWith(".css"))::iterator) {
                files.put(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    @Test
    public void theGeneratedSheetsAreUpToDate() throws IOException {
        Map<String, String> expected = LargeTextSheet.generateAll(LargeTextSheet.read(CSS));
        Map<String, String> actual = readDir(LARGE);
        String how = " - regenerate with: java -cp target/classes com.coffeeshop.coffeeshopmanagement.service.LargeTextSheet"
                + " (or run LargeTextSheet.main from the IDE in the project folder), then commit src/main/resources/css/large/";
        assertEquals("the files in css/large/ must be exactly the generated ones" + how, expected.keySet(), actual.keySet());
        for (var file : expected.entrySet()) {
            assertEquals("css/large/" + file.getKey() + " is out of date" + how, file.getValue(), actual.get(file.getKey()));
        }
    }

    @Test
    public void noRuleOfTheLargeSheetsIsBelow12Px() throws IOException {
        List<String> small = new ArrayList<>();
        int rules = 0;
        for (var file : readDir(LARGE).entrySet()) {
            Matcher m = SIZE.matcher(file.getValue());
            while (m.find()) {
                rules++;
                if (Integer.parseInt(m.group(1)) < 12) small.add(file.getKey() + " " + m.group());
            }
        }
        assertTrue("the sheets were not read", rules > 100);
        assertTrue("sizes below 12px in the large text: " + small, small.isEmpty());
    }

    @Test
    public void everyRuleThatSetsASmallSizeHasALargerOneInTheCompanion() throws IOException {
        int small = 0;
        for (var sheet : LargeTextSheet.read(CSS).entrySet()) {
            if (LargeTextSheet.SKIPPED.contains(sheet.getKey())) continue;
            for (String[] rule : LargeTextSheet.rulesWithSize(sheet.getValue())) {
                int px = Integer.parseInt(rule[1]);
                String companion = Files.readString(LARGE.resolve(sheet.getKey()), StandardCharsets.UTF_8);
                assertTrue(sheet.getKey() + ": " + rule[0] + " has no large size", companion.contains(rule[0] + " {\n    -fx-font-size: "
                        + LargeTextSheet.scale(px) + "px !important;"));
                if (px <= 11) small++;
            }
        }
        assertTrue("the parser found only " + small + " rules of 10 or 11 px (TODO 5.44 counted about 50 in the sheets)", small >= 45);
    }

    @Test
    public void everySizeOfTheLargeSheetsIsImportant() throws IOException {
        for (var file : readDir(LARGE).entrySet()) {
            Matcher m = Pattern.compile("-fx-font-size[^;]*;").matcher(file.getValue());
            int count = 0;
            while (m.find()) {
                count++;
                assertTrue(file.getKey() + ": " + m.group() + " must be !important, or the screen's own size wins", m.group().contains("!important"));
            }
            assertTrue(file.getKey() + " sets no size", count > 0);
        }
    }

    @Test
    public void theLargeSizeIsAlwaysLargerAndKeepsTheOrderOfTheSizes() {
        for (int px = 8; px < 60; px++) {
            assertTrue(px + " must grow", LargeTextSheet.scale(px) > px);
            // up to 18 px (the text people read) two sizes never swap or become equal; above, a heading may equal the next one (18 and 19 px)
            if (px + 1 <= 18) assertTrue("the order of " + px + " and " + (px + 1) + " must stay", LargeTextSheet.scale(px) < LargeTextSheet.scale(px + 1));
            else assertTrue("the order of " + px + " and " + (px + 1) + " must not swap", LargeTextSheet.scale(px) <= LargeTextSheet.scale(px + 1));
        }
        assertEquals(12, LargeTextSheet.scale(10));
        assertEquals(13, LargeTextSheet.scale(11));
        assertEquals("headings in boxes of a fixed size grow by one only", 23, LargeTextSheet.scale(22));
    }

    @Test
    public void theInvoiceIsNotMadeLargerBecauseItIsAPictureOfThePaper() throws IOException {
        assertFalse(Files.exists(LARGE.resolve("receipt.css")));
        assertTrue(LargeTextSheet.SKIPPED.contains("receipt.css"));
    }

    @Test
    public void aClassTheScreensSizeDifferentlyKeepsEachScreensSize() {
        // .page-title is 22 px in one sheet and 25 px in another: each companion keeps its own, so nothing is decided by file order.
        Map<String, String> sheets = new TreeMap<>();
        sheets.put("a.css", ".page-title { -fx-font-size: 22px; }");
        sheets.put("b.css", ".page-title { -fx-font-size: 25px; -fx-text-fill: red; }");
        Map<String, String> files = LargeTextSheet.generateAll(sheets);
        assertTrue(files.get("a.css").contains(".page-title {\n    -fx-font-size: 23px !important;"));
        assertTrue(files.get("b.css").contains(".page-title {\n    -fx-font-size: 26px !important;"));
        assertFalse("only the size is repeated", files.get("b.css").contains("text-fill"));
    }

    @Test
    public void aSelectorListIsKeptTogetherAndASheetWithoutASizeHasNoCompanion() {
        Map<String, String> sheets = new TreeMap<>();
        sheets.put("a.css", ".x,\n.y:hover   .z { -fx-font-size: 10px; -fx-padding: 1px; }\n.w { -fx-padding: 2px; }");
        sheets.put("b.css", ".w { -fx-padding: 2px; }");
        Map<String, String> files = LargeTextSheet.generateAll(sheets);
        assertTrue(files.get("a.css").contains(".x,\n.y:hover .z {\n    -fx-font-size: 12px !important;"));
        assertFalse(files.get("a.css").contains(".w"));
        assertFalse(files.containsKey("b.css"));
        assertTrue(files.containsKey("base.css"));
    }
}
