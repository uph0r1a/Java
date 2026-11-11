package com.coffeeshop.coffeeshopmanagement.config;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * TODO 6.53: the packaged program is called "lunavera", not "app": the jlink launcher, the image folder and the zip in {@code pom.xml}, and the
 * names the packaging notes quote. Reads the files from the project folder; it does not run jlink.
 */
public class PackagedNameTest {

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String setting(String pom, String tag) {
        Matcher m = Pattern.compile("<" + tag + ">([^<]*)</" + tag + ">").matcher(pom);
        assertTrue("<" + tag + "> is missing from pom.xml", m.find());
        return m.group(1).trim();
    }

    @Test
    public void launcherImageAndZipAreAllCalledLunavera() throws IOException {
        String pom = read("pom.xml");
        assertEquals("lunavera", setting(pom, "launcher"));
        assertEquals("lunavera", setting(pom, "jlinkImageName"));
        assertEquals("lunavera", setting(pom, "jlinkZipName"));
    }

    @Test
    public void thePackagingNotesQuoteTheSameNames() throws IOException {
        String notes = read("docs/PACKAGING.md");
        assertTrue(notes.contains("target/lunavera.zip"));
        assertTrue(notes.contains("bin/lunavera"));
        assertTrue("the old name must not come back", !notes.contains("target/app") && !notes.contains("bin/app"));
    }
}
