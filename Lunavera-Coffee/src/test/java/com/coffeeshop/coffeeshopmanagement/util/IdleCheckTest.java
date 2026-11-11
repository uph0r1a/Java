package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * TODO 6.35: what the idle timer does at each look ({@link IdleTracker#checkIdle}), and the parts of {@code IdleLogout} that
 * only attach listeners to windows, which need a JavaFX toolkit to run and are pinned here by reading the source.
 */
public class IdleCheckTest {

    private static final Duration LIMIT = Duration.ofMinutes(15);
    private final AtomicLong now = new AtomicLong(5_000_000);
    private final IdleTracker tracker = new IdleTracker(now::get);

    private void pass(Duration time) {
        now.addAndGet(time.toMillis());
    }

    @Test
    public void anActivePersonIsLeftAlone() {
        pass(Duration.ofMinutes(14));
        assertFalse(tracker.checkIdle(true, LIMIT));
    }

    @Test
    public void anIdlePersonIsReportedOnceAndTheClockRestarts() {
        pass(Duration.ofMinutes(15));
        assertTrue(tracker.checkIdle(true, LIMIT));
        assertFalse("the next look must not report the same idleness again", tracker.checkIdle(true, LIMIT));
        pass(Duration.ofMinutes(15));
        assertTrue("and a person who stays away is reported again after another full limit", tracker.checkIdle(true, LIMIT));
    }

    @Test
    public void nobodySignedInNeverLogsOutAndTheClockStartsAtTheSignIn() {
        pass(Duration.ofHours(5));
        assertFalse(tracker.checkIdle(false, LIMIT));
        assertEquals("the clock was restarted while nobody was signed in", Duration.ZERO, tracker.idleFor());
        pass(Duration.ofMinutes(14));
        assertFalse("a person who signs in is not logged out for the hours before", tracker.checkIdle(true, LIMIT));
    }

    @Test
    public void aLimitOfZeroMeansNeverAndANegativeOrMissingLimitDoesNotLogOutEither() {
        pass(Duration.ofDays(2));
        assertFalse(tracker.checkIdle(true, Duration.ZERO));
        pass(Duration.ofDays(2));
        assertFalse(tracker.checkIdle(true, Duration.ofMinutes(-1)));
        assertFalse(tracker.checkIdle(true, null));
    }

    // ---- the wiring, read from the source (it needs windows and a timer to run)

    private static String logoutSource() throws IOException {
        return Files.readString(Path.of("src", "main", "java", "com", "coffeeshop", "coffeeshopmanagement", "util", "IdleLogout.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    public void everyKindOfActivityCountsAndEveryWindowGetsTheFilter() throws IOException {
        String source = logoutSource();
        for (String type : new String[]{"MouseEvent.MOUSE_MOVED", "MouseEvent.MOUSE_PRESSED", "MouseEvent.MOUSE_DRAGGED",
                "KeyEvent.KEY_PRESSED", "ScrollEvent.SCROLL", "TouchEvent.TOUCH_PRESSED"}) {
            assertTrue(type + " must count as activity", source.contains(type));
        }
        assertTrue("windows opened later are watched", source.contains("Window.getWindows().addListener"));
        assertTrue("windows already open are watched", source.contains("Window.getWindows().forEach(IdleLogout::watch)"));
        assertTrue("a window that gets a new scene is watched again", source.contains("window.sceneProperty().addListener"));
        assertTrue("a scene gets the filter only once", source.contains("containsKey(MARK)"));
        assertTrue("the activity is an event FILTER, so a control that consumes the event still counts",
                source.contains("scene.addEventFilter(type, event -> TRACKER.touch())"));
    }

    @Test
    public void theTimerLocksFirstAndSignsOutOnlyWhenItCannotLock() throws IOException {
        String source = logoutSource();
        assertTrue(source.contains("TRACKER.checkIdle(Session.getCurrentUser() != null, limit)"));
        int lock = source.indexOf("TillLock.lockForIdle()");
        int signOut = source.indexOf("SessionGuard.idleLogout(limit)");
        assertTrue(lock > 0 && signOut > lock);
        assertTrue("ending the session shows a dialog, so it runs after the animation pulse", source.contains("Platform.runLater"));
    }

    @Test
    public void aLimitOfZeroIsLoggedAsAWarningAndInstallsNoTimer() throws IOException {
        String source = logoutSource();
        int zero = source.indexOf("if (limit.isZero())");
        assertTrue(zero > 0);
        String branch = source.substring(zero, source.indexOf("install(() -> limit", zero));
        assertTrue(branch.contains("LOGGER.warning"));
        assertTrue(branch.contains("return;"));
    }
}
