package com.coffeeshop.coffeeshopmanagement.controller;

import com.coffeeshop.coffeeshopmanagement.service.PosShortcutKeys;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * TODO 5.45: the screen side of the till shortcuts. Needs the JavaFX jars (a key code and a key combination need no window).
 * The rest reads the sources, so the wiring cannot be dropped without a red test.
 */
public class PosShortcutsSourceTest {

    private static final Path CONTROLLERS = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "controller");

    private static String read(String name) throws IOException {
        return Files.readString(CONTROLLERS.resolve(name), StandardCharsets.UTF_8);
    }

    @Test
    public void everyKeyInTheTableIsARealKeyCodeAndNoTwoCombinationsAreEqual() {
        Set<KeyCombination> seen = new HashSet<>();
        for (PosShortcutKeys.Entry entry : PosShortcutKeys.ENTRIES) {
            for (PosShortcutKeys.Key key : entry.keys()) {
                KeyCode.valueOf(key.code()); // an unknown name throws
                assertTrue(key.id() + " is registered twice", seen.add(PosShortcuts.combination(key)));
            }
        }
    }

    @Test
    public void everyActionHasAHandlerInTheScreenClass() throws IOException {
        String source = read("PosShortcuts.java");
        for (PosShortcutKeys.Action action : PosShortcutKeys.Action.values()) {
            assertTrue("PosShortcuts has no handler for " + action, source.contains("handlers.put(Action." + action + ","));
        }
    }

    @Test
    public void theTillInstallsThemAndTakesThemOffWhenTheSceneChanges() throws IOException {
        assertTrue(read("OrderController.java").contains("PosShortcuts.install("));
        String shortcuts = read("PosShortcuts.java");
        assertTrue("keys must come off the old scene", shortcuts.contains("old.getAccelerators()::remove"));
        assertTrue(shortcuts.contains("sceneProperty().addListener"));
    }

    @Test
    public void theQuantityKeysGoThroughTheTillsOwnRules() throws IOException {
        assertTrue("+, - and Delete must use adjustQuantity (stock check, 'remove the line?')",
                read("OrderController.java").contains("this::adjustQuantity"));
    }

    @Test
    public void aSmallHelpButtonAndF1ShowTheSameList() throws IOException {
        String shortcuts = read("PosShortcuts.java");
        assertTrue(shortcuts.contains("posHelpButton"));
        assertTrue(shortcuts.contains("showHelp"));
        assertTrue(shortcuts.contains("pos.key.help.title"));
    }
}
