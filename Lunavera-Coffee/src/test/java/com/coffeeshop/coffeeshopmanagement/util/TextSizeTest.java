package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.coffeeshop.coffeeshopmanagement.service.LargeTextSheet;

import static org.junit.Assert.*;

/** TODO 5.44: the choice is kept per computer, and no small size is written in code where the large text could not reach it. */
public class TextSizeTest {

    private Path sandbox;
    private String realDataDir;

    @Before
    public void useAnEmptyDataFolder() throws IOException {
        realDataDir = System.getProperty("lunavera.dataDir");
        sandbox = Files.createTempDirectory("textsize");
        System.setProperty("lunavera.dataDir", sandbox.resolve("data").toString());
    }

    @After
    public void restore() {
        if (realDataDir == null) System.clearProperty("lunavera.dataDir"); else System.setProperty("lunavera.dataDir", realDataDir);
    }

    @Test
    public void theTextIsNormalUntilTheLargeSizeIsChosen() {
        assertFalse(TextSize.isLarge());
        assertEquals("-fx-font-size: 11px;", TextSize.size(11));
    }

    @Test
    public void theLargeChoiceIsRememberedAndMakesCodeStylesLarger() {
        UiSettings.put(TextSize.KEY, TextSize.LARGE);
        assertTrue(TextSize.isLarge());
        assertEquals("-fx-font-size: 13px;", TextSize.size(11));
        assertEquals("-fx-font-size: 12px;", TextSize.size(10));
        UiSettings.put(TextSize.KEY, TextSize.NORMAL);
        assertFalse(TextSize.isLarge());
    }

    @Test
    public void anUnknownValueMeansTheNormalSize() {
        UiSettings.put(TextSize.KEY, "huge");
        assertFalse(TextSize.isLarge());
    }

    /**
     * A style written in code beats a style sheet, so a small size written there is out of the large text's reach. This finds every
     * "-fx-font-size: Npx" in a string of the program that is 12 px or less and not made by {@link TextSize#size}.
     */
    @Test
    public void noSmallSizeIsWrittenInCodeWithoutTextSize() throws IOException {
        Pattern literal = Pattern.compile("-fx-font-size:\\s*([0-9]+)px");
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Paths.get("src/main/java"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                if (file.getFileName().toString().equals("TextSize.java")) continue;
                String[] lines = Files.readString(file, StandardCharsets.UTF_8).split("\n");
                for (int i = 0; i < lines.length; i++) {
                    Matcher m = literal.matcher(lines[i]);
                    while (m.find()) {
                        if (Integer.parseInt(m.group(1)) <= 12 && !lines[i].trim().startsWith("*") && !lines[i].trim().startsWith("//")) {
                            found.add(file.getFileName() + ":" + (i + 1) + " " + m.group());
                        }
                    }
                }
            }
        }
        assertTrue("Write these with TextSize.size(px) so the large text reaches them: " + found, found.isEmpty());
    }

    @Test
    public void everyWindowFollowsTheChoiceThroughAppStyle() throws IOException {
        String style = Files.readString(Paths.get("src/main/java/com/coffeeshop/coffeeshopmanagement/util/AppStyle.java"), StandardCharsets.UTF_8);
        assertTrue("apply() must sync the large text for every new scene", style.contains("syncLargeText(scene);"));
        assertTrue("a screen swapped into a window brings its own sheets", style.contains("// and it brings its own sheets"));
        assertTrue("choosing the size reaches the windows that are open now", style.contains("static void refreshAll()"));
        assertEquals("the folder name the code uses is the folder that is generated", "/css/large/", AppStyle.LARGE_DIR);
        assertEquals(LargeTextSheet.DIR_NAME, "large");
    }
}
