package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * TODO 6.35: {@link PasswordReveal} swaps a password box and a plain box with the eye button, which needs real controls and a
 * toolkit to run (TestFX with Monocle was not added: its versions could not be checked here). What can be pinned without one:
 * the icon, and the rules of the swap as written in the source.
 */
public class PasswordRevealTest {

    private static String source() throws IOException {
        return Files.readString(Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "util", "PasswordReveal.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    public void theHiddenIconIsAnOpenEyeAndTheShownIconAddsASlash() {
        String hidden = PasswordReveal.iconContent(false);
        String shown = PasswordReveal.iconContent(true);
        assertFalse(hidden.isBlank());
        assertTrue("the crossed-out eye starts with the open eye", shown.startsWith(hidden));
        assertTrue(shown.length() > hidden.length());
    }

    @Test
    public void theTwoBoxesShareOneTextSoNothingTypedIsLost() throws IOException {
        assertTrue(source().contains("plainField.textProperty().bindBidirectional(passwordField.textProperty())"));
    }

    @Test
    public void theRevealedBoxKeepsTheLengthCapAndTheEnterKey() throws IOException {
        String source = source();
        assertTrue("the cap of limit() must apply to the revealed copy too", source.contains("new TextFormatter<>(passwordField.getTextFormatter().getFilter())"));
        assertTrue("Enter in the revealed box signs in like Enter in the hidden one", source.contains("passwordField.getOnAction().handle(e)"));
    }

    @Test
    public void theSwapHidesOneBoxAndShowsTheOtherAndKeepsTheFocusAtTheEndOfTheText() throws IOException {
        String source = source();
        for (String line : new String[]{"plainField.setVisible(show)", "plainField.setManaged(show)", "passwordField.setVisible(!show)",
                "passwordField.setManaged(!show)", "active.requestFocus()", "active.positionCaret(passwordField.getText().length())"}) {
            assertTrue(line, source.contains(line));
        }
    }

    @Test
    public void theRevealedBoxStartsHiddenAndTheEyeIsNotATabStop() throws IOException {
        String source = source();
        assertTrue(source.contains("plainField.setVisible(false)"));
        assertTrue(source.contains("plainField.setManaged(false)"));
        assertTrue(source.contains("toggleButton.setFocusTraversable(false)"));
    }

    @Test
    public void aMissingFieldOrAParentThatIsNotAPaneIsIgnoredNotACrash() throws IOException {
        String source = source();
        int guard = source.indexOf("passwordField == null || toggleButton == null");
        assertTrue(guard > 0);
        assertTrue(source.substring(guard, guard + 200).contains("return;"));
    }
}
