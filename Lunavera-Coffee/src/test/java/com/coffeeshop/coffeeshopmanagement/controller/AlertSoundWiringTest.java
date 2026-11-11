package com.coffeeshop.coffeeshopmanagement.controller;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * TODO 5.47 reads the sources that cannot be run without a screen or a sound card: each of the three events plays its cue, the switch is in
 * Settings and is saved per computer, the media module is required (so the jlink image includes it), and the player can never raise an error.
 */
public class AlertSoundWiringTest {

    private static final String MAIN = "src/main/java/com/coffeeshop/coffeeshopmanagement/";

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    @Test
    public void eachOfTheThreeEventsPlaysItsCue() throws IOException {
        String till = read(MAIN + "controller/OrderController.java");
        assertTrue("a scanned code that is not found", read(MAIN + "controller/ProductCatalog.java").contains("AlertSounds.Cue.CODE_NOT_FOUND")); // moved out of the till (TODO 6.54)
        assertTrue("a payment that could not be saved", till.contains("AlertSounds.Cue.PAYMENT_REFUSED"));
        assertTrue("a booking that is due", read(MAIN + "controller/ReservationPanel.java").contains("AlertSounds.Cue.BOOKING_DUE"));
    }

    @Test
    public void theSoundComesBeforeTheBoxItAccompaniesSoItIsHeardWhileTheBoxIsOpen() throws IOException {
        String panel = read(MAIN + "controller/ReservationPanel.java");
        assertTrue(panel.indexOf("Cue.BOOKING_DUE") < panel.indexOf("AlertUtil.info(I18n.t(\"reservation.remind.title\")"));
        String till = read(MAIN + "controller/OrderController.java");
        int refused = till.indexOf("Cue.PAYMENT_REFUSED");
        assertTrue(refused > 0 && refused < till.indexOf("AlertUtil.error(I18n.t(\"pos.pay.fail.title\")"));
        assertTrue("not for a bill another till changed (that has its own box)", till.indexOf("StaleOrderAlert.show(error, items, this::reloadSelected)") < refused);
    }

    @Test
    public void theSwitchIsInSettingsAndSavedForThisComputerOnly() throws IOException {
        String dialog = read(MAIN + "controller/SettingsDialog.java");
        assertTrue(dialog.contains("AlertSounds.isOn()"));
        assertTrue(dialog.contains("AlertSounds.choose(soundBox.isSelected())"));
        assertTrue("the owner can hear both sounds", dialog.contains("Cue.CODE_NOT_FOUND") && dialog.contains("Cue.BOOKING_DUE"));
        assertTrue("a try button plays whatever the switch says", dialog.contains("AlertPlayer.preview("));
    }

    @Test
    public void theMediaModuleIsRequiredAndTheDependencyIsPinnedWithTheOthers() throws IOException {
        assertTrue(read("src/main/java/module-info.java").contains("requires javafx.media;"));
        String pom = read("pom.xml");
        assertTrue(pom.contains("<artifactId>javafx-media</artifactId>"));
        int at = pom.indexOf("<artifactId>javafx-media</artifactId>");
        assertEquals("same version as javafx-controls", "21.0.6", pom.substring(pom.indexOf("<version>", at) + 9, pom.indexOf("</version>", at)));
        assertTrue("the packaging note says the image now needs it", read("docs/PACKAGING.md").contains("javafx.media"));
    }

    @Test
    public void thePlayerNeverBlocksTheScreenAndNeverRaisesAnError() throws IOException {
        String player = read(MAIN + "util/AlertPlayer.java");
        assertTrue("loads and plays off the screen's thread", player.contains("new Thread(") && player.contains("setDaemon(true)"));
        assertTrue("catches everything, also a missing javafx.media at run time", player.contains("catch (Throwable"));
        assertTrue("plays a copied file: JavaFX media cannot open a jrt: address", player.contains("createTempFile") && player.contains("toUri()"));
        assertFalse("the screen's thread is never asked to wait", player.contains("Platform.runLater") || player.contains(".join()") || player.contains("Thread.sleep"));
    }
}
