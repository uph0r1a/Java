package com.coffeeshop.coffeeshopmanagement.util;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Only one copy of the app may use a data folder at a time (TODO 3.3, 3.33). A second copy on the same database could
 * overwrite it (a restore) or fight over tables. The first copy keeps a lock on {@code lunavera.lock} in the data
 * folder; the operating system releases it by itself when the app ends or crashes, so there is never a stale lock to clean up.
 */
public final class SingleInstance {

    /** Thrown when another copy already holds the lock. */
    public static final class AlreadyRunningException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public AlreadyRunningException(String message) {
            super(message);
        }
    }

    private static FileChannel channel;
    private static FileLock lock;

    private SingleInstance() {
    }

    /** Takes the lock for the current data folder; returns false when another copy already has it. */
    public static synchronized boolean tryAcquire() throws IOException {
        if (lock != null && lock.isValid()) return true; // this copy already holds it
        Path dir = DataPaths.dataDir();
        Files.createDirectories(dir);
        FileChannel opened = FileChannel.open(dir.resolve("lunavera.lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock acquired = opened.tryLock();
            if (acquired == null) {
                opened.close();
                return false;
            }
            channel = opened;
            lock = acquired;
            return true;
        } catch (OverlappingFileLockException sameJvm) {
            opened.close();
            return false;
        }
    }

    /** Gives the lock back (tests, and a clean shutdown). */
    public static synchronized void release() {
        try {
            if (lock != null) lock.release();
            if (channel != null) channel.close();
        } catch (IOException ignored) {
            // The OS frees it anyway when the process ends.
        } finally {
            lock = null;
            channel = null;
        }
    }
}
