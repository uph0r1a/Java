package com.coffeeshop.coffeeshopmanagement.util;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** TODO 5.41: the program knows its version and says it on the screen, in the log and in the crash report. */
public class AppVersionTest {

    private static Properties props(String version, String built) {
        Properties p = new Properties();
        if (version != null) p.setProperty("version", version);
        if (built != null) p.setProperty("built", built);
        return p;
    }

    private static String read(String first, String... more) throws IOException {
        return Files.readString(Path.of(first, more));
    }

    @Test
    public void aFilledInVersionIsUsedAndAnythingElseIsDev() {
        assertEquals("1.4.2", AppVersion.versionOf(props("1.4.2", null)));
        assertEquals("1.0-SNAPSHOT", AppVersion.versionOf(props(" 1.0-SNAPSHOT ", null)));
        assertEquals(AppVersion.DEV, AppVersion.versionOf(props("${project.version}", null)));
        assertEquals(AppVersion.DEV, AppVersion.versionOf(props("", null)));
        assertEquals(AppVersion.DEV, AppVersion.versionOf(props(null, null)));
    }

    @Test
    public void theBuildDateIsTheDatePartOfTheTimestampOrNothing() {
        assertEquals("2026-11-06", AppVersion.builtOf(props(null, "2026-11-06T00:00:00Z")));
        assertEquals("2026-11-06", AppVersion.builtOf(props(null, "2026-11-06T09:30:00+07:00")));
        assertEquals("", AppVersion.builtOf(props(null, "${project.build.outputTimestamp}")));
        assertEquals("", AppVersion.builtOf(props(null, "yesterday")));
        assertEquals("", AppVersion.builtOf(props(null, null)));
    }

    @Test
    public void theVersionIsNeverEmptyAndTheLineForTheLogNamesTheProgramAndTheVersion() {
        assertFalse(AppVersion.version().isEmpty());
        assertNotNull(AppVersion.built());
        String line = AppVersion.logLine();
        assertTrue(line, line.startsWith("Lunavera Coffee " + AppVersion.version()));
        assertTrue(line, line.contains("Java " + System.getProperty("java.version")));
        assertFalse("one line", line.contains("\n"));
    }

    @Test
    public void theScreenTextShowsTheVersionInBothLanguages() {
        String before = I18n.current();
        try {
            for (String language : I18n.LANGUAGES) {
                I18n.setLanguage(language);
                String label = AppVersion.label();
                assertTrue(language + ": " + label, label.contains(AppVersion.version()));
                assertFalse(language + ": a message key or a hole was left: " + label, label.contains("{") || label.contains("app.version"));
            }
        } finally {
            I18n.setLanguage(before);
        }
    }

    @Test
    public void whenMavenBuiltItTheVersionIsTheOneInThePom() throws IOException {
        Matcher m = Pattern.compile("<artifactId>coffee-shop-management</artifactId>\\s*<version>([^<]+)</version>").matcher(read("pom.xml"));
        assertTrue("the project's own version is in the pom", m.find() && m.group(1) != null);
        String pomVersion = m.group(1);
        // Under Maven the resource is filtered and the two agree; run from the sources the placeholder is still there and it says "dev".
        if (!AppVersion.DEV.equals(AppVersion.version())) {
            assertEquals(pomVersion, AppVersion.version());
        }
    }

    @Test
    public void onlyTheVersionFileIsFilteredByMaven() throws IOException {
        String pom = read("pom.xml");
        int resources = pom.indexOf("<resources>");
        String block = pom.substring(resources, pom.indexOf("</resources>", resources));
        assertTrue(block.contains("<include>app-version.properties</include>"));
        assertTrue("every other resource keeps its ${...} and %", block.contains("<exclude>app-version.properties</exclude>"));
        assertEquals("exactly one filtered directory", 1, block.split("<filtering>true</filtering>", -1).length - 1);
        String file = read("src", "main", "resources", "app-version.properties");
        assertTrue(file.contains("version=${project.version}"));
        assertTrue(file.contains("built=${project.build.outputTimestamp}"));
        assertTrue(pom.contains("<addDefaultImplementationEntries>true</addDefaultImplementationEntries>"));
    }

    @Test
    public void theLogTheCrashReportAndTheScreensUseIt() throws IOException {
        String java = "src/main/java/com/coffeeshop/coffeeshopmanagement/";
        assertTrue(read(java + "util/AppLogging.java").contains("AppVersion.logLine()"));
        assertTrue(read(java + "util/CrashHandler.java").contains("AppVersion.logLine()"));
        assertTrue(read(java + "controller/LoginController.java").contains("AppVersion.label()"));
        assertTrue(read(java + "controller/SettingsDialog.java").contains("AppVersion.label()"));
    }
}
