package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * TODO 5.47: the sounds are off until switched on, the choice is kept per computer, every event has a real, short, small sound file, and the
 * low tone really is low and the chime really is high. No JavaFX: {@link AlertPlayer} (which needs it) is not touched.
 */
public class AlertSoundsTest {

    private Path sandbox;
    private String realDataDir;

    @Before
    public void useAnEmptyDataFolder() throws IOException {
        realDataDir = System.getProperty("lunavera.dataDir");
        sandbox = Files.createTempDirectory("alertsounds");
        System.setProperty("lunavera.dataDir", sandbox.resolve("data").toString());
    }

    @After
    public void restore() {
        if (realDataDir == null) System.clearProperty("lunavera.dataDir"); else System.setProperty("lunavera.dataDir", realDataDir);
    }

    @Test
    public void theSoundsAreOffUntilSomebodySwitchesThemOn() {
        assertFalse(AlertSounds.isOn());
    }

    @Test
    public void theChoiceIsRememberedAndOnlyReportedWhenItChanges() {
        assertFalse("already off: nothing changed", AlertSounds.choose(false));
        assertTrue(AlertSounds.choose(true));
        assertTrue(AlertSounds.isOn());
        assertFalse("already on", AlertSounds.choose(true));
        assertTrue(AlertSounds.choose(false));
        assertFalse(AlertSounds.isOn());
    }

    @Test
    public void everyEventHasASoundFileThatIsShortSmallAndPlayable() throws Exception {
        for (AlertSounds.Cue cue : AlertSounds.Cue.values()) {
            byte[] bytes = bytesOf(cue.resource());
            assertTrue(cue + ": a tiny file, under 40 KB (it is " + bytes.length + ")", bytes.length > 1000 && bytes.length < 40_000);
            try (AudioInputStream audio = AudioSystem.getAudioInputStream(new java.io.ByteArrayInputStream(bytes))) {
                AudioFormat format = audio.getFormat();
                assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding());
                assertEquals("mono", 1, format.getChannels());
                double seconds = audio.getFrameLength() / (double) format.getFrameRate();
                assertTrue(cue + ": short, not a long noise (" + seconds + " s)", seconds >= 0.2 && seconds <= 1.0);
            }
        }
    }

    @Test
    public void theLowToneIsLowAndTheChimeIsHigh() throws Exception {
        double low = pitch(AlertSounds.Cue.CODE_NOT_FOUND.resource());
        double chime = pitch(AlertSounds.Cue.BOOKING_DUE.resource());
        assertTrue("low tone near 200 Hz, was " + low, low > 100 && low < 400);
        assertTrue("chime well above it, was " + chime, chime > 700);
        assertEquals("a refused payment sounds like a code not found", AlertSounds.Cue.CODE_NOT_FOUND.resource(), AlertSounds.Cue.PAYMENT_REFUSED.resource());
        assertNotEquals("the booking chime is a different sound", AlertSounds.Cue.CODE_NOT_FOUND.resource(), AlertSounds.Cue.BOOKING_DUE.resource());
    }

    @Test
    public void aSoundIsNeverLoudEnoughToClip() throws Exception {
        for (AlertSounds.Cue cue : AlertSounds.Cue.values()) {
            short[] samples = samples(cue.resource());
            int peak = 0;
            for (short s : samples) peak = Math.max(peak, Math.abs(s));
            assertTrue(cue + " peaks at " + peak, peak > 3000 && peak < 22_000);
        }
    }

    private static byte[] bytesOf(String resource) throws IOException {
        try (InputStream in = AlertSoundsTest.class.getResourceAsStream(resource)) {
            assertNotNull("bundled sound " + resource, in);
            return in.readAllBytes();
        }
    }

    private static short[] samples(String resource) throws Exception {
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(new java.io.ByteArrayInputStream(bytesOf(resource))))) {
            byte[] raw = audio.readAllBytes();
            short[] out = new short[raw.length / 2];
            for (int i = 0; i < out.length; i++) out[i] = (short) ((raw[2 * i] & 0xff) | (raw[2 * i + 1] << 8));
            return out;
        }
    }

    /** The pitch of the first quarter of the sound, from its zero crossings (good enough to tell a low tone from a chime). */
    private static double pitch(String resource) throws Exception {
        short[] s = samples(resource);
        int n = s.length / 4, crossings = 0;
        for (int i = 1; i < n; i++) if ((s[i - 1] < 0) != (s[i] < 0)) crossings++;
        return crossings / 2.0 / (n / 16000.0);
    }
}
