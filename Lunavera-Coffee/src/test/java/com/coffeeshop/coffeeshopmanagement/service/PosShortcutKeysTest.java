package com.coffeeshop.coffeeshopmanagement.service;

import com.coffeeshop.coffeeshopmanagement.service.PosShortcutKeys.Action;
import com.coffeeshop.coffeeshopmanagement.service.PosShortcutKeys.Entry;
import com.coffeeshop.coffeeshopmanagement.service.PosShortcutKeys.Key;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** TODO 5.45: the till's shortcut table. Plain data, so it runs without JavaFX. */
public class PosShortcutKeysTest {

    private static final Path I18N = Path.of("src", "main", "resources", "i18n");

    @Test
    public void noTwoActionsShareAKey() {
        assertTrue("two actions on one key: " + PosShortcutKeys.clashes(), PosShortcutKeys.clashes().isEmpty());
    }

    @Test
    public void everyActionHasExactlyOneEntry() {
        Set<Action> seen = new HashSet<>();
        for (Entry entry : PosShortcutKeys.ENTRIES) {
            assertTrue(entry.action() + " is listed twice", seen.add(entry.action()));
            assertFalse(entry.action() + " has no key", entry.keys().isEmpty());
        }
        assertEquals("an action with no entry has no key", Set.of(Action.values()), seen);
    }

    @Test
    public void theTwoOldKeysAreStillThere() {
        assertEquals(List.of(new Key("F", true, false)), entry(Action.FIND).keys());
        assertEquals(List.of(new Key("ENTER", true, false)), entry(Action.PAY).keys());
    }

    @Test
    public void theKeysThatAreAlsoCharactersWaitForTheCursorToLeaveATextBox() {
        for (Action action : List.of(Action.REMOVE_LINE, Action.MORE, Action.LESS)) {
            assertTrue(action + " is a character you type and must not act while typing", entry(action).needsNoTyping());
        }
        for (Action action : List.of(Action.FIND, Action.PAY, Action.LOCK, Action.HELP, Action.FIND_CUSTOMER)) {
            assertFalse(action + " has a modifier or is a function key, it works anywhere", entry(action).needsNoTyping());
        }
    }

    @Test
    public void aBareLetterIsNeverAShortcut() {
        // a bare letter or digit would fire while the cashier types a name or a phone
        for (Entry entry : PosShortcutKeys.ENTRIES) {
            for (Key key : entry.keys()) {
                boolean single = key.code().length() == 1;
                assertFalse(entry.action() + " is on the bare key " + key.code(), single && !key.ctrl());
            }
        }
    }

    @Test
    public void everyLabelIsInBothLanguageFilesAndInTheHelpText() throws IOException {
        for (String file : List.of("messages_vi.properties", "messages_en.properties")) {
            String text = Files.readString(I18N.resolve(file), StandardCharsets.UTF_8);
            for (Entry entry : PosShortcutKeys.ENTRIES) {
                assertTrue(file + " lacks " + entry.labelKey(), text.contains("\n" + entry.labelKey() + "="));
            }
            for (String key : List.of("pos.key.help.tip", "pos.key.help.title", "pos.key.help.note")) {
                assertTrue(file + " lacks " + key, text.contains("\n" + key + "="));
            }
        }
    }

    private static Entry entry(Action action) {
        return PosShortcutKeys.ENTRIES.stream().filter(e -> e.action() == action).findFirst().orElseThrow();
    }
}
