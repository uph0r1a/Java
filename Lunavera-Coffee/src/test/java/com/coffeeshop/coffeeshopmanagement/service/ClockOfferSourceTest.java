package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * TODO 5.38: the questions are asked where people sign in and out. Reads the sources as text (like
 * {@code BackupOnLeavingSourceTest}), so a new sign-out button that forgets the clock is caught without JavaFX or a database.
 */
public class ClockOfferSourceTest {

    private static final Path ROOT = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement");

    private static String read(String file) throws IOException {
        return Files.readString(ROOT.resolve(file), StandardCharsets.UTF_8);
    }

    /** The text of one method, from its signature to the next method at the same indent. */
    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("method not found: " + signature, start >= 0);
        int end = source.indexOf("\n    }\n", start);
        return source.substring(start, end);
    }

    @Test
    public void everyLogoutButtonAsksAboutTheClockBeforeSigningOut() throws IOException {
        // the one logout of the till, the customer screen, the employee home and the admin screens (TODO 6.54)
        for (String screen : new String[]{"controller/ScreenNavigation.java"}) {
            String logout = method(read(screen), "public void logout(ActionEvent event)");
            assertTrue(screen + " signs out without the clock question", logout.contains("ClockOffer.beforeSignOut("));
            assertTrue(screen + " must still end the session", logout.contains("Session.clear()"));
        }
    }

    @Test
    public void signingInAsksToClockIn() throws IOException {
        String login = method(read("controller/LoginController.java"), "private void enterApplication(");
        assertTrue(login.contains("ClockOffer.afterSignIn()"));
        // after the forced password box, which may sign the person out again
        assertTrue(login.indexOf("showForced") < login.indexOf("ClockOffer.afterSignIn()"));
    }

    @Test
    public void theLockScreenAsksAtSignOutAndWhenAnotherCashierTakesOver() throws IOException {
        String lock = read("controller/LockScreen.java");
        assertTrue(lock.contains("ClockOffer.beforeSignOut(SessionGuard::endSession)"));
        assertTrue(lock.contains("ClockOffer::afterSignIn"));
    }

    @Test
    public void theIdleSignOutAsksAfterTheSessionHasEnded() throws IOException {
        String idle = method(read("util/SessionGuard.java"), "public static void idleLogout(");
        assertTrue(idle.contains("ClockOffer.afterIdleLogout("));
        // the till is locked for the next person first: asking must never keep the session open
        assertTrue(idle.indexOf("endSession()") < idle.indexOf("ClockOffer.afterIdleLogout("));
    }

    @Test
    public void theQuestionNeverBlocksASignOutOnAFailure() throws IOException {
        String offer = read("util/ClockOffer.java");
        String signOut = method(offer, "public static void beforeSignOut(");
        // three ways out (nothing to ask, answered, database failure), each one carries on with the sign-out
        assertTrue(signOut.split("carryOn\\.run\\(\\)", -1).length - 1 >= 4);
    }
}
