package com.coffeeshop.coffeeshopmanagement.service;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TODO 2.62: the daily backup is also tried when a person logs out, when the idle logout fires and when the window closes, and
 * every start goes through the one-at-a-time lock. Reads the sources as text (no JavaFX, no database): a logout or close path that
 * stops asking for the backup fails here.
 */
public class BackupOnLeavingSourceTest {

    private static final Path ROOT = Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement");

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
    }

    /** The text of one method, from its signature to the next method at the same indent. */
    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("method not found: " + signature, start >= 0);
        int end = source.indexOf("\n    }\n", start);
        return source.substring(start, end);
    }

    @Test
    public void everyLogoutHandlerGoesThroughTheOfferWhichStartsTheBackup() throws IOException {
        assertTrue(method(read("controller/BackupDialogs.java"), "public static void offerBeforeLeaving").contains("autoBackupInBackground()"));
        // the one logout of every screen with a sidebar (TODO 6.54)
        for (String screen : new String[]{"controller/ScreenNavigation.java"}) {
            String logout = method(read(screen), "public void logout(ActionEvent event)");
            assertTrue(screen + " logs out without the leaving offer (and so without the backup)", logout.contains("BackupDialogs.offerBeforeLeaving"));
        }
    }

    @Test
    public void theIdleLogoutAndAForcedLogoutStartTheBackup() throws IOException {
        String guard = read("util/SessionGuard.java");
        assertTrue(method(guard, "public static void endSession()").contains("autoBackupInBackground()"));
        assertTrue(method(guard, "public static void forceLogout()").contains("autoBackupInBackground()"));
    }

    @Test
    public void closingTheProgramWaitsForTheBackupAfterTheLastAutosave() throws IOException {
        String stop = method(read("LunaveraApplication.java"), "public void stop()");
        int flush = stop.indexOf("flushOrdered");
        int backup = stop.indexOf("autoBackupBeforeExit(");
        int release = stop.indexOf("SingleInstance.release()");
        assertTrue("the backup is missing from stop()", backup >= 0);
        assertTrue("the last autosave must be in the database before it is copied", flush >= 0 && flush < backup);
        assertTrue("the data folder is given back only after the backup", backup < release);
    }

    @Test
    public void theStartAndTheHourlyTimerUseTheSameGuardedStart() throws IOException {
        String application = read("LunaveraApplication.java");
        assertFalse("a raw thread would bypass the one-at-a-time lock", application.contains("new Thread(BackupService::autoBackupIfDue"));
        assertTrue(application.split("autoBackupInBackground\\(\\)", -1).length - 1 >= 2);
    }

    @Test
    public void everyAutomaticBackupRunsInsideTheLock() throws IOException {
        String service = read("service/BackupService.java");
        assertTrue(service.contains("AUTOMATIC.run(0, BackupService::autoBackupNow)"));
        assertTrue(service.contains("AUTOMATIC.run(maxMillis, BackupService::autoBackupNow)"));
        int mentions = service.split("autoBackupNow", -1).length - 1;
        assertTrue("the body is named three times: its declaration and the two calls inside the lock (found " + mentions + ")", mentions == 3);
    }
}
