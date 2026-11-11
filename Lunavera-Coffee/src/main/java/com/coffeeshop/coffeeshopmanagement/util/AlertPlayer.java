package com.coffeeshop.coffeeshopmanagement.util;

import javafx.scene.media.AudioClip;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plays the sounds of {@link AlertSounds} (TODO 5.47). A sound is an extra: it never blocks the screen and never raises an error, so a
 * till with no speaker, a missing audio device or a runtime without {@code javafx.media} just stays quiet (the problem is logged once).
 *
 * <p>The file is copied to a temporary file the first time it is used and played from there: JavaFX media cannot open the
 * {@code jrt:} address a resource has inside a jlink image. Loading and playing happen on a short-lived background thread, so the
 * screen does not wait even for the first sound.
 */
public final class AlertPlayer {

    private static final Logger LOGGER = Logger.getLogger(AlertPlayer.class.getName());
    private static final Map<AlertSounds.Cue, AudioClip> CLIPS = new EnumMap<>(AlertSounds.Cue.class);
    private static boolean givenUp;

    private AlertPlayer() {
    }

    /** Plays {@code cue} when sounds are on for this computer; does nothing otherwise. Returns at once. */
    public static void play(AlertSounds.Cue cue) {
        if (cue == null || !AlertSounds.isOn()) return;
        start(cue);
    }

    /** Plays {@code cue} whatever the switch says (the "Try it" button of Settings, so the owner can check the speaker). */
    public static void preview(AlertSounds.Cue cue) {
        if (cue != null) start(cue);
    }

    private static void start(AlertSounds.Cue cue) {
        Thread thread = new Thread(() -> playNow(cue), "lunavera-alert-sound");
        thread.setDaemon(true);
        thread.start();
    }

    private static void playNow(AlertSounds.Cue cue) {
        try {
            AudioClip clip;
            synchronized (CLIPS) {
                if (givenUp) return;
                clip = CLIPS.get(cue);
                if (clip == null) {
                    clip = new AudioClip(copyOut(cue.resource()).toUri().toString());
                    CLIPS.put(cue, clip);
                }
            }
            clip.play();
        } catch (Throwable problem) { // a sound must never reach the cashier as an error (also NoClassDefFoundError without javafx.media)
            synchronized (CLIPS) {
                givenUp = true;
            }
            LOGGER.log(Level.WARNING, "The alert sounds are switched off for this run: " + problem, problem);
        }
    }

    private static Path copyOut(String resource) throws Exception {
        try (InputStream in = AlertPlayer.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Sound file missing: " + resource);
            Path file = Files.createTempFile("lunavera-sound-", ".wav");
            file.toFile().deleteOnExit();
            Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return file;
        }
    }
}
