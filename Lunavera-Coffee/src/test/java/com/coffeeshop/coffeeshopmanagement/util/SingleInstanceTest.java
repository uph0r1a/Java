package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SingleInstanceTest {
    private String oldDir;

    @Before
    public void useATempFolder() throws IOException {
        oldDir = System.getProperty(DataPaths.DATA_DIR_PROPERTY);
        Path dir = Files.createTempDirectory("lunavera-lock");
        System.setProperty(DataPaths.DATA_DIR_PROPERTY, dir.toString());
        SingleInstance.release();
    }

    @After
    public void restore() {
        SingleInstance.release();
        if (oldDir == null) System.clearProperty(DataPaths.DATA_DIR_PROPERTY);
        else System.setProperty(DataPaths.DATA_DIR_PROPERTY, oldDir);
    }

    @Test
    public void theLockCanBeTakenAgainAfterItIsReleased() throws IOException {
        assertTrue(SingleInstance.tryAcquire());
        assertTrue("asking again from the same copy is fine", SingleInstance.tryAcquire());
        SingleInstance.release();
        assertTrue(SingleInstance.tryAcquire());
    }

    @Test
    public void aSecondHolderOfTheSameFolderIsRefused() throws Exception {
        assertTrue(SingleInstance.tryAcquire());
        // Another copy of the app is another process; the nearest thing in one JVM is a second channel on the same file.
        try (java.nio.channels.FileChannel other = java.nio.channels.FileChannel.open(
                DataPaths.dataDir().resolve("lunavera.lock"), java.nio.file.StandardOpenOption.WRITE)) {
            boolean refused;
            try {
                refused = other.tryLock() == null;
            } catch (java.nio.channels.OverlappingFileLockException e) {
                refused = true;
            }
            assertTrue(refused);
        }
    }

    @Test
    public void flushOrderedWaitsForEarlierJobs() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean ran = new java.util.concurrent.atomic.AtomicBoolean();
        Async.runOrderedRaw(() -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) { }
            ran.set(true);
        });
        assertTrue(Async.flushOrdered(5000));
        assertTrue("the queued save finished before flush returned", ran.get());
    }

    @Test
    public void passwordMayNotContainTheUsername() {
        assertTrue(ValidationUtil.containsUsername("Admin123x", "admin"));
        assertFalse(ValidationUtil.containsUsername("Zx9!pass", "admin"));
        assertFalse(ValidationUtil.containsUsername("abc123", "ab")); // a 2-letter name is too short to judge
    }
}
