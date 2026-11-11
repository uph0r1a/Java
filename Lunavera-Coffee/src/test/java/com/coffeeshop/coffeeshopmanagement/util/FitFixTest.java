package com.coffeeshop.coffeeshopmanagement.util;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/** TODO 1.7: what {@link FitFix} changes (and leaves alone) in a layout; the measuring on real screens is {@code tools.ScreenFitCheck}. */
public class FitFixTest {

    /** Controls need the JavaFX toolkit. Where there is no display (a build server) these tests are skipped, not failed. */
    @BeforeClass
    public static void startToolkit() {
        try {
            javafx.application.Platform.startup(() -> { });
        } catch (IllegalStateException alreadyRunning) {
            // fine: another test class started it
        } catch (Throwable noDisplay) {
            Assume.assumeNoException("no JavaFX toolkit here", noDisplay);
        }
    }

    @Test
    public void aLabelAndAButtonInARowKeepTheirWidthButATextFieldStillGivesWay() {
        Label label = new Label("From");
        Button button = new Button("Clear");
        TextField field = new TextField();
        HBox row = new HBox(label, field, button);
        FitFix.protect(row);
        assertEquals(Region.USE_PREF_SIZE, label.getMinWidth(), 0);
        assertEquals(Region.USE_PREF_SIZE, button.getMinWidth(), 0);
        assertEquals("a text box is what shrinks", Region.USE_COMPUTED_SIZE, field.getMinWidth(), 0);
    }

    @Test
    public void aWidthSomeoneSetOnPurposeIsKept() {
        Button button = new Button("Pay");
        button.setMinWidth(30);
        FitFix.protect(new HBox(button));
        assertEquals(30, button.getMinWidth(), 0);
    }

    @Test
    public void aWrappingLabelKeepsTheHeightItsTextNeeds() {
        Label label = new Label("A long sentence that wraps");
        label.setWrapText(true);
        FitFix.protect(new VBox(label));
        assertEquals(Region.USE_PREF_SIZE, label.getMinHeight(), 0);
    }

    @Test
    public void aLabelThatDoesNotWrapAndIsNotInARowIsNotTouched() {
        Label label = new Label("Title");
        FitFix.protect(new VBox(label));
        assertEquals(Region.USE_COMPUTED_SIZE, label.getMinWidth(), 0);
        assertEquals(Region.USE_COMPUTED_SIZE, label.getMinHeight(), 0);
    }

    @Test
    public void everyPlainLabelOfAFormCanBeMadeToWrap() {
        Label one = new Label("one");
        Label two = new Label("two");
        VBox form = new VBox(one, new HBox(two));
        FitFix.wrapLabels(form);
        assertTrue(one.isWrapText());
        assertTrue(two.isWrapText());
    }
}
