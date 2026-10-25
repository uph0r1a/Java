package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * TODO 5.36: the receipt, history and report windows must have the main window as owner, or Windows lets the till cover a receipt
 * that has just opened. {@link SceneNavigator#newOwnedStage()} is the one place that creates a window; this reads the sources so a
 * new window written with a bare {@code new Stage()} fails the build instead of coming back as the same complaint.
 */
public class OwnedWindowsTest {

    @Test
    public void onlySceneNavigatorCreatesAStageAndEveryOtherWindowAsksItForOne() throws IOException {
        String call = "new Stage" + "()";
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                if (file.getFileName().toString().equals("SceneNavigator.java")) continue;
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String code = lines.get(i).strip();
                    if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) continue;
                    if (lines.get(i).contains(call)) offenders.add(file.getFileName() + ":" + (i + 1));
                }
            }
        }
        assertTrue("These make a window without an owner; use SceneNavigator.newOwnedStage() (TODO 5.36): " + offenders,
                offenders.isEmpty());
    }

    @Test
    public void theOwnedStageSetsTheMainWindowAsOwner() throws IOException {
        String source = Files.readString(Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "util",
                "SceneNavigator.java"));
        int start = source.indexOf("public static Stage newOwnedStage()");
        assertTrue(start >= 0);
        String body = source.substring(start, source.indexOf("\n    }\n", start));
        assertTrue(body, body.contains("initOwner(main)"));
        assertTrue("never modal: the till must stay usable", !body.contains("initModality"));
        assertTrue("the receipt window comes from it", source.contains("Stage stage = newOwnedStage();"));
    }
}
