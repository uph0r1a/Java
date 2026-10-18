package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** The last folder of a save dialog is remembered and a vanished one is ignored (TODO 5.17). */
public class RememberedFolderTest {

    private String realDataDir;
    private Path sandbox;

    @Before
    public void useASandbox() throws IOException {
        realDataDir = System.getProperty("lunavera.dataDir");
        sandbox = Files.createTempDirectory("lunavera-folder-test");
        System.setProperty("lunavera.dataDir", sandbox.resolve("data").toString());
    }

    @After
    public void restoreAndClean() throws IOException {
        if (realDataDir == null) System.clearProperty("lunavera.dataDir"); else System.setProperty("lunavera.dataDir", realDataDir);
        try (var walk = Files.walk(sandbox)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    public void startsInTheFallbackUntilSomethingIsRemembered() throws IOException {
        File fallback = Files.createDirectories(sandbox.resolve("home")).toFile();
        assertEquals(fallback, RememberedFolder.startFor("backup", fallback));
        assertNull(RememberedFolder.startFor("backup", new File(sandbox.toFile(), "no-such-folder")));
        assertNull(RememberedFolder.startFor("backup", null));
    }

    @Test
    public void theFolderOfTheChosenFileIsUsedNextTime() throws IOException {
        File fallback = Files.createDirectories(sandbox.resolve("home")).toFile();
        File usb = Files.createDirectories(sandbox.resolve("usb")).toFile();
        RememberedFolder.remember("backup", new File(usb, "lunavera-backup-1.zip"));

        assertEquals(usb.getAbsoluteFile(), RememberedFolder.startFor("backup", fallback).getAbsoluteFile());
        assertEquals("another dialog keeps its own folder", fallback, RememberedFolder.startFor("other", fallback));
    }

    @Test
    public void aRememberedFolderThatIsGoneFallsBack() throws IOException {
        File fallback = Files.createDirectories(sandbox.resolve("home")).toFile();
        File usb = Files.createDirectories(sandbox.resolve("usb")).toFile();
        RememberedFolder.remember("backup", new File(usb, "b.zip"));
        usb.delete();

        assertEquals(fallback, RememberedFolder.startFor("backup", fallback));
    }

    @Test
    public void rememberingNothingIsHarmless() {
        RememberedFolder.remember("backup", null);
        RememberedFolder.remember("backup", new File("/definitely/not/here/x.zip"));
        assertNull(RememberedFolder.startFor("backup", null));
    }
}
